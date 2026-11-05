package dev.vestiges.dungeon;

/**
 * The numbers of a dungeon run that the design log fixed, as pure maths (unit-tested). They are drafts for the balance pass. The next-key odds
 * join here with the rewards.
 */
public final class DungeonRules {
	private DungeonRules() {
	}

	/** Lives per player: 3 in tiers 1 to 9, 2 in 10 to 24, 1 from 25. */
	public static int startingLives(int tier) {
		return tier >= 25 ? 1 : tier >= 10 ? 2 : 3;
	}

	/** Mob level in a dungeon: 12, plus 3 for every tier (tier 1 is level 15). */
	public static int mobLevel(int tier) {
		return 12 + 3 * Math.max(1, tier);
	}

	/** Mob health in a dungeon: +50% for every player beyond the first. */
	public static double partyHealth(int players) {
		return 1.0 + 0.5 * Math.max(0, players - 1);
	}

	/** After clearing tier N the next key is N-1 (15%), N (55%), N+1 (25%) or N+2 (5%); never below tier 1. {@code roll} is in [0, 1). */
	public static int nextTier(int tier, double roll) {
		int step = roll < 0.15 ? -1 : roll < 0.70 ? 0 : roll < 0.95 ? 1 : 2;
		return Math.max(1, tier + step);
	}

	/** The next key keeps the theme 60% of the time, and otherwise takes another open theme (the same one when there is no other). */
	public static final double KEEP_THEME = 0.60;

	public static String nextTheme(String current, java.util.List<String> open, double keepRoll, double pickRoll) {
		java.util.List<String> others = open.stream().filter(t -> !t.equals(current)).toList();
		if (keepRoll < KEEP_THEME || others.isEmpty()) {
			return current;
		}
		return others.get(Math.min(others.size() - 1, (int) (pickRoll * others.size())));
	}

	/** Fighting rooms hold 6 to 10 monsters. */
	public static final int MIN_MONSTERS = 6;
	public static final int MAX_MONSTERS = 10;
}
