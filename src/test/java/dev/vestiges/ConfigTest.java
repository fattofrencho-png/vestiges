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
	void defaultsTurnEverySystemOn() {
		ServerConfig c = new ServerConfig().normalize();
		assertEquals(VestigesSystem.values().length, c.systems.size());
		for (VestigesSystem s : VestigesSystem.values()) {
			assertTrue(c.enabled(s), s.id);
		}
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
