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
		assertFalse(c.enabled(VestigesSystem.CHARACTER));
		assertFalse(c.enabled(VestigesSystem.ENCHANT_LEVELS));
		c.applyPreset(VestigesSystem.Preset.COMFORT);
		assertEquals(VestigesSystem.Preset.COMFORT, c.preset());
		assertTrue(c.enabled(VestigesSystem.FARMING));
		assertFalse(c.enabled(VestigesSystem.CHARACTER));
		c.setWithNeeds(VestigesSystem.CODEX, true);
		assertEquals(null, c.preset(), "a changed switch makes it a custom choice");
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
}
