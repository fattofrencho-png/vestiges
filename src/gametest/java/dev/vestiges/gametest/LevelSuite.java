package dev.vestiges.gametest;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.net.TreePayloads;
import dev.vestiges.tree.PlayerTree;
import dev.vestiges.tree.TreeDefs;
import dev.vestiges.tree.TreeRules;
import dev.vestiges.tree.TreeService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** S3: XP and levels follow the curve, the passive trees obey their rules over the real network, gear and enchants follow the ladder. */
final class LevelSuite {
	private LevelSuite() {
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode survival @p");
		server.runCommand("clear @p");
		levels(ctx, server, c);
		trees(ctx, server, c);
		gear(ctx, server, c);
		enchants(ctx, server, c);
		server.runCommand("gamemode creative @p");
	}

	// ------------------------------------------------------------------ levels

	private static PlayerLevel level(TestServerContext server) {
		return server.computeOnServer(s -> LevelService.get(s.getPlayerList().getPlayers().get(0)));
	}

	private static void setLevel(TestServerContext server, int level, long xp, int paragon) {
		server.runOnServer(s -> LevelService.set(s.getPlayerList().getPlayers().get(0), PlayerLevel.START.withXp(level, xp, paragon)));
	}

	private static void levels(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Levels and XP");
		var snapshot = T.switches();
		c.check("levels are switched on", server.computeOnServer(s -> LevelService.enabled()));
		setLevel(server, 1, 0, 0);
		int wrong = 0;
		String firstWrong = "";
		for (int l = 1; l < LevelCurve.MAX_LEVEL; l++) {
			int from = l;
			long need = LevelCurve.need(l);
			server.runOnServer(s -> LevelService.addXp(s.getPlayerList().getPlayers().get(0), need, "autocheck"));
			int now = level(server).level();
			if (now != from + 1) {
				wrong++;
				if (firstWrong.isEmpty()) {
					firstWrong = "level " + from + " + " + need + " XP gave level " + now;
				}
				setLevel(server, from + 1, 0, 0);
			}
		}
		c.check("exactly the needed XP lifts the level by one, for levels 1 to 99", wrong == 0, wrong + " wrong. " + firstWrong);
		c.check("level 100 is reached", level(server).level() == LevelCurve.MAX_LEVEL, "level " + level(server).level());
		server.runOnServer(s -> LevelService.addXp(s.getPlayerList().getPlayers().get(0), LevelCurve.PARAGON_XP, "autocheck"));
		c.check("at level 100 enough XP gives a Paragon point", level(server).paragon() == 1, "paragon " + level(server).paragon());

		for (int l : new int[] {1, 10, 25, 50, 75, 100}) {
			setLevel(server, l, 0, 0);
			ctx.waitTicks(3);
			double max = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getMaxHealth());
			double expect = 20 + LevelCurve.bonusHealth(l, 0);
			c.check("max health at level " + l + " is " + expect, Math.abs(max - expect) < 0.01, "was " + max);
			double mult = server.computeOnServer(s -> LevelService.damageMultiplier(s.getPlayerList().getPlayers().get(0)));
			c.check("damage multiplier at level " + l, Math.abs(mult - LevelCurve.damageMultiplier(l, 0)) < 0.001,
				"was " + mult + ", curve " + LevelCurve.damageMultiplier(l, 0));
		}

		setLevel(server, 30, 5000, 0);
		long lost = server.computeOnServer(s -> LevelService.applyDeathPenalty(s.getPlayerList().getPlayers().get(0)));
		PlayerLevel after = level(server);
		double share = lost / 5000.0;
		c.check("a death costs 5-15% of the progress and never a level", lost > 0 && share >= 0.049 && share <= 0.151 && after.level() == 30,
			"lost " + lost + " of 5000, level " + after.level());

