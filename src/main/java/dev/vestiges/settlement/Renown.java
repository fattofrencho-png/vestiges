package dev.vestiges.settlement;

import java.util.Map;

/**
 * Renown (Batch 4, approved), pure so it is unit-tested. Five scores: Size (1 per 100 player-placed blocks; each block type counts fully up to
 * 1,000 blocks, then 1 point per 1,000 more), Variety (5 per block type with at least 16 placed), Homes (3 per bed), People (5 per villager
 * living there, +5 per worker rank above Novice) and Deeds (kept forever). Levels Camp → Capital at 0 / 80 / 250 / 600 / 1,200 / 2,500 / 5,000.
 */
public final class Renown {
	public static final String[] NAMES = {"Camp", "Settlement", "Hamlet", "Village", "Town", "City", "Capital"};
	public static final int[] THRESHOLDS = {0, 80, 250, 600, 1200, 2500, 5000};
	public static final int[] RADII = {24, 32, 48, 64, 80, 112, 160};
	public static final int[] SLOTS = {3, 6, 10, 18, 30, 50, 80};
	public static final int[] WEALTH = {1, 1, 2, 2, 3, 4, 5};
	/** A level drops only after 3 Minecraft days below its line. */
	public static final int DROP_AFTER_TICKS = 3 * 24000;

	private Renown() {
	}

	/** Level 1 (Camp) to 7 (Capital). */
	public static int level(int points) {
		int l = 1;
		for (int i = 1; i < THRESHOLDS.length; i++) {
			if (points >= THRESHOLDS[i]) {
				l = i + 1;
			}
		}
		return l;
	}

	public static String name(int level) {
		return NAMES[Math.max(1, Math.min(7, level)) - 1];
	}

	public static int radius(int level) {
		return RADII[Math.max(1, Math.min(7, level)) - 1];
	}

	/** Points needed for the next level, or 0 at Capital. */
	public static int next(int level) {
		return level >= 7 ? 0 : THRESHOLDS[level];
	}

	public static int size(Map<String, Integer> placedByType) {
		double blocks = 0;
		for (int n : placedByType.values()) {
			blocks += Math.min(n, 1000) + Math.max(0, n - 1000) / 10.0;
		}
		return (int) (blocks / 100);
	}

	public static int variety(Map<String, Integer> placedByType) {
		int types = 0;
		for (int n : placedByType.values()) {
			if (n >= 16) {
				types++;
			}
		}
		return types * 5;
	}

	public static int homes(int beds) {
		return beds * 3;
	}

	public static int people(int villagers, int ranksAboveNovice) {
		return villagers * 5 + ranksAboveNovice * 5;
	}

	/**
	 * Moves the level after a count: up at once, down only after {@link #DROP_AFTER_TICKS} below the line ({@code interval} = ticks since the
	 * last count). Keeps the best level ever reached (feats read it).
	 */
	public static SettlementData.Stats advance(SettlementData.Stats s, int interval) {
		int target = level(s.total());
		int lvl = s.level();
		int below = s.belowTicks();
		if (target > lvl) {
			lvl = target;
			below = 0;
		} else if (target < lvl) {
			below += interval;
			if (below >= DROP_AFTER_TICKS) {
				lvl = target;
				below = 0;
			}
		} else {
			below = 0;
		}
		return new SettlementData.Stats(s.size(), s.variety(), s.homes(), s.people(), s.deeds(), lvl, Math.max(s.bestLevel(), lvl), below, s.bestPoints());
	}
}
