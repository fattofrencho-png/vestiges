package dev.vestiges.level;

/**
 * Mob level = 75% of the average level of nearby players + the layer bonus, capped per layer (Batch 3). Used for kill XP now; M3 makes it
 * each mob's real level with matching stats.
 */
public final class MobLevels {
	public enum Layer {
		SURFACE, CAVES, DEEPSLATE
	}

	private MobLevels() {
	}

	public static int compute(double averagePlayerLevel, Layer layer, double share, int cavesBonus, int deepslateBonus, int surfaceCap, int cavesCap,
							  int deepslateCap) {
		int bonus = switch (layer) {
			case SURFACE -> 0;
			case CAVES -> cavesBonus;
			case DEEPSLATE -> deepslateBonus;
		};
		int cap = switch (layer) {
			case SURFACE -> surfaceCap;
			case CAVES -> cavesCap;
			case DEEPSLATE -> deepslateCap;
		};
		int level = (int) Math.round(averagePlayerLevel * share) + bonus;
		return Math.max(1, Math.min(cap, level));
	}
}
