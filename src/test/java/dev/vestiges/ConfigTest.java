package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.vestiges.config.ClientConfig;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import org.junit.jupiter.api.Test;

class ConfigTest {
	private static final Gson GSON = new Gson();

	@Test
	void defaultsMatchTheFullPreset() {
		ServerConfig c = new ServerConfig().normalize();
		assertEquals(VestigesSystem.values().length, c.systems.size());
		for (VestigesSystem s : VestigesSystem.values()) {
			assertEquals(s.onByDefault(), c.enabled(s), s.id);
		}
		assertEquals(VestigesSystem.Preset.FULL, c.preset());
	}

	@Test
	void theCoreCanNeverBeSwitchedOff() {
		ServerConfig c = GSON.fromJson("{\"systems\":{\"inventory\":false,\"map\":false}}", ServerConfig.class).normalize();
		assertTrue(c.enabled(VestigesSystem.INVENTORY));
		assertTrue(c.enabled(VestigesSystem.MAP));
		c.set(VestigesSystem.MAP, false);
		c.setWithNeeds(VestigesSystem.INVENTORY, false);
		assertTrue(c.enabled(VestigesSystem.MAP));
		assertTrue(c.enabled(VestigesSystem.INVENTORY));
		for (VestigesSystem.Preset p : VestigesSystem.Preset.values()) {
			assertTrue(VestigesSystem.MAP.inPreset(p), p.label);
			assertTrue(VestigesSystem.INVENTORY.inPreset(p), p.label);
		}
	}

	@Test
	void presetsAreRecognised() {
		ServerConfig c = new ServerConfig().normalize();
		c.applyPreset(VestigesSystem.Preset.CORE);
		assertEquals(VestigesSystem.Preset.CORE, c.preset());
		assertTrue(c.enabled(VestigesSystem.CHARACTER), "Core is the RPG");
		assertTrue(c.enabled(VestigesSystem.ENCHANT_LEVELS));
		assertTrue(c.enabled(VestigesSystem.FARMING));
		assertFalse(c.enabled(VestigesSystem.STACKS));
		assertFalse(c.enabled(VestigesSystem.WORKERS));
		c.applyPreset(VestigesSystem.Preset.COMFORT);
		assertEquals(VestigesSystem.Preset.COMFORT, c.preset());
		assertTrue(c.enabled(VestigesSystem.CHARACTER));
		assertTrue(c.enabled(VestigesSystem.STACKS));
		assertFalse(c.enabled(VestigesSystem.WORKERS));
		c.applyPreset(VestigesSystem.Preset.FULL);
		assertEquals(VestigesSystem.Preset.FULL, c.preset());
		assertTrue(c.enabled(VestigesSystem.WORKERS));
		c.setWithNeeds(VestigesSystem.CODEX, false);
		assertEquals(null, c.preset(), "a changed switch makes it a custom choice");
	}

	@Test
	void eachPresetIncludesTheOneBefore() {
		VestigesSystem.Preset[] presets = VestigesSystem.Preset.values();
		for (VestigesSystem s : VestigesSystem.values()) {
			for (int i = 1; i < presets.length; i++) {
				assertTrue(!s.inPreset(presets[i - 1]) || s.inPreset(presets[i]), s.id + " in " + presets[i].label);
			}
		}
	}

	@Test
	void gameRulesAndFreecamStartOffInEveryPreset() {
		for (VestigesSystem.Preset p : VestigesSystem.Preset.values()) {
			assertFalse(VestigesSystem.FREECAM.inPreset(p), p.label);
			assertFalse(VestigesSystem.SLEEP.inPreset(p), p.label);
			assertFalse(VestigesSystem.GRIEFING.inPreset(p), p.label);
		}
	}

