package dev.vestiges.level;

/**
 * The agreed level numbers (Batch 4 rework, 2026-10-10), free of Minecraft types so they are unit-tested.
 * <ul>
 * <li>Kill XP = 10 + 4 × mob level, times the rank multiplier (elites, bosses).</li>
 * <li>Level gap: full XP up to 5 levels below you, then 10% less per extra level (never under 10%); +5% per level above you (max +50%).</li>
 * <li>XP for level L → L+1 = kill XP at level L × (8 + 0.9 L): about 9 same-level kills at level 1, about 97 at level 99.</li>
 * <li>Each level: +1 max health and +2% damage. Paragon (after 100): 40,000 XP each, +0.5 max health and +0.5% damage, halved after 50.</li>
 * <li>Death: lose 5% (level 1) up to 15% (level 100) of the current level's requirement, never below the start of the level.</li>
 * </ul>
 */
public final class LevelCurve {
	public static final int MAX_LEVEL = 100;
	public static final long PARAGON_XP = 40_000;
	public static final int PARAGON_SOFT_CAP = 50;

	private LevelCurve() {
	}

	/** XP to go from {@code level} to {@code level + 1} (levels 1..99); Paragon levels at 100. */
	public static long need(int level) {
		if (level >= MAX_LEVEL) {
			return PARAGON_XP;
		}
		int l = Math.max(1, level);
		return Math.round(killXp(l) * (8 + 0.9 * l));
	}

	/** Total XP from level 1 to {@code level}. */
	public static long totalTo(int level) {
		long sum = 0;
		for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
			sum += need(l);
		}
		return sum;
	}

	public static int killXp(int mobLevel) {
		return 10 + 4 * Math.max(0, mobLevel);
	}

	/** XP multiplier from the gap between the player and the mob. */
	public static double gapMultiplier(int playerLevel, int mobLevel) {
		int below = playerLevel - mobLevel;
		if (below <= 5) {
			int above = Math.max(0, mobLevel - playerLevel);
			return 1.0 + Math.min(0.5, above * 0.05);
		}
		return Math.max(0.1, 1.0 - (below - 5) * 0.1);
	}

	/** Bonus max health from levels and Paragon. */
	public static double bonusHealth(int level, int paragon) {
		return Math.max(0, Math.min(level, MAX_LEVEL) - 1) + paragonBonus(paragon, 0.5);
	}

	/** Damage multiplier from levels and Paragon (1.0 at level 1). */
	public static double damageMultiplier(int level, int paragon) {
		double pct = Math.max(0, Math.min(level, MAX_LEVEL) - 1) * 2.0 + paragonBonus(paragon, 0.5);
		return 1.0 + pct / 100.0;
	}

	/** Paragon bonuses: {@code perLevel} each up to 50, half that after. */
	static double paragonBonus(int paragon, double perLevel) {
		int full = Math.min(paragon, PARAGON_SOFT_CAP);
		int half = Math.max(0, paragon - PARAGON_SOFT_CAP);
		return full * perLevel + half * perLevel / 2.0;
	}

	/** XP lost on death, taken from progress only (a level is never lost). */
	public static long deathLoss(int level, long progress) {
		double pct = 5.0 + 10.0 * (Math.min(level, MAX_LEVEL) - 1) / 99.0;
		long loss = Math.round(need(level) * pct / 100.0);
		return Math.min(progress, loss);
	}
}
