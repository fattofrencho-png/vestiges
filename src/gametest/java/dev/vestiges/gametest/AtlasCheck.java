package dev.vestiges.gametest;

import dev.vestiges.client.features.ClientFeatures;
import dev.vestiges.client.map.WorldMapScreen;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.world.WorldAtlas;
import dev.vestiges.world.WorldPlans;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Place names in a real world, one world per run because closing a world stalls the test tool. Run with {@code tools\run_suite.ps1 atlas-vanilla}
 * and {@code tools\run_suite.ps1 atlas-vestiges}. Vanilla: the mod is installed but the default world type is used, so no continent names may
 * reach the player. Vestiges: the world is made by picking the Vestiges world type on the Create World screen, and the continents must arrive
 * named, in the right places, with the map screen drawing them.
 */
public final class AtlasCheck implements FabricClientGameTest {
	private static final String SEED = "vestiges";
	private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TYPE = ResourceKey.create(Registries.WORLD_PRESET, dev.vestiges.Vestiges.id("trenec"));

	static boolean handles(String suite) {
		return suite != null && (suite.equals("atlas-vanilla") || suite.equals("atlas-vestiges"));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String suite = System.getenv("VESTIGES_SUITE");
		if (!handles(suite)) {
			return;
		}
		boolean vestiges = suite.equals("atlas-vestiges");
		Checks c = new Checks(suite);
		ctx.getInput().resizeWindow(1600, 900);
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0));
		try {
			c.section("World creation");
			ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
			ctx.waitForScreen(CreateWorldScreen.class);
			boolean listed = ctx.computeOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().getNormalPresetList().stream().anyMatch(t -> t.preset() != null && t.preset().is(TYPE)));
			c.check("the Vestiges world type is in the World Type list", listed);
			c.check("the world type has a readable name", !net.minecraft.client.resources.language.I18n.get("generator.vestiges.trenec").startsWith("generator."),
				net.minecraft.client.resources.language.I18n.get("generator.vestiges.trenec"));
			ctx.runOnClient(mc -> {
				var state = ((CreateWorldScreen) mc.gui.screen()).getUiState();
				state.setSeed(SEED);
				if (vestiges) {
					state.setWorldType(state.getNormalPresetList().stream().filter(t -> t.preset() != null && t.preset().is(TYPE)).findFirst().orElseThrow());
				}
			});
			ctx.waitTicks(5);
			boolean picked = ctx.computeOnClient(mc -> {
				var preset = ((CreateWorldScreen) mc.gui.screen()).getUiState().getWorldType().preset();
				return preset != null && preset.is(TYPE);
			});
			c.check(vestiges ? "the Vestiges world type is selected on the screen" : "the default world type stays selected", picked == vestiges);
			ctx.takeScreenshot(suite + "_create_world");
			ctx.clickScreenButton("selectWorld.create");
			ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 6000);
			ctx.waitTicks(60);
			TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");

			c.section("What the world knows");
			boolean planned = ctx.computeOnClient(mc -> WorldAtlas.active(mc.getSingleplayerServer()));
			c.check(vestiges ? "the world uses the Vestiges terrain" : "the world is plain vanilla terrain", planned == vestiges);
			boolean switchOn = ConfigStore.server().enabled(VestigesSystem.CONTINENTS);
			c.check("the Continents switch matches the world", switchOn == vestiges, "switch " + switchOn);

			c.section("What the player is told");
			int lands = ClientFeatures.lands().size();
			if (!vestiges) {
				c.check("a vanilla world sends no continents", lands == 0, "got " + lands);
				c.check("a vanilla world names no place", ClientFeatures.placeAt(0, 0) == null, String.valueOf(ClientFeatures.placeAt(0, 0)));
			} else {
				int expected = WorldPlans.current().lands().size();
				c.check("all continents arrive", lands == expected, lands + " of " + expected);
				for (int i = 0; i < Math.min(lands, expected); i++) {
					c.check("continent " + i + " is called " + WorldPlans.current().lands().get(i).name(), ClientFeatures.lands().get(i).name().equals(WorldPlans.current().lands().get(i).name()));
				}
				c.check("spawn is on Trenec", "Trenec".equals(ClientFeatures.placeAt(0, 0)), String.valueOf(ClientFeatures.placeAt(0, 0)));
				for (var land : ClientFeatures.lands()) {
					c.check(land.name() + "'s label stands on " + land.name(), land.name().equals(ClientFeatures.placeAt(land.x(), land.z())), String.valueOf(ClientFeatures.placeAt(land.x(), land.z())));
				}
				c.check("far past the globe edge is the open sea", "the open sea".equals(ClientFeatures.placeAt(500000, 0)));
			}

			c.section("Codex Places");
			// The Codex counts only players in survival, so the player is a survival player for a moment at spawn.
			server.runCommand("gamemode survival @p");
			ctx.waitTicks(100);
			var found = server.computeOnServer(s -> {
				var player = s.getPlayerList().getPlayers().get(0);
				var progress = dev.vestiges.codex.CodexService.get(player);
				return progress.lands();
			});
			server.runCommand("gamemode spectator @p");
			if (vestiges) {
				c.check("a biome found at spawn is saved with its continent", found.values().stream().anyMatch(l -> l.contains("Trenec")), found.toString());
			} else {
				c.check("a vanilla world saves no continent for places", found.isEmpty(), found.toString());
			}

			c.section("The map screen");
			if (vestiges && lands > 0) {
				var trenec = ClientFeatures.lands().getFirst();
				server.runCommand("tp @p " + trenec.x() + " 200 " + trenec.z());
				ctx.waitTicks(40);
			}
			ctx.runOnClient(mc -> mc.setScreenAndShow(new WorldMapScreen()));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				for (int i = 0; i < 3; i++) {
					mc.gui.screen().mouseScrolled(300, 300, 0, -1);
				}
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot(suite + "_map");
			c.check("the map screen opened without an error", ctx.computeOnClient(mc -> mc.gui.screen() instanceof WorldMapScreen));
			ctx.runOnClient(mc -> mc.setScreenAndShow(null));
		} catch (RuntimeException | AssertionError e) {
			c.check("the check finished without an exception", false, e.toString());
		}
		c.done();
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("autocheck-finished.txt"), "done");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		System.out.println("[atlascheck] finished");
	}
}