	@Test
	void eachPresetKeepsWhatItsFeaturesNeed() {
		for (VestigesSystem.Preset p : VestigesSystem.Preset.values()) {
			for (VestigesSystem s : VestigesSystem.values()) {
				for (VestigesSystem need : s.needs()) {
					assertTrue(!s.inPreset(p) || need.inPreset(p), s.id + " needs " + need.id + " in " + p.label);
				}
			}
		}
	}

	@Test
	void ascensionStepsAreSkippedWhileTheirFeatureIsOff() {
		ServerConfig c = new ServerConfig().normalize();
		assertFalse(dev.vestiges.tree.TreeService.Check.needing(VestigesSystem.ELITES, c, "Kill 1 elite", 0, 1).done());
		c.set(VestigesSystem.ELITES, false);
		assertTrue(dev.vestiges.tree.TreeService.Check.needing(VestigesSystem.ELITES, c, "Kill 1 elite", 0, 1).done());
	}

	@Test
	void featsAreBlockedWhileWhatTheyUseIsOff() {
		java.util.List<dev.vestiges.data.Feat.Goal> codex = java.util.List.of(new dev.vestiges.data.Feat.Goal("codex", 100));
		dev.vestiges.data.Feat charted = new dev.vestiges.data.Feat("explorer", 0, "Charted", "", "", 0, false, codex, "", "", "", "", 0);
		dev.vestiges.data.Feat hunter = new dev.vestiges.data.Feat("warden", 0, "Elite Hunter", "", "", 0, true, java.util.List.of(), "ward_lantern", "", "", "elites", 10);
		ServerConfig c = new ServerConfig().normalize();
		assertFalse(charted.blockedIn(c));
		assertFalse(hunter.blockedIn(c));
		c.set(VestigesSystem.CODEX, false);
		assertTrue(charted.blockedIn(c));
		c.set(VestigesSystem.ELITES, false);
		assertTrue(hunter.blockedIn(c), "counter");
		c.set(VestigesSystem.ELITES, true);
		c.set(VestigesSystem.SETTLEMENTS, false);
		assertTrue(hunter.blockedIn(c), "reward");
	}

	@Test
	void switchesKeepWhatTheyNeed() {
		ServerConfig c = new ServerConfig().normalize();
		c.setWithNeeds(VestigesSystem.FEATS, false);
		assertFalse(c.enabled(VestigesSystem.SETTLEMENTS), "needs feats");
		assertFalse(c.enabled(VestigesSystem.WORKERS), "needs settlements, which needs feats");
		c.setWithNeeds(VestigesSystem.WORKERS, true);
		assertTrue(c.enabled(VestigesSystem.SETTLEMENTS));
		assertTrue(c.enabled(VestigesSystem.VILLAGERS));
		assertTrue(c.enabled(VestigesSystem.FEATS));
	}

	@Test
	void eachWorldKeepsItsOwnSettings(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
		java.nio.file.Path older = dir.resolve("older/vestiges/settings.json");
		dev.vestiges.config.ConfigStore.openWorld(older);
		assertTrue(java.nio.file.Files.exists(older));
		assertTrue(dev.vestiges.config.ConfigStore.newWorld());
		assertFalse(dev.vestiges.config.ConfigStore.chosenAtCreation(), "a world from before the welcome page keeps its game rules");
		assertTrue(dev.vestiges.config.ConfigStore.server().enabled(VestigesSystem.CHARACTER), "and everything stays on");

		ServerConfig choice = new ServerConfig().normalize();
		choice.applyPreset(VestigesSystem.Preset.CORE);
		choice.setWithNeeds(VestigesSystem.CHARACTER, false);
		dev.vestiges.config.ConfigStore.setPendingWorld(choice);
		java.nio.file.Path created = dir.resolve("created/vestiges/settings.json");
		dev.vestiges.config.ConfigStore.openWorld(created);
		assertTrue(dev.vestiges.config.ConfigStore.chosenAtCreation());
		assertFalse(dev.vestiges.config.ConfigStore.server().enabled(VestigesSystem.CHARACTER));

		dev.vestiges.config.ConfigStore.openWorld(older);
		assertFalse(dev.vestiges.config.ConfigStore.newWorld());
		assertTrue(dev.vestiges.config.ConfigStore.server().enabled(VestigesSystem.CHARACTER), "the other world's choice doesn't leak");

		dev.vestiges.config.ConfigStore.openWorld(created);
		assertFalse(dev.vestiges.config.ConfigStore.server().enabled(VestigesSystem.CHARACTER), "reopening keeps the choice");
		dev.vestiges.config.ConfigStore.closeWorld();
	}

