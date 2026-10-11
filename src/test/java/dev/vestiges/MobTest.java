package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.vestiges.level.MobLevels;
import dev.vestiges.mobs.ArmorFormula;
import dev.vestiges.mobs.MobData;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M3a: mob levels, ranks over the cap, the saved mob data, the armor formula and the uncapped attributes. */
class MobTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void levelsFollowPlayersAndLayers() {
		// 75% of the players' level plus the layer bonus, capped per layer; elites may go over the cap.
		assertEquals(18, MobLevels.raw(20, MobLevels.Layer.CAVES, 0.75, 3, 6));
		assertEquals(40, MobLevels.cap(MobLevels.Layer.CAVES, 20, 40, 50));
		assertEquals(0, MobLevels.overCap(MobData.NORMAL));
		assertEquals(3, MobLevels.overCap(MobData.CHAMPION));
		assertEquals(6, MobLevels.overCap(MobData.ELITE));
		assertEquals(10, MobLevels.overCap(MobData.WARLORD));
	}

	@Test
	void rankStrength() {
		assertEquals(1, new MobData(5, MobData.NORMAL, List.of(), 1f, false).strength());
		assertEquals(2, new MobData(5, MobData.CHAMPION, List.of("swift"), 1f, false).strength());
		assertEquals(3, new MobData(5, MobData.ELITE, List.of("swift", "venom"), 1f, false).strength());
		assertEquals(5, new MobData(5, MobData.WARLORD, List.of("swift", "venom", "brute"), 1f, false).strength());
	}

	@Test
	void mobDataSavesAndLoads() {
		MobData d = new MobData(23, MobData.ELITE, List.of("caller", "spiked"), 1.12f, true);
		var json = MobData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
		assertEquals(d, MobData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	void armorFormula() {
		// Vanilla full diamond (20) blocks 50%, a heavy build (60) 75%, never more than 85%; toughness counts half.
		assertEquals(0.5f, ArmorFormula.blocked(20, 0), 1e-4);
		assertEquals(0.75f, ArmorFormula.blocked(60, 0), 1e-4);
		assertEquals(0.85f, ArmorFormula.blocked(500, 0), 1e-4);
		assertEquals(ArmorFormula.blocked(24, 0), ArmorFormula.blocked(20, 8), 1e-4);
		assertEquals(0f, ArmorFormula.blocked(0, 0), 1e-4);
	}

	@Test
	void attributesAreUncapped() {
		assertEquals(100.0, Attributes.ARMOR.value().sanitizeValue(100.0), 1e-9);
		assertEquals(60.0, Attributes.ARMOR_TOUGHNESS.value().sanitizeValue(60.0), 1e-9);
		assertEquals(50_000.0, Attributes.MAX_HEALTH.value().sanitizeValue(50_000.0), 1e-9);
		assertTrue(Attributes.MOVEMENT_SPEED.value().sanitizeValue(5000.0) <= 1024.0);
	}
}
