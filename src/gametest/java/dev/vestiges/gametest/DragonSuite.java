package dev.vestiges.gametest;

import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import dev.vestiges.dragon.DragonDef;
import dev.vestiges.dragon.DragonMark;
import dev.vestiges.dragon.DragonService;
import dev.vestiges.dragon.PlayerDragons;
import dev.vestiges.dungeon.DungeonService;
import dev.vestiges.feats.FeatService;
import dev.vestiges.inventory.InventoryService;
import dev.vestiges.mixin.EnderDragonFightAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * S12: the first dragons (Act I). Slice 1: the six data files load, the kill count is per world and set only by an operator command, and the
 * ender chest page count follows the dragons a player was rewarded for. Slice 2: dragon 1 is in the End with its name and health, a hit is capped
 * at 8%, a player lost in the void cannot hurt it, a real kill counts and rewards, and the crystals refuse below the gate level and summon above it.
 */
final class DragonSuite {
	private DragonSuite() {
	}

	private static int kills(TestServerContext server) {
		return server.computeOnServer(s -> DragonService.world(s).kills());
	}

	private static int pages(TestServerContext server) {
		return server.computeOnServer(s -> InventoryService.unlockedPages(s.getPlayerList().getPlayers().get(0)));
	}

	/** A copy of what the dragon in the End looks like right now. */
	private record Snap(boolean present, String name, double max, double health, int number, boolean marked, double damage) {
	}