	@Test
	void everyNeedExistsAndOnlyBuiltFeaturesAreLive() {
		for (VestigesSystem s : VestigesSystem.values()) {
			for (VestigesSystem n : s.needs()) {
				assertTrue(n != null && n != s, s.id);
			}
			assertFalse(s.help.isBlank(), s.id);
		}
		assertTrue(VestigesSystem.ENCHANT_LEVELS.creationOnly());
		assertFalse(VestigesSystem.FREECAM.built());
	}

	@Test
	void agreedNumbersAreTheDefaults() {
		ServerConfig.Numbers n = new ServerConfig().numbers;
		assertEquals(0.75, n.mobLevelShare);
		assertEquals(20, n.surfaceLevelCap);
		assertEquals(40, n.cavesLevelCap);
		assertEquals(50, n.deepslateLevelCap);
		assertEquals(250, n.waystoneBlocksPerEmerald);
	}

	@Test
	void fileKeepsChoicesAndAddsNewSystems() {
		// An older file that only knows two systems, one switched off.
		String json = "{\"systems\":{\"dungeons\":false,\"map\":true}}";
		ServerConfig c = GSON.fromJson(json, ServerConfig.class).normalize();
		assertFalse(c.enabled(VestigesSystem.DUNGEONS));
		assertTrue(c.enabled(VestigesSystem.MAP));
		assertTrue(c.enabled(VestigesSystem.WORKERS), "systems missing from the file default to on");
		assertEquals(VestigesSystem.values().length, c.systems.size());
		assertEquals(0.75, c.numbers.mobLevelShare, "missing numbers fall back to defaults");
	}

	@Test
	void roundTripKeepsChanges() {
		ServerConfig c = new ServerConfig().normalize();
		c.set(VestigesSystem.ELITES, false);
		c.numbers.surfaceLevelCap = 25;
		ServerConfig back = GSON.fromJson(GSON.toJson(c), ServerConfig.class).normalize();
		assertFalse(back.enabled(VestigesSystem.ELITES));
		assertEquals(25, back.numbers.surfaceLevelCap);
	}

	@Test
	void systemIdsAreUniqueAndFindable() {
		for (VestigesSystem s : VestigesSystem.values()) {
			assertEquals(s, VestigesSystem.byId(s.id));
		}
		assertEquals(null, VestigesSystem.byId("nope"));
	}

	@Test
	void clientLinesDefaultOn() {
		ClientConfig c = GSON.fromJson("{\"lookAtLines\":{\"health\":false}}", ClientConfig.class).normalize();
		assertTrue(c.lookAtEnabled);
		assertFalse(c.lineOn("health"));
		assertTrue(c.lineOn("tool"));
	}

	@Test
	void whatWasSwitchedOffWithAParentComesBackWithIt() {
		ServerConfig c = new ServerConfig().normalize();
		c.setWithNeeds(VestigesSystem.CHARACTER, false);
		assertFalse(c.enabled(VestigesSystem.PASSIVE_TREE));
		c.setWithNeeds(VestigesSystem.CHARACTER, true);
		assertTrue(c.enabled(VestigesSystem.PASSIVE_TREE), "the trees return with levels");
		assertTrue(c.remembered.isEmpty());

		c.setWithNeeds(VestigesSystem.PASSIVE_TREE, false);
		c.setWithNeeds(VestigesSystem.CHARACTER, false);
		c.setWithNeeds(VestigesSystem.CHARACTER, true);
		assertFalse(c.enabled(VestigesSystem.PASSIVE_TREE), "a child the player had switched off stays off");
	}

