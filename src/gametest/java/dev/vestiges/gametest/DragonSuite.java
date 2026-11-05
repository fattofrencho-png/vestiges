package dev.vestiges.gametest;

import dev.vestiges.Vestiges;
import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.codex.CodexService;
import dev.vestiges.data.VestigesData;
import dev.vestiges.dragon.DragonDef;
import dev.vestiges.dragon.DragonMark;
import dev.vestiges.dragon.DragonMoves;
import dev.vestiges.dragon.DragonMoves2;
import dev.vestiges.dragon.DragonService;
import dev.vestiges.dragon.PlayerDragons;
import dev.vestiges.dungeon.DungeonService;
import dev.vestiges.feats.FeatService;
import dev.vestiges.inventory.EnderPages;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.InventoryService;
import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.LevelService;
import dev.vestiges.mixin.EnderDragonFightAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
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
		} catch (RuntimeException | AssertionError e) {
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

	private static long totalXp(TestServerContext server) {
		return server.computeOnServer(s -> {
			var l = LevelService.get(s.getPlayerList().getPlayers().get(0));
			return LevelCurve.totalTo(l.level()) + l.xp();
		});
	}

	private static int carried(TestServerContext server, net.minecraft.world.item.Item item) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			int n = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(item)) {
					n += p.getInventory().getItem(i).getCount();
				}
			}
			return n;
		});
	}

	/** The highest enchantment level on any enchanted book the player carries. */
	private static int bestBookLevel(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			int best = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack stack = p.getInventory().getItem(i);
				var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
				if (stack.is(Items.ENCHANTED_BOOK) && stored != null) {
					for (var e : stored.entrySet()) {
						best = Math.max(best, e.getIntValue());
					}
				}
			}
			return best;
		});
	}

	/** The pillar crystals standing on the first, second... pillars (by the order the repair uses). */
	private static int crystalsOnPillars(TestServerContext server, int from, int to) {
		return server.computeOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			var spikes = EndSpikeFeature.getSpikesForLevel(end);
			int n = 0;
			for (int i = from; i < Math.min(to, spikes.size()); i++) {
				n += end.getEntitiesOfClass(EndCrystal.class, spikes.get(i).getTopBoundingBox()).size();
			}
			return n;
		});
	}

	private static void breakPillarCrystals(TestServerContext server, int from, int to) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			var spikes = EndSpikeFeature.getSpikesForLevel(end);
			for (int i = from; i < Math.min(to, spikes.size()); i++) {
				end.getEntitiesOfClass(EndCrystal.class, spikes.get(i).getTopBoundingBox()).forEach(net.minecraft.world.entity.Entity::discard);
			}
		});
	}

	/** The exit portal, or (before one has been found) where the fountain's bedrock pillar stands. */
	private static BlockPos portalOrFallback(TestServerContext server) {
		BlockPos portal = portal(server);
		if (portal != null) {
			return portal;
		}
		return server.computeOnServer(s -> new BlockPos(0, s.getLevel(Level.END).getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) - 4, 0));
	}

	private static boolean waitGone(TestServerContext server, int ticks) {
		try {
			server.waitFor(s -> {
				ServerLevel end = s.getLevel(Level.END);
				var fight = end == null ? null : end.getDragonFight();
				var id = fight == null ? null : fight.dragonUUID();
				return id == null || !(end.getEntity(id) instanceof EnderDragon d) || d.isRemoved();
			}, ticks);
			return true;
		} catch (RuntimeException | AssertionError e) {
			return false;
		}
	}

	private static boolean waitUntil(TestServerContext server, java.util.function.Predicate<net.minecraft.server.MinecraftServer> test, int ticks) {
		try {
			server.waitFor(test::test, ticks);
			return true;
		} catch (RuntimeException | AssertionError e) {
			return false;
		}
	}

	private static void die(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("kill @p");
		ctx.waitForScreen(DeathScreen.class);
		ctx.waitTicks(10);
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null, 600);
		ctx.waitTicks(20);
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
		long xpBefore = totalXp(server);
		DragonService.repairStepOverride = 20;
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
		c.check("the fighter was paid XP (a level's worth at level 4 and the feat)", totalXp(server) - xpBefore >= LevelCurve.need(4), (totalXp(server) - xpBefore) + " XP");
		c.check("32 emeralds (32 x 1)", carried(server, Items.EMERALD) == 32, String.valueOf(carried(server, Items.EMERALD)));
		c.check("a Level VI enchanted book", bestBookLevel(server) >= 6, "best level " + bestBookLevel(server));
		c.check("the lore chapter is in the Codex", server.computeOnServer(s -> CodexService.get(s.getPlayerList().getPlayers().get(0)).stage("lore/tulva")) == 1);
		ctx.waitTicks(60);
		c.check("the Dragonslayer feat is done", server.computeOnServer(s -> FeatService.get(s.getPlayerList().getPlayers().get(0)).done().contains("dragonslayer")));
		c.check("so the End dungeon theme is open", server.computeOnServer(s -> DungeonService.unlocked(s.getPlayerList().getPlayers().get(0),
			VestigesData.DUNGEON_THEMES.get(Vestiges.id("end")))));

		c.section("The arena repairs itself");
		breakPillarCrystals(server, 7, 10);
		c.check("three pillar crystals are gone", crystalsOnPillars(server, 7, 10) == 0, String.valueOf(crystalsOnPillars(server, 7, 10)));
		c.check("the repair runs and ends", waitUntil(server, s -> !DragonService.repairing(), 900));
		c.check("the three crystals are back", crystalsOnPillars(server, 7, 10) == 3, String.valueOf(crystalsOnPillars(server, 7, 10)));
		DragonService.repairStepOverride = -1;

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

	/** S12b: a fight that is lost or abandoned. The empty arena resets it; if everyone dies the dragon heals, leaves, and the crystals can call it again. */
	static void lost(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges level set @p 5");
		server.runCommand("execute in minecraft:the_end run tp @p 0 90 0");

		c.section("An empty arena resets the fight");
		c.check("dragon 1 appears", waitForDragon(server, 1, 1200));
		hit(server, 1000f, 0, -1, 0);
		Snap hurt = dragon(server);
		c.check("it is hurt", hurt.health() < hurt.max() - 1, hurt.health() + " of " + hurt.max());
		c.check("the player is credited", server.computeOnServer(s -> DragonService.world(s).hurt().size()) == 1);
		DragonService.emptyResetOverride = 100;
		server.runCommand("gamemode spectator @p");
		c.check("with nobody in the arena its health comes back", waitUntil(server, s -> {
			ServerLevel end = s.getLevel(Level.END);
			EnderDragon d = (EnderDragon) end.getEntity(end.getDragonFight().dragonUUID());
			return d != null && d.getHealth() >= d.getMaxHealth() - 0.01f;
		}, 600));
		c.check("nobody is credited any more", server.computeOnServer(s -> DragonService.world(s).hurt().isEmpty()));
		c.check("the dragon stays", dragon(server).present() && dragon(server).number() == 1);
		DragonService.emptyResetOverride = -1;

		c.section("Everyone dies");
		server.runCommand("gamemode creative @p");
		ctx.waitTicks(30);
		hit(server, 1000f, 0, -1, 0);
		c.check("hurt again", dragon(server).health() < dragon(server).max() - 1);

		// Ender chest pages: the second page holds 5 diamonds, opens as a chest, is saved, and is still there after the death below.
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			DragonService.setPlayer(p, PlayerDragons.EMPTY.with(9));
			((ExtraHolder) p).vestiges$pages().container(1).setItem(0, new ItemStack(Items.DIAMOND, 5));
		});
		c.check("one rewarded dragon makes 2 pages", pages(server) == 2, String.valueOf(pages(server)));
		String opened = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			InventoryService.openPage(p, 1);
			boolean chest = p.containerMenu instanceof ChestMenu m && m.getContainer() == ((ExtraHolder) p).vestiges$pages().container(1);
			return chest + " | " + p.containerMenu.getSlot(0).getItem().getCount() + " | " + net.minecraft.world.item.Items.DIAMOND.equals(p.containerMenu.getSlot(0).getItem().getItem());
		});
		c.check("page 2 opens as a chest that shows the diamonds", opened.startsWith("true | 5 | true"), opened);
		boolean stayed = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var before = p.containerMenu;
			InventoryService.openPage(p, 2);
			return p.containerMenu == before;
		});
		c.check("page 3 is still locked and does not open", stayed);
		String saved = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var ops = s.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
			var tag = EnderPages.CODEC.encodeStart(ops, ((ExtraHolder) p).vestiges$pages().toList()).getOrThrow();
			var back = EnderPages.CODEC.parse(ops, tag).getOrThrow();
			return back.size() + " page(s), " + back.get(0).page() + ", " + back.get(0).items().get(0).getCount() + " x " + back.get(0).items().get(0).getItem();
		});
		c.check("the page survives being saved and loaded", saved.startsWith("1 page(s), 1, 5 x"), saved);
		// Switching pages from the open ender chest, as a click on the page list does: first page, then ask for page 2.
		server.runOnServer(s -> InventoryService.openPage(s.getPlayerList().getPlayers().get(0), 0));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> ClientInventory.send("ender_page", 1, 0, ""));
		ctx.waitTicks(15);
		c.check("asking for page 2 from the open ender chest switches to it", server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return p.containerMenu instanceof ChestMenu m && m.getContainer() == ((ExtraHolder) p).vestiges$pages().container(1);
		}));
		ctx.runOnClient(mc -> ClientInventory.send("ender_page", 5, 0, ""));
		ctx.waitTicks(10);
		c.check("asking for a locked page changes nothing", server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return p.containerMenu instanceof ChestMenu m && m.getContainer() == ((ExtraHolder) p).vestiges$pages().container(1);
		}));
		ctx.takeScreenshot("dragons-ender-page-2");
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
		die(ctx, server);
		ctx.waitTicks(40);
		c.check("the fight is over and nothing was counted", server.computeOnServer(s -> DragonService.world(s).active()) == 0 && kills(server) == 0);
		c.check("nobody got a reward", !server.computeOnServer(s -> DragonService.player(s.getPlayerList().getPlayers().get(0)).has(1)));
		c.check("the ender chest page and its diamonds survived the death", server.computeOnServer(s ->
			((ExtraHolder) s.getPlayerList().getPlayers().get(0)).vestiges$pages().container(1).getItem(0).getCount()) == 5 && pages(server) == 2);
		c.check("the End is open for a new summoning", server.computeOnServer(s -> ((EnderDragonFightAccessor) s.getLevel(Level.END).getDragonFight()).vestiges$killed()));
		c.check("the pillar repair has begun", DragonService.repairing());
		c.check("the boss bar is hidden", !server.computeOnServer(s -> ((EnderDragonFightAccessor) s.getLevel(Level.END).getDragonFight()).vestiges$bar().isVisible()));

		c.section("Four crystals call it again");
		DragonService.repairStepOverride = 20;
		server.runCommand("execute in minecraft:the_end run tp @p 0 90 0");
		ctx.waitTicks(40);
		BlockPos portal = portalOrFallback(server);
		ring(server, portal);
		ctx.waitTicks(5);
		InteractionResult placed = place(server, portal);
		c.check("dragon 1 has no gate: the crystal goes down", placed.consumesAction(), placed + " | " + rimReport(server, portal));
		c.check("Tulva comes back", waitForDragon(server, 1, 2400));
		c.check("with the full 200 health", Math.abs(dragon(server).max() - 200.0) < 0.5 && dragon(server).health() > 199);
		DragonService.repairStepOverride = -1;
	}

	/** Makes the dragon in the End act as dragon {@code number} (its moves), without a summoning. */
	private static void actAs(TestServerContext server, int number) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			EnderDragon d = (EnderDragon) end.getEntity(end.getDragonFight().dragonUUID());
			d.setAttached(DragonService.MARK, new DragonMark(number, 1, false));
			DragonMoves.reset();
		});
	}

	private static void phase(TestServerContext server, net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase<?> phase) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			((EnderDragon) end.getEntity(end.getDragonFight().dragonUUID())).getPhaseManager().setPhase(phase);
		});
	}

	/** Puts the player this far east of the dragon, at its height, held in the air. */
	private static void besideDragon(TestServerContext server, double east) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			EnderDragon d = (EnderDragon) end.getEntity(end.getDragonFight().dragonUUID());
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.setNoGravity(true);
			p.setDeltaMovement(Vec3.ZERO);
			p.setPos(d.getX() + east, d.getY(), d.getZ());
		});
	}

	private static void hitCrystals(TestServerContext server, int from, int to) {
		server.runOnServer(s -> {
			ServerLevel end = s.getLevel(Level.END);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var spikes = EndSpikeFeature.getSpikesForLevel(end);
			for (int i = from; i < Math.min(to, spikes.size()); i++) {
				for (EndCrystal crystal : end.getEntitiesOfClass(EndCrystal.class, spikes.get(i).getTopBoundingBox())) {
					crystal.hurtServer(end, end.damageSources().playerAttack(p), 10f);
				}
			}
		});
	}

	/** S12c: the signature moves of dragons 2 to 4 (Belaz, Morst, Dhars). The dragon in the End takes on each dragon's moves in turn. */
	static void moves(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges level set @p 30");
		server.runCommand("execute in minecraft:the_end run tp @p 0 90 0");
		c.check("dragon 1 appears", waitForDragon(server, 1, 1200));
		c.check("dragon 1 has no moves of its own", server.computeOnServer(s -> DragonService.def(1).moves().isEmpty()));

		c.section("Belaz: the ring of fire when it perches");
		actAs(server, 2);
		phase(server, EnderDragonPhase.HOLDING_PATTERN);
		ctx.waitTicks(30);
		int ringsBefore = DragonMoves.rings;
		server.runCommand("gamemode survival @p");
		float healthBefore = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getHealth());
		besideDragon(server, 6);
		phase(server, EnderDragonPhase.SITTING_SCANNING);
		c.check("the perch starts a ring", waitUntil(server, s -> DragonMoves.rings > ringsBefore, 100), DragonMoves.rings + " rings");
		c.check("a player in the ring burns and is hurt", waitUntil(server, s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return p.getHealth() < healthBefore - 0.5f || p.isDeadOrDying();
		}, 120));
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setNoGravity(false));
		server.runCommand("gamemode creative @p");
		server.runCommand("effect clear @p");

		c.section("Morst: the wing shove");
		actAs(server, 3);
		phase(server, EnderDragonPhase.HOLDING_PATTERN);
		int shovesBefore = DragonMoves.shoves;
		// The dragon flies on during the one-second warning, so keep the player beside it until the shove has fired.
		for (int i = 0; i < 80 && DragonMoves.shoves == shovesBefore; i++) {
			besideDragon(server, 6);
			ctx.waitTicks(2);
		}
		c.check("a player near the flying dragon is warned and then shoved", DragonMoves.shoves > shovesBefore, DragonMoves.shoves + " shoves");
		double sideways = Math.hypot(DragonMoves.lastShove.x, DragonMoves.lastShove.z);
		c.check("the shove is sideways at the agreed speed with a little lift", Math.abs(sideways - 1.4) < 0.01 && Math.abs(DragonMoves.lastShove.y - 0.35) < 0.01,
			DragonMoves.lastShove.toString());
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setNoGravity(false));

		c.section("Dhars: the last three destroyed crystals grow back, twice at most");
		actAs(server, 4);
		DragonMoves.regrowDelayOverride = 100;
		DragonMoves.regrowWarnOverride = 40;
		c.check("ten pillar crystals stand", crystalsOnPillars(server, 0, 10) == 10, String.valueOf(crystalsOnPillars(server, 0, 10)));
		hitCrystals(server, 0, 3);
		ctx.waitTicks(5);
		c.check("three are destroyed", crystalsOnPillars(server, 0, 3) == 0, String.valueOf(crystalsOnPillars(server, 0, 3)));
		c.check("after the wait they grow back", waitUntil(server, s -> DragonMoves.wavesRun() == 1, 400));
		c.check("all three stand again", crystalsOnPillars(server, 0, 3) == 3, String.valueOf(crystalsOnPillars(server, 0, 3)));
		hitCrystals(server, 0, 3);
		c.check("a second wave grows them back again", waitUntil(server, s -> DragonMoves.wavesRun() == 2, 400));
		c.check("all three stand again after the second wave", crystalsOnPillars(server, 0, 3) == 3, String.valueOf(crystalsOnPillars(server, 0, 3)));
		hitCrystals(server, 0, 3);
		ctx.waitTicks(260);
		c.check("a third time nothing grows back", crystalsOnPillars(server, 0, 3) == 0 && DragonMoves.wavesRun() == 2,
			crystalsOnPillars(server, 0, 3) + " crystals, " + DragonMoves.wavesRun() + " waves");
		DragonMoves.regrowDelayOverride = -1;
		DragonMoves.regrowWarnOverride = -1;

		c.section("Durhos: the shade flickers in flight and darkens the End when it perches");
		actAs(server, 6);
		phase(server, EnderDragonPhase.HOLDING_PATTERN);
		int hiddenBefore = DragonMoves2.hidden;
		int seenHidden = 0;
		int seenVisible = 0;
		for (int i = 0; i < 60; i++) {
			boolean invisible = server.computeOnServer(s -> {
				ServerLevel end = s.getLevel(Level.END);
				return end.getEntity(end.getDragonFight().dragonUUID()).isInvisible();
			});
			seenHidden += invisible ? 1 : 0;
			seenVisible += invisible ? 0 : 1;
			ctx.waitTicks(5);
		}
		c.check("in flight it goes out of sight and comes back", seenHidden > 0 && seenVisible > 0 && DragonMoves2.hidden > hiddenBefore,
			seenHidden + " hidden, " + seenVisible + " visible, " + (DragonMoves2.hidden - hiddenBefore) + " flickers");
		int darkBefore = DragonMoves2.darkened;
		besideDragon(server, 10);
		int sat = 0;
		int hiddenWhileSitting = 0;
		boolean dark = false;
		for (int i = 0; i < 20 && !dark; i++) {
			phase(server, EnderDragonPhase.SITTING_SCANNING);
			ctx.waitTicks(3);
			boolean[] now = server.computeOnServer(s -> {
				ServerLevel end = s.getLevel(Level.END);
				EnderDragon d = (EnderDragon) end.getEntity(end.getDragonFight().dragonUUID());
				boolean sitting = d.getPhaseManager().getCurrentPhase().isSitting();
				return new boolean[] {sitting, sitting && d.isInvisible(),
					s.getPlayerList().getPlayers().get(0).hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)};
			});
			sat += now[0] ? 1 : 0;
			hiddenWhileSitting += now[1] ? 1 : 0;
			dark = now[2] && DragonMoves2.darkened > darkBefore;
		}
		c.check("when it perches the players near are in Darkness", dark, "sat " + sat + " times, darkened " + (DragonMoves2.darkened - darkBefore));
		c.check("and while it sits it is plainly visible", sat > 0 && hiddenWhileSitting == 0, sat + " samples sitting, " + hiddenWhileSitting + " of them invisible");
		server.runCommand("effect clear @p");
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setNoGravity(false));

		c.section("Ilvig: phantoms join while it perches, five at most");
		actAs(server, 5);
		DragonMoves2.swarmEveryOverride = 20;
		int swarmedBefore = DragonMoves2.swarmed;
		int most = 0;
		for (int i = 0; i < 16; i++) {
			phase(server, EnderDragonPhase.SITTING_SCANNING);
			ctx.waitTicks(20);
			most = Math.max(most, server.computeOnServer(s -> DragonMoves2.swarm(s.getLevel(Level.END)).size()));
		}
		c.check("two came at the first perch", DragonMoves2.swarmed - swarmedBefore >= 2, (DragonMoves2.swarmed - swarmedBefore) + " spawned");
		c.check("they pile up to five and no further", most == 5, "most alive " + most);
		DragonMoves2.swarmEveryOverride = -1;

		c.section("The swarm burns away with the dragon");
		server.runCommand("execute in minecraft:the_end run kill @e[type=minecraft:ender_dragon]");
		c.check("the fight ends", waitUntil(server, s -> DragonService.world(s).active() == 0, 1500));
		c.check("no phantom of the swarm is left", server.computeOnServer(s -> DragonMoves2.swarm(s.getLevel(Level.END)).size()) == 0);
	}
}
