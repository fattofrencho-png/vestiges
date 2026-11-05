package dev.vestiges.gametest;

import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.quest.QuestRules;
import dev.vestiges.quest.QuestService;
import dev.vestiges.quest.QuestState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * S9: the quest engine (start, steps, kills, rewards, standing, the switch, the offer gate) with the shipped guide and side quests, and the A2
 * crafting grid (2x2 without a crafting table, 3x3 with the table slot filled). Quest texts are drafts, so this checks behaviour, not wording.
 */
final class QuestSuite {
	private QuestSuite() {
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static QuestState state(TestServerContext server) {
		return server.computeOnServer(s -> QuestService.get(player(s)));
	}

	private static long totalXp(TestServerContext server) {
		return server.computeOnServer(s -> {
			PlayerLevel l = LevelService.get(player(s));
			return LevelCurve.totalTo(l.level()) + l.xp();
		});
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

	private static boolean start(TestServerContext server, String id) {
		return server.computeOnServer(s -> QuestService.adminStart(player(s), id));
	}

	private static void event(TestServerContext server, String type, String target, int amount, int times) {
		server.runOnServer(s -> {
			for (int i = 0; i < times; i++) {
				QuestService.event(player(s), type, target, amount);
			}
			QuestService.check(player(s));
		});
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode survival @p");
		server.runCommand("vestiges config system quests true");
		for (String id : new String[] {"a_little_stronger", "easy_harvest", "side_shepherd", "side_weaponsmith", "side_farmer", "test_note", "test_choice"}) {
			server.runOnServer(s -> QuestService.adminReset(player(s), id));
		}
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START));
		ctx.waitTicks(5);

		// ---------------------------------------------------------------- a guide quest with a level step
		c.section("Guide quest: A Little Stronger");
		c.check("it starts", start(server, "a_little_stronger"));
		c.check("it is running at level 1", state(server).active().containsKey("a_little_stronger") && !state(server).done("a_little_stronger"));
		c.check("starting it again does nothing", !start(server, "a_little_stronger"));
		long xp0 = totalXp(server);
		server.runOnServer(s -> {
			LevelService.set(player(s), PlayerLevel.START.withXp(2, 0, 0));
			QuestService.check(player(s));
		});
		ctx.waitTicks(5);
		c.check("reaching level 2 finishes it", state(server).done("a_little_stronger") && !state(server).active().containsKey("a_little_stronger"),
			"active " + state(server).active().keySet() + ", finished " + state(server).finished());
		c.check("its reward adds XP (25 in the file)", totalXp(server) > LevelCurve.totalTo(2), "xp " + xp0 + " -> " + totalXp(server));
		c.check("a finished guide quest cannot be taken again", !server.computeOnServer(s -> QuestService.accept(player(s), "a_little_stronger")));

		// ---------------------------------------------------------------- harvest steps and an item reward
		c.section("Guide quest: Easy Harvest (10 harvests, bread)");
		int bread0 = count(server, Items.BREAD);
		c.check("it starts", start(server, "easy_harvest"));
		event(server, "harvest", "minecraft:wheat", 1, 4);
		QuestState.Active a = state(server).active().get("easy_harvest");
		c.check("4 harvests count 4", a != null && a.count() == 4, a == null ? "not running" : "count " + a.count());
		event(server, "kill", "minecraft:zombie", 1, 3);
		a = state(server).active().get("easy_harvest");
		c.check("kills do not count toward a harvest step", a != null && a.count() == 4);
		event(server, "harvest", "minecraft:wheat", 1, 6);
		ctx.waitTicks(5);
		c.check("the 10th harvest finishes it", state(server).done("easy_harvest"), "active " + state(server).active().keySet());
		c.check("the reward gave the bread (8)", count(server, Items.BREAD) - bread0 == 8, "bread " + bread0 + " -> " + count(server, Items.BREAD));

		// ---------------------------------------------------------------- kill steps
		c.section("Side quest steps: kills");
		c.check("Shepherd's quest starts (admin)", start(server, "side_shepherd"));
		event(server, "kill", "minecraft:zombie", 1, 5);
		a = state(server).active().get("side_shepherd");
		c.check("5 kills of anything finish the kill step and move to the next", a != null && a.step() == 1, a == null ? "not running" : "step " + a.step() + ", count " + a.count());
		c.check("Weaponsmith's quest starts (admin)", start(server, "side_weaponsmith"));
		event(server, "kill", "minecraft:zombie", 1, 1);
		a = state(server).active().get("side_weaponsmith");
		c.check("an ordinary kill does not count toward an elite step", a != null && a.step() == 0 && a.count() == 0, a == null ? "not running" : "step " + a.step() + ", count " + a.count());
		event(server, "kill", "minecraft:zombie", 2, 1);
		a = state(server).active().get("side_weaponsmith");
		c.check("an elite kill counts", a != null && (a.step() == 1 || a.count() == 1), a == null ? "not running" : "step " + a.step() + ", count " + a.count());

		// ---------------------------------------------------------------- offers and the gate
		c.section("Offers and the accept gate");
		c.check("a villager quest cannot be accepted without an offer", !server.computeOnServer(s -> QuestService.accept(player(s), "side_farmer")));
		server.runOnServer(s -> QuestService.offer(player(s), "side_farmer", ""));
		c.check("after an offer it can be accepted", server.computeOnServer(s -> QuestService.accept(player(s), "side_farmer")));
		c.check("...and is then running", state(server).active().containsKey("side_farmer"));
		c.check("an unknown quest cannot be accepted", !server.computeOnServer(s -> QuestService.accept(player(s), "no_such_quest")));

		// ---------------------------------------------------------------- admin tools
		c.section("Admin: complete, reset, notes, choices");
		c.expectOk(server, "vestiges quest start test_note");
		c.check("a quest whose step is already true (be level 1) finishes as it starts", state(server).done("test_note"));
		c.expectOk(server, "vestiges quest reset test_note");
		c.check("reset makes it new again", !state(server).done("test_note") && !state(server).active().containsKey("test_note"));
		c.expectOk(server, "vestiges quest start easy_harvest");
		c.expectOk(server, "vestiges quest complete easy_harvest");
		c.check("complete finishes a running quest", state(server).done("easy_harvest"));
		c.expectOk(server, "vestiges quest reset easy_harvest");
		c.expectOk(server, "vestiges quest start test_choice");
		c.check("a choice quest waits for a choice", state(server).active().containsKey("test_choice") && !state(server).done("test_choice"));
		c.survey(server, "vestiges quest note test_note");
		c.expectOk(server, "vestiges quest reset test_choice");

		// ---------------------------------------------------------------- standing
		c.section("Reputation");
		server.runOnServer(s -> QuestService.adminReputation(player(s), "bright_path", 30));
		c.check("standing is set", state(server).standing().getOrDefault("bright_path", 0) == 30, "standing " + state(server).standing());
		server.runOnServer(s -> QuestService.adminReputation(player(s), "vitera", 500));
		c.check("standing is capped at " + QuestRules.MAX_REP, state(server).standing().getOrDefault("vitera", 0) == QuestRules.MAX_REP,
			"standing " + state(server).standing());
		server.runOnServer(s -> QuestService.adminReputation(player(s), "bright_path", -500));
		c.check("standing is capped below at -" + QuestRules.MAX_REP, state(server).standing().getOrDefault("bright_path", 0) == -QuestRules.MAX_REP);
		server.runOnServer(s -> {
			QuestService.adminReputation(player(s), "bright_path", 0);
			QuestService.adminReputation(player(s), "vitera", 0);
		});

		// ---------------------------------------------------------------- the switch
		c.section("Switch off");
		server.runCommand("vestiges config system quests false");
		ctx.waitTicks(3);
		c.check("the quests switch is off", !server.computeOnServer(s -> QuestService.enabled()));
		server.runOnServer(s -> QuestService.adminReset(player(s), "easy_harvest"));
		start(server, "easy_harvest");
		event(server, "harvest", "minecraft:wheat", 1, 3);
		QuestState.Active off = state(server).active().get("easy_harvest");
		c.check("with Quests off, events do not count", off == null || off.count() == 0, off == null ? "not running" : "count " + off.count());
		server.runCommand("vestiges config system quests true");
		server.runOnServer(s -> QuestService.adminReset(player(s), "easy_harvest"));
		ctx.waitTicks(3);
		c.check("the switch comes back on", server.computeOnServer(s -> QuestService.enabled()));

		grid(ctx, server, c);

		// Leave the player as the other suites expect: pockets empty, level 1, quests reset.
		for (String id : new String[] {"a_little_stronger", "easy_harvest", "side_shepherd", "side_weaponsmith", "side_farmer", "test_note", "test_choice"}) {
			server.runOnServer(s -> QuestService.adminReset(player(s), id));
		}
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START));
		server.runCommand("gamemode creative @p");
		c.note("not covered: talking to a real villager, hand-in of 'bring' steps, the offer and choice screens (client), titles worn, tab quests unlocking by turn, sharing progress between two players");
	}

	// ---------------------------------------------------------------- A2: the crafting grid

	/** Menu slot of the crafting grid's position 0 to 8 (row by row): vanilla's four keep slots 1-4, the table adds five at the end. */
	private static int gridSlot(InventoryMenu menu, int pos) {
		int extra = menu.slots.size() - 5;
		return switch (pos) {
			case 0 -> 1;
			case 1 -> 2;
			case 3 -> 3;
			case 4 -> 4;
			case 2 -> extra;
			case 5 -> extra + 1;
			case 6 -> extra + 2;
			case 7 -> extra + 3;
			default -> extra + 4;
		};
	}

	private static void fill(TestServerContext server, Item[] grid) {
		server.runOnServer(s -> {
			InventoryMenu menu = player(s).inventoryMenu;
			for (int pos = 0; pos < 9; pos++) {
				menu.slots.get(gridSlot(menu, pos)).set(grid[pos] == null ? ItemStack.EMPTY : new ItemStack(grid[pos]));
			}
		});
	}

	private static Item result(TestServerContext server) {
		return server.computeOnServer(s -> player(s).inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).getItem().getItem());
	}

	private static void grid(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("A2: crafting grid");
		Item p = Items.OAK_PLANKS;
		Item st = Items.STICK;
		Item[] pickaxe = {p, p, p, null, st, null, null, st, null};
		Item[] square = {p, p, null, p, p, null, null, null, null};
		server.runOnServer(s -> ((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY));
		ctx.waitTicks(3);
		boolean refuses = server.computeOnServer(s -> {
			InventoryMenu menu = player(s).inventoryMenu;
			return !menu.slots.get(gridSlot(menu, 2)).mayPlace(new ItemStack(Items.OAK_PLANKS));
		});
		c.check("without a table the extra grid slots take nothing", refuses);
		fill(server, square);
		ctx.waitTicks(3);
		c.check("without a table the 2x2 grid crafts a crafting table from 4 planks", result(server) == Items.CRAFTING_TABLE, "result " + result(server));
		fill(server, pickaxe);
		ctx.waitTicks(3);
		c.check("without a table a 3x3 recipe does not craft", result(server) != Items.WOODEN_PICKAXE, "result " + result(server));
		fill(server, new Item[9]);

		server.runOnServer(s -> ((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, new ItemStack(Items.CRAFTING_TABLE)));
		ctx.waitTicks(3);
		boolean accepts = server.computeOnServer(s -> {
			InventoryMenu menu = player(s).inventoryMenu;
			return menu.slots.get(gridSlot(menu, 2)).mayPlace(new ItemStack(Items.OAK_PLANKS));
		});
		c.check("with the table the extra grid slots take items", accepts);
		fill(server, pickaxe);
		ctx.waitTicks(3);
		c.check("with the table the 3x3 grid crafts a wooden pickaxe", result(server) == Items.WOODEN_PICKAXE, "result " + result(server));
		fill(server, square);
		ctx.waitTicks(3);
		c.check("with the table a 2x2 recipe still works", result(server) == Items.CRAFTING_TABLE, "result " + result(server));
		fill(server, new Item[9]);
		ctx.waitTicks(3);
		c.check("an empty grid has no result", server.computeOnServer(s -> player(s).inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty()));
		server.runOnServer(s -> ((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY));
		c.note("the Bank move, racks and shulker items are covered by the inventory suite (S4); the inventory screen's look is the UI room's");
	}
}
