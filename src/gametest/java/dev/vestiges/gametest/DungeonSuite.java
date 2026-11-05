package dev.vestiges.gametest;

import dev.vestiges.Vestiges;
import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.data.VestigesData;
import dev.vestiges.dungeon.DungeonKeys;
import dev.vestiges.dungeon.DungeonPlots;
import dev.vestiges.dungeon.DungeonRuns;
import dev.vestiges.dungeon.DungeonService;
import dev.vestiges.dungeon.RoomLayout;
import dev.vestiges.feats.FeatService;
import dev.vestiges.tree.TreeService;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * S12: dungeons. The key (theme and tier on the item), the portal frame (right size and blocks, switch off, broken), going in with a key, the
 * rooms of a run (13, built ahead, doors that close on entry and open when the monsters are dead, walked through room by room), unbreakable
 * walls, both exit frames, plots that are cleared before they are reused, ending a run, the First Key feat, and the dungeon world's rules. The
 * click on the frame is the game's own use-block event.
 */
final class DungeonSuite {
	private DungeonSuite() {
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> {
			int n = 0;
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(item)) {
					n += st.getCount();
				}
			}
			return n;
		});
	}

	private static int tuff(TestServerContext server) {
		return count(server, Items.CHISELED_TUFF);
	}

	private static int copper(TestServerContext server) {
		return count(server, Items.COPPER_BLOCK.weathering().unaffected());
	}

	private static boolean inDungeon(TestServerContext server) {
		return server.computeOnServer(s -> player(s).level().dimension().equals(DungeonService.DUNGEON));
	}

	private static DungeonRuns runs(TestServerContext server) {
		return server.computeOnServer(DungeonService::runs);
	}

	private static DungeonRuns.Run first(TestServerContext server) {
		DungeonRuns r = runs(server);
		return r.runs().isEmpty() ? null : r.runs().get(0);
	}

	/** Builds the smallest frame (2 x 3 inside) three blocks east of the player, standing on the pad. Returns the lowest interior corner. */
	private static BlockPos buildFrame(TestServerContext server, BlockPos feet) {
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		String in = "execute in minecraft:overworld run ";
		server.runCommand(in + "fill " + (x + 1) + " " + y + " " + z + " " + (x + 4) + " " + (y + 4) + " " + z + " minecraft:air");
		server.runCommand(in + "fill " + (x + 2) + " " + y + " " + z + " " + (x + 3) + " " + y + " " + z + " minecraft:copper_block");
		server.runCommand(in + "fill " + (x + 2) + " " + (y + 4) + " " + z + " " + (x + 3) + " " + (y + 4) + " " + z + " minecraft:copper_block");
		server.runCommand(in + "fill " + (x + 1) + " " + (y + 1) + " " + z + " " + (x + 1) + " " + (y + 3) + " " + z + " minecraft:chiseled_tuff");
		server.runCommand(in + "fill " + (x + 4) + " " + (y + 1) + " " + z + " " + (x + 4) + " " + (y + 3) + " " + z + " minecraft:chiseled_tuff");
		return new BlockPos(x + 2, y + 1, z);
	}

	/** The player right-clicks a block with a key in the main hand: the game's own use-block event. */
	private static InteractionResult useKey(TestServerContext server, String theme, int tier, BlockPos block) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, DungeonKeys.create(theme, tier));
			return UseBlockCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false));
		});
	}

	private static void backToPad(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		server.runCommand("execute in minecraft:overworld run tp @p " + feet.getX() + " " + feet.getY() + " " + feet.getZ());
		ctx.waitTicks(20);
	}

	/** Moves the player inside the dungeon world. */
	private static void tpIn(TestServerContext server, double x, double y, double z) {
		server.runCommand(String.format(Locale.ROOT, "execute in vestiges:dungeon run tp @p %.1f %.1f %.1f", x, y, z));
	}

	/** A block of the dungeon world. */
	private static boolean block(TestServerContext server, int x, int y, int z, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> test) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(DungeonService.DUNGEON);
			return test.test(level.getBlockState(new BlockPos(x, y, z)));
		});
	}

	private static boolean bars(TestServerContext server, int x, int z) {
		int y = DungeonPlots.FLOOR_Y;
		return block(server, x, y + 1, z, st -> st.is(Blocks.IRON_BARS)) && block(server, x, y + 4, z + 2, st -> st.is(Blocks.IRON_BARS))
			&& block(server, x, y + 4, z - 2, st -> st.is(Blocks.IRON_BARS));
	}

	private static boolean open(TestServerContext server, int x, int z) {
		int y = DungeonPlots.FLOOR_Y;
		return block(server, x, y + 1, z, st -> st.isAir()) && block(server, x, y + 4, z + 2, st -> st.isAir()) && block(server, x, y + 4, z - 2, st -> st.isAir());
	}

	/** Monsters of a room that are alive. */
	private static int monsters(TestServerContext server, int runId, int room) {
		return server.computeOnServer(s -> {
			DungeonRuns.Run run = DungeonService.runs(s).byId(runId);
			ServerLevel level = s.getLevel(DungeonService.DUNGEON);
			String tag = "vdr" + runId + "r" + room;
			return level.getEntities((Entity) null, run.rooms().get(room).box(), e -> e.isAlive() && e.entityTags().contains(tag)).size();
		});
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Data and the dungeon world");
		c.check("the Overgrown theme is loaded", VestigesData.DUNGEON_THEMES.get(Vestiges.id("overgrown")) != null,
			String.valueOf(VestigesData.DUNGEON_THEMES.all().keySet()));
		c.check("its rooms are loaded (8: start, 4 fighting shapes, Lieutenant, Boss, reward)", VestigesData.DUNGEON_ROOMS.all().size() >= 8,
			String.valueOf(VestigesData.DUNGEON_ROOMS.all().size()));
		boolean worldExists = server.computeOnServer(s -> s.getLevel(DungeonService.DUNGEON) != null);
		c.check("the dungeon world exists", worldExists);
		if (!worldExists) {
			return;
		}
		boolean rules = server.computeOnServer(s -> {
			var type = s.getLevel(DungeonService.DUNGEON).dimensionType();
			return type.hasFixedTime() && !type.hasSkyLight() && type.hasCeiling();
		});
		c.check("the dungeon world has a fixed time, no sky and a ceiling (no weather, no sun)", rules);
		c.check("nothing is running at the start", runs(server).runs().isEmpty());

		c.section("Keys");
		String name = server.computeOnServer(s -> DungeonKeys.create("overgrown", 3).getHoverName().getString());
		c.check("a key reads Overgrown Key, Tier 3", "Overgrown Key, Tier 3".equals(name), name);
		DungeonKeys.Data data = server.computeOnServer(s -> DungeonKeys.read(DungeonKeys.create("overgrown", 7)));
		c.check("the theme and tier come back from the item", data != null && data.theme().equals("overgrown") && data.tier() == 7,
			String.valueOf(data));
		c.check("an ordinary item is not a key", server.computeOnServer(s -> DungeonKeys.read(new ItemStack(Items.TRIAL_KEY)) == null));
		server.runCommand("clear @p");
		c.expectOk(server, "vestiges give key overgrown 2");
		c.check("the command gives one key", count(server, DungeonKeys.item()) == 1, String.valueOf(count(server, DungeonKeys.item())));
		c.expectRejected(server, "vestiges give key nonsense 1");
		c.expectRejected(server, "vestiges give key overgrown 0");
		c.expectOk(server, "vestiges give frame");
		c.check("the frame kit is 6 chiseled tuff and 4 copper blocks", tuff(server) == 6 && copper(server) == 4, tuff(server) + " tuff, " + copper(server) + " copper");
		server.runCommand("clear @p");

		c.section("The portal frame");
		server.runCommand("gamemode creative @p");
		BlockPos feet = T.pad(ctx, server, 600, 600);
		BlockPos inside = buildFrame(server, feet);
		BlockPos tuffBlock = inside.west();
		String top = "execute in minecraft:overworld run setblock " + (inside.getX() + 1) + " " + (inside.getY() + 3) + " " + inside.getZ() + " minecraft:";
		server.runCommand(top + "stone");
		InteractionResult broken = useKey(server, "overgrown", 2, tuffBlock);
		ctx.waitTicks(10);
		c.check("a broken frame does nothing", broken == InteractionResult.PASS && !inDungeon(server) && runs(server).runs().isEmpty(), String.valueOf(broken));
		c.check("and the key stays in the hand", server.computeOnServer(s -> DungeonKeys.read(player(s).getMainHandItem()) != null));
		server.runCommand(top + "chiseled_tuff");
		InteractionResult wrongBlock = useKey(server, "overgrown", 2, tuffBlock);
		c.check("tuff in the top row (the wrong block) does nothing", wrongBlock == InteractionResult.PASS && runs(server).runs().isEmpty());
		buildFrame(server, feet);

		c.section("The switch");
		server.runCommand("vestiges config system dungeons false");
		ctx.waitTicks(5);
		InteractionResult off = useKey(server, "overgrown", 2, tuffBlock);
		ctx.waitTicks(10);
		c.check("with the Dungeons switch off the frame does nothing", off == InteractionResult.PASS && !inDungeon(server) && runs(server).runs().isEmpty(), String.valueOf(off));
		c.check("and the key stays in the pocket", server.computeOnServer(s -> DungeonKeys.read(player(s).getMainHandItem()) != null));
		server.runCommand("vestiges config system dungeons true");
		ctx.waitTicks(5);

		c.section("Going in");
		long began = System.nanoTime();
		InteractionResult entered = useKey(server, "overgrown", 2, tuffBlock);
		long tookMs = (System.nanoTime() - began) / 1_000_000;
		ctx.waitTicks(60);
		c.note("starting a run (three rooms built) took " + tookMs + " ms on the server thread");
		c.check("the click is used up", entered == InteractionResult.SUCCESS, String.valueOf(entered));
		c.check("the player is in the dungeon world", inDungeon(server));
		c.check("the key is used up", server.computeOnServer(s -> player(s).getMainHandItem().isEmpty()));
		DungeonRuns r = runs(server);
		c.check("one run is saved, on plot 0, Overgrown, tier 2", r.runs().size() == 1 && r.runs().get(0).plot() == 0 && r.runs().get(0).tier() == 2
			&& r.runs().get(0).theme().equals("overgrown"), r.runs().toString());
		DungeonRuns.Run run = first(server);
		DungeonRuns.Member me = run == null || run.party().isEmpty() ? null : run.party().get(0);
		c.check("the player is a member with 3 lives who remembers the portal", me != null && me.lives() == 3 && !me.out()
			&& me.returnDim().equals("minecraft:overworld") && me.returnPos().equals(inside.east()), String.valueOf(me));
		if (run == null) {
			return;
		}
		int runId = run.id();
		DungeonRuns.Room start = run.rooms().get(0);
		Vec3 at = server.computeOnServer(s -> player(s).position());
		c.check("they stand in the start room", at.x >= start.x() && at.x < start.x() + start.size() && Math.abs(at.z - start.centerZ()) < 16
			&& at.y >= DungeonPlots.FLOOR_Y + 1 && at.y < DungeonPlots.FLOOR_Y + 3, at.toString());
		boolean built = block(server, start.x() + 8, DungeonPlots.FLOOR_Y, start.centerZ() + 5, st -> !st.isAir())
			&& block(server, start.x() - 1, DungeonPlots.FLOOR_Y + 2, start.centerZ() + 5, st -> !st.isAir())
			&& block(server, start.x() + 8, DungeonPlots.FLOOR_Y + 2, start.centerZ() + 5, st -> st.isAir())
			&& block(server, start.x(), DungeonPlots.FLOOR_Y + 1, start.centerZ() - 2, st -> st.is(Blocks.CHISELED_TUFF));
		c.check("the start room is built: a floor, walls, open air, an exit frame", built);
		c.check("the info command lists the run", c.expectOk(server, "vestiges dungeon info").text().contains("overgrown tier 2"));
		ctx.takeScreenshot("dungeon_start_room");

		c.section("The rooms of a run");
		c.check("13 rooms are planned in the agreed order", run.rooms().size() == 13 && run.rooms().stream().map(DungeonRuns.Room::role).toList().equals(RoomLayout.ORDER),
			run.rooms().stream().map(DungeonRuns.Room::role).toList().toString());
		c.check("the sizes are 32, 40 for the Lieutenant, 48 for the Boss", run.rooms().get(0).size() == 32 && run.rooms().get(7).size() == 40
			&& run.rooms().get(11).size() == 48 && run.rooms().get(12).size() == 32);
		c.check("only the first three rooms are built at the start", run.rooms().get(0).built() && run.rooms().get(1).built() && run.rooms().get(2).built()
			&& run.rooms().stream().filter(DungeonRuns.Room::built).count() == 3, run.rooms().stream().filter(DungeonRuns.Room::built).count() + " built");
		c.check("the start room is already done", run.rooms().get(0).state() == DungeonRuns.Room.CLEARED);
		DungeonRuns.Room r1 = run.rooms().get(1);
		DungeonRuns.Room r2 = run.rooms().get(2);
		c.check("the doors on the way in are open (start to room 1)", open(server, start.eastWallX(), start.centerZ()) && open(server, r1.westWallX(), r1.centerZ()));
		c.check("room 1's exit is shut with bars until its monsters are dead", bars(server, r1.eastWallX(), r1.centerZ()));
		c.check("room 2's entrance is open", open(server, r2.westWallX(), r2.centerZ()));
		c.check("a fighting room has spawn points and a cache spot, but no boss spot", r1.spawns().size() >= 10 && r1.caches().size() == 1 && r1.boss().isEmpty(),
			r1.spawns().size() + " spawns");
		ctx.takeScreenshot("dungeon_room1_before");

		c.section("Fighting a room");
		tpIn(server, r1.x() + 6.5, DungeonPlots.FLOOR_Y + 1, r1.centerZ() + 0.5);
		ctx.waitTicks(30);
		DungeonRuns.Run now = first(server);
		c.check("walking in wakes the room", now.rooms().get(1).state() == DungeonRuns.Room.ACTIVE, String.valueOf(now.rooms().get(1).state()));
		int alive = monsters(server, runId, 1);
		c.check("6 to 10 monsters appear", alive >= 6 && alive <= 10, String.valueOf(alive));
		c.check("the doors close behind the party", bars(server, r1.westWallX(), r1.centerZ()) && bars(server, r1.eastWallX(), r1.centerZ()));
		c.check("the rooms ahead get built (up to room 3)", now.rooms().get(3).built() && !now.rooms().get(4).built());
		ctx.takeScreenshot("dungeon_room1_fight");
		server.runCommand("kill @e[tag=vdr" + runId + "r1]");
		ctx.waitTicks(25);
		now = first(server);
		c.check("when the last monster dies the room is cleared", now.rooms().get(1).state() == DungeonRuns.Room.CLEARED, String.valueOf(now.rooms().get(1).state()));
		c.check("and both doors open", open(server, r1.westWallX(), r1.centerZ()) && open(server, r1.eastWallX(), r1.centerZ()));
		c.check("the next room stays quiet", now.rooms().get(2).state() == DungeonRuns.Room.IDLE && monsters(server, runId, 2) == 0);

		c.section("Walking through the whole run");
		boolean allShapes = true;
		StringBuilder shapes = new StringBuilder();
		for (int k = 2; k <= 11; k++) {
			DungeonRuns.Room room = first(server).rooms().get(k);
			shapes.append(room.shape()).append(' ');
			tpIn(server, room.x() + 6.5, DungeonPlots.FLOOR_Y + 1, room.centerZ() + 0.5);
			ctx.waitTicks(25);
			DungeonRuns.Run during = first(server);
			int n = monsters(server, runId, k);
			boolean right = room.role().equals("combat") ? n >= 6 && n <= 10 : n == 1;
			boolean shut = during.rooms().get(k).state() == DungeonRuns.Room.ACTIVE && bars(server, room.westWallX(), room.centerZ())
				&& bars(server, room.eastWallX(), room.centerZ());
			allShapes &= right && shut;
			if (!right || !shut) {
				c.check("room " + k + " (" + room.role() + ", " + room.shape() + ") woke with its monsters and shut doors", false, n + " monsters, state "
					+ during.rooms().get(k).state());
			}
			server.runCommand("kill @e[tag=vdr" + runId + "r" + k + "]");
			ctx.waitTicks(25);
			DungeonRuns.Run after = first(server);
			boolean done = after.rooms().get(k).state() == DungeonRuns.Room.CLEARED && open(server, room.westWallX(), room.centerZ())
				&& open(server, room.eastWallX(), room.centerZ());
			allShapes &= done;
			if (!done) {
				c.check("room " + k + " opened again after its monsters died", false, "state " + after.rooms().get(k).state());
			}
		}
		c.check("rooms 2 to 11 (fighting rooms, Lieutenant, Boss) each woke, shut, and opened again (shapes " + shapes.toString().trim() + ")", allShapes);
		DungeonRuns.Room reward = first(server).rooms().get(12);
		c.check("every room is built by now", first(server).rooms().stream().allMatch(DungeonRuns.Room::built));
		c.check("the Lieutenant and Boss rooms have a boss spot, the reward room four cache spots", first(server).rooms().get(7).boss().size() == 1
			&& first(server).rooms().get(11).boss().size() == 1 && reward.caches().size() == 4 && reward.spawns().isEmpty());
		tpIn(server, reward.x() + 6.5, DungeonPlots.FLOOR_Y + 1, reward.centerZ() + 0.5);
		ctx.waitTicks(25);
		c.check("stepping into the reward room finishes the run's rooms", first(server).rooms().get(12).state() == DungeonRuns.Room.CLEARED);
		ctx.takeScreenshot("dungeon_reward_room");

		c.section("The walls are unbreakable");
		server.runCommand("gamemode survival @p");
		ctx.waitTicks(5);
		BlockPos wall = new BlockPos(reward.x() + 5, DungeonPlots.FLOOR_Y + 2, reward.z() - 1);
		BlockPos floorSpot = new BlockPos(reward.x() + 5, DungeonPlots.FLOOR_Y, reward.centerZ() + 6);
		boolean wallBreak = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(DungeonService.DUNGEON);
			return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player(s), wall, level.getBlockState(wall), null);
		});
		boolean floorBreak = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(DungeonService.DUNGEON);
			return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player(s), floorSpot, level.getBlockState(floorSpot), null);
		});
		c.check("a survival player cannot break a wall or the floor", !wallBreak && !floorBreak, wallBreak + " / " + floorBreak);
		boolean placedBreak = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(DungeonService.DUNGEON);
			BlockPos spot = floorSpot.above();
			level.setBlock(spot, Blocks.DIRT.defaultBlockState(), 3);
			PlacedBlocks.markPlaced(level, spot);
			return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player(s), spot, level.getBlockState(spot), null);
		});
		c.check("but a block the player placed can be broken", placedBreak);
		server.runCommand("gamemode creative @p");

		c.section("Leaving by the reward room's exit frame");
		tpIn(server, reward.x() + reward.size() - 1 + 0.5, DungeonPlots.FLOOR_Y + 1, reward.centerZ() + 0.0);
		ctx.waitTicks(40);
		c.check("stepping into the exit frame sends the player back to the overworld", !inDungeon(server));
		Vec3 home = server.computeOnServer(s -> player(s).position());
		c.check("at the portal they came through", Math.abs(home.x - (inside.getX() + 1.5)) < 1.0 && Math.abs(home.z - (inside.getZ() + 0.5)) < 1.0
			&& Math.abs(home.y - inside.getY()) < 1.5, home.toString());
		r = runs(server);
		c.check("the run is over and plot 0 is being cleared", r.runs().isEmpty() && r.isClearing(0) && !r.freePlots().contains(0), r.toString());

		c.section("A new run while plot 0 is still being cleared");
		backToPad(ctx, server, feet);
		useKey(server, "overgrown", 5, tuffBlock);
		ctx.waitTicks(60);
		r = runs(server);
		DungeonRuns.Run second = first(server);
		c.check("it takes plot 1, not the dirty one", inDungeon(server) && second != null && second.plot() == 1 && second.tier() == 5, r.toString());
		c.check("its run number counts on", second != null && second.id() == runId + 1, String.valueOf(second == null ? -1 : second.id()));
		server.runOnServer(s -> DungeonService.endRun(s, DungeonService.runs(s).runs().get(0).id()));
		ctx.waitTicks(40);
		c.check("ending the run (the operator tool) sends the player home", !inDungeon(server) && runs(server).runs().isEmpty());

		c.section("Plots are cleared, then reused");
		boolean cleared = false;
		for (int i = 0; i < 80 && !cleared; i++) {
			ctx.waitTicks(20);
			cleared = runs(server).clearing().isEmpty();
		}
		c.check("both plots are cleared within a minute", cleared, runs(server).clearing().toString());
		r = runs(server);
		c.check("and free again", r.freePlots().contains(0) && r.freePlots().contains(1), r.freePlots().toString());
		boolean empty = block(server, r1.x() + 8, DungeonPlots.FLOOR_Y, r1.centerZ(), st -> st.isAir())
			&& block(server, r1.westWallX(), DungeonPlots.FLOOR_Y + 2, r1.z() + 3, st -> st.isAir())
			&& block(server, reward.x() + 5, DungeonPlots.FLOOR_Y, reward.centerZ(), st -> st.isAir());
		c.check("the rooms of the first run are gone", empty);
		boolean noMobs = server.computeOnServer(s -> s.getLevel(DungeonService.DUNGEON).getEntities((Entity) null,
			new net.minecraft.world.phys.AABB(-10, 0, -10, 1100, 255, 1100), e -> !(e instanceof ServerPlayer)).isEmpty());
		c.check("and nothing is left lying around", noMobs);
		backToPad(ctx, server, feet);
		useKey(server, "overgrown", 1, tuffBlock);
		ctx.waitTicks(60);
		c.check("a new run takes plot 0 again (the lowest free one)", inDungeon(server) && first(server) != null && first(server).plot() == 0,
			String.valueOf(first(server)));

		c.section("Leaving by the start room's exit frame, or some other way");
		DungeonRuns.Room s0 = first(server).rooms().get(0);
		tpIn(server, s0.x() + 0.5, DungeonPlots.FLOOR_Y + 1, s0.centerZ() + 0.0);
		ctx.waitTicks(40);
		c.check("the start room's exit frame leads home too", !inDungeon(server) && runs(server).runs().isEmpty());
		backToPad(ctx, server, feet);
		useKey(server, "overgrown", 1, tuffBlock);
		ctx.waitTicks(60);
		c.check("another run starts", inDungeon(server) && runs(server).runs().size() == 1);
		server.runCommand("execute in minecraft:overworld run tp @p " + feet.getX() + " " + feet.getY() + " " + feet.getZ());
		ctx.waitTicks(40);
		c.check("walking out by command ends the run", !inDungeon(server) && runs(server).runs().isEmpty());

		c.section("The First Key feat");
		server.runCommand("clear @p");
		server.runOnServer(s -> FeatService.grant(player(s), "first_key", false));
		server.runCommand("vestiges level set @p 14");
		server.runOnServer(s -> TreeService.setCounter(player(s), "elites", 5));
		for (int i = 0; i < 6; i++) {
			server.runOnServer(s -> FeatService.check(player(s)));
		}
		c.check("at level 14 the feat is not earned", count(server, DungeonKeys.item()) == 0);
		server.runCommand("vestiges level set @p 15");
		server.runCommand("vestiges config system dungeons false");
		ctx.waitTicks(5);
		for (int i = 0; i < 6; i++) {
			server.runOnServer(s -> FeatService.check(player(s)));
		}
		c.check("with the Dungeons switch off the feat is not earned", count(server, DungeonKeys.item()) == 0);
		server.runCommand("vestiges config system dungeons true");
		ctx.waitTicks(5);
		for (int i = 0; i < 6; i++) {
			server.runOnServer(s -> FeatService.check(player(s)));
		}
		c.check("level 15 and 5 elites earn the feat: a tier 1 key", count(server, DungeonKeys.item()) == 1, String.valueOf(count(server, DungeonKeys.item())));
		DungeonKeys.Data firstKey = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				DungeonKeys.Data d = DungeonKeys.read(p.getInventory().getItem(i));
				if (d != null) {
					return d;
				}
			}
			return null;
		});
		c.check("it is an Overgrown key of tier 1", firstKey != null && firstKey.theme().equals("overgrown") && firstKey.tier() == 1, String.valueOf(firstKey));
		c.check("and the blocks of the smallest frame", tuff(server) == 6 && copper(server) == 4, tuff(server) + " tuff, " + copper(server) + " copper");
		server.runOnServer(s -> FeatService.check(player(s)));
		c.check("the feat pays only once", count(server, DungeonKeys.item()) == 1);

		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges config system dungeons true");
	}
}
