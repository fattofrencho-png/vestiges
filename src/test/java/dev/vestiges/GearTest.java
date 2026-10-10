package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.gear.Rarity;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The approved Batch 3 rarity checks, the gear ladder and the loot-level mapping (IV = vanilla max). */
class GearTest {
	@Test
	void rarityMatchesTheBatch3Checks() {
		// Sharpness IV book = Superior.
		assertEquals(Rarity.Tier.SUPERIOR, Rarity.score(0, Map.of("sharpness", 4), Set.of()).tier());
		// Iron pickaxe Efficiency IV + Unbreaking IV = Rare.
		assertEquals(Rarity.Tier.RARE, Rarity.score(Rarity.materialPoints("iron_pickaxe"), Map.of("efficiency", 4, "unbreaking", 4), Set.of()).tier());
		// Iron pickaxe Efficiency VI = Relic with one star.
		Rarity.Result relic = Rarity.score(Rarity.materialPoints("iron_pickaxe"), Map.of("efficiency", 6), Set.of());
		assertEquals(Rarity.Tier.RELIC, relic.tier());
		assertEquals(1, relic.stars());
		// Table-enchanted diamond boots top out at Epic.
		assertEquals(Rarity.Tier.EPIC, Rarity.score(Rarity.materialPoints("diamond_boots"),
			Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4, "depth_strider", 3), Set.of()).tier());
		// Netherite gear with everything at V = Legendary.
		assertEquals(Rarity.Tier.LEGENDARY, Rarity.score(Rarity.materialPoints("netherite_sword"),
			Map.of("sharpness", 5, "looting", 5, "unbreaking", 5, "mending", 5, "fire_aspect", 5), Set.of()).tier());
		// A plain wooden sword is Common; curses pull the score down.
		assertEquals(Rarity.Tier.COMMON, Rarity.score(Rarity.materialPoints("wooden_sword"), Map.of(), Set.of()).tier());
		assertEquals(Rarity.Tier.COMMON, Rarity.score(30, Map.of("binding_curse", 1), Set.of("binding_curse")).tier());
	}

	@Test
	void ladderLevels() {
		assertEquals(1, GearLadder.levelForPath("wooden_sword"));
		assertEquals(1, GearLadder.levelForPath("leather_helmet"));
		assertEquals(5, GearLadder.levelForPath("chainmail_chestplate"));
		assertEquals(5, GearLadder.levelForPath("bow"));
		assertEquals(10, GearLadder.levelForPath("copper_spear"));
		assertEquals(10, GearLadder.levelForPath("crossbow"));
		assertEquals(15, GearLadder.levelForPath("golden_axe"));
		assertEquals(20, GearLadder.levelForPath("iron_boots"));
		assertEquals(20, GearLadder.levelForPath("turtle_helmet"));
		assertEquals(30, GearLadder.levelForPath("diamond_spear"));
		assertEquals(30, GearLadder.levelForPath("mace"));
		assertEquals(40, GearLadder.levelForPath("netherite_leggings"));
		assertEquals(40, GearLadder.levelForPath("elytra"));
	}

	@Test
	void lootSeesVanillaLevels() {
		// Fortune (vanilla max III): I 1, II 2, III 2, IV 3; V and VI sometimes 4.
		assertEquals(1, EnchantScale.lootLevel(3, 1, 0.9));
		assertEquals(2, EnchantScale.lootLevel(3, 2, 0.9));
		assertEquals(2, EnchantScale.lootLevel(3, 3, 0.9));
		assertEquals(3, EnchantScale.lootLevel(3, 4, 0.9));
		assertEquals(3, EnchantScale.lootLevel(3, 5, 0.9));
		assertEquals(4, EnchantScale.lootLevel(3, 5, 0.1));
		assertEquals(4, EnchantScale.lootLevel(3, 6, 0.4));
		// Silk Touch (vanilla max I): always 1.
		assertEquals(1, EnchantScale.lootLevel(1, 3, 0.9));
	}

	@Test
	void flameBurnTimes() {
		assertEquals(1f, EnchantScale.flameSeconds(1));
		assertEquals(5f, EnchantScale.flameSeconds(4));
		assertEquals(9f, EnchantScale.flameSeconds(6));
	}
}
