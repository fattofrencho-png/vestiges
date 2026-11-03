package dev.vestiges.gametest;

import dev.vestiges.client.features.ClientFeatures;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * Automatic checks with pass/fail lines instead of screenshots. Runs only when {@code VESTIGES_SUITE} names suites (comma separated, for
 * example {@code sweep}); `tools/run_suite.ps1` sets it, runs the game under a time limit and stops it once the finished marker exists.
 * Reports are written to {@code report-<suite>.md} in the run folder (see {@link Checks}). Without the variable this test does nothing,
 * and {@link ScreenshotTour} does nothing when it is set.
 */
public final class AutoCheck implements FabricClientGameTest {
	private static final String SEED = "vestiges";
	private static final List<String> COUNTERS = List.of("kills", "ores", "deepslate", "crops", "sword_kills", "logs", "crossbow_kills",
		"mace_kills", "ancient_debris", "shovel_blocks", "bow_kills", "spear_kills", "cluster_blocks", "coral_blocks");
	private static final List<String> RARITIES = List.of("common", "uncommon", "superior", "rare", "epic", "legendary", "relic");
	private static final List<String> MARKERS = List.of("bell", "banner16", "banner48", "ward6", "ward12", "ward30", "gold_block");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String wanted = System.getenv("VESTIGES_SUITE");
		if (wanted == null || wanted.isBlank()) {
			return;
		}
		ctx.getInput().resizeWindow(1920, 1080);
		createWorld(ctx);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode creative @p");
		ctx.waitTicks(20);
		for (String suite : wanted.split(",")) {
			Checks checks = new Checks(suite.trim());
			var switches = T.switches();
			// Every suite starts with empty pockets and an empty Bank, so one suite cannot pay for another.
			server.runCommand("clear @p");
			server.runOnServer(s -> ((dev.vestiges.inventory.ExtraHolder) s.getPlayerList().getPlayers().get(0)).vestiges$setBank(0L));
			try {
				switch (suite.trim()) {
					case "sweep" -> sweep(ctx, server, checks);
					case "map" -> MapSuite.run(ctx, server, checks);
					case "levels" -> LevelSuite.run(ctx, server, checks);
					case "inventory" -> InventorySuite.run(ctx, server, checks);
						case "mobs" -> MobSuite.run(ctx, server, checks);
						case "loot" -> LootSuite.run(ctx, server, checks);
						case "codex" -> CodexSuite.run(ctx, server, checks);
						case "structures" -> LootSuite.structuresSurface(ctx, server, checks);
						case "structures2" -> LootSuite.structuresDeep(ctx, server, checks);
					default -> checks.check("suite exists", false, "unknown suite " + suite);
				}
			} catch (RuntimeException | AssertionError e) {
				checks.check("suite finished without an exception", false, e.toString());
			}
			T.restore(server, switches);
			ctx.waitTicks(5);
			checks.done();
		}
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("autocheck-finished.txt"), "done");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		System.out.println("[autocheck] finished");
	}

	/** The Create World screen with the defaults (the welcome page's starting choice), then Create. */
	private static void createWorld(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().setSeed(SEED));
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 3600);
	}

	// ---------------------------------------------------------------- S1 sweep

	private static void sweep(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("World");
		List<String> on = new ArrayList<>();
		List<String> off = new ArrayList<>();
		for (VestigesSystem s : VestigesSystem.values()) {
			(ConfigStore.server().enabled(s) ? on : off).add(s.id);
		}
		c.note("systems on at start: " + on);
		c.note("systems off at start: " + off);

		c.section("Info and data");
		c.expectOk(server, "vestiges info");
		String dataBefore = c.expectOk(server, "vestiges data").text();
		c.expectOk(server, "vestiges config list");
		c.expectOk(server, "vestiges config reload");
		c.check("data lists the 24 feats", dataBefore.contains("24"), dataBefore);

		c.section("Character");
		c.expectOk(server, "vestiges level set @p 1");
		c.expectOk(server, "vestiges level set @p 25");
		c.expectOk(server, "vestiges level set @p 100 500");
		c.expectOk(server, "vestiges level set @p 10");
		c.expectOk(server, "vestiges xp add @p 1000");
		c.expectOk(server, "vestiges tree reset @p");
		for (String key : COUNTERS) {
			c.expectOk(server, "vestiges tree counter @p " + key + " 5");
		}
		c.expectRejected(server, "vestiges level set @p 0");
		c.expectRejected(server, "vestiges level set @p 101");
		c.expectRejected(server, "vestiges xp add @p 0");

		c.section("Items and markers");
		c.expectOk(server, "vestiges give waystone");
		c.expectOk(server, "vestiges give waystone @p 3");
		for (String m : MARKERS) {
			c.expectOk(server, "vestiges give marker " + m);
		}
		c.expectRejected(server, "vestiges give marker nonsense");

		c.section("Feats");
		List<String> feats = VestigesData.FEATS.all().keySet().stream().map(k -> k.getPath()).sorted().toList();
		c.check("24 feats are loaded", feats.size() == 24, "found " + feats.size());
		for (String f : feats) {
			c.expectOk(server, "vestiges feat grant @p " + f);
		}
		for (String f : feats) {
			c.expectOk(server, "vestiges feat revoke @p " + f);
		}
		c.expectRejected(server, "vestiges feat grant @p not_a_feat");

		c.section("Quests");
		c.expectOk(server, "vestiges quest list");
		List<String> quests = VestigesData.QUESTS.all().keySet().stream().map(k -> k.getPath()).sorted().toList();
		c.check("quests are loaded", quests.size() >= 20, "found " + quests.size());
		for (String q : quests) {
			c.survey(server, "vestiges quest start " + q);
			c.survey(server, "vestiges quest note " + q);
			c.survey(server, "vestiges quest complete " + q);
			c.survey(server, "vestiges quest reset " + q);
		}
		for (String group : List.of("farmers", "smiths", "scholars", "nonsense")) {
			c.survey(server, "vestiges reputation " + group + " 10");
		}
		c.expectRejected(server, "vestiges reputation farmers 101");

		c.section("Codex");
		c.expectOk(server, "vestiges codex info @p");
		c.survey(server, "vestiges codex set @p \"blocks/stone\" 3");
		c.survey(server, "vestiges codex set @p \"mobs/zombie\" 4");
		c.expectRejected(server, "vestiges codex set @p \"blocks/stone\" 5");
		c.survey(server, "vestiges codex set @p \"nonsense/entry\" 1");
		c.expectOk(server, "vestiges codex reset @p");

		c.section("Loot");
		for (int level : new int[] {1, 10, 25, 50, 75, 100}) {
			c.expectOk(server, "vestiges loot roll " + level);
		}
		for (String r : RARITIES) {
			c.survey(server, "vestiges loot roll 50 " + r);
		}
		for (int level : new int[] {5, 30, 60, 100}) {
			Checks.Result r = c.expectOk(server, "vestiges loot test " + level);
			c.note("loot test " + level + ": " + r.text());
		}
		c.survey(server, "vestiges loot chest 10");
		c.expectRejected(server, "vestiges loot roll 10 mythic");

		c.section("Settlements and mobs with nothing there");
		c.survey(server, "vestiges settlement info");
		c.survey(server, "vestiges settlement renown");
		c.survey(server, "vestiges mob info");

		c.section("A levelled monster");
		server.runCommand("execute at @p run summon minecraft:zombie ~3 ~ ~");
		ctx.waitTicks(10);
		Checks.Result mob = c.survey(server, "vestiges mob info");
		c.note("mob info: " + mob.text());
		for (String rank : List.of("normal", "champion", "elite", "warlord")) {
			c.survey(server, "vestiges mob rank " + rank + " 30");
		}
		c.expectRejected(server, "vestiges mob rank boss");

		c.section("Switches");
		List<VestigesSystem> flippable = new ArrayList<>();
		for (VestigesSystem s : VestigesSystem.values()) {
			if (s.built() && !s.core() && !s.creationOnly()) {
				flippable.add(s);
			}
		}
		c.note("switches tested: " + flippable.stream().map(s -> s.id).toList());
		for (VestigesSystem s : flippable) {
			c.expectOk(server, "vestiges config system " + s.id + " false");
			ctx.waitTicks(3);
			c.check(s.id + " is off on the server", !ConfigStore.server().enabled(s));
			c.check(s.id + " is off on the client", !ctx.computeOnClient(mc -> ClientFeatures.on(s)));
			c.expectOk(server, "vestiges config system " + s.id + " true");
			ctx.waitTicks(3);
			c.check(s.id + " is on again on the server", ConfigStore.server().enabled(s));
			c.check(s.id + " is on again on the client", ctx.computeOnClient(mc -> ClientFeatures.on(s)));
		}
		c.expectRejected(server, "vestiges config system map false");
		c.expectRejected(server, "vestiges config system nonsense true");

		c.section("Everything off: nothing crashes");
		for (VestigesSystem s : flippable) {
			server.runCommand("vestiges config system " + s.id + " false");
		}
		ctx.waitTicks(5);
		for (String cmd : List.of("vestiges info", "vestiges data", "vestiges config list", "vestiges quest list", "vestiges codex info @p",
			"vestiges loot roll 30", "vestiges level set @p 20", "vestiges xp add @p 50", "vestiges mob info", "vestiges settlement info")) {
			c.survey(server, cmd);
		}
		server.runCommand("execute at @p run summon minecraft:zombie ~3 ~ ~");
		server.runCommand("execute at @p run summon minecraft:skeleton ~-3 ~ ~");
		server.runCommand("execute at @p run summon minecraft:creeper ~ ~ ~3");
		server.runCommand("give @p minecraft:bow");
		ctx.waitTicks(40);
		for (VestigesSystem s : flippable) {
			server.runCommand("vestiges config system " + s.id + " true");
		}
		ctx.waitTicks(5);
		c.check("every switch is back on", flippable.stream().allMatch(s -> ConfigStore.server().enabled(s)));

		c.section("Reload");
		server.runCommand("reload");
		ctx.waitTicks(200);
		String dataAfter = c.expectOk(server, "vestiges data").text();
		c.check("data is the same after /reload", dataBefore.equals(dataAfter), dataBefore + " vs " + dataAfter);
		c.check("feats are still 24 after /reload", VestigesData.FEATS.all().size() == 24);
	}
}
