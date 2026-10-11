package dev.vestiges.grave;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.level.LevelService;
import dev.vestiges.server.Permissions;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gravestones. On death (after totems, instead of dropping the inventory) the items go into the player's own head, placed where they died.
 * Only the owner (or an operator) can collect it, by right-clicking or breaking it. After {@code graveDays} Minecraft days it decays into a
 * skeleton skull that keeps only the most valuable item, and its map marker disappears.
 */
public final class GraveService {
	public static final AttachmentType<GraveList> GRAVES = AttachmentRegistry.create(Vestiges.id("graves"),
		builder -> builder.persistent(GraveList.CODEC));
	private static final long DAY = 24000L;

	private GraveService() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			return collect(sp, (ServerLevel) level, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !isGraveBlock(state)) {
				return true;
			}
			Grave g = list(sp.level().getServer()).at(dim(level), pos);
			if (g == null) {
				return true;
			}
			collect(sp, (ServerLevel) level, pos);
			return false;
		});
		ServerTickEvents.END_SERVER_TICK.register(GraveService::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sendAll(handler.player)));
	}

	private static GraveList list(MinecraftServer server) {
		GraveList l = server.overworld().getAttached(GRAVES);
		return l != null ? l : GraveList.EMPTY;
	}

	private static void save(MinecraftServer server, GraveList list) {
		server.overworld().setAttached(GRAVES, list);
	}

	private static boolean isGraveBlock(BlockState s) {
		return s.is(Blocks.PLAYER_HEAD) || s.is(Blocks.SKELETON_SKULL);
	}

	// ---- creating ---------------------------------------------------------------------------------------------------

	/** Called from the death hook, just before vanilla would drop the inventory. Returns whether the items went into a grave. */
	public static boolean createGrave(ServerPlayer player) {
		if (!LevelService.enabled()) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		BlockPos spot = findSpot(level, player.blockPosition());
		if (spot == null) {
			return false;
		}
		Inventory inv = player.getInventory();
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.removeItemNoUpdate(i);
			if (!s.isEmpty()) {
				items.add(s);
			}
		}
		items.addAll(dev.vestiges.inventory.InventoryHooks.slots(player).drain());
		if (items.isEmpty()) {
			return true;
		}
		int rotation = Math.floorMod(Math.round(player.getYRot() * 16.0f / 360.0f), 16);
		level.setBlock(spot, Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, rotation), 3);
		BlockEntity be = level.getBlockEntity(spot);
		if (be != null) {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
			be.applyComponentsFromItemStack(head);
			be.setChanged();
		}
		MinecraftServer server = level.getServer();
		GraveList list = list(server);
		Grave g = new Grave(list.nextId(), player.getUUID(), player.getName().getString(), dim(level), spot.immutable(),
			server.overworld().getGameTime(), false, false, List.copyOf(items));
		save(server, list.add(g));
		player.sendSystemMessage(Component.literal("Your items rest in a grave at " + spot.getX() + ", " + spot.getY() + ", " + spot.getZ()
			+ ". Collect it within " + ConfigStore.server().numbers.graveDays + " days.").withStyle(ChatFormatting.GOLD));
		LevelService.notice(player, "grave", graveJson(g));
		return true;
	}

	/** The death spot, or the first free space a few blocks above it; inside the world's height. */
	private static BlockPos findSpot(ServerLevel level, BlockPos death) {
		int y = Math.max(level.getMinY() + 1, Math.min(level.getMaxY() - 1, death.getY()));
		BlockPos base = new BlockPos(death.getX(), y, death.getZ());
		for (int dy = 0; dy <= 12; dy++) {
			BlockPos p = base.above(dy);
			if (p.getY() >= level.getMaxY()) {
				break;
			}
			BlockState s = level.getBlockState(p);
			if (s.canBeReplaced() && level.getBlockEntity(p) == null) {
				return p;
			}
		}
		return null;
	}

	// ---- collecting -------------------------------------------------------------------------------------------------

	private static boolean collect(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!isGraveBlock(level.getBlockState(pos))) {
			return false;
		}
		MinecraftServer server = level.getServer();
		GraveList list = list(server);
		Grave g = list.at(dim(level), pos);
		if (g == null) {
			return false;
		}
		if (!g.owner().equals(player.getUUID()) && !Permissions.isOperator(player)) {
			player.sendSystemMessage(Component.literal("This is " + g.ownerName() + "'s grave. Only they can open it.").withStyle(ChatFormatting.RED));
			return true;
		}
		level.removeBlock(pos, false);
		save(server, list.remove(g.id()));
		for (ItemStack s : g.items()) {
			ItemStack copy = s.copy();
			if (!player.getInventory().add(copy) && !copy.isEmpty()) {
				ItemEntity drop = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), copy);
				drop.setTarget(player.getUUID());
				level.addFreshEntity(drop);
			}
		}
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1f, 0.8f);
		player.sendSystemMessage(Component.literal("You collected your grave.").withStyle(ChatFormatting.GREEN));
		removedNotice(server, g);
		return true;
	}

	// ---- decay ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		GraveList list = list(server);
		if (list.graves().isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		long life = Math.max(1, ConfigStore.server().numbers.graveDays) * DAY;
		GraveList out = list;
		for (Grave g : list.graves()) {
			long age = now - g.created();
			if (!g.decayed() && age >= life) {
				ItemStack keep = ItemValue.mostValuable(g.items());
				Grave decayed = g.decayTo(keep.isEmpty() ? List.of() : List.of(keep));
				out = out.replace(decayed);
				ServerPlayer owner = server.getPlayerList().getPlayer(g.owner());
				if (owner != null) {
					owner.sendSystemMessage(Component.literal("Your grave crumbled into a skull. Only your " + keep.getHoverName().getString()
						+ " remains there.").withStyle(ChatFormatting.RED));
				}
				removedNotice(server, g);
			} else if (!g.decayed() && !g.warned() && age >= life - DAY) {
				out = out.replace(g.warnedNow());
				ServerPlayer owner = server.getPlayerList().getPlayer(g.owner());
				if (owner != null) {
					owner.sendSystemMessage(Component.literal("Your grave at " + g.pos().getX() + ", " + g.pos().getY() + ", " + g.pos().getZ()
						+ " will crumble in 1 day.").withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		// Turn decayed graves into skulls once their chunk is loaded.
		for (Grave g : out.graves()) {
			if (!g.decayed()) {
				continue;
			}
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(g.dimension())));
			if (level != null && level.isLoaded(g.pos()) && level.getBlockState(g.pos()).is(Blocks.PLAYER_HEAD)) {
				int rot = level.getBlockState(g.pos()).getValue(SkullBlock.ROTATION);
				level.setBlock(g.pos(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rot), 3);
			}
		}
		if (out != list) {
			save(server, out);
		}
	}

	// ---- client markers ---------------------------------------------------------------------------------------------

	private static JsonObject graveJson(Grave g) {
		JsonObject o = new JsonObject();
		o.addProperty("id", g.id());
		o.addProperty("dimension", g.dimension());
		o.addProperty("x", g.pos().getX());
		o.addProperty("y", g.pos().getY());
		o.addProperty("z", g.pos().getZ());
		o.addProperty("expires", g.created() + Math.max(1, ConfigStore.server().numbers.graveDays) * DAY);
		return o;
	}

	private static void sendAll(ServerPlayer player) {
		JsonArray arr = new JsonArray();
		for (Grave g : list(player.level().getServer()).graves()) {
			if (g.owner().equals(player.getUUID()) && !g.decayed()) {
				arr.add(graveJson(g));
			}
		}
		JsonObject root = new JsonObject();
		root.add("graves", arr);
		LevelService.notice(player, "graves", root);
	}

	private static void removedNotice(MinecraftServer server, Grave g) {
		ServerPlayer owner = server.getPlayerList().getPlayer(g.owner());
		if (owner != null) {
			JsonObject o = new JsonObject();
			o.addProperty("id", g.id());
			LevelService.notice(owner, "grave_removed", o);
		}
	}

	private static String dim(Level level) {
		return level.dimension().identifier().toString();
	}
}
