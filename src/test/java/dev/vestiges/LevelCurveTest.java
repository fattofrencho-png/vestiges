package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.MobLevels;
import dev.vestiges.level.PlayerLevel;
import org.junit.jupiter.api.Test;

/** The agreed XP rework numbers (2026-10-10). */
class LevelCurveTest {
	@Test
	void needMatchesTheAgreedTable() {
		assertEquals(125, LevelCurve.need(1));
		assertEquals(850, LevelCurve.need(10));
		assertEquals(2340, LevelCurve.need(20));
		assertEquals(11130, LevelCurve.need(50));
		assertEquals(39423, LevelCurve.need(99));
		assertEquals(40000, LevelCurve.need(100), "Paragon levels");
		long total = LevelCurve.totalTo(100);
		assertTrue(total > 1_300_000 && total < 1_500_000, "about 1.4 million XP to level 100, was " + total);
	}

	@Test
	void killXpAndGap() {
		assertEquals(90, LevelCurve.killXp(20));
		assertEquals(1.0, LevelCurve.gapMultiplier(25, 20), 1e-9);
		assertEquals(0.9, LevelCurve.gapMultiplier(26, 20), 1e-9);
		assertEquals(0.1, LevelCurve.gapMultiplier(60, 20), 1e-9, "a level-60 player farming level-20 mobs gets 10%");
		assertEquals(1.25, LevelCurve.gapMultiplier(20, 25), 1e-9);
		assertEquals(1.5, LevelCurve.gapMultiplier(10, 40), 1e-9, "never more than +50%");
	}

	@Test
	void statsPerLevel() {
		assertEquals(0, LevelCurve.bonusHealth(1, 0));
		assertEquals(99, LevelCurve.bonusHealth(100, 0));
		assertEquals(1.0, LevelCurve.damageMultiplier(1, 0), 1e-9);
		assertEquals(2.98, LevelCurve.damageMultiplier(100, 0), 1e-9);
		assertEquals(99 + 25, LevelCurve.bonusHealth(100, 50), 1e-9);
		assertEquals(99 + 25 + 12.5, LevelCurve.bonusHealth(100, 100), 1e-9, "Paragon bonuses halve after 50");
	}

	@Test
	void deathNeverCostsALevel() {
		assertEquals(6, LevelCurve.deathLoss(1, 100), "5% of 125 at level 1");
		assertEquals(0, LevelCurve.deathLoss(50, 0), "nothing to lose at the start of a level");
		assertEquals(40, LevelCurve.deathLoss(50, 40), "never more than the progress");
		assertEquals(6000, LevelCurve.deathLoss(100, 1_000_000), "15% at level 100 (of a Paragon level)");
	}

	@Test
	void xpRollsIntoLevelsThenParagon() {
		PlayerLevel p = PlayerLevel.START.addXp(125 + 10);
		assertEquals(2, p.level());
		assertEquals(10, p.xp());
		PlayerLevel top = new PlayerLevel(100, 0, 0, java.util.Set.of()).addXp(85_000);
		assertEquals(100, top.level());
		assertEquals(2, top.paragon());
		assertEquals(5_000, top.xp());
	}

	@Test
	void mobLevelsFollowTheLayerCaps() {
		assertEquals(20, MobLevels.compute(60, MobLevels.Layer.SURFACE, 0.75, 3, 6, 20, 40, 50));
		assertEquals(18, MobLevels.compute(20, MobLevels.Layer.CAVES, 0.75, 3, 6, 20, 40, 50));
		assertEquals(50, MobLevels.compute(80, MobLevels.Layer.DEEPSLATE, 0.75, 3, 6, 20, 40, 50));
		assertEquals(1, MobLevels.compute(0, MobLevels.Layer.SURFACE, 0.75, 3, 6, 20, 40, 50));
	}
}
