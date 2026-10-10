package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestiges.map.MapMath;
import dev.vestiges.waystone.WaystoneRules;
import org.junit.jupiter.api.Test;

class MapAndWaystoneTest {
	@Test
	void regionsAndLocalCoordsHandleNegatives() {
		assertEquals(0, MapMath.region(0));
		assertEquals(0, MapMath.region(511));
		assertEquals(1, MapMath.region(512));
		assertEquals(-1, MapMath.region(-1));
		assertEquals(511, MapMath.local(-1));
		assertEquals(0, MapMath.local(-512));
		assertEquals(-2, MapMath.region(-513));
	}

	@Test
	void slicesAreSixteenBlocksTall() {
		assertEquals(0, MapMath.slice(0));
		assertEquals(0, MapMath.slice(15));
		assertEquals(-1, MapMath.slice(-1));
		assertEquals(-4, MapMath.slice(-64));
		assertEquals("u-2", MapMath.sliceKey(-2));
		assertEquals(-2, MapMath.sliceOf("u-2"));
		assertTrue(MapMath.isSurface(MapMath.SURFACE));
	}

	@Test
	void layerLabels() {
		assertEquals("Surface", MapMath.label("surface", false));
		assertEquals("Caves (y 32 to 47)", MapMath.label("u2", false));
		assertEquals("Deepslate (y -16 to -1)", MapMath.label("u-1", false));
		assertEquals("Nether (y 32 to 47)", MapMath.label("u2", true));
	}

	@Test
	void reliefBrightensUphillAndDarkensDownhill() {
		assertTrue(MapMath.relief(70, 68) > 1f);
		assertTrue(MapMath.relief(66, 68) < 1f);
		assertEquals(1f, MapMath.relief(68, 68));
		assertEquals(0xFF000000, MapMath.shade(0xFF101010, 0f));
		assertEquals(0xFFFFFFFF, MapMath.shade(0xFFF0F0F0, 2f));
	}

	@Test
	void tripCostIsOneEmeraldPer250BlocksMinimumOne() {
		assertEquals(1, WaystoneRules.cost(10, 250));
		assertEquals(1, WaystoneRules.cost(499, 250));
		assertEquals(2, WaystoneRules.cost(500, 250));
		assertEquals(8, WaystoneRules.cost(2000, 250));
		assertEquals(1, WaystoneRules.cost(100, 0), "a bad config value never divides by zero");
	}

	@Test
	void combatLastsTenSeconds() {
		assertTrue(WaystoneRules.inCombat(1000, 900, true, 0));
		assertFalse(WaystoneRules.inCombat(1000, 700, true, 0));
		assertFalse(WaystoneRules.inCombat(1000, 900, false, 0), "no attacker means no fight");
		assertTrue(WaystoneRules.inCombat(1000, 0, false, 950), "hitting a mob counts too");
		assertFalse(WaystoneRules.inCombat(1000, 0, false, 0));
	}

	@Test
	void namesAreCleaned() {
		assertEquals("Home", WaystoneRules.cleanName("  Home  ", "X"));
		assertEquals("X", WaystoneRules.cleanName("   ", "X"));
		assertEquals("Old Mill", WaystoneRules.cleanName("Old    Mill", "X"));
		assertEquals(32, WaystoneRules.cleanName("a".repeat(50), "X").length());
	}
}