	private static Snap dragon(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			var fight = end == null ? null : end.getDragonFight();
			var id = fight == null ? null : fight.dragonUUID();
			if (id == null || !(end.getEntity(id) instanceof EnderDragon d)) {
				return new Snap(false, "", 0, 0, 0, false, 1.0);
			}
			DragonMark m = DragonService.mark(d);
			return new Snap(true, d.hasCustomName() ? d.getCustomName().getString() : "", d.getMaxHealth(), d.getHealth(), m == null ? 0 : m.number(), m != null,
				DragonService.damageMultiplier(d));
		});
	}

	/** Waits until a living dragon of this number (0 = unmarked) stands in the End. */
	private static boolean waitForDragon(TestServerContext server, int number, int ticks) {
		try {
			server.waitFor(s -> {
				ServerLevel end = s.getLevel(Level.END);
				var fight = end == null ? null : end.getDragonFight();
				var id = fight == null ? null : fight.dragonUUID();
				if (id == null || !(end.getEntity(id) instanceof EnderDragon d) || d.isDeadOrDying()) {
					return false;
				}
				DragonMark m = DragonService.mark(d);
				return (m == null ? 0 : m.number()) == number;
			}, ticks);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** One hit on the dragon by the player; returns the health it lost. With {@code y} below 0 the player stays where they are. */
	private static float hit(TestServerContext server, float amount, double x, double y, double z) {
		return server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			EnderDragon d = (EnderDragon) end.getEntity(end.getDragonFight().dragonUUID());
			Vec3 back = p.position();
			if (y >= 0) {
				p.setPos(x, y, z);
			}
			float before = d.getHealth();
			d.hurtServer(end, end.damageSources().playerAttack(p), amount);
			p.setPos(back.x, back.y, back.z);
			return before - d.getHealth();
		});
	}

	private static BlockPos portal(TestServerContext server) {
		return server.computeOnServer(s -> ((EnderDragonFightAccessor) s.getLevel(Level.END).getDragonFight()).vestiges$portal());
	}

	/** Puts end crystals on the three ring spots that are not WEST (the 4th is put down by {@link #place}). */
	private static void ring(TestServerContext server, BlockPos portal) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (d != Direction.WEST) {
					BlockPos at = portal.above(1).relative(d, 3);
					end.addFreshEntity(new EndCrystal(end, at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
				}
			}
		});
	}

	/** The player right-clicks the west rim block of the portal with end crystals, as in the game. */
	private static InteractionResult place(TestServerContext server, BlockPos portal) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			ItemStack stack = new ItemStack(Items.END_CRYSTAL, 4);
			p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos clicked = portal.relative(Direction.WEST, 3);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false);
			return stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
		});
	}

	/** What stands at the west rim spot, for a failure report: the block, the space above and anything in the crystal's box. */
	private static String rimReport(TestServerContext server, BlockPos portal) {
		return server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			BlockPos clicked = portal.relative(Direction.WEST, 3);
			var box = new net.minecraft.world.phys.AABB(clicked.getX(), clicked.getY() + 1, clicked.getZ(), clicked.getX() + 1, clicked.getY() + 3, clicked.getZ() + 1);
			return "portal " + portal.toShortString() + ", rim block " + end.getBlockState(clicked).getBlock() + ", above empty " + end.isEmptyBlock(clicked.above())
				+ ", entities in the box " + end.getEntities((net.minecraft.world.entity.Entity) null, box).stream().map(e -> e.getType().toString()).toList()
				+ ", player at " + s.getPlayerList().getPlayers().get(0).blockPosition().toShortString();
		});
	}

	private static int crystalsAtWest(TestServerContext server, BlockPos portal) {
		return server.computeOnServer(s -> {
			BlockPos at = portal.above(1).relative(Direction.WEST, 3);
			return s.getLevel(Level.END).getEntitiesOfClass(EndCrystal.class, new net.minecraft.world.phys.AABB(at)).size();
		});
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		data(ctx, server, c);
		fights(ctx, server, c);
	}

	private static void data(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("The data");
		c.check("six dragons are loaded", VestigesData.DRAGONS.all().size() == 6, String.valueOf(VestigesData.DRAGONS.all().size()));
		DragonDef two = server.computeOnServer(s -> DragonService.def(2));
		c.check("dragon 2 is Belaz, the Ember, gate level 8", two != null && two.name().equals("Belaz") && two.gate() == 8 && two.title().equals("the Ember"),
			String.valueOf(two));
		c.check("there is no dragon 7 yet (summoning would give a plain respawn)", server.computeOnServer(s -> DragonService.def(7)) == null);
		c.check("the Dragons switch is on in a new world", DragonService.enabled());

		c.section("The kill count");
		c.check("a new world starts at 0 kills", kills(server) == 0, String.valueOf(kills(server)));
		String info = c.expectOk(server, "vestiges dragon info").text();
		c.check("info names the next dragon", info != null, info);
		c.expectOk(server, "vestiges dragon count 3");
		c.check("the count is set by the operator command", kills(server) == 3, String.valueOf(kills(server)));
		c.expectOk(server, "vestiges dragon count 0");
		c.check("and back to 0", kills(server) == 0);

		c.section("Ender chest pages");
		c.check("a new player has 1 page", pages(server) == 1, String.valueOf(pages(server)));
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			DragonService.setPlayer(p, PlayerDragons.EMPTY.with(1).with(2).with(3));
		});
		c.check("three rewarded dragons make 4 pages", pages(server) == 4, String.valueOf(pages(server)));
		server.runCommand("vestiges config system dragons false");
		ctx.waitTicks(5);
		c.check("with the Dragons switch off the player has only the first page", pages(server) == 1, String.valueOf(pages(server)));
		server.runCommand("vestiges config system dragons true");
		server.runOnServer(s -> DragonService.setPlayer(s.getPlayerList().getPlayers().get(0), PlayerDragons.EMPTY));
		c.check("clearing the record goes back to 1 page", pages(server) == 1, String.valueOf(pages(server)));
	}

	private static void fights(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges level set @p 5");
		server.runCommand("execute in minecraft:the_end run tp @p 0 90 0");

		c.section("Dragon 1 is already in the End");
		c.check("a dragon appears with the number 1 mark", waitForDragon(server, 1, 1200));
		Snap one = dragon(server);
		c.check("the boss bar name is the lore name, title and level", one.name().equals("Tulva, the First · Lv 4"), one.name());
		c.check("alone, it has the vanilla 200 health", Math.abs(one.max() - 200.0) < 0.5, String.valueOf(one.max()));
		c.check("the world knows dragon 1 is being fought", server.computeOnServer(s -> DragonService.world(s).active()) == 1);
		c.check("the End dungeon theme is still locked", !server.computeOnServer(s -> DungeonService.unlocked(s.getPlayerList().getPlayers().get(0),
			VestigesData.DUNGEON_THEMES.get(Vestiges.id("end")))));

		c.section("Hits");
		float lostFar = hit(server, 1000f, 400, 90, 0);
		c.check("a player out beyond the arena cannot hurt it", lostFar == 0f, String.valueOf(lostFar));
		float lostVoid = hit(server, 1000f, 0, 5, 0);
		c.check("a player in the void under the island cannot hurt it", lostVoid == 0f, String.valueOf(lostVoid));
		c.check("neither of those two counts as a fighter", server.computeOnServer(s -> DragonService.world(s).hurt().isEmpty()));
		ctx.waitTicks(25);
		float lost = hit(server, 1000f, 0, -1, 0);
		c.check("one huge hit takes at most 8% of 200 health (16)", lost > 0.5f && lost <= 16.01f, String.valueOf(lost));
		c.check("the player who hit it is now a fighter", server.computeOnServer(s -> DragonService.world(s).hurt().size()) == 1);

		c.section("A real kill");
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			((EnderDragon) end.getEntity(end.getDragonFight().dragonUUID())).setHealth(1f);
		});
		ctx.waitTicks(25);
		hit(server, 100f, 0, -1, 0);
		try {
			server.waitFor(s -> DragonService.world(s).kills() == 1, 1500);
		} catch (RuntimeException e) {
			c.note("the kill did not finish in time: " + e);
		}
		c.check("the world kill count is 1", kills(server) == 1, String.valueOf(kills(server)));
		c.check("the fight is over", server.computeOnServer(s -> DragonService.world(s).active()) == 0);
		c.check("the fighter is rewarded for dragon 1", server.computeOnServer(s -> DragonService.player(s.getPlayerList().getPlayers().get(0)).has(1)));
		c.check("that unlocks the 2nd ender chest page", pages(server) == 2, String.valueOf(pages(server)));
		ctx.waitTicks(60);
		c.check("the Dragonslayer feat is done", server.computeOnServer(s -> FeatService.get(s.getPlayerList().getPlayers().get(0)).done().contains("dragonslayer")));
		c.check("so the End dungeon theme is open", server.computeOnServer(s -> DungeonService.unlocked(s.getPlayerList().getPlayers().get(0),
			VestigesData.DUNGEON_THEMES.get(Vestiges.id("end")))));

		c.section("The gate");
		BlockPos portal = portal(server);
		c.check("the exit portal exists after the kill", portal != null);
		if (portal == null) {
			return;
		}
		// The kill paid XP, so the level has moved on: put it back below the gate.
		server.runCommand("vestiges level set @p 5");
		ring(server, portal);
		ctx.waitTicks(5);
		int refusalsBefore = DragonService.refusals;
		InteractionResult refused = place(server, portal);
		c.check("the gate itself said no (not vanilla)", DragonService.refusals == refusalsBefore + 1, refusalsBefore + " -> " + DragonService.refusals);
		c.check("level 5 is below dragon 2's gate: the 4th crystal refuses", refused == InteractionResult.FAIL, refused + " | " + rimReport(server, portal));
		c.check("and no crystal was put down", crystalsAtWest(server, portal) == 0, String.valueOf(crystalsAtWest(server, portal)));
		c.check("no dragon came", !dragon(server).present() || dragon(server).number() != 2);
		server.runCommand("vestiges level set @p 8");
		ctx.waitTicks(5);
		InteractionResult placed = place(server, portal);
		c.check("level 8 passes the gate: the crystal goes down", placed.consumesAction(), placed + " | " + rimReport(server, portal));

		c.section("Dragon 2 arrives");
		c.check("Belaz appears", waitForDragon(server, 2, 2400));
		Snap two = dragon(server);
		c.check("its boss bar name is Belaz, the Ember · Lv 8", two.name().equals("Belaz, the Ember · Lv 8"), two.name());
		c.check("alone, it has 40% more health than the vanilla dragon (280)", Math.abs(two.max() - 280.0) < 0.5, String.valueOf(two.max()));
		c.check("it hits 20% harder", Math.abs(two.damage() - 1.2) < 1e-6, String.valueOf(two.damage()));
		c.check("the 8% cap grows with its health: at most 22.4 from one hit", hit(server, 1000f, 0, -1, 0) <= 22.5f);

		c.section("A command kill does not count");
		server.runCommand("execute in minecraft:the_end run kill @e[type=minecraft:ender_dragon]");
		try {
			server.waitFor(s -> DragonService.world(s).active() == 0, 1500);
		} catch (RuntimeException e) {
			c.note("the dragon did not finish dying in time: " + e);
		}
		c.check("the fight is over", server.computeOnServer(s -> DragonService.world(s).active()) == 0);
		c.check("but the kill count is still 1", kills(server) == 1, String.valueOf(kills(server)));
		c.check("and the player was not rewarded for dragon 2", !server.computeOnServer(s -> DragonService.player(s.getPlayerList().getPlayers().get(0)).has(2)));

		c.section("After dragon 6 the respawn is plain");
		server.runCommand("vestiges dragon count 6");
		ring(server, portal);
		ctx.waitTicks(5);
		InteractionResult plain = place(server, portal);
		c.check("no gate for a dragon that has no data: the crystal goes down", plain.consumesAction(), String.valueOf(plain));
		c.check("a vanilla dragon appears", waitForDragon(server, 0, 2400));
		Snap vanilla = dragon(server);
		c.check("it has 200 health, no mark and no new name", vanilla.present() && !vanilla.marked() && Math.abs(vanilla.max() - 200.0) < 0.5 && vanilla.name().isEmpty(),
			vanilla.toString());
		server.runCommand("vestiges dragon count 0");
	}
}
