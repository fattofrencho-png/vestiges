package dev.vestiges.settlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import dev.vestiges.server.MapNet;
import dev.vestiges.server.Permissions;
import dev.vestiges.settlement.SettlementData.Marker;
import dev.vestiges.settlement.SettlementData.Settlement;
import dev.vestiges.settlement.SettlementData.Stats;
import dev.vestiges.settlement.SettlementData.World;
import dev.vestiges.waystone.WaystoneRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Settlements and markers (M5, approved). Bells found settlements whose radius grows with renown; Banners name areas; Ward Lanterns make
 * no-spawn pockets; Gold Blocks keep 3x3 chunks loaded. Bells and Ward Lanterns set up over minutes without fighting. Inside a settlement,
 * players who are not members can open doors but cannot break, place, or take from player-placed containers. Renown is counted every 10
 * minutes while the area is loaded.
 */
public final class SettlementService {
	public static final AttachmentType<World> WORLD = AttachmentRegistry.create(Vestiges.id("settlements"), b -> b.persistent(World.CODEC));

	private static final int RENOWN_INTERVAL = 10 * 60 * 20;
	private static final int SETUP_RANGE = 128;
	private static final int BELL_SPACING = 2000;
	private static final int MAX_MEMBERS = 20;

	private record Pending(ServerLevel level, BlockPos pos, UUID owner, MarkerItems.Info info, int ticks) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, String> AREAS = new HashMap<>();
	private static final Map<UUID, Integer> HERO = new HashMap<>();
	/** Spawn-proof circles per dimension: x, z, radius² (rebuilt on every save). */
	private static volatile Map<String, List<long[]>> pacified = Map.of();

