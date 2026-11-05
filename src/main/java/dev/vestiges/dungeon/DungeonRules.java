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

	/** Fighting rooms hold 6 to 10 monsters. */
	public static final int MIN_MONSTERS = 6;
	public static final int MAX_MONSTERS = 10;
}