	@Test
	void aSwitchTurnedOnByNeedDoesNotBringBackItsSiblings() {
		ServerConfig c = new ServerConfig().normalize();
		c.setWithNeeds(VestigesSystem.FEATS, false);
		c.setWithNeeds(VestigesSystem.WORKERS, true);
		assertTrue(c.enabled(VestigesSystem.SETTLEMENTS));
		assertFalse(c.enabled(VestigesSystem.WAYSTONES), "waystones were not asked for");
		c.setWithNeeds(VestigesSystem.FEATS, false);
		c.setWithNeeds(VestigesSystem.FEATS, true);
		assertTrue(c.enabled(VestigesSystem.WAYSTONES) && c.enabled(VestigesSystem.SETTLEMENTS) && c.enabled(VestigesSystem.WORKERS),
			"choosing the parent brings back everything it took with it, down the chain");
	}

	@Test
	void aFeatureWithTwoParentsWaitsForBoth() {
		ServerConfig c = new ServerConfig().normalize();
		c.setWithNeeds(VestigesSystem.VILLAGERS, false);
		c.setWithNeeds(VestigesSystem.FEATS, false);
		assertFalse(c.enabled(VestigesSystem.WORKERS));
		c.setWithNeeds(VestigesSystem.FEATS, true);
		assertTrue(c.enabled(VestigesSystem.SETTLEMENTS));
		assertFalse(c.enabled(VestigesSystem.WORKERS), "villagers are still off");
		c.setWithNeeds(VestigesSystem.VILLAGERS, true);
		assertTrue(c.enabled(VestigesSystem.WORKERS) && c.enabled(VestigesSystem.TRADES));
	}

	@Test
	void rememberedSwitchesAreSavedAndPresetsForgetThem() {
		ServerConfig c = new ServerConfig().normalize();
		c.setWithNeeds(VestigesSystem.CHARACTER, false);
		ServerConfig again = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(c), ServerConfig.class).normalize();
		assertTrue(again.remembered.contains("passive_tree"));
		again.setWithNeeds(VestigesSystem.CHARACTER, true);
		assertTrue(again.enabled(VestigesSystem.PASSIVE_TREE));
		c.applyPreset(VestigesSystem.Preset.CORE);
		assertTrue(c.remembered.isEmpty());
	}

	@Test
	void switchesAreGroupedUnderTheOnesTheyNeed() {
		assertEquals(java.util.List.of(VestigesSystem.ENCHANT_LEVELS, VestigesSystem.LOOT, VestigesSystem.PER_PLAYER_LOOT),
			VestigesSystem.listed(VestigesSystem.Category.LOOT));
		assertEquals(1, VestigesSystem.LOOT.depth());
		assertEquals(1, VestigesSystem.PASSIVE_TREE.depth());
		assertEquals(1, VestigesSystem.ELITES.depth());
		assertEquals(2, VestigesSystem.WORKERS.depth(), "workers sit under settlements, which sit under feats");
		assertEquals(VestigesSystem.SETTLEMENTS, VestigesSystem.WORKERS.parent());
		assertEquals(1, VestigesSystem.TRADES.depth());
		java.util.List<VestigesSystem> comfort = VestigesSystem.listed(VestigesSystem.Category.COMFORT);
		assertEquals(VestigesSystem.SORTING, comfort.get(0), "inventory helpers come first");
		assertEquals(VestigesSystem.GRIEFING, comfort.get(comfort.size() - 1), "server rules come last");
		for (VestigesSystem.Category cat : VestigesSystem.Category.values()) {
			for (VestigesSystem s : VestigesSystem.values()) {
				assertEquals(s.category == cat, VestigesSystem.listed(cat).contains(s), s.id + " is listed exactly once, in its own group");
			}
		}
	}
}
