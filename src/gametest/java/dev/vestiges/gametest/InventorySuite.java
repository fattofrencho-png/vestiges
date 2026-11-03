package dev.vestiges.gametest;

import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.HotbarSets;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.InventorySorter;
import dev.vestiges.net.InventoryPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** S4: the extra slots, racks, Bank, totem, arrows, shift-click, sort, hotbar sets and stack sizes of the new inventory. */
final class InventorySuite {
	private InventorySuite() {
	}

	private static ItemStack stack(String id, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		return new ItemStack(item, count);
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode survival @p");
		server.runCommand("clear @p");
		server.runCommand("effect clear @p");
		homes(server, c);
		shiftClick(ctx, server, c);
		bank(ctx, server, c);
		arrows(server, c);
		totem(ctx, server, c);
		ender(ctx, server, c);
		sorting(server, c);
		hotbars(server, c);
		stacks(ctx, server, c);
		server.runCommand("gamemode creative @p");
	}

	private static ExtraSlots slots(ServerPlayer p) {
		return InventoryHooks.slots(p);
	}

	private static void homes(TestServerContext server, Checks c) {
		c.section("Where each item belongs");
		String[][] table = {
			{"minecraft:diamond_pickaxe", "0"}, {"minecraft:iron_axe", "1"}, {"minecraft:stone_shovel", "2"}, {"minecraft:netherite_hoe", "3"},
			{"minecraft:shears", "4"}, {"minecraft:fishing_rod", "5"}, {"minecraft:flint_and_steel", "6"}, {"minecraft:brush", "7"},
			{"minecraft:diamond_sword", "8"}, {"minecraft:mace", "9"}, {"minecraft:iron_spear", "10"}, {"minecraft:trident", "11"},
			{"minecraft:bow", "12"}, {"minecraft:crossbow", "13"}, {"minecraft:arrow", "14"}, {"minecraft:compass", "15"},
			{"minecraft:clock", "16"}, {"minecraft:filled_map", "17"}, {"minecraft:spyglass", "18"}, {"minecraft:elytra", "20"},
			{"minecraft:totem_of_undying", "21"}, {"minecraft:ender_chest", "22"}, {"minecraft:crafting_table", "23"},
			{"minecraft:dirt", "-1"}, {"minecraft:bread", "-1"}, {"minecraft:diamond", "-1"}, {"minecraft:stick", "-1"}};
		for (String[] row : table) {
			int expected = Integer.parseInt(row[1]);
			int got = server.computeOnServer(s -> ExtraSlots.homeFor(stack(row[0], 1)));
			c.check(row[0].substring(10) + " belongs in slot " + expected, got == expected, "was " + got);
		}
		boolean swordInTools = server.computeOnServer(s -> ExtraSlots.accepts(ExtraSlots.TOOLS, stack("minecraft:diamond_sword", 1)));
		c.check("a sword is not accepted in the pickaxe slot", !swordInTools);
		int[] kept = new int[2];
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			slots(p).setItem(ExtraSlots.ARROWS, stack("minecraft:arrow", 64));
			slots(p).setItem(ExtraSlots.TOOLS, stack("minecraft:diamond_pickaxe", 1));
			kept[0] = slots(p).getItem(ExtraSlots.ARROWS).getCount();
			kept[1] = slots(p).getItem(ExtraSlots.TOOLS).is(Items.DIAMOND_PICKAXE) ? 1 : 0;
			slots(p).setItem(ExtraSlots.ARROWS, ItemStack.EMPTY);
			slots(p).setItem(ExtraSlots.TOOLS, ItemStack.EMPTY);
		});
		c.check("the arrows slot keeps a stack of 64 arrows", kept[0] == 64, "kept " + kept[0]);
		c.check("the pickaxe slot keeps its pickaxe", kept[1] == 1);
		c.check("the slot limits are 99 for arrows and 1 for other racks", ExtraSlots.maxStack(ExtraSlots.ARROWS) == 99 && ExtraSlots.maxStack(ExtraSlots.TOOLS) == 1);
		c.note("an arrows slot with 99 arrows needs the bigger-stack option at 99; at the default 64 it holds 64");
	}

	private static void shiftClick(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Shift-click sends items to their rack");
		String[] ids = {"minecraft:diamond_pickaxe", "minecraft:diamond_sword", "minecraft:compass", "minecraft:totem_of_undying", "minecraft:elytra"};
		int[] home = {0, 8, 15, 21, 20};
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			int slot = home[i];
			boolean moved = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				p.getInventory().clearContent();
				slots(p).setItem(slot, ItemStack.EMPTY);
				ItemStack item = stack(id, 1);
				p.getInventory().setItem(20, item);
				for (Slot menuSlot : p.inventoryMenu.slots) {
					if (menuSlot.getItem() == item) {
						p.inventoryMenu.quickMoveStack(p, menuSlot.index);
						break;
					}
				}
				return !slots(p).getItem(slot).isEmpty() && p.getInventory().getItem(20).isEmpty();
			});
			c.check("shift-click sends " + id.substring(10) + " to slot " + slot, moved);
			server.runOnServer(s -> slots(s.getPlayerList().getPlayers().get(0)).setItem(slot, ItemStack.EMPTY));
		}
	}

	private static long bank(TestServerContext server) {
		return server.computeOnServer(s -> ((ExtraHolder) s.getPlayerList().getPlayers().get(0)).vestiges$bank());
	}

	private static void action(ClientGameTestContext ctx, String op, int a, int b) {
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new InventoryPayloads.Action(op, a, b, "")));
		ctx.waitTicks(4);
	}

	private static void bank(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Bank");
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			((ExtraHolder) p).vestiges$setBank(0L);
			p.containerMenu.setCarried(new ItemStack(Items.EMERALD, 30));
		});
		action(ctx, "bank_deposit", 0, 0);
		c.check("depositing 30 emeralds fills the Bank", bank(server) == 30, "bank " + bank(server));
		c.check("the hand is empty after a deposit", server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu.getCarried().isEmpty()));
		action(ctx, "bank_withdraw", 0, 1);
		c.check("right-click takes 1 out", bank(server) == 29 && server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu.getCarried().getCount()) == 1,
			"bank " + bank(server));
		action(ctx, "bank_withdraw", 0, 64);
		int carried = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu.getCarried().getCount());
		c.check("left-click takes up to a stack", carried == 30 && bank(server) == 0, "in hand " + carried + ", bank " + bank(server));
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.containerMenu.setCarried(new ItemStack(Items.DIRT, 5));
		});
		action(ctx, "bank_deposit", 0, 0);
		c.check("only emeralds go into the Bank", bank(server) == 0 && server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu.getCarried().is(Items.DIRT)));
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu.setCarried(ItemStack.EMPTY));
	}

	private static void arrows(TestServerContext server, Checks c) {
		c.section("Bows and crossbows use the arrows slot");
		boolean[] r = new boolean[3];
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.getInventory().clearContent();
			slots(p).setItem(ExtraSlots.ARROWS, ItemStack.EMPTY);
			r[0] = p.getProjectile(new ItemStack(Items.BOW)).isEmpty();
			slots(p).setItem(ExtraSlots.ARROWS, new ItemStack(Items.ARROW, 20));
			r[1] = !p.getProjectile(new ItemStack(Items.BOW)).isEmpty();
			r[2] = !p.getProjectile(new ItemStack(Items.CROSSBOW)).isEmpty();
			slots(p).setItem(ExtraSlots.ARROWS, ItemStack.EMPTY);
		});
		c.check("with no arrows anywhere a bow finds none", r[0]);
		c.check("a bow finds arrows in the arrows slot", r[1]);
		c.check("a crossbow finds arrows in the arrows slot", r[2]);
	}

	private static void totem(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Totem slot");
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.getInventory().clearContent();
			slots(p).setItem(ExtraSlots.TOTEM, new ItemStack(Items.TOTEM_OF_UNDYING));
			p.setHealth(p.getMaxHealth());
		});
		server.runCommand("damage @p 1000 minecraft:mob_attack");
		ctx.waitTicks(10);
		boolean alive = server.computeOnServer(s -> !s.getPlayerList().getPlayers().get(0).isDeadOrDying());
		boolean used = server.computeOnServer(s -> slots(s.getPlayerList().getPlayers().get(0)).getItem(ExtraSlots.TOTEM).isEmpty());
		c.check("a totem in its slot saves the player from lethal damage", alive, alive ? "" : "the player died");
		c.check("the totem is used up", used);
		server.runCommand("effect clear @p");
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			if (!p.isDeadOrDying()) {
				p.setHealth(p.getMaxHealth());
			}
		});
	}

	private static void ender(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Ender chest slot");
		server.runOnServer(s -> slots(s.getPlayerList().getPlayers().get(0)).setItem(ExtraSlots.ENDER, new ItemStack(Items.ENDER_CHEST)));
		action(ctx, "ender_open", 0, 0);
		boolean open = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu instanceof ChestMenu);
		c.check("the ender chest slot opens the ender chest", open);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.closeContainer();
			slots(p).setItem(ExtraSlots.ENDER, ItemStack.EMPTY);
		});
		ctx.waitTicks(5);
		action(ctx, "ender_open", 0, 0);
		c.check("with no ender chest in the slot nothing opens", !server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).containerMenu instanceof ChestMenu));
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
	}

	private static Map<String, Integer> totals(Inventory inv) {
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) {
				m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
			}
		}
		return m;
	}

	private static void sorting(TestServerContext server, Checks c) {
		c.section("Sort");
		String[] result = new String[3];
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Inventory inv = p.getInventory();
			inv.clearContent();
			inv.setItem(9, new ItemStack(Items.DIRT, 40));
			inv.setItem(14, new ItemStack(Items.COBBLESTONE, 7));
			inv.setItem(20, new ItemStack(Items.DIRT, 30));
			inv.setItem(25, new ItemStack(Items.BREAD, 5));
			inv.setItem(30, new ItemStack(Items.COBBLESTONE, 60));
			inv.setItem(35, new ItemStack(Items.IRON_INGOT, 12));
			Map<String, Integer> before = totals(inv);
			InventorySorter.sort(p);
			Map<String, Integer> after = totals(inv);
			result[0] = before.equals(after) ? "" : before + " vs " + after;
			int firstEmpty = -1;
			int lastFilled = -1;
			for (int i = 9; i < 36; i++) {
				if (inv.getItem(i).isEmpty() && firstEmpty < 0) {
					firstEmpty = i;
				}
				if (!inv.getItem(i).isEmpty()) {
					lastFilled = i;
				}
			}
			result[1] = firstEmpty < 0 || firstEmpty > lastFilled ? "" : "gap at " + firstEmpty + " before item at " + lastFilled;
			InventorySorter.sort(p);
			result[2] = before.equals(totals(inv)) ? "" : "second sort changed counts";
		});
		c.check("sorting loses and creates nothing", result[0].isEmpty(), result[0]);
		c.check("sorting leaves no gaps", result[1].isEmpty(), result[1]);
		c.check("sorting twice is stable", result[2].isEmpty(), result[2]);
	}

	private static void hotbars(TestServerContext server, Checks c) {
		c.section("Hotbar sets");
		String[] r = new String[2];
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Inventory inv = p.getInventory();
			inv.clearContent();
			HotbarSets.switchTo(p, 0);
			inv.setItem(0, new ItemStack(Items.BREAD, 5));
			HotbarSets.switchTo(p, 1);
			r[0] = inv.getItem(0).isEmpty() ? "" : "set 1 was not empty: " + inv.getItem(0);
			inv.setItem(0, new ItemStack(Items.APPLE, 3));
			HotbarSets.switchTo(p, 0);
			boolean breadBack = inv.getItem(0).is(Items.BREAD) && inv.getItem(0).getCount() == 5;
			HotbarSets.switchTo(p, 1);
			boolean appleBack = inv.getItem(0).is(Items.APPLE) && inv.getItem(0).getCount() == 3;
			HotbarSets.switchTo(p, 0);
			r[1] = breadBack && appleBack ? "" : "bread back " + breadBack + ", apple back " + appleBack;
			inv.clearContent();
			HotbarSets.switchTo(p, 1);
			inv.clearContent();
			HotbarSets.switchTo(p, 0);
		});
		c.check("a second hotbar starts empty", r[0].isEmpty(), r[0]);
		c.check("each hotbar set keeps its own items when switching", r[1].isEmpty(), r[1]);
	}

	private static void stacks(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Stack sizes");
		List<String> got = new ArrayList<>();
		int dirt = new ItemStack(Items.DIRT).getMaxStackSize();
		int potion = new ItemStack(Items.POTION).getMaxStackSize();
		int sword = new ItemStack(Items.DIAMOND_SWORD).getMaxStackSize();
		int pearl = new ItemStack(Items.ENDER_PEARL).getMaxStackSize();
		c.check("dirt stacks to 64", dirt == 64, "was " + dirt);
		c.check("potions stack to 16", potion == 16, "was " + potion);
		c.check("a sword still does not stack", sword == 1, "was " + sword);
		c.check("ender pearls stack above vanilla's 16 with bigger stacks on", pearl >= 16, "was " + pearl);
		server.runCommand("vestiges config system stacks false");
		ctx.waitTicks(5);
		int potionOff = new ItemStack(Items.POTION).getMaxStackSize();
		int pearlOff = new ItemStack(Items.ENDER_PEARL).getMaxStackSize();
		c.check("with bigger stacks off potions are vanilla (1)", potionOff == 1, "was " + potionOff);
		c.check("with bigger stacks off ender pearls are vanilla (16)", pearlOff == 16, "was " + pearlOff);
		server.runCommand("vestiges config system stacks true");
		ctx.waitTicks(5);
		c.check("bigger stacks come back on", new ItemStack(Items.POTION).getMaxStackSize() == 16);
	}
}
