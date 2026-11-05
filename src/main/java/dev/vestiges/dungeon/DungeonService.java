package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.feats.FeatService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Portal dungeons. A key used on a portal frame takes everyone within 8 blocks into the dungeon world, to the start room of a fresh plot; stepping
 * into the exit frame there (or running out of lives, or finishing) sends a player back to the portal. Runs are saved on the overworld, so a
 * restart does not strand anyone. The whole system sits behind the Dungeons switch: off, a frame does nothing and the key stays in the pocket.
 */
public final class DungeonService {
	public static final ResourceKey<Level> DUNGEON = ResourceKey.create(Registries.DIMENSION, Vestiges.id("dungeon"));
	public static final AttachmentType<DungeonRuns> RUNS = AttachmentRegistry.create(Vestiges.id("dungeon_runs"),
		builder -> builder.persistent(DungeonRuns.CODEC));
	/** Everyone this close to the portal goes in together. */
	public static final double PARTY_RADIUS = 8.0;

	private DungeonService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.DUNGEONS);
	}

	public static void register() {
		DungeonKeys.register();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (DungeonKeys.read(held) == null) {
				return InteractionResult.PASS;
			}
			return useKey((ServerLevel) level, sp, held, hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection())) ? InteractionResult.SUCCESS
				: InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 == 0) {
				tick(server);
			}
		});
		// Anyone who logs in inside the dungeon world without a run (it ended while they were away) is sent home.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.player;
			if (p.level().dimension().equals(DUNGEON) && runs(server).byPlayer(p.getUUID()) == null) {
				sendHome(server, p, null);
			}
		});
	}

	// ---- saved state -------------------------------------------------------------------------------------------------

	public static DungeonRuns runs(MinecraftServer server) {
		DungeonRuns r = server.overworld().getAttached(RUNS);
		return r == null ? DungeonRuns.EMPTY : r;
	}

	static void save(MinecraftServer server, DungeonRuns runs) {
		server.overworld().setAttached(RUNS, runs);
	}

	public static ServerLevel dungeonLevel(MinecraftServer server) {
		return server.getLevel(DUNGEON);
	}

	// ---- the frame ---------------------------------------------------------------------------------------------------

	/** The kind of block a state is, for {@link PortalFrame}. */
	public static PortalFrame.Kind kindOf(BlockState state) {
		if (state.isAir()) {
			return PortalFrame.Kind.AIR;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.equals("chiseled_tuff")) {
			return PortalFrame.Kind.TUFF;
		}
		return switch (path) {
			case "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "waxed_copper_block", "waxed_exposed_copper",
				"waxed_weathered_copper", "waxed_oxidized_copper" -> PortalFrame.Kind.COPPER;
			default -> PortalFrame.Kind.OTHER;
		};
	}

	public static PortalFrame.Blocks view(Level level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		return (x, y, z) -> kindOf(level.getBlockState(pos.set(x, y, z)));
	}

	/** The frame at or next to a position, or null. */
	public static PortalFrame.Shape frameAt(Level level, BlockPos... candidates) {
		PortalFrame.Blocks view = view(level);
		for (BlockPos c : candidates) {
			PortalFrame.Shape s = PortalFrame.find(view, c.getX(), c.getY(), c.getZ());
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	// ---- using a key -------------------------------------------------------------------------------------------------

	/**
	 * A key used on a block. Returns true when it was a portal frame (the click is used up, with a message when the run cannot start), false when
	 * the click is none of the key's business, or the Dungeons switch is off.
	 */
	public static boolean useKey(ServerLevel level, ServerPlayer p, ItemStack held, BlockPos... clicked) {
		DungeonKeys.Data key = DungeonKeys.read(held);
		if (key == null || !enabled() || level.dimension().equals(DUNGEON)) {
			return false;
		}
		PortalFrame.Shape frame = frameAt(level, clicked);
		if (frame == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		DungeonTheme theme = VestigesData.DUNGEON_THEMES.get(Vestiges.id(key.theme()));
		if (theme == null) {
			warn(p, "This key belongs to a dungeon theme this world does not have.");
			return true;
		}
		if (!unlocked(p, theme)) {
			warn(p, "The " + theme.name() + " dungeons are still closed to you.");
			return true;
		}
		if (dungeonLevel(server) == null) {
			warn(p, "The dungeon world is missing. Update the world's data packs and try again.");
			return true;
		}
		if (runs(server).byPlayer(p.getUUID()) != null) {
			warn(p, "You are already in a dungeon.");
			return true;
		}
		start(server, level, frame, p, key, held);
		return true;
	}

	/** True when the player may enter this theme. */
	public static boolean unlocked(ServerPlayer p, DungeonTheme theme) {
		return theme.openFromStart() || FeatService.get(p).done().contains(theme.unlockFeat());
	}

	private static void warn(ServerPlayer p, String text) {
		p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	/** The players who go in together: the one holding the key and everyone else within 8 blocks of the portal who is not in a run. */
	static List<ServerPlayer> party(ServerLevel level, PortalFrame.Shape frame, ServerPlayer leader, DungeonRuns runs) {
		Vec3 center = new Vec3(frame.centerX(), frame.y() + frame.height() / 2.0, frame.centerZ());
		List<ServerPlayer> out = new ArrayList<>();
		out.add(leader);
		for (ServerPlayer other : level.players()) {
			if (other != leader && other.isAlive() && !other.isSpectator() && other.position().distanceTo(center) <= PARTY_RADIUS
				&& runs.byPlayer(other.getUUID()) == null) {
				out.add(other);
			}
		}
		return out;
	}

	private static void start(MinecraftServer server, ServerLevel from, PortalFrame.Shape frame, ServerPlayer leader, DungeonKeys.Data key, ItemStack held) {
		DungeonRuns all = runs(server);
		ServerLevel dungeon = dungeonLevel(server);
		int plot = all.peekPlot();
		DungeonBuilder.buildStart(dungeon, plot);
		BlockPos back = BlockPos.containing(frame.centerX(), frame.y(), frame.centerZ());
		String fromDim = from.dimension().identifier().toString();
		List<DungeonRuns.Member> members = new ArrayList<>();
		List<ServerPlayer> party = party(from, frame, leader, all);
		for (ServerPlayer m : party) {
			members.add(new DungeonRuns.Member(m.getUUID(), m.getGameProfile().name(), DungeonRules.startingLives(key.tier()), false, fromDim, back));
		}
		DungeonRuns.Run run = new DungeonRuns.Run(all.nextRun(), plot, key.theme(), key.tier(), server.overworld().getGameTime(), List.copyOf(members));
		save(server, all.with(run));
		held.shrink(1);
		String title = DungeonKeys.themeName(key.theme()) + " dungeon, tier " + key.tier();
		Vec3 spawn = DungeonBuilder.startSpawn(plot);
		for (ServerPlayer m : party) {
			m.teleport(new TeleportTransition(dungeon, spawn, Vec3.ZERO, -90f, 0f, TeleportTransition.DO_NOTHING));
			m.sendSystemMessage(Component.literal(title + ". Step into the frame on the west wall to leave for good.").withStyle(ChatFormatting.GOLD));
		}
		Vestiges.LOGGER.info("Vestiges: dungeon run {} started on plot {} ({}, tier {}, {} players)", run.id(), plot, key.theme(), key.tier(), party.size());
	}

	// ---- leaving and ending ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		DungeonRuns all = runs(server);
		if (all.runs().isEmpty()) {
			return;
		}
		for (DungeonRuns.Run run : all.runs()) {
			for (DungeonRuns.Member m : run.party()) {
				if (m.out()) {
					continue;
				}
				ServerPlayer p = server.getPlayerList().getPlayer(m.id());
				if (p == null) {
					continue;
				}
				if (!p.level().dimension().equals(DUNGEON)) {
					// They left some other way (a command, a respawn): they are out.
					markOut(server, run.id(), m.id());
				} else if (p.getBoundingBox().intersects(DungeonBuilder.exitZone(run.plot()))) {
					leave(server, p);
				}
			}
		}
	}

	/** A player steps out of their run for good and goes back to the portal. */
	public static boolean leave(MinecraftServer server, ServerPlayer p) {
		DungeonRuns.Run run = runs(server).byPlayer(p.getUUID());
		if (run == null) {
			return false;
		}
		DungeonRuns.Member m = run.member(p.getUUID());
		markOut(server, run.id(), p.getUUID());
		sendHome(server, p, m);
		p.sendSystemMessage(Component.literal("You left the dungeon.").withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** Operators: ends a run and sends everyone in it home. */
	public static boolean endRun(MinecraftServer server, int runId) {
		DungeonRuns.Run run = runs(server).byId(runId);
		if (run == null) {
			return false;
		}
		for (DungeonRuns.Member m : run.party()) {
			ServerPlayer p = server.getPlayerList().getPlayer(m.id());
			if (p != null && !m.out() && p.level().dimension().equals(DUNGEON)) {
				sendHome(server, p, m);
			}
		}
		finish(server, runId);
		return true;
	}

	private static void markOut(MinecraftServer server, int runId, UUID player) {
		DungeonRuns all = runs(server);
		DungeonRuns.Run run = all.byId(runId);
		if (run == null) {
			return;
		}
		DungeonRuns.Member m = run.member(player);
		if (m == null || m.out()) {
			return;
		}
		DungeonRuns.Run next = run.withMember(m.withOut());
		save(server, all.replace(next));
		if (!next.anyoneIn()) {
			finish(server, runId);
		}
	}

	/** Frees the plot: the run is over. */
	private static void finish(MinecraftServer server, int runId) {
		DungeonRuns all = runs(server);
		DungeonRuns.Run run = all.byId(runId);
		if (run == null) {
			return;
		}
		save(server, all.end(runId));
		ServerLevel dungeon = dungeonLevel(server);
		if (dungeon != null) {
			DungeonBuilder.clearStart(dungeon, run.plot());
		}
		Vestiges.LOGGER.info("Vestiges: dungeon run {} ended, plot {} is free", runId, run.plot());
	}

	/** Teleports a player to where their portal stood (the world's spawn when that place is gone). */
	static void sendHome(MinecraftServer server, ServerPlayer p, DungeonRuns.Member m) {
		ServerLevel level = m == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(m.returnDim())));
		if (level == null) {
			p.teleport(TeleportTransition.createDefault(p, TeleportTransition.DO_NOTHING));
			return;
		}
		Vec3 pos = Vec3.atBottomCenterOf(m.returnPos());
		p.teleport(new TeleportTransition(level, pos, Vec3.ZERO, p.getYRot(), 0f, TeleportTransition.DO_NOTHING));
	}

	// ---- the First Key feat ------------------------------------------------------------------------------------------

	/** The First Key feat's reward: the tier 1 Overgrown key and the blocks of the smallest frame. */
	public static void giveFirstKit(ServerPlayer p) {
		give(p, DungeonKeys.create("overgrown", 1));
		for (ItemStack s : DungeonKeys.frameKit()) {
			give(p, s);
		}
	}

	public static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) {
			net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack);
			drop.setTarget(p.getUUID());
			p.level().addFreshEntity(drop);
		}
	}
}
