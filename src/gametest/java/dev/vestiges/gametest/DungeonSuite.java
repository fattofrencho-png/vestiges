package dev.vestiges.gametest;

import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import dev.vestiges.dungeon.DungeonBuilder;
import dev.vestiges.dungeon.DungeonKeys;
import dev.vestiges.dungeon.DungeonPlots;
import dev.vestiges.dungeon.DungeonRuns;
import dev.vestiges.dungeon.DungeonService;
import dev.vestiges.feats.FeatService;
import dev.vestiges.tree.TreeService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * S12: dungeons. The key (theme and tier on the item), the portal frame (right size and blocks, switch off, broken), going in with a key, the start
 * room in the dungeon world, leaving by the exit frame, the plot being reused, ending a run, the First Key feat, and the dungeon world's rules. The
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

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Data and the dungeon world");
		c.check("the Overgrown theme is loaded", VestigesData.DUNGEON_THEMES.get(Vestiges.id("overgrown")) != null,
			String.valueOf(VestigesData.DUNGEON_THEMES.all().keySet()));
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
		InteractionResult entered = useKey(server, "overgrown", 2, tuffBlock);
		ctx.waitTicks(60);
		c.check("the click is used up", entered == InteractionResult.SUCCESS, String.valueOf(entered));
		c.check("the player is in the dungeon world", inDungeon(server));
		c.check("the key is used up", server.computeOnServer(s -> player(s).getMainHandItem().isEmpty()));
		DungeonRuns r = runs(server);
		c.check("one run is saved, on plot 0, Overgrown, tier 2", r.runs().size() == 1 && r.runs().get(0).plot() == 0 && r.runs().get(0).tier() == 2
			&& r.runs().get(0).theme().equals("overgrown"), r.runs().toString());
		DungeonRuns.Member me = r.runs().isEmpty() || r.runs().get(0).party().isEmpty() ? null : r.runs().get(0).party().get(0);
		c.check("the player is a member with 3 lives who remembers the portal", me != null && me.lives() == 3 && !me.out()
			&& me.returnDim().equals("minecraft:overworld") && me.returnPos().equals(inside.east()), String.valueOf(me));
		Vec3 at = server.computeOnServer(s -> player(s).position());
		int startX = DungeonBuilder.startX(0);
		c.check("they stand in the start room", at.x >= startX && at.x < startX + DungeonBuilder.START_SIZE && Math.abs(at.z - DungeonPlots.originZ(0)) < 16
			&& at.y >= DungeonPlots.FLOOR_Y + 1 && at.y < DungeonPlots.FLOOR_Y + 3, at.toString());
		boolean built = server.computeOnServer(s -> {
			var level = s.getLevel(DungeonService.DUNGEON);
			int z = DungeonPlots.originZ(0);
			return level.getBlockState(new BlockPos(startX + 8, DungeonPlots.FLOOR_Y, z + 5)).is(Blocks.MOSS_BLOCK)
				&& level.getBlockState(new BlockPos(startX - 1, DungeonPlots.FLOOR_Y + 2, z + 5)).is(Blocks.MOSSY_STONE_BRICKS)
				&& level.getBlockState(new BlockPos(startX + 8, DungeonPlots.FLOOR_Y + 2, z + 5)).isAir()
				&& level.getBlockState(new BlockPos(startX, DungeonPlots.FLOOR_Y + 1, z - 2)).is(Blocks.CHISELED_TUFF);
		});
		c.check("the start room is built: moss floor, walls, open air, an exit frame", built);
		c.check("the info command lists the run", c.expectOk(server, "vestiges dungeon info").text().contains("overgrown tier 2"));
		ctx.takeScreenshot("dungeon_start_room");

		c.section("Leaving by the exit frame");
		server.runCommand("execute in vestiges:dungeon run tp @p " + (startX + 0.5) + " " + (DungeonPlots.FLOOR_Y + 1) + " " + (DungeonPlots.originZ(0) - 0.0));
		ctx.waitTicks(40);
		c.check("stepping into the exit frame sends the player back to the overworld", !inDungeon(server));
		Vec3 home = server.computeOnServer(s -> player(s).position());
		c.check("at the portal they came through", Math.abs(home.x - (inside.getX() + 1.5)) < 1.0 && Math.abs(home.z - (inside.getZ() + 0.5)) < 1.0
			&& Math.abs(home.y - inside.getY()) < 1.5, home.toString());
		r = runs(server);
		c.check("the run is over and plot 0 is free again", r.runs().isEmpty() && r.freePlots().contains(0), r.toString());

		c.section("A new run reuses the plot; ops can end it");
		backToPad(ctx, server, feet);
		useKey(server, "overgrown", 5, tuffBlock);
		ctx.waitTicks(60);
		r = runs(server);
		c.check("the second run takes plot 0 again", inDungeon(server) && r.runs().size() == 1 && r.runs().get(0).plot() == 0 && r.runs().get(0).tier() == 5,
			r.toString());
		int runId = r.runs().isEmpty() ? -1 : r.runs().get(0).id();
		c.check("its run number counts on", runId == 2, String.valueOf(runId));
		server.runOnServer(s -> DungeonService.endRun(s, DungeonService.runs(s).runs().get(0).id()));
		ctx.waitTicks(40);
		c.check("ending the run sends the player home and frees the plot", !inDungeon(server) && runs(server).runs().isEmpty());

		c.section("Leaving some other way ends the run too");
		backToPad(ctx, server, feet);
		useKey(server, "overgrown", 1, tuffBlock);
		ctx.waitTicks(60);
		c.check("a third run starts", inDungeon(server) && runs(server).runs().size() == 1);
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
		DungeonKeys.Data first = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				DungeonKeys.Data d = DungeonKeys.read(p.getInventory().getItem(i));
				if (d != null) {
					return d;
				}
			}
			return null;
		});
		c.check("it is an Overgrown key of tier 1", first != null && first.theme().equals("overgrown") && first.tier() == 1, String.valueOf(first));
		c.check("and the blocks of the smallest frame", tuff(server) == 6 && copper(server) == 4, tuff(server) + " tuff, " + copper(server) + " copper");
		server.runOnServer(s -> FeatService.check(player(s)));
		c.check("the feat pays only once", count(server, DungeonKeys.item()) == 1);

		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges config system dungeons true");
	}
}
