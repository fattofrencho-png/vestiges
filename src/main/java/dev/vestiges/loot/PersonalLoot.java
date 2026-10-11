package dev.vestiges.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player chests (M3b, approved): every naturally generated chest, barrel and chest minecart gives each player their own loot the first time
 * they open it, rolled for their own loot level. Each copy keeps what its player leaves in it. The container's loot table moves into
 * {@link #SOURCE} when it loads, so vanilla never rolls it (no hopper or breaking exploits); breaking a loot chest needs sneaking and drops only
 * the chest. {@link #SOURCE} is synced so each client can sparkle the loot chests its player has not opened yet.
 */
public final class PersonalLoot {
	/** The loot table and who already opened their copy. */
	public record LootSource(Identifier table, List<UUID> opened) {
		public static final Codec<LootSource> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("table").forGetter(LootSource::table),
			UUIDUtil.CODEC.listOf().optionalFieldOf("opened", List.of()).forGetter(LootSource::opened)
		).apply(i, LootSource::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, LootSource> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, LootSource::table,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), LootSource::opened,
			LootSource::new);

		LootSource with(UUID player) {
			List<UUID> list = new ArrayList<>(opened);
			list.add(player);
			return new LootSource(table, List.copyOf(list));
		}
	}

	/** Each player's copy (27 slots), by player UUID. */
	public record LootCopies(Map<String, List<ItemStack>> byPlayer) {
		public static final LootCopies EMPTY = new LootCopies(Map.of());
		public static final Codec<LootCopies> CODEC = Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf())
			.xmap(LootCopies::new, LootCopies::byPlayer);
	}

	public static final AttachmentType<LootSource> SOURCE = AttachmentRegistry.create(Vestiges.id("loot_source"),
		b -> b.persistent(LootSource.CODEC).syncWith(LootSource.STREAM_CODEC, AttachmentSyncPredicate.all()));
	public static final AttachmentType<LootCopies> COPIES = AttachmentRegistry.create(Vestiges.id("loot_copies"),
		b -> b.persistent(LootCopies.CODEC));

	/** Containers that loaded with a loot table; converted at the end of the tick, once their chunk is fully loaded. */
	private static final List<Object> PENDING = new ArrayList<>();
	/** How many players look at their copy of each loot chest (the lid stays open while anyone does). */
	private static final Map<BlockEntity, Integer> VIEWERS = new java.util.IdentityHashMap<>();

	private PersonalLoot() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.PER_PLAYER_LOOT);
	}

	public static void register() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			if (lootTable(be) != null && be.getAttached(SOURCE) == null) {
				PENDING.add(be);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (lootTable(entity) != null && entity.getAttached(SOURCE) == null) {
				PENDING.add(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			List<Object> todo = new ArrayList<>(PENDING);
			PENDING.clear();
			if (enabled()) {
				todo.forEach(PersonalLoot::convert);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			VIEWERS.clear();
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (be == null || player.isCreative() || !enabled() || (be.getAttached(SOURCE) == null && lootTable(be) == null)) {
				return true;
			}
			if (!player.isShiftKeyDown()) {
				player.sendOverlayMessage(Component.literal("Sneak to break a loot chest (its loot stays behind)"));
				return false;
			}
			if (be instanceof RandomizableContainerBlockEntity r) {
				r.setLootTable(null);
			}
			return true;
		});
	}

	/** Natural chests and barrels, and chest minecarts, that still carry a vanilla loot table. */
	private static ResourceKey<LootTable> lootTable(Object holder) {
		if ((holder instanceof ChestBlockEntity || holder instanceof BarrelBlockEntity) && holder instanceof RandomizableContainerBlockEntity r) {
			return r.getLootTable();
		}
		if (holder instanceof AbstractMinecartContainer cart) {
			return cart.getContainerLootTable();
		}
		return null;
	}

	private static void convert(Object holder) {
		ResourceKey<LootTable> table = lootTable(holder);
		if (table == null || !(holder instanceof AttachmentTarget target) || target.getAttached(SOURCE) != null) {
			return;
		}
		if (holder instanceof RandomizableContainerBlockEntity r) {
			if (r.isRemoved()) {
				return;
			}
			target.setAttached(SOURCE, new LootSource(table.identifier(), List.of()));
			r.setLootTable(null);
			r.setLootTableSeed(0);
			r.setChanged();
		} else if (holder instanceof AbstractMinecartContainer cart && !cart.isRemoved()) {
			target.setAttached(SOURCE, new LootSource(table.identifier(), List.of()));
			cart.setContainerLootTable(null);
			cart.setContainerLootTableSeed(0);
		}
	}

	/** System switched off: give the container its vanilla loot table back, so vanilla rolls it as usual. */
	private static void restore(AttachmentTarget target, LootSource src) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, src.table());
		if (target instanceof RandomizableContainerBlockEntity r) {
			r.setLootTable(key);
			r.setChanged();
		} else if (target instanceof AbstractMinecartContainer cart) {
			cart.setContainerLootTable(key);
		}
		target.removeAttached(SOURCE);
	}

	/** Called when a player opens a loot container: their own copy, or null to let vanilla handle it. */
	public static AbstractContainerMenu open(Object holder, int id, Inventory inventory, Player player) {
		if (!(player instanceof ServerPlayer sp) || player.isSpectator() || !(holder instanceof AttachmentTarget target)) {
			return null;
		}
		LootSource src = target.getAttached(SOURCE);
		if (src == null) {
			if (!enabled() || lootTable(holder) == null) {
				return null;
			}
			convert(holder);
			src = target.getAttached(SOURCE);
			if (src == null) {
				return null;
			}
		}
		if (!enabled()) {
			restore(target, src);
			return null;
		}
		return ChestMenu.threeRows(id, inventory, copyFor(holder, target, src, sp));
	}

	private static Personal copyFor(Object holder, AttachmentTarget target, LootSource src, ServerPlayer player) {
		String key = player.getUUID().toString();
		List<ItemStack> items = target.getAttachedOrElse(COPIES, LootCopies.EMPTY).byPlayer().get(key);
		Personal copy = new Personal(holder, target, key);
		if (items != null) {
			for (int i = 0; i < Math.min(items.size(), copy.getContainerSize()); i++) {
				copy.getItems().set(i, items.get(i).copy());
			}
			copy.loading = false;
			return copy;
		}
		ServerLevel level = player.level();
		ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, src.table());
		LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position(holder))
			.withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
		table.fill(copy, params, level.getRandom().nextLong());
		CriteriaTriggers.GENERATE_LOOT.trigger(player, tableKey);
		copy.loading = false;
		copy.save();
		target.setAttached(SOURCE, src.with(player.getUUID()));
		return copy;
	}

	private static Vec3 position(Object holder) {
		if (holder instanceof BlockEntity be) {
			return Vec3.atCenterOf(be.getBlockPos());
		}
		return ((AbstractMinecartContainer) holder).position();
	}

	/**
	 * Opens or closes the lid (chest) or the front (barrel) with its sound. Done here rather than through the container's own opener count,
	 * which only counts menus showing the real inventory and would shut the lid while a copy is open.
	 */
	private static void viewer(BlockEntity be, int delta) {
		int before = VIEWERS.getOrDefault(be, 0);
		int after = Math.max(0, before + delta);
		if (after == 0) {
			VIEWERS.remove(be);
		} else {
			VIEWERS.put(be, after);
		}
		if ((before == 0) == (after == 0) || !(be.getLevel() instanceof ServerLevel level) || be.isRemoved()) {
			return;
		}
		boolean open = after > 0;
		net.minecraft.core.BlockPos pos = be.getBlockPos();
		net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
		if (be instanceof ChestBlockEntity) {
			level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
			level.playSound(null, pos, open ? net.minecraft.sounds.SoundEvents.CHEST_OPEN : net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
				net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
		} else if (be instanceof BarrelBlockEntity && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
			level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, open), 3);
			level.playSound(null, pos, open ? net.minecraft.sounds.SoundEvents.BARREL_OPEN : net.minecraft.sounds.SoundEvents.BARREL_CLOSE,
				net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
		}
	}

	/** One player's copy: saved on every change; opening and closing it moves the chest lid like the real one. */
	private static final class Personal extends SimpleContainer {
		private final Object holder;
		private final AttachmentTarget target;
		private final String key;
		boolean loading = true;

		Personal(Object holder, AttachmentTarget target, String key) {
			super(27);
			this.holder = holder;
			this.target = target;
			this.key = key;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!loading) {
				save();
			}
		}

		void save() {
			Map<String, List<ItemStack>> all = new HashMap<>(target.getAttachedOrElse(COPIES, LootCopies.EMPTY).byPlayer());
			List<ItemStack> items = new ArrayList<>(getContainerSize());
			for (int i = 0; i < getContainerSize(); i++) {
				items.add(getItem(i).copy());
			}
			all.put(key, items);
			target.setAttached(COPIES, new LootCopies(all));
			if (holder instanceof BlockEntity be) {
				be.setChanged();
			}
		}

		@Override
		public void startOpen(ContainerUser user) {
			if (holder instanceof BlockEntity be) {
				viewer(be, 1);
			}
		}

		@Override
		public void stopOpen(ContainerUser user) {
			if (holder instanceof BlockEntity be) {
				viewer(be, -1);
			}
		}

		@Override
		public boolean stillValid(Player player) {
			if (holder instanceof BlockEntity be) {
				return Container.stillValidBlockEntity(be, player);
			}
			AbstractMinecartContainer cart = (AbstractMinecartContainer) holder;
			return cart.isAlive() && player.distanceToSqr(cart) < 64;
		}
	}
}
