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
		newSteps(ctx, server, c);

		// Leave the player as the other suites expect: pockets empty, level 1, quests reset.
		for (String id : new String[] {"a_little_stronger", "easy_harvest", "side_shepherd", "side_weaponsmith", "side_farmer", "test_note", "test_choice", "test_new_steps"}) {
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

	// ---------------------------------------------------------------- the steps and rewards added for quest content

	private static int stepOf(TestServerContext server, String id) {
		QuestState.Active a = state(server).active().get(id);
		return a == null ? -1 : a.step();
	}

	private static boolean killed(TestServerContext server, int level, int rank, java.util.List<String> powers) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			net.minecraft.server.level.ServerLevel lv = p.level();
			net.minecraft.world.entity.Entity e = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("husk")).spawn(lv, p.blockPosition().offset(3, 0, 0),
				net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (!(e instanceof net.minecraft.world.entity.Mob mob)) {
				return false;
			}
			dev.vestiges.mobs.MobService.apply(mob, new dev.vestiges.mobs.MobData(level, rank, powers, 1.0f, false));
			mob.hurtServer(lv, lv.damageSources().playerAttack(p), 100000f);
			boolean dead = mob.isDeadOrDying();
			mob.discard();
			QuestService.check(p);
			return dead;
		});
	}

	private static void chest(TestServerContext server, String table) {
		server.runOnServer(s -> {
			net.minecraft.advancements.triggers.CriteriaTriggers.GENERATE_LOOT.trigger(player(s), net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace(table)));
			QuestService.check(player(s));
		});
	}

	private static void newSteps(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Quest content: new steps, emerald tiers, gear and refunds");
		String id = "test_new_steps";
		server.runCommand("gamemode survival @p");
		server.runCommand("advancement revoke @p only minecraft:story/mine_stone");
		server.runOnServer(s -> {
			QuestService.adminReset(player(s), id);
			LevelService.set(player(s), PlayerLevel.START);
			((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY);
		});
		ctx.waitTicks(3);
		int emeralds0 = count(server, Items.EMERALD);
		c.check("the test quest starts", start(server, id));
		c.check("it waits on its first step", stepOf(server, id) == 0, "step " + stepOf(server, id));

		event(server, "kill", "minecraft:zombie", 1, 1);
		c.check("hub_open ignores other events", stepOf(server, id) == 0);
		event(server, "hub_open", "", 1, 1);
		c.check("hub_open: the event moves it on", stepOf(server, id) == 1, "step " + stepOf(server, id));
		event(server, "track_quest", id, 1, 1);
		c.check("track_quest: the event moves it on", stepOf(server, id) == 2, "step " + stepOf(server, id));

		ctx.waitTicks(2);
		c.check("table_slot: waits while the slot is empty", stepOf(server, id) == 2);
		server.runOnServer(s -> {
			((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, new ItemStack(Items.CRAFTING_TABLE));
			QuestService.check(player(s));
		});
		c.check("table_slot: a table in the slot moves it on", stepOf(server, id) == 3, "step " + stepOf(server, id));

		// grid_craft: take a result from the player's own inventory menu with the table slot filled.
		Item p = Items.OAK_PLANKS;
		Item st = Items.STICK;
		fill(server, new Item[] {p, p, p, null, st, null, null, st, null});
		ctx.waitTicks(3);
		server.runOnServer(s -> {
			ServerPlayer pl = player(s);
			pl.inventoryMenu.clicked(InventoryMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, pl);
			pl.inventoryMenu.setCarried(ItemStack.EMPTY);
			QuestService.check(pl);
		});
		c.check("grid_craft: taking a craft result with a table in the slot moves it on", stepOf(server, id) == 4, "step " + stepOf(server, id));
		fill(server, new Item[9]);

		// loot_chest: only the generation of a chests/ table counts; fishing and other tables don't.
		chest(server, "gameplay/fishing");
		c.check("loot_chest: a table outside chests/ does not count", stepOf(server, id) == 4 && state(server).active().get(id).count() == 0);
		chest(server, "chests/simple_dungeon");
		chest(server, "chests/simple_dungeon");
		ctx.waitTicks(2);
		c.check("loot_chest: two chests move it on", stepOf(server, id) == 5, "step " + stepOf(server, id));

		// Real kills: the event comes from the death of a mob with MobData (the player is level 1).
		c.check("a normal husk dies", killed(server, 5, 0, java.util.List.of()));
		c.check("kill_rank: a normal kill does not count", stepOf(server, id) == 5);
		c.check("a Champion husk dies", killed(server, 1, dev.vestiges.mobs.MobData.CHAMPION, java.util.List.of()));
		ctx.waitTicks(2);
		c.check("kill_rank: a Champion moves it on", stepOf(server, id) == 6, "step " + stepOf(server, id));
		c.check("a Warlord husk with Brute dies", killed(server, 1, dev.vestiges.mobs.MobData.WARLORD, java.util.List.of("brute")));
		c.check("kill_power: another power does not count", stepOf(server, id) == 6, "step " + stepOf(server, id));
		c.check("an Elite husk with Swift dies", killed(server, 1, dev.vestiges.mobs.MobData.ELITE, java.util.List.of("swift")));
		ctx.waitTicks(2);
		c.check("kill_power: the Swift power moves it on", stepOf(server, id) == 7, "step " + stepOf(server, id));
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START));
		c.check("a husk 2 levels above the player dies", killed(server, 3, 0, java.util.List.of()));
		c.check("kill_high_level: a gap of 2 does not count", stepOf(server, id) == 7, "step " + stepOf(server, id));
		// The kills above gave XP: put the player back at level 1 so the gap is what the check says.
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START));
		c.check("a husk 4 levels above the player dies", killed(server, 5, 0, java.util.List.of()));
		ctx.waitTicks(2);
		c.check("kill_high_level: a gap of 4 moves it on", stepOf(server, id) == 8, "step " + stepOf(server, id));

		// advancement and stat steps
		ctx.waitTicks(45);
		c.check("advancement: waits while the player lacks it", stepOf(server, id) == 8, "step " + stepOf(server, id));
		server.runCommand("advancement grant @p only minecraft:story/mine_stone");
		server.runOnServer(s -> QuestService.check(player(s)));
		ctx.waitTicks(3);
		c.check("advancement: having it moves it on", stepOf(server, id) == 9, "step " + stepOf(server, id));
		server.runOnServer(s -> {
			player(s).awardStat(net.minecraft.stats.Stats.JUMP, 2);
			QuestService.check(player(s));
		});
		c.check("stat: 2 of 3 jumps wait", stepOf(server, id) == 9, "step " + stepOf(server, id));
		server.runOnServer(s -> {
			player(s).awardStat(net.minecraft.stats.Stats.JUMP, 1);
			QuestService.check(player(s));
		});
		c.check("stat: the 3rd jump moves it on", stepOf(server, id) == 10, "step " + stepOf(server, id));
		int refunds0 = server.computeOnServer(s -> dev.vestiges.tree.TreeService.freeRefunds(player(s)));
		server.runOnServer(s -> {
			player(s).awardStat(net.minecraft.stats.Stats.TRADED_WITH_VILLAGER, 1);
			QuestService.check(player(s));
		});
		ctx.waitTicks(3);
		c.check("trade: a villager trade finishes the quest", state(server).done(id), "active " + state(server).active().keySet());
		c.check("reward: 5 emeralds at level 1 (tier x1)", count(server, Items.EMERALD) - emeralds0 == 5, "emeralds " + emeralds0 + " -> " + count(server, Items.EMERALD));
		c.check("reward: two free tree refunds", server.computeOnServer(s -> dev.vestiges.tree.TreeService.freeRefunds(player(s))) - refunds0 == 2);
		c.check("reward: an Uncommon piece of gear", server.computeOnServer(s -> {
			for (ItemStack stack : player(s).getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty() && dev.vestiges.gear.Rarity.applies(stack) && dev.vestiges.gear.Rarity.of(stack).tier() == dev.vestiges.gear.Rarity.Tier.UNCOMMON) {
					return true;
				}
			}
			return false;
		}));

		// Emerald tiers: the same quest pays 4 times as much at level 30 (price tier x4).
		server.runOnServer(s -> {
			QuestService.adminReset(player(s), "test_note");
			LevelService.set(player(s), PlayerLevel.START.withXp(30, 0, 0));
		});
		int e1 = count(server, Items.EMERALD);
		start(server, "test_note");
		ctx.waitTicks(2);
		c.check("tiers: test_note (9 emeralds) pays 9 x 4 at level 30", count(server, Items.EMERALD) - e1 == 36, "emeralds " + e1 + " -> " + count(server, Items.EMERALD));

		// XP with Levels off: vanilla XP instead.
		server.runOnServer(s -> {
			QuestService.adminReset(player(s), "test_note");
			LevelService.set(player(s), PlayerLevel.START);
		});
		server.runCommand("vestiges config system character false");
		ctx.waitTicks(3);
		int xp0 = server.computeOnServer(s -> player(s).totalExperience);
		int e2 = count(server, Items.EMERALD);
		start(server, "test_note");
		ctx.waitTicks(2);
		int xp1 = server.computeOnServer(s -> player(s).totalExperience);
		c.check("Levels off: quest XP is paid as vanilla XP", xp1 > xp0, "vanilla xp " + xp0 + " -> " + xp1);
		c.check("Levels off: emeralds are not multiplied", count(server, Items.EMERALD) - e2 == 9, "emeralds " + e2 + " -> " + count(server, Items.EMERALD));
		server.runCommand("vestiges config system character true");
		ctx.waitTicks(3);

		server.runOnServer(s -> {
			QuestService.adminReset(player(s), id);
			QuestService.adminReset(player(s), "test_note");
			((ExtraHolder) player(s)).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY);
		});
		c.note("not covered by machine: hub_open and track_quest are sent by the client (Quests tab, Track button), the checks send the same events directly; a real chest, a real craft click and a real villager trade are for the hand test");
	}
}
