package dev.vestiges.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.client.villager.ClientVillagers;
import dev.vestiges.client.villager.VillagerScreen;
import dev.vestiges.data.QuestDef;
import dev.vestiges.data.VestigesData;
import dev.vestiges.net.VillagerPayloads;
import dev.vestiges.villager.VillagerLibrary;
import dev.vestiges.villager.VillagerService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * S10: villagers. Names and profiles, the library, right-click opening our screen instead of vanilla trading, level-band trades (locked offers,
 * prices, daily stock, shift-buy), the Wandering Trader, the Trade system switch, and a quest offered through the screen. Right-click is simulated
 * through the same event the game fires, and buying goes through the real client packet.
 */
final class VillagerSuite {
	private VillagerSuite() {
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

	private static AbstractVillager tagged(MinecraftServer s, String tag) {
		for (Entity e : s.overworld().getAllEntities()) {
			if (e instanceof AbstractVillager v && v.entityTags().contains(tag)) {
				return v;
			}
		}
		return null;
	}

	private static String idOf(TestServerContext server, String tag) {
		return server.computeOnServer(s -> {
			AbstractVillager v = tagged(s, tag);
			return v == null ? "" : v.getUUID().toString();
		});
	}

	/** Right-click: fires the game's use-entity event for the player's main hand. */
	private static InteractionResult click(TestServerContext server, String tag) {
		return server.computeOnServer(s -> {
			AbstractVillager v = tagged(s, tag);
			ServerPlayer p = player(s);
			return UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
		});
	}

	private static void act(ClientGameTestContext ctx, String op, String id, String arg) {
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new VillagerPayloads.Act(op, id, arg)));
		ctx.waitTicks(25);
	}

	/** Buys from the test toolsmith; it is stood next to the player first, because villagers wander. */
	private static void buy(ClientGameTestContext ctx, TestServerContext server, String id, String arg) {
		server.runCommand("tp @e[tag=vt_tool] @p");
		ctx.waitTicks(3);
		act(ctx, "buy", id, arg);
	}

	private static JsonObject last() {
		return ClientVillagers.last();
	}

	private static JsonObject trade(JsonObject view, int index) {
		JsonArray a = view.getAsJsonArray("trades");
		return a.get(index).getAsJsonObject();
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Trade data");
		java.util.List<String> missing = new java.util.ArrayList<>();
		int total = 0;
		for (var def : VestigesData.VILLAGER_TRADES.all().values()) {
			for (var offer : def.offers()) {
				total++;
				var id = net.minecraft.resources.Identifier.tryParse(offer.item());
				if (id == null || net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
					missing.add(def.job() + ": " + offer.item());
				}
			}
		}
		c.check("every offer is a real item (" + total + " offers)", missing.isEmpty(), missing.toString());

		c.section("Setup");
		// Creative players pay nothing, so the buyer is a survival player (back to creative at the end for the other suites).
		server.runCommand("gamemode survival @p");
		server.runCommand("vestiges level set @p 1");
		server.runCommand("clear @p");
		server.runCommand("give @p emerald 64");
		server.runCommand("summon villager ~3 ~ ~ {Xp:1,Invulnerable:1b,Tags:[\"vt_tool\"],VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:toolsmith\",level:1}}");
		server.runCommand("summon villager ~-3 ~ ~ {Xp:1,Invulnerable:1b,Tags:[\"vt_job\"],VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:farmer\",level:1}}");
		server.runCommand("summon wandering_trader ~ ~ ~3 {Xp:1,Invulnerable:1b,Tags:[\"vt_trader\"]}");
		ctx.waitTicks(80);
		String toolId = idOf(server, "vt_tool");
		c.check("the test villagers exist", !toolId.isEmpty() && !idOf(server, "vt_job").isEmpty() && !idOf(server, "vt_trader").isEmpty());

		c.section("Right-click opens our screen");
		InteractionResult result = click(server, "vt_tool");
		ctx.waitFor(mc -> mc.gui.screen() instanceof VillagerScreen, 100);
		c.check("right-click is handled (vanilla trading is cancelled)", result == InteractionResult.SUCCESS, String.valueOf(result));
		boolean ours = ctx.computeOnClient(mc -> mc.gui.screen() instanceof VillagerScreen);
		boolean vanilla = ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen);
		c.check("our villager screen is open, not the vanilla trade screen", ours && !vanilla);
		JsonObject view = last();
		c.check("the view names the villager and its job", view != null && !view.get("name").getAsString().isBlank() && "Toolsmith".equals(view.get("job").getAsString()),
			view == null ? "no view" : view.get("name").getAsString() + " / " + view.get("job").getAsString());
		ctx.takeScreenshot("villager_overview");

		c.section("Library");
		VillagerLibrary lib = server.computeOnServer(s -> player(s).getAttached(VillagerService.LIBRARY));
		c.check("the villager is in the library", lib != null && lib.met().containsKey(toolId), lib == null ? "empty" : lib.met().size() + " met");
		if (lib != null && lib.met().containsKey(toolId)) {
			c.check("the library entry keeps the name", lib.met().get(toolId).profile().name().equals(view.get("name").getAsString()));
		}
		ctx.runOnClient(mc -> mc.setScreenAndShow(null));

		c.section("Trades by level band");
		click(server, "vt_tool");
		ctx.waitFor(mc -> mc.gui.screen() instanceof VillagerScreen, 100);
		view = last();
		int offers = view.getAsJsonArray("trades").size();
		c.check("a toolsmith has its full job list (13 offers)", offers == 13, String.valueOf(offers));
		int locked = 0;
		for (int i = 0; i < offers; i++) {
			if (trade(view, i).get("locked").getAsBoolean()) {
				locked++;
			}
		}
		c.check("at level 1 only tier 1 is open (3 open, 10 locked)", locked == 10, locked + " locked");
		c.check("the view shows a level below 10 and tier 1", view.get("level").getAsInt() < 10 && view.get("tier").getAsInt() == 1, "level " + view.get("level") + ", tier " + view.get("tier"));
		ctx.runOnClient(mc -> ((VillagerScreen) mc.gui.screen()).showTab("Trades"));
		ctx.waitTicks(5);
		ctx.takeScreenshot("villager_trades");
		ctx.runOnClient(mc -> ((VillagerScreen) mc.gui.screen()).showTab("About"));
		ctx.waitTicks(5);
		ctx.takeScreenshot("villager_about");
		ctx.runOnClient(mc -> ((VillagerScreen) mc.gui.screen()).showTab("Trades"));
		int price0 = trade(view, 0).get("price").getAsInt();
		int emeraldsBefore = count(server, Items.EMERALD);
		buy(ctx, server, toolId, "0");
		c.check("buying an open offer takes its price", count(server, Items.EMERALD) == emeraldsBefore - price0, emeraldsBefore + " -> " + count(server, Items.EMERALD) + " (price " + price0 + ")");
		c.check("and gives the item", count(server, Items.STONE_PICKAXE) == 1, String.valueOf(count(server, Items.STONE_PICKAXE)));
		c.check("the screen refreshed: one fewer in stock", last().getAsJsonArray("trades").get(0).getAsJsonObject().get("left").getAsInt() == 11);

		int before = count(server, Items.EMERALD);
		buy(ctx, server, toolId, "12");
		c.check("a locked offer is refused", count(server, Items.EMERALD) == before && count(server, Items.DIAMOND_PICKAXE) == 0);

		int shovelPrice = trade(last(), 2).get("price").getAsInt();
		before = count(server, Items.EMERALD);
		buy(ctx, server, toolId, "2:all");
		int bought = count(server, Items.STONE_SHOVEL);
		c.check("shift-buy takes the whole daily stock (12)", bought == 12 && count(server, Items.EMERALD) == before - 12 * shovelPrice, bought + " bought, emeralds " + before + " -> " + count(server, Items.EMERALD));
		before = count(server, Items.EMERALD);
		buy(ctx, server, toolId, "2");
		c.check("sold out stays sold out", count(server, Items.EMERALD) == before && count(server, Items.STONE_SHOVEL) == 12);

		server.runCommand("vestiges level set @p 10");
		ctx.waitTicks(5);
		click(server, "vt_tool");
		ctx.waitTicks(10);
		view = last();
		c.check("at level 10 tier 2 opens", view.get("tier").getAsInt() == 2 && !trade(view, 3).get("locked").getAsBoolean());
		before = count(server, Items.EMERALD);
		int ironPrice = trade(view, 3).get("price").getAsInt();
		buy(ctx, server, toolId, "3");
		c.check("a tier 2 offer can now be bought", count(server, Items.IRON_PICKAXE) == 1 && count(server, Items.EMERALD) == before - ironPrice,
			"iron pickaxes " + count(server, Items.IRON_PICKAXE) + ", emeralds " + before + " -> " + count(server, Items.EMERALD) + ", price " + ironPrice);

		server.runCommand("clear @p emerald");
		buy(ctx, server, toolId, "4");
		c.check("without emeralds nothing is sold", count(server, Items.IRON_AXE) == 0);

		c.section("Wandering Trader");
		server.runCommand("give @p emerald 64");
		ctx.runOnClient(mc -> mc.setScreenAndShow(null));
		InteractionResult r2 = click(server, "vt_trader");
		ctx.waitFor(mc -> mc.gui.screen() instanceof VillagerScreen, 100);
		view = last();
		c.check("the trader opens the same screen", r2 == InteractionResult.SUCCESS && "trader".equals(view.get("kind").getAsString()));
		c.check("with a rotating pick of 6 offers, none locked", view.getAsJsonArray("trades").size() == 6 && !trade(view, 0).get("locked").getAsBoolean() && !trade(view, 5).get("locked").getAsBoolean());
		ctx.runOnClient(mc -> ((VillagerScreen) mc.gui.screen()).showTab("Trades"));
		ctx.waitTicks(5);
		ctx.takeScreenshot("villager_trader");

		c.section("Quests through the screen");
		String questJob = null;
		for (QuestDef d : VestigesData.QUESTS.all().values()) {
			if ("villager".equals(d.start()) && d.job() != null && !d.job().isBlank()) {
				questJob = d.job();
				break;
			}
		}
		if (questJob == null) {
			c.note("no quest starts from a villager, so the Quests tab was not checked");
		} else {
			server.runCommand("summon villager ~ ~ ~-3 {Xp:1,Invulnerable:1b,Tags:[\"vt_quest\"],VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:" + questJob + "\",level:1}}");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
			click(server, "vt_quest");
			ctx.waitFor(mc -> mc.gui.screen() instanceof VillagerScreen, 100);
			view = last();
			c.check("a " + questJob + " offers its quest in the view", view.has("quest") && !view.getAsJsonObject("quest").get("name").getAsString().isBlank(),
				view.has("quest") ? view.getAsJsonObject("quest").toString() : "no quest");
			ctx.runOnClient(mc -> ((VillagerScreen) mc.gui.screen()).showTab("Quests"));
			ctx.waitTicks(5);
			ctx.takeScreenshot("villager_quest_tab");
		}

		c.section("Turn in at the giver");
		server.runCommand("gamemode creative @p");
		server.runCommand("summon villager ~ ~ ~4 {Xp:1,Invulnerable:1b,Tags:[\"vt_carto\"],VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:cartographer\",level:1}}");
		ctx.waitTicks(60);
		String cartoId = idOf(server, "vt_carto");
		boolean accepted = server.computeOnServer(s -> {
			ServerPlayer pl = player(s);
			dev.vestiges.quest.QuestService.adminReset(pl, "side_cartographer");
			dev.vestiges.quest.QuestService.offer(pl, "side_cartographer", cartoId);
			return dev.vestiges.quest.QuestService.accept(pl, "side_cartographer");
		});
		c.check("the cartographer quest is accepted with the villager as its giver", accepted && !cartoId.isEmpty());
		server.computeOnServer(s -> {
			dev.vestiges.quest.QuestService.event(player(s), "new_biome", "", 1);
			dev.vestiges.quest.QuestService.check(player(s));
			return true;
		});
		ctx.waitTicks(10);
		var st = server.computeOnServer(s -> dev.vestiges.quest.QuestService.get(player(s)));
		c.check("with every step done the quest is not finished yet: it waits to be turned in", st.active().containsKey("side_cartographer") && !st.finished().containsKey("side_cartographer"),
			"active " + st.active().containsKey("side_cartographer") + ", finished " + st.finished().containsKey("side_cartographer"));
		ctx.runOnClient(mc -> mc.setScreenAndShow(null));
		InteractionResult cartoClick = click(server, "vt_carto");
		ctx.waitTicks(20);
		String shown = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		c.check("the villager's screen offers the turn-in", last().has("turnin") && last().getAsJsonArray("turnin").size() == 1,
			"view keys " + last().keySet() + ", turnin " + last().get("turnin") + ", server says " + server.computeOnServer(s -> {
				var v = (net.minecraft.world.entity.npc.villager.Villager) tagged(s, "vt_carto");
				return dev.vestiges.quest.QuestService.readyAt(player(s), v) + " / active " + dev.vestiges.quest.QuestService.get(player(s)).active();
			}));
		ctx.runOnClient(mc -> mc.setScreenAndShow(null));
		server.runCommand("tp @p ~40 ~ ~");
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new dev.vestiges.net.QuestPayloads.Action("turn_in", "side_cartographer", "")));
		ctx.waitTicks(25);
		st = server.computeOnServer(s -> dev.vestiges.quest.QuestService.get(player(s)));
		c.check("it cannot be turned in from far away", st.active().containsKey("side_cartographer") && !st.finished().containsKey("side_cartographer"));
		server.runCommand("tp @p @e[tag=vt_carto,limit=1]");
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new dev.vestiges.net.QuestPayloads.Action("turn_in", "side_cartographer", "")));
		ctx.waitTicks(25);
		st = server.computeOnServer(s -> dev.vestiges.quest.QuestService.get(player(s)));
		c.check("turned in near the giver, the quest finishes", st.finished().containsKey("side_cartographer") && !st.active().containsKey("side_cartographer"));

		c.section("The Trade system switch");
		ctx.runOnClient(mc -> mc.setScreenAndShow(null));
		server.runCommand("vestiges config system trades false");
		ctx.waitTicks(5);
		InteractionResult off = click(server, "vt_tool");
		ctx.waitTicks(10);
		boolean screenOpen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof VillagerScreen);
		c.check("with the switch off vanilla trading is left alone", off == InteractionResult.PASS && !screenOpen, String.valueOf(off));
		server.runCommand("vestiges config system trades true");
		server.runCommand("gamemode creative @p");
	}
}