	private SettlementService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.SETTLEMENTS);
	}

	public static World world(MinecraftServer server) {
		World w = server.overworld().getAttached(WORLD);
		return w != null ? w : World.EMPTY;
	}

	public static void save(MinecraftServer server, World w) {
		server.overworld().setAttached(WORLD, w);
		rebuildPacified(w);
	}

	public static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
	}

	static boolean inside(BlockPos center, int radius, BlockPos pos) {
		long dx = pos.getX() - center.getX();
		long dz = pos.getZ() - center.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	public static boolean inCombat(ServerPlayer p) {
		return WaystoneRules.inCombat(p.tickCount, p.getLastHurtByMobTimestamp(), p.getLastHurtByMob() != null, p.getLastHurtMobTimestamp());
	}

	public static void register() {
		UseBlockCallback.EVENT.register(SettlementService::onUseBlock);
		UseItemCallback.EVENT.register(SettlementService::onUseItem);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> onEntity(player, level, entity, true));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> onEntity(player, level, entity, false));
		PlayerBlockBreakEvents.BEFORE.register(SettlementService::beforeBreak);
		ServerTickEvents.END_SERVER_TICK.register(SettlementService::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> rebuildPacified(world(server)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			AREAS.clear();
			HERO.clear();
			pacified = Map.of();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sendMap(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AREAS.remove(handler.player.getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(dev.vestiges.net.SettlementPayloads.Action.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> action(ctx.player(), payload.op(), payload.id(), payload.arg())));
	}

	// ---- protection ------------------------------------------------------------------------------------------------------

	/** The standing settlement covering this spot that this player may not change (null = allowed). */
	public static Settlement lockedFor(Player player, Level level, BlockPos pos) {
		if (!enabled() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
			return null;
		}
		if (sp.getAbilities().instabuild && Permissions.isOperator(sp)) {
			return null;
		}
		String d = dim(sl);
		for (Settlement s : world(sl.getServer()).settlements()) {
			if (s.standing() && s.dimension().equals(d) && inside(s.bellPos(), s.radius(), pos) && !s.member(sp.getUUID())) {
				return s;
			}
		}
		return null;
	}

	private static void denied(ServerPlayer p, Settlement s) {
		p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("This is " + s.displayName() + ". Only its members can do that."));
	}

	private static boolean doorLike(BlockState state) {
		Block b = state.getBlock();
		return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		BlockPos pos = hit.getBlockPos();
		Settlement locked = lockedFor(sp, sl, pos);
		if (locked != null) {
			BlockState state = sl.getBlockState(pos);
			BlockEntity be = sl.getBlockEntity(pos);
			boolean container = (be instanceof Container || be instanceof LecternBlockEntity) && PlacedBlocks.isPlaced(sl, pos);
			boolean placing = stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL)
				|| stack.is(Items.FIRE_CHARGE);
			if (container || placing && !(doorLike(state) && !sp.isSecondaryUseActive())) {
				denied(sp, locked);
				return refuse(sp);
			}
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND && enabled() && !sp.isSecondaryUseActive()) {
			World w = world(sl.getServer());
			String d = dim(sl);
			for (Settlement s : w.settlements()) {
				if (s.active() && s.name().isEmpty() && s.owner().equals(sp.getUUID()) && s.dimension().equals(d) && s.bell() == pos.asLong()) {
					prompt(sp, "settlement", s.id(), "Name your settlement");
				}
			}
			for (Marker m : w.markers()) {
				if (m.kind() != null && m.kind().banner() && m.name().isEmpty() && m.owner().equals(sp.getUUID()) && m.dimension().equals(d)
					&& m.pos() == pos.asLong()) {
					prompt(sp, "banner", String.valueOf(m.id()), "Name this Banner's area");
					return InteractionResult.SUCCESS;
				}
			}
		}
		MarkerItems.Info info = MarkerItems.read(stack);
		if (info != null && enabled()) {
			BlockPos at = new BlockPlaceContext(new UseOnContext(player, hand, hit)).getClickedPos().immutable();
			String error = canPlace(sp, sl, at, info);
			if (error != null) {
				MapNet.message(sp, error, true);
				return refuse(sp);
			}
			PENDING.add(new Pending(sl, at, sp.getUUID(), info, 1));
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
			Settlement locked = lockedFor(player, level, player.blockPosition());
			if (locked != null) {
				denied((ServerPlayer) player, locked);
				return refuse((ServerPlayer) player);
			}
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult onEntity(Player player, Level level, Entity entity, boolean use) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (use && entity instanceof AbstractVillager) {
			dev.vestiges.feats.FeatService.metVillager(sp);
		}
		if (entity instanceof HangingEntity || entity instanceof ArmorStand || entity instanceof AbstractMinecartContainer) {
			Settlement locked = lockedFor(sp, level, entity.blockPosition());
			if (locked != null) {
				denied(sp, locked);
				return refuse(sp);
			}
		}
		return InteractionResult.PASS;
	}

	/**
	 * Refuses a use the client already acted out (it placed the block and took the item from its hand before the server said no): sending the
	 * inventory again puts the item back on the player's screen.
	 */
	public static InteractionResult refuse(ServerPlayer p) {
		p.containerMenu.sendAllDataToRemote();
		return InteractionResult.FAIL;
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
			return true;
		}
		Settlement locked = lockedFor(sp, sl, pos);
		if (locked != null) {
			denied(sp, locked);
			return false;
		}
		MinecraftServer server = sl.getServer();
		World w = world(server);
		String d = dim(sl);
		for (Settlement s : w.settlements()) {
			if (s.hasBell() && s.dimension().equals(d) && s.bell() == pos.asLong()) {
				if (!s.owner().equals(sp.getUUID()) && !Permissions.isOperator(sp)) {
					MapNet.message(sp, "This Bell belongs to someone else.", true);
					return false;
				}
				takeMarkerBlock(sp, sl, pos, state, be, MarkerType.BELL, s.owner(), s.id(), "");
				save(server, w.put(s.withBell(SettlementData.NO_POS, s.dimension())));
				message(server, s.owner(), s.displayName() + " is inactive until its Bell is placed again.");
				refreshMaps(server);
				return false;
			}
		}
		for (Marker m : w.markers()) {
			if (m.dimension().equals(d) && m.pos() == pos.asLong()) {
				if (!m.owner().equals(sp.getUUID()) && !Permissions.isOperator(sp)) {
					MapNet.message(sp, "This marker belongs to someone else.", true);
					return false;
				}
				if (m.kind() == MarkerType.GOLD_BLOCK) {
					forceChunks(sl, pos, false);
				}
				takeMarkerBlock(sp, sl, pos, state, be, m.kind(), m.owner(), "", m.name());
				save(server, w.removeMarker(m.id()));
				refreshMaps(server);
				return false;
			}
		}
		return dev.vestiges.tree.BuildingNodes.beforeBreak(sp, sl, pos, state, be);
	}

	/** Removes a marker block; the marker item (keeping banner patterns) drops for its owner only. */
	private static void takeMarkerBlock(ServerPlayer breaker, ServerLevel level, BlockPos pos, BlockState state, BlockEntity be, MarkerType type,
										UUID owner, String settlement, String area) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, be);
		ItemStack base = drops.isEmpty() ? new ItemStack(type.item) : drops.get(0);
		ItemStack item = MarkerItems.mark(base, type, settlement, area);
		PlacedBlocks.take(level, pos);
		level.removeBlock(pos, false);
		// The owner gets it straight back; anyone else (an operator) leaves it on the ground for the owner only, and it never despawns.
		if (breaker.getUUID().equals(owner) && breaker.getInventory().add(item)) {
			return;
		}
		ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
		drop.setTarget(owner);
		drop.setUnlimitedLifetime();
		level.addFreshEntity(drop);
	}

	/** Markers whose block is gone (explosions, commands) are dropped from the records, checking loaded chunks only. */
	private static void prune(MinecraftServer server) {
		World w = world(server);
		World next = w;
		for (Settlement s : w.settlements()) {
			ServerLevel level = s.hasBell() ? level(server, s.dimension()) : null;
			if (level != null && level.isLoaded(s.bellPos()) && !level.getBlockState(s.bellPos()).is(net.minecraft.world.level.block.Blocks.BELL)) {
				next = next.put(s.withBell(SettlementData.NO_POS, s.dimension()));
				message(server, s.owner(), s.displayName() + " lost its Bell and is inactive until a Bell is set up again.");
			}
		}
		for (Marker m : w.markers()) {
			ServerLevel level = level(server, m.dimension());
			MarkerType k = m.kind();
			if (k == null || level == null || !level.isLoaded(m.blockPos())) {
				continue;
			}
			BlockState state = level.getBlockState(m.blockPos());
			boolean there = k.banner() ? state.is(net.minecraft.tags.BlockTags.BANNERS) : state.is(Block.byItem(k.item));
			if (!there) {
				if (k == MarkerType.GOLD_BLOCK) {
					forceChunks(level, m.blockPos(), false);
				}
				next = next.removeMarker(m.id());
			}
		}
		if (next != w) {
			save(server, next);
			refreshMaps(server);
		}
	}

	// ---- placing markers -----------------------------------------------------------------------------------------------

	static String canPlace(ServerPlayer p, ServerLevel level, BlockPos pos, MarkerItems.Info info) {
		if (inCombat(p)) {
			return "You can't place markers during a fight.";
		}
		if (level.dimension() == Level.END) {
			return "Markers can't be placed in the End.";
		}
		// Villages are fine (settlements grow around them, user 2026-10-29); every other structure is not.
		if (level.structureManager().getStructureWithPieceAt(pos, h -> !h.is(net.minecraft.tags.StructureTags.VILLAGE)).isValid()) {
			return "Markers can't be placed inside structures (villages are fine).";
		}
		World w = world(level.getServer());
		UUID me = p.getUUID();
		String d = dim(level);
		MarkerType type = info.type();
		if (type == MarkerType.BELL) {
			Settlement moving = info.settlement().isEmpty() ? null : w.settlement(info.settlement());
			if (moving != null && !moving.owner().equals(me)) {
				return "This Bell belongs to someone else.";
			}
			if (moving == null && w.settlements().stream().filter(s -> s.owner().equals(me)).count() >= MarkerType.BELL.limit) {
				return "You already have " + MarkerType.BELL.limit + " settlements.";
			}
			for (Settlement s : w.settlements()) {
				if (s != moving && s.hasBell() && s.dimension().equals(d) && inside(s.bellPos(), BELL_SPACING - 1, pos)) {
					return "Too close to " + s.displayName() + ": Bells must be 2,000 blocks apart.";
				}
			}
			return null;
		}
		long mine = w.markers().stream().filter(m -> m.owner().equals(me) && m.kind() != null && m.kind().family().equals(type.family())).count();
		if (mine >= type.limit) {
			return "You already have " + mine + " of these placed (the most is " + type.limit + ").";
		}
		if (type == MarkerType.GOLD_BLOCK) {
			if (!claimedBy(p, level, pos)) {
				return "A Gold Block only works inside your settlement or ward.";
			}
			for (Marker m : w.markers()) {
				if (m.kind() == MarkerType.GOLD_BLOCK && m.dimension().equals(d) && Math.abs((m.blockPos().getX() >> 4) - (pos.getX() >> 4)) < 3
					&& Math.abs((m.blockPos().getZ() >> 4) - (pos.getZ() >> 4)) < 3) {
					return "It would overlap another Gold Block's chunks.";
				}
			}
			return null;
		}
		for (Marker m : w.markers()) {
			MarkerType k = m.kind();
			if (k != null && k.family().equals(type.family()) && m.dimension().equals(d) && inside(m.blockPos(), k.radius + type.radius - 1, pos)) {
				return "It would overlap another " + (type.banner() ? "Banner" : "Ward Lantern") + "'s area.";
			}
		}
		return null;
	}

	private static void confirm(MinecraftServer server, Pending p) {
		BlockState state = p.level().getBlockState(p.pos());
		MarkerType type = p.info().type();
		boolean there = type.banner() ? state.is(net.minecraft.tags.BlockTags.BANNERS) : state.is(Block.byItem(type.item));
		if (!there) {
			return;
		}
		World w = world(server);
		String d = dim(p.level());
		ServerPlayer owner = server.getPlayerList().getPlayer(p.owner());
		if (type == MarkerType.BELL) {
			Settlement s = p.info().settlement().isEmpty() ? null : w.settlement(p.info().settlement());
			if (s == null) {
				s = new Settlement(UUID.randomUUID().toString(), "", p.owner(), List.of(), d, p.pos().asLong(), SettlementData.SETUP,
					MarkerType.BELL.setupTicks, Stats.START, SettlementData.Shared.EMPTY);
			} else {
				s = s.withBell(p.pos().asLong(), d);
			}
			save(server, w.put(s));
			message(server, p.owner(), "Bell placed. Stay within 128 blocks without fighting for 10 minutes to set up your settlement.");
		} else {
			Marker m = new Marker(w.nextId(), type.id, p.owner(), d, p.pos().asLong(), p.info().area(), type.setupTicks);
			save(server, w.add(m));
			if (type == MarkerType.GOLD_BLOCK) {
				forceChunks(p.level(), p.pos(), true);
				message(server, p.owner(), "Gold Block placed: its chunk and the 8 around it stay loaded.");
			} else if (type.ward()) {
				message(server, p.owner(), "Ward Lantern placed. Stay nearby without fighting for 5 minutes to set it up.");
			} else if (m.name().isEmpty() && owner != null) {
				prompt(owner, "banner", String.valueOf(m.id()), "Name this Banner's area");
			}
		}
		refreshMaps(server);
	}

	private static void forceChunks(ServerLevel level, BlockPos pos, boolean on) {
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced(cx + dx, cz + dz, on);
			}
		}
	}

	// ---- claimed areas and no-spawn areas -------------------------------------------------------------------------------

	/** Inside a settlement this player belongs to, or one of their own Ward Lanterns' areas. */
	public static boolean claimedBy(Player player, Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl)) {
			return false;
		}
		World w = world(sl.getServer());
		String d = dim(sl);
		for (Settlement s : w.settlements()) {
			if (s.standing() && s.dimension().equals(d) && s.member(player.getUUID()) && inside(s.bellPos(), s.radius(), pos)) {
				return true;
			}
		}
		for (Marker m : w.markers()) {
			if (m.kind() != null && m.kind().ward() && m.owner().equals(player.getUUID()) && m.dimension().equals(d)
				&& inside(m.blockPos(), m.kind().radius, pos)) {
				return true;
			}
		}
		return false;
	}

	private static void rebuildPacified(World w) {
		Map<String, List<long[]>> map = new HashMap<>();
		for (Settlement s : w.settlements()) {
			if (s.active()) {
				map.computeIfAbsent(s.dimension(), k -> new ArrayList<>())
					.add(new long[] {s.bellPos().getX(), s.bellPos().getZ(), (long) s.radius() * s.radius()});
			}
		}
		for (Marker m : w.markers()) {
			if (m.kind() != null && m.kind().ward() && m.active()) {
				map.computeIfAbsent(m.dimension(), k -> new ArrayList<>())
					.add(new long[] {m.blockPos().getX(), m.blockPos().getZ(), (long) m.kind().radius * m.kind().radius});
			}
		}
		pacified = map;
	}

	/** True where no monster may spawn naturally: active settlements and Ward Lanterns, at full height. */
	public static boolean pacified(Level level, BlockPos pos) {
		if (!enabled()) {
			return false;
		}
		List<long[]> circles = pacified.get(dim(level));
		if (circles == null) {
			return false;
		}
		for (long[] c : circles) {
			long dx = pos.getX() - c[0];
			long dz = pos.getZ() - c[1];
			if (dx * dx + dz * dz <= c[2]) {
				return true;
			}
		}
		return false;
	}

	// ---- every second: setting up, entry titles, raids; every 10 minutes: renown ------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			List<Pending> later = new ArrayList<>();
			for (Pending p : List.copyOf(PENDING)) {
				if (p.ticks() > 0) {
					later.add(new Pending(p.level(), p.pos(), p.owner(), p.info(), p.ticks() - 1));
				} else {
					confirm(server, p);
				}
			}
			PENDING.clear();
			PENDING.addAll(later);
		}
		if (!enabled()) {
			return;
		}
		int t = server.getTickCount();
		if (t % 20 == 0) {
			setup(server);
			areas(server);
			raids(server);
		}
		if (t % 1200 == 600) {
			prune(server);
		}
		if (t % RENOWN_INTERVAL == 0) {
			for (Settlement s : world(server).settlements()) {
				if (s.active()) {
					renown(server, s.id());
				}
			}
		}
	}

	private static void setup(MinecraftServer server) {
		World w = world(server);
		World next = w;
		for (Settlement s : w.settlements()) {
			if (s.hasBell() && SettlementData.SETUP.equals(s.state())) {
				int left = countDown(server, s.owner(), s.dimension(), s.bellPos(), s.setupLeft(), MarkerType.BELL, s.displayName());
				if (left <= 0) {
					next = next.put(s.withState(SettlementData.ACTIVE, 0));
					ServerPlayer owner = server.getPlayerList().getPlayer(s.owner());
					ServerLevel level = level(server, s.dimension());
					if (level != null) {
						level.playSound(null, s.bellPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
					}
					message(server, s.owner(), "Your settlement is set up: monsters no longer spawn inside it.");
					if (owner != null && s.name().isEmpty()) {
						prompt(owner, "settlement", s.id(), "Name your settlement");
					}
				} else if (left != s.setupLeft()) {
					next = next.put(s.withState(SettlementData.SETUP, left));
				}
			}
		}
		for (Marker m : w.markers()) {
			if (m.kind() != null && m.kind().ward() && !m.active()) {
				int left = countDown(server, m.owner(), m.dimension(), m.blockPos(), m.setupLeft(), m.kind(), m.kind().label);
				if (left != m.setupLeft()) {
					next = next.putMarker(m.withSetupLeft(Math.max(0, left)));
					if (left <= 0) {
						message(server, m.owner(), "Your Ward Lantern is set up: monsters no longer spawn around it.");
					}
				}
			}
		}
		if (next != w) {
			save(server, next);
			if (next.settlements().stream().anyMatch(s -> s.active() && w.settlement(s.id()) != null && !w.settlement(s.id()).active())) {
				for (Settlement s : next.settlements()) {
					Settlement before = w.settlement(s.id());
					if (s.active() && before != null && !before.active()) {
						renown(server, s.id());
					}
				}
				refreshMaps(server);
			}
		}
	}

	/** Counts a setup timer down while its owner is near and not fighting (a fight restarts it); shows the owner a bar. */
	private static int countDown(MinecraftServer server, UUID ownerId, String dimension, BlockPos pos, int left, MarkerType type, String label) {
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (owner == null || !dim(owner.level()).equals(dimension) || !inside(pos, SETUP_RANGE, owner.blockPosition())) {
			return left;
		}
		int now = inCombat(owner) ? type.setupTicks : left - 20;
		JsonObject o = new JsonObject();
		o.addProperty("label", "Setting up " + label);
		o.addProperty("left", Math.max(0, now));
		o.addProperty("total", type.setupTicks);
		o.addProperty("fight", now == type.setupTicks && left != type.setupTicks);
		LevelService.notice(owner, "setup", o);
		return now;
	}

	private static void areas(MinecraftServer server) {
		World w = world(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String d = dim(p.level());
			BlockPos pos = p.blockPosition();
			Settlement in = null;
			for (Settlement s : w.settlements()) {
				if (s.standing() && s.dimension().equals(d) && inside(s.bellPos(), s.radius(), pos)) {
					in = s;
				}
			}
			Marker banner = null;
			for (Marker m : w.markers()) {
				if (m.kind() != null && m.kind().banner() && m.dimension().equals(d) && inside(m.blockPos(), m.kind().radius, pos)) {
					banner = m;
				}
			}
			String key = (in == null ? "" : in.id()) + "|" + (banner == null ? "" : banner.id());
			String last = AREAS.put(p.getUUID(), key);
			if (key.equals(last) || "|".equals(key)) {
				continue;
			}
			JsonObject o = new JsonObject();
			if (banner != null && (last == null || !last.endsWith("|" + banner.id()))) {
				o.addProperty("title", banner.name().isEmpty() ? "Unnamed area" : banner.name());
				o.addProperty("subtitle", in == null ? "" : in.displayName());
			} else if (in != null && (last == null || !last.startsWith(in.id() + "|"))) {
				o.addProperty("title", in.displayName());
				String owner = in.member(p.getUUID()) ? "" : "  ·  " + ownerName(server, in.owner()) + "'s settlement";
				o.addProperty("subtitle", Renown.name(in.stats().level()) + owner);
			} else {
				continue;
			}
			LevelService.notice(p, "area", o);
		}
	}

	/** A raid won (vanilla gives the Hero of the Village effect): feats, and a deed for the settlement it was won in. */
	private static void raids(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			MobEffectInstance hero = p.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			Integer last = HERO.get(p.getUUID());
			if (hero == null) {
				HERO.remove(p.getUUID());
				continue;
			}
			HERO.put(p.getUUID(), hero.getDuration());
			if (last == null || hero.getDuration() > last + 40) {
				int omen = hero.getAmplifier() + 1;
				dev.vestiges.feats.FeatService.raidWon(p, omen);
				World w = world(server);
				String d = dim(p.level());
				for (Settlement s : w.settlements()) {
					if (s.standing() && s.dimension().equals(d) && inside(s.bellPos(), s.radius() + 32, p.blockPosition())) {
						Stats st = s.stats();
						save(server, w.put(s.withStats(new Stats(st.size(), st.variety(), st.homes(), st.people(), st.deeds() + 30 * omen, st.level(),
							st.bestLevel(), st.belowTicks(), st.bestPoints()))));
						message(server, s.owner(), s.displayName() + " defended a raid: +" + 30 * omen + " renown.");
						break;
					}
				}
			}
		}
	}

	/** Counts a settlement's renown (only when nearly all its area is loaded) and moves its level. */
	public static void renown(MinecraftServer server, String id) {
		World w = world(server);
		Settlement s = w.settlement(id);
		if (s == null || !s.hasBell()) {
			return;
		}
		ServerLevel level = level(server, s.dimension());
		if (level == null || !level.isLoaded(s.bellPos())) {
			return;
		}
		int r = s.radius();
		BlockPos c = s.bellPos();
		Map<String, Integer> byType = new HashMap<>();
		int[] beds = {0};
		int chunks = 0;
		int loaded = 0;
		for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
			for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
				chunks++;
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				loaded++;
				PlacedBlocks.forEach(chunk, packed -> {
					BlockPos bp = BlockPos.of(packed);
					if (!inside(c, r, bp)) {
						return;
					}
					BlockState st = chunk.getBlockState(bp);
					if (st.isAir()) {
						return;
					}
					byType.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString(), 1, Integer::sum);
					if (placedBed(level, bp, st)) {
						beds[0]++;
					}
				});
			}
		}
		if (loaded < chunks * 0.9) {
			return;
		}
		Stats old = s.stats();
		Stats counted = new Stats(Renown.size(byType), Renown.variety(byType), Renown.homes(beds[0]), Renown.people(0, 0), old.deeds(), old.level(),
			old.bestLevel(), old.belowTicks(), old.bestPoints());
		Stats next = Renown.advance(counted, RENOWN_INTERVAL);
		ServerPlayer owner = server.getPlayerList().getPlayer(s.owner());
		int best = next.bestPoints();
		if (counted.total() > old.bestPoints() && owner != null) {
			LevelService.addXp(owner, 5L * (counted.total() - old.bestPoints()), "building");
			best = counted.total();
		}
		next = new Stats(next.size(), next.variety(), next.homes(), next.people(), next.deeds(), next.level(), next.bestLevel(), next.belowTicks(), best);
		save(server, w.put(s.withStats(next)));
		if (next.level() > old.level()) {
			message(server, s.owner(), s.displayName() + " is now a " + Renown.name(next.level()) + "!");
			refreshMaps(server);
		} else if (next.level() < old.level()) {
			message(server, s.owner(), s.displayName() + " dropped to " + Renown.name(next.level()) + ".");
			refreshMaps(server);
		} else if (next.belowTicks() == RENOWN_INTERVAL) {
			message(server, s.owner(), s.displayName() + " has fallen below " + Renown.name(old.level()) + ": it drops in 3 days unless renown comes back.");
		}
	}

	// ---- naming, members, and what the client sees -----------------------------------------------------------------------

	private static void prompt(ServerPlayer p, String kind, String id, String title) {
		JsonObject o = new JsonObject();
		o.addProperty("kind", kind);
		o.addProperty("id", id);
		o.addProperty("title", title);
		LevelService.notice(p, "name_prompt", o);
	}

	static String cleanName(String raw) {
		String n = raw == null ? "" : raw.replace("§", "").trim();
		return n.length() > 32 ? n.substring(0, 32).trim() : n;
	}

	private static void action(ServerPlayer p, String op, String id, String arg) {
		if (!enabled()) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		World w = world(server);
		switch (op) {
			case "name_settlement" -> {
				Settlement s = w.settlement(id);
				String n = cleanName(arg);
				if (s != null && s.owner().equals(p.getUUID()) && s.name().isEmpty() && !n.isEmpty()) {
					save(server, w.put(s.withName(n)));
					MapNet.message(p, "Welcome to " + n + ".", false);
				}
			}
			case "name_banner" -> {
				Marker m = parseMarker(w, id);
				String n = cleanName(arg);
				if (m != null && m.owner().equals(p.getUUID()) && m.name().isEmpty() && !n.isEmpty()) {
					save(server, w.putMarker(m.withName(n)));
					AREAS.remove(p.getUUID());
				}
			}
			case "add_member" -> {
				Settlement s = w.settlement(id);
				ServerPlayer other = server.getPlayerList().getPlayerByName(cleanName(arg));
				if (s == null || !s.owner().equals(p.getUUID())) {
					return;
				}
				if (other == null) {
					MapNet.message(p, "No online player called " + cleanName(arg) + ".", true);
				} else if (!s.member(other.getUUID()) && s.members().size() < MAX_MEMBERS) {
					List<UUID> m = new ArrayList<>(s.members());
					m.add(other.getUUID());
					save(server, w.put(s.withMembers(m)));
					MapNet.message(other, p.getName().getString() + " added you to " + s.displayName() + ".", false);
					dev.vestiges.codex.CodexService.mergeShared(other);
					sendMap(other);
				}
			}
			case "remove_member" -> {
				Settlement s = w.settlement(id);
				if (s != null && s.owner().equals(p.getUUID())) {
					List<UUID> m = new ArrayList<>(s.members());
					m.removeIf(u -> u.toString().equals(arg));
					save(server, w.put(s.withMembers(m)));
				}
			}
			default -> {
			}
		}
		dev.vestiges.server.HubNet.send(p);
		refreshMaps(server);
	}

	private static Marker parseMarker(World w, String id) {
		try {
			return w.marker(Integer.parseInt(id));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void message(MinecraftServer server, UUID player, String text) {
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p != null) {
			MapNet.message(p, text, false);
		}
	}

	static String ownerName(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null ? p.getName().getString() : "another player";
	}

	/** The Settlements tab: every settlement this player owns or belongs to, and their markers against the limits. */
	public static JsonObject snapshot(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		World w = world(server);
		JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled());
		root.addProperty("nextCount", Math.max(1, (RENOWN_INTERVAL - server.getTickCount() % RENOWN_INTERVAL + 1199) / 1200));
		JsonArray list = new JsonArray();
		for (Settlement s : w.settlements()) {
			if (!s.member(p.getUUID())) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("id", s.id());
			o.addProperty("name", s.displayName());
			o.addProperty("owner", ownerName(server, s.owner()));
			o.addProperty("mine", s.owner().equals(p.getUUID()));
			o.addProperty("state", s.state());
			o.addProperty("setupLeft", s.setupLeft());
			o.addProperty("level", s.stats().level());
			o.addProperty("levelName", Renown.name(s.stats().level()));
			o.addProperty("points", s.stats().total());
			o.addProperty("next", Renown.next(s.stats().level()));
			o.addProperty("radius", s.radius());
			o.addProperty("size", s.stats().size());
			o.addProperty("variety", s.stats().variety());
			o.addProperty("homes", s.stats().homes());
			o.addProperty("people", s.stats().people());
			o.addProperty("deeds", s.stats().deeds());
			o.addProperty("dropIn", s.stats().belowTicks() > 0 ? Math.max(0, Renown.DROP_AFTER_TICKS - s.stats().belowTicks()) : 0);
			if (s.hasBell()) {
				o.addProperty("x", s.bellPos().getX());
				o.addProperty("z", s.bellPos().getZ());
			}
			JsonArray members = new JsonArray();
			for (UUID m : s.members()) {
				JsonObject mo = new JsonObject();
				mo.addProperty("id", m.toString());
				mo.addProperty("name", ownerName(server, m));
				members.add(mo);
			}
			o.add("members", members);
			list.add(o);
		}
		root.add("settlements", list);
		JsonObject markers = new JsonObject();
		for (String family : List.of("banner", "ward", "gold_block")) {
			long n = w.markers().stream().filter(m -> m.owner().equals(p.getUUID()) && m.kind() != null && m.kind().family().equals(family)).count();
			markers.addProperty(family, n);
		}
		markers.addProperty("bell", w.settlements().stream().filter(s -> s.owner().equals(p.getUUID())).count());
		root.add("markers", markers);
		return root;
	}

	/** Map circles for this player: their settlements (as owner or member) and their own markers. */
	public static void sendMap(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		World w = world(server);
		JsonArray circles = new JsonArray();
		for (Settlement s : w.settlements()) {
			if (s.standing() && s.member(p.getUUID())) {
				circles.add(circle("settlement", s.displayName(), s.dimension(), s.bellPos(), s.radius(), s.active()));
			}
		}
		for (Marker m : w.markers()) {
			if (m.owner().equals(p.getUUID()) && m.kind() != null) {
				String kind = m.kind().banner() ? "banner" : m.kind().ward() ? "ward" : "gold";
				circles.add(circle(kind, m.name().isEmpty() ? m.kind().label : m.name(), m.dimension(), m.blockPos(), m.kind().radius, m.active()));
			}
		}
		JsonObject root = new JsonObject();
		root.add("circles", circles);
		MapNet.send(p, "settlements", root);
	}

	private static JsonObject circle(String kind, String name, String dimension, BlockPos pos, int radius, boolean active) {
		JsonObject o = new JsonObject();
		o.addProperty("kind", kind);
		o.addProperty("name", name);
		o.addProperty("dimension", dimension);
		o.addProperty("x", pos.getX());
		o.addProperty("z", pos.getZ());
		o.addProperty("radius", radius);
		o.addProperty("active", active);
		return o;
	}

	public static void refreshMaps(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			sendMap(p);
		}
	}

	// ---- what other systems ask -------------------------------------------------------------------------------------

	/** Settlements this player owns or belongs to. */
	public static List<Settlement> of(ServerPlayer p) {
		List<Settlement> out = new ArrayList<>();
		for (Settlement s : world(p.level().getServer()).settlements()) {
			if (s.member(p.getUUID())) {
				out.add(s);
			}
		}
		return out;
	}

	public static int bestOwnedLevel(ServerPlayer p) {
		int best = 0;
		for (Settlement s : world(p.level().getServer()).settlements()) {
			if (s.owner().equals(p.getUUID())) {
				best = Math.max(best, s.stats().bestLevel());
			}
		}
		return best;
	}

	public static int ownedAtLevel(ServerPlayer p, int level) {
		int n = 0;
		for (Settlement s : world(p.level().getServer()).settlements()) {
			if (s.owner().equals(p.getUUID()) && s.stats().bestLevel() >= level) {
				n++;
			}
		}
		return n;
	}

	/** Renown levels gained over all owned settlements (Local Landmark repeats once per level). */
	public static int levelsGained(ServerPlayer p) {
		int n = 0;
		for (Settlement s : world(p.level().getServer()).settlements()) {
			if (s.owner().equals(p.getUUID())) {
				n += s.stats().bestLevel() - 1;
			}
		}
		return n;
	}

	/**
	 * One count per placed bed: its foot half, or its head half when the foot was not remembered (beds placed before both halves were
	 * remembered only had their foot marked).
	 */
	private static boolean placedBed(ServerLevel level, BlockPos pos, BlockState st) {
		if (!(st.getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock)) {
			return false;
		}
		if (st.getValue(net.minecraft.world.level.block.AbstractBedBlock.PART) == BedPart.FOOT) {
			return true;
		}
		return !PlacedBlocks.isPlaced(level, pos.relative(net.minecraft.world.level.block.AbstractBedBlock.getConnectedDirection(st)));
	}

	/** Player-placed beds within {@code radius} blocks of the player (Founder: "5 beds in one area"). */
	public static int placedBedsNear(ServerPlayer p, int radius) {
		ServerLevel level = p.level();
		BlockPos c = p.blockPosition();
		int[] beds = {0};
		for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) {
			for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk != null) {
					PlacedBlocks.forEach(chunk, packed -> {
						BlockPos bp = BlockPos.of(packed);
						if (bp.distSqr(c) <= (double) radius * radius) {
							BlockState st = chunk.getBlockState(bp);
							if (placedBed(level, bp, st)) {
								beds[0]++;
							}
						}
					});
				}
			}
		}
		return beds[0];
	}

	/** Codex sharing: a member's block or biome progress is kept on each of their settlements and given to the other members. */
	public static void shareCodex(ServerPlayer p, String entry, int count, List<Long> spots) {
		MinecraftServer server = p.level().getServer();
		World w = world(server);
		World next = w;
		for (Settlement s : w.settlements()) {
			if (!s.member(p.getUUID())) {
				continue;
			}
			Map<String, Integer> counts = new HashMap<>(s.shared().counts());
			Map<String, List<Long>> spotMap = new HashMap<>(s.shared().spots());
			if (spots == null) {
				counts.merge(entry, count, Math::max);
			} else {
				List<Long> merged = new ArrayList<>(spotMap.getOrDefault(entry, List.of()));
				for (long sp : spots) {
					if (!merged.contains(sp) && merged.size() < dev.vestiges.codex.CodexRules.BIOME_CHUNKS) {
						merged.add(sp);
					}
				}
				spotMap.put(entry, List.copyOf(merged));
			}
			next = next.put(s.withShared(new SettlementData.Shared(Map.copyOf(counts), Map.copyOf(spotMap))));
			List<UUID> everyone = new ArrayList<>(s.members());
			everyone.add(s.owner());
			for (UUID u : everyone) {
				ServerPlayer other = u.equals(p.getUUID()) ? null : server.getPlayerList().getPlayer(u);
				if (other != null) {
					dev.vestiges.codex.CodexService.receiveShared(other, entry, count, spots);
				}
			}
		}
		if (next != w) {
			server.overworld().setAttached(WORLD, next);
		}
	}
}