		ConfigStore.server();
		server.runCommand("vestiges config system character false");
		ctx.waitTicks(3);
		long given = server.computeOnServer(s -> LevelService.addXp(s.getPlayerList().getPlayers().get(0), 100, "autocheck"));
		c.check("with levels switched off no XP is given", given == 0, "given " + given);
		server.runCommand("vestiges config system character true");
		c.check("turning levels back on does not bring back what was turned off with them (by design)", !ConfigStore.server().enabled(VestigesSystem.PASSIVE_TREE));
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		setLevel(server, 20, 0, 0);
	}

	// ------------------------------------------------------------------ trees

	private static PlayerTree tree(TestServerContext server) {
		return server.computeOnServer(s -> TreeService.get(s.getPlayerList().getPlayers().get(0)));
	}

	private static void send(ClientGameTestContext ctx, String op, String wheel, String node, int value) {
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new TreePayloads.Action(op, wheel, node, value)));
		ctx.waitTicks(3);
	}

	private static void trees(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Passive trees (actions sent over the real network)");
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges tree reset @p");
		setLevel(server, 20, 0, 0);
		for (String wheelId : List.of("fight", "craft")) {
			TreeDefs.Wheel wheel = server.computeOnServer(s -> TreeService.wheel(wheelId));
			c.check(wheelId + " wheel is loaded", wheel != null && !wheel.nodes().isEmpty());
			if (wheel == null) {
				continue;
			}
			TreeDefs.Node start = wheel.nodes().stream().filter(n -> n.start().isPresent()).findFirst().orElseThrow();
			send(ctx, "start", wheelId, "", start.start().get());
			c.check(wheelId + ": a start point is picked", tree(server).wheel(wheelId).start() == start.start().get());

			Map<String, Set<String>> adj = TreeRules.adjacency(wheel.edges());
			List<String> order = new ArrayList<>();
			ArrayDeque<String> queue = new ArrayDeque<>(List.of(start.id()));
			Set<String> seen = new HashSet<>(Set.of(start.id()));
			while (!queue.isEmpty()) {
				String id = queue.poll();
				order.add(id);
				for (String next : adj.getOrDefault(id, Set.of())) {
					if (seen.add(next)) {
						queue.add(next);
					}
				}
			}
			int points = server.computeOnServer(s -> TreeService.points(s.getPlayerList().getPlayers().get(0), wheelId));
			int expected = wheelId.equals("fight") ? 20 : 10;
			c.check(wheelId + ": points at level 20 are " + expected, points == expected, "points " + points);
			c.check(wheelId + ": the wheel has more nodes than points", order.size() > points + 5, order.size() + " reachable nodes");

			for (int i = 1; i < order.size() && i < points + 12; i++) {
				send(ctx, "take", wheelId, order.get(i), 0);
			}
			PlayerTree.WheelState state = tree(server).wheel(wheelId);
			int spent = TreeService.spent(wheelId, state);
			c.check(wheelId + ": every point is spent, none more", spent == points, "spent " + spent + " of " + points);

			String extra = order.stream().filter(id -> !state.taken().contains(id) && !id.equals(start.id())).findFirst().orElse(null);
			if (extra != null) {
				send(ctx, "take", wheelId, extra, 0);
				c.check(wheelId + ": a node past the points is refused", TreeService.spent(wheelId, tree(server).wheel(wheelId)) == points);
			}

			String leaf = state.taken().stream().filter(id -> TreeRules.stillConnected(id, start.id(), state.taken(), adj)).findFirst().orElse(null);
			if (leaf != null) {
				server.runCommand("clear @p");
				send(ctx, "refund", wheelId, leaf, 0);
				c.check(wheelId + ": a refund without emeralds is refused", tree(server).wheel(wheelId).taken().contains(leaf));
				int cost = server.computeOnServer(s -> TreeService.refundCost(s.getPlayerList().getPlayers().get(0)));
				c.check(wheelId + ": a refund at level 20 costs 3 emeralds", cost == 3, "cost " + cost);
				server.runCommand("give @p minecraft:emerald 5");
				send(ctx, "refund", wheelId, leaf, 0);
				boolean refunded = !tree(server).wheel(wheelId).taken().contains(leaf);
				int left = server.computeOnServer(s -> dev.vestiges.server.Emeralds.count(s.getPlayerList().getPlayers().get(0)));
				c.check(wheelId + ": a refund with emeralds works and takes " + cost, refunded && left == 5 - cost, "refunded " + refunded + ", emeralds left " + left);
			}
		}
		server.runCommand("vestiges tree reset @p");
		c.check("tree reset empties both wheels", TreeService.spent("fight", tree(server).wheel("fight")) == 0
			&& TreeService.spent("craft", tree(server).wheel("craft")) == 0);
		c.note("not covered: ascendancies and keystone rivals (need their own checklists)");
	}

	// ------------------------------------------------------------------ gear

	private static void gear(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Gear ladder");
		ItemStack netherite = new ItemStack(Items.NETHERITE_CHESTPLATE);
		ItemStack wood = new ItemStack(Items.WOODEN_SWORD);
		int nl = GearLadder.levelFor(netherite);
		int wl = GearLadder.levelFor(wood);
		c.check("netherite needs a higher level than wood", nl > wl, "netherite " + nl + ", wood " + wl);
		setLevel(server, 1, 0, 0);
		ctx.waitTicks(3);
		boolean tooHigh1 = server.computeOnServer(s -> GearLadder.tooHigh(s.getPlayerList().getPlayers().get(0), new ItemStack(Items.NETHERITE_CHESTPLATE)));
		boolean woodHigh = server.computeOnServer(s -> GearLadder.tooHigh(s.getPlayerList().getPlayers().get(0), new ItemStack(Items.WOODEN_SWORD)));
		c.check("netherite is too high at level 1", tooHigh1);
		c.check("a wooden sword is not too high at level 1", !woodHigh);
		double armorLow = armor(ctx, server);
		setLevel(server, 100, 0, 0);
		double armorHigh = armor(ctx, server);
		c.check("netherite armor gives less at level 1 than at level 100", armorLow < armorHigh, "level 1: " + armorLow + ", level 100: " + armorHigh);
		c.check("netherite is not too high at level 100",
			!server.computeOnServer(s -> GearLadder.tooHigh(s.getPlayerList().getPlayers().get(0), new ItemStack(Items.NETHERITE_CHESTPLATE))));
		server.runCommand("clear @p");
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));
		server.runCommand("vestiges config system gear false");
		ctx.waitTicks(3);
		server.runCommand("vestiges config system gear true");
		setLevel(server, 20, 0, 0);
	}

	private static double armor(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE)));
		ctx.waitTicks(30);
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(Attributes.ARMOR));
	}

	// ------------------------------------------------------------------ enchants

	private static void enchants(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Enchantments I-VI");
		List<String> tooHigh = new ArrayList<>();
		int[] counted = {0};
		int sharp = server.computeOnServer(s -> {
			var lookup = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			lookup.listElements().forEach(h -> {
				counted[0]++;
				if (h.value().getMaxLevel() > 6) {
					tooHigh.add(h.key().identifier() + "=" + h.value().getMaxLevel());
				}
			});
			return lookup.getOrThrow(Enchantments.SHARPNESS).value().getMaxLevel();
		});
		c.check("no enchantment goes above VI", tooHigh.isEmpty(), tooHigh.toString());
		c.check("the enchantments are loaded", counted[0] >= 40, counted[0] + " found");
		c.check("sharpness tops out at IV in the rescaled pack", sharp == 4, "max " + sharp);

		// IV + IV = V on the anvil.
		String result = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var lookup = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			var sharpness = lookup.getOrThrow(Enchantments.SHARPNESS);
			ItemStack left = new ItemStack(Items.DIAMOND_SWORD);
			left.enchant(sharpness, 4);
			ItemStack right = new ItemStack(Items.DIAMOND_SWORD);
			right.enchant(sharpness, 4);
			p.getInventory().add(new ItemStack(Items.EMERALD, 64));
			AnvilMenu menu = new AnvilMenu(77, p.getInventory(), ContainerLevelAccess.NULL);
			menu.getSlot(0).set(left);
			menu.getSlot(1).set(right);
			menu.slotsChanged(menu.getSlot(0).container);
			ItemStack out = menu.getSlot(2).getItem();
			if (out.isEmpty()) {
				return "no result";
			}
			int level = EnchantmentHelper.getEnchantmentsForCrafting(out).getLevel(sharpness);
			return "sharpness " + level + ", cost " + menu.getCost();
		});
		c.check("anvil: sharpness IV + IV gives V", result.startsWith("sharpness 5"), result);
		server.runCommand("clear @p");
		server.runCommand("give @p minecraft:diamond_sword");
		Checks.Result over = c.run(server, "enchant @p minecraft:sharpness 7");
		c.check("/enchant stops at the rescaled maximum", over.crashed() || over.refused(), over.text());
	}
}
