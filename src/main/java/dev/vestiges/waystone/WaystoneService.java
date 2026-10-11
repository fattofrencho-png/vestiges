package dev.vestiges.waystone;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.server.MapNet;
import dev.vestiges.server.Permissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Waystones: place one (from an earned Waystone item), right-click it to open the terminal, travel to your other waystones (or ones shared with
 * you) for emeralds. Temporary until later milestones: the item comes from {@code /vestiges give waystone} (the First Waystone feat in M5),
 * any spot is allowed (claimed areas only, M5), and trips are paid from inventory emeralds (the Bank pays from M2).
 */
public final class WaystoneService {
	private record Pending(ServerLevel level, BlockPos pos, UUID owner, int ticksLeft) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private WaystoneService() {
	}

	public static void register() {
		WaystoneStore.init();
		UseBlockCallback.EVENT.register(WaystoneService::onUse);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.isClientSide() || !state.is(Blocks.LODESTONE) || !(player instanceof ServerPlayer sp)) {
				return true;
			}
			return onBreak(sp, (ServerLevel) level, pos);
		});
		ServerTickEvents.END_SERVER_TICK.register(WaystoneService::tickPending);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendMarkers(handler.player));
	}

	private static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.MAP);
	}

	// ---- placing, using, breaking ------------------------------------------------------------------------------------

	private static InteractionResult onUse(net.minecraft.world.entity.player.Player player, Level level, InteractionHand hand,
										   net.minecraft.world.phys.BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !enabled()) {
			return InteractionResult.PASS;
		}
		ServerLevel sl = (ServerLevel) level;
		ItemStack stack = player.getItemInHand(hand);
		BlockPos clicked = hit.getBlockPos();
		if (level.getBlockState(clicked).is(Blocks.LODESTONE) && !player.isSecondaryUseActive()) {
			Waystone w = WaystoneStore.get(sl.getServer()).at(dimId(sl), clicked);
			if (w != null && !stack.is(Items.COMPASS)) {
				if (hand == InteractionHand.MAIN_HAND) {
					if (inCombat(sp)) {
						MapNet.message(sp, "You can't use a waystone during a fight.", true);
					} else {
						openTerminal(sp, w);
					}
				}
				return InteractionResult.SUCCESS;
			}
		}
		if (WaystoneItems.isWaystone(stack)) {
			BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, hand, hit));
			PENDING.add(new Pending(sl, ctx.getClickedPos().immutable(), sp.getUUID(), 2));
		}
		return InteractionResult.PASS;
	}

	/** Placement is confirmed a tick later, once vanilla has really put the lodestone down. */
	private static void tickPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> waiting = new ArrayList<>();
		for (Pending p : List.copyOf(PENDING)) {
			if (p.ticksLeft() > 0) {
				waiting.add(new Pending(p.level(), p.pos(), p.owner(), p.ticksLeft() - 1));
			} else {
				confirmPlaced(server, p);
			}
		}
		PENDING.clear();
		PENDING.addAll(waiting);
	}

	private static void confirmPlaced(MinecraftServer server, Pending p) {
		if (!p.level().getBlockState(p.pos()).is(Blocks.LODESTONE)) {
			return;
		}
		WaystoneList list = WaystoneStore.get(server);
		if (list.at(dimId(p.level()), p.pos()) != null) {
			return;
		}
		int id = list.nextId();
		Waystone w = new Waystone(id, "Waystone " + id, dimId(p.level()), p.pos(), p.owner(), List.of());
		WaystoneStore.set(server, list.add(w));
		ServerPlayer owner = server.getPlayerList().getPlayer(p.owner());
		if (owner != null) {
			MapNet.message(owner, "Waystone placed. Right-click it to open the terminal.", false);
			sendMarkers(owner);
		}
	}

	/** Returns whether vanilla may break the block. Waystones are owner-bound: the owner gets the Waystone item back. */
	private static boolean onBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		WaystoneList list = WaystoneStore.get(server);
		Waystone w = list.at(dimId(level), pos);
		if (w == null) {
			return true;
		}
		boolean owner = w.owner().equals(player.getUUID());
		if (!owner && !Permissions.isOperator(player)) {
			MapNet.message(player, "This waystone belongs to someone else.", true);
			return false;
		}
		level.removeBlock(pos, false);
		WaystoneStore.set(server, list.remove(w.id()));
		if (!player.getAbilities().instabuild) {
			ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WaystoneItems.create(1));
			drop.setTarget(w.owner());
			level.addFreshEntity(drop);
		}
		refreshMarkers(server, w);
		return false;
	}

	// ---- terminal and travel -----------------------------------------------------------------------------------------

	static void openTerminal(ServerPlayer player, Waystone from) {
		MinecraftServer server = player.level().getServer();
		WaystoneList list = prune(server);
		from = list.byId(from.id());
		if (from == null) {
			return;
		}
		int per = ConfigStore.server().numbers.waystoneBlocksPerEmerald;
		JsonObject root = new JsonObject();
		root.addProperty("from", from.id());
		root.addProperty("fromName", from.name());
		root.addProperty("fromMine", from.owner().equals(player.getUUID()));
		root.addProperty("inCombat", inCombat(player));
		root.addProperty("emeralds", (int) Math.min(Integer.MAX_VALUE, dev.vestiges.server.Emeralds.countWithBank(player)));
		root.addProperty("free", player.getAbilities().instabuild);
		JsonArray arr = new JsonArray();
		for (Waystone w : list.waystones()) {
			if (w.id() == from.id() || !w.usableBy(player.getUUID())) {
				continue;
			}
			JsonObject o = toJson(server, w, player);
			boolean sameDim = w.dimension().equals(from.dimension());
			o.addProperty("sameDimension", sameDim);
			if (sameDim) {
				double dist = Math.sqrt(from.pos().distSqr(w.pos()));
				o.addProperty("distance", (int) dist);
				o.addProperty("cost", tripCost(player, dist, per));
			}
			arr.add(o);
		}
		root.add("waystones", arr);
		MapNet.send(player, "waystone_terminal", root);
	}

	public static void travel(ServerPlayer player, int fromId, int toId) {
		MinecraftServer server = player.level().getServer();
		WaystoneList list = prune(server);
		Waystone from = list.byId(fromId);
		Waystone to = list.byId(toId);
		if (from == null || to == null || !to.usableBy(player.getUUID())) {
			MapNet.message(player, "That waystone is gone.", true);
			return;
		}
		if (!dimId(player.level()).equals(from.dimension()) || player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(from.pos())) > WaystoneRules.USE_RANGE) {
			MapNet.message(player, "Trips start at a waystone: stand next to it.", true);
			return;
		}
		if (!to.dimension().equals(from.dimension())) {
			MapNet.message(player, "Waystones only connect within one dimension.", true);
			return;
		}
		if (inCombat(player)) {
			MapNet.message(player, "You can't travel during a fight.", true);
			return;
		}
		int cost = tripCost(player, Math.sqrt(from.pos().distSqr(to.pos())), ConfigStore.server().numbers.waystoneBlocksPerEmerald);
		boolean free = player.getAbilities().instabuild;
		if (!free && dev.vestiges.server.Emeralds.countWithBank(player) < cost) {
			MapNet.message(player, "You need " + cost + " emeralds for this trip.", true);
			return;
		}
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(to.dimension())));
		if (level == null) {
			return;
		}
		if (!free) {
			dev.vestiges.server.Emeralds.payWithBank(player, cost);
		}
		ServerLevel fromLevel = (ServerLevel) player.level();
		fromLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
		BlockPos p = to.pos();
		player.teleportTo(level, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
		level.playSound(null, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
		MapNet.message(player, "Arrived at " + to.name() + (free ? "." : " (" + cost + " emerald" + (cost == 1 ? "" : "s") + ")."), false);
	}

	public static void rename(ServerPlayer player, int id, String name) {
		MinecraftServer server = player.level().getServer();
		WaystoneList list = WaystoneStore.get(server);
		Waystone w = list.byId(id);
		if (w == null || !w.owner().equals(player.getUUID())) {
			MapNet.message(player, "Only the owner can rename a waystone.", true);
			return;
		}
		Waystone renamed = w.withName(WaystoneRules.cleanName(name, w.name()));
		WaystoneStore.set(server, list.replace(renamed));
		refreshMarkers(server, renamed);
		openTerminal(player, renamed);
	}

	public static void share(ServerPlayer player, int id, String targetName) {
		MinecraftServer server = player.level().getServer();
		WaystoneList list = WaystoneStore.get(server);
		Waystone w = list.byId(id);
		if (w == null || !w.owner().equals(player.getUUID())) {
			MapNet.message(player, "Only the owner can share a waystone.", true);
			return;
		}
		ServerPlayer target = server.getPlayerList().getPlayerByName(targetName.strip());
		if (target == null || target == player) {
			MapNet.message(player, targetName.isBlank() ? "Type a player name." : targetName + " is not online.", true);
			return;
		}
		Waystone shared = w.sharedWith(target.getUUID());
		WaystoneStore.set(server, list.replace(shared));
		MapNet.message(player, "Shared " + w.name() + " with " + target.getName().getString() + ".", false);
		MapNet.message(target, player.getName().getString() + " shared the waystone " + w.name() + " with you.", false);
		refreshMarkers(server, shared);
	}

	// ---- markers for the map ---------------------------------------------------------------------------------------

	public static void sendMarkers(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		JsonArray arr = new JsonArray();
		for (Waystone w : WaystoneStore.get(server).waystones()) {
			if (w.usableBy(player.getUUID())) {
				arr.add(toJson(server, w, player));
			}
		}
		JsonObject root = new JsonObject();
		root.add("waystones", arr);
		MapNet.send(player, "waystones", root);
	}

	private static void refreshMarkers(MinecraftServer server, Waystone w) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (w.usableBy(p.getUUID())) {
				sendMarkers(p);
			}
		}
	}

	private static JsonObject toJson(MinecraftServer server, Waystone w, ServerPlayer viewer) {
		JsonObject o = new JsonObject();
		o.addProperty("id", w.id());
		o.addProperty("name", w.name());
		o.addProperty("dimension", w.dimension());
		o.addProperty("x", w.pos().getX());
		o.addProperty("y", w.pos().getY());
		o.addProperty("z", w.pos().getZ());
		o.addProperty("mine", w.owner().equals(viewer.getUUID()));
		ServerPlayer owner = server.getPlayerList().getPlayer(w.owner());
		o.addProperty("owner", owner != null ? owner.getName().getString() : "another player");
		return o;
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	/** Drops records whose lodestone is gone (explosions, commands), checking only loaded chunks. */
	private static WaystoneList prune(MinecraftServer server) {
		WaystoneList list = WaystoneStore.get(server);
		WaystoneList out = list;
		for (Waystone w : list.waystones()) {
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(w.dimension())));
			if (level != null && level.isLoaded(w.pos()) && !level.getBlockState(w.pos()).is(Blocks.LODESTONE)) {
				out = out.remove(w.id());
			}
		}
		if (out != list) {
			WaystoneStore.set(server, out);
		}
		return out;
	}

	private static boolean inCombat(ServerPlayer p) {
		return WaystoneRules.inCombat(p.tickCount, p.getLastHurtByMobTimestamp(), p.getLastHurtByMob() != null, p.getLastHurtMobTimestamp());
	}

	/** The trip cost after the player's passive tree discount ("waystone trips cost X% less"), never below 1. */
	static int tripCost(ServerPlayer player, double dist, int per) {
		int base = WaystoneRules.cost(dist, per);
		double mult = dev.vestiges.tree.StatEngine.get(player).mult("waystone_cost");
		return Math.max(1, (int) Math.round(base * Math.max(0.0, mult)));
	}

	private static int countEmeralds(ServerPlayer p) {
		Inventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void takeEmeralds(ServerPlayer p, int amount) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				int take = Math.min(amount, s.getCount());
				inv.removeItem(i, take);
				amount -= take;
			}
		}
	}

	private static String dimId(Level level) {
		return level.dimension().identifier().toString();
	}
}
