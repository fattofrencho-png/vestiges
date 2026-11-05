package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.data.VillagerLoreDef;
import dev.vestiges.villager.VillagerProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VillagerProfileTest {
	private static VillagerLoreDef lore() throws IOException {
		try (Reader in = new InputStreamReader(VillagerProfileTest.class.getResourceAsStream("/data/vestiges/vestiges/villager_lore/draft.json"))) {
			return VillagerLoreDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
		}
	}

	@Test
	void theDraftListsLoad() throws IOException {
		VillagerLoreDef lore = lore();
		assertFalse(lore.firstNames().isEmpty());
		assertFalse(lore.turningPoints().isEmpty());
		assertFalse(lore.traits().isEmpty());
	}

	@Test
	void sameSeedSameVillagerDifferentSeedsVary() throws IOException {
		VillagerLoreDef lore = lore();
		assertEquals(VillagerProfile.generate(lore, 42, "Trenec", "Restena"), VillagerProfile.generate(lore, 42, "Trenec", "Restena"));
		Set<String> names = new HashSet<>();
		for (long seed = 0; seed < 300; seed++) {
			names.add(VillagerProfile.generate(lore, seed, null, null).name());
		}
		assertTrue(names.size() > 60, "names vary, got " + names.size());
		assertNotEquals(VillagerProfile.generate(lore, 1, null, null), VillagerProfile.generate(lore, 2, null, null));
	}

	@Test
	void birthplacesFollowTheWorld() throws IOException {
		VillagerLoreDef lore = lore();
		int near = 0;
		int region = 0;
		int far = 0;
		for (long seed = 0; seed < 3000; seed++) {
			String place = VillagerProfile.generate(lore, seed, "Trenec", "Restena").birthplace();
			if (place.equals(lore.birthplaceNear())) {
				near++;
			} else if (place.contains("Trenec")) {
				region++;
			} else if (place.contains("Restena")) {
				far++;
			}
		}
		// 60% near, 30% in the region, 10% far (the spec's weights), within a few points.
		assertTrue(Math.abs(near / 3000.0 - 0.6) < 0.05, "near " + near);
		assertTrue(Math.abs(region / 3000.0 - 0.3) < 0.05, "region " + region);
		assertTrue(Math.abs(far / 3000.0 - 0.1) < 0.04, "far " + far);
		for (long seed = 0; seed < 200; seed++) {
			assertEquals(lore.birthplaceNear(), VillagerProfile.generate(lore, seed, null, null).birthplace(), "a vanilla world only has a village near here");
		}
	}

	@Test
	void aRareLoreHookAppearsAboutOneInFifty() throws IOException {
		VillagerLoreDef lore = lore();
		int hooks = 0;
		for (long seed = 0; seed < 10000; seed++) {
			if (!VillagerProfile.generate(lore, seed, null, null).hook().isEmpty()) {
				hooks++;
			}
		}
		assertTrue(hooks > 100 && hooks < 320, "hooks " + hooks + " of 10000");
	}

	@Test
	void aTurningPointThatGivesABynameKeepsIt() throws IOException {
		VillagerLoreDef lore = lore();
		for (long seed = 0; seed < 500; seed++) {
			VillagerProfile p = VillagerProfile.generate(lore, seed, null, null);
			for (VillagerLoreDef.Turning t : lore.turningPoints()) {
				if (t.text().equals(p.turning()) && !t.byname().isEmpty()) {
					assertEquals(t.byname(), p.byname());
				}
			}
			assertEquals(2, p.traits().size());
		}
	}
}
