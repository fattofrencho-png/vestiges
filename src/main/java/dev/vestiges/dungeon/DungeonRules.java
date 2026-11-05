package dev.vestiges.dungeon;

/**
 * The numbers of a dungeon run that the design log fixed, as pure maths (unit-tested). They are drafts for the balance pass. Mob levels, party
 * health and the next-key odds join here with their slices.
 */
public final class DungeonRules {
	private DungeonRules() {
	}

	/** Lives per player: 3 in tiers 1 to 9, 2 in 10 to 24, 1 from 25. */
	public static int startingLives(int tier) {
		return tier >= 25 ? 1 : tier >= 10 ? 2 : 3;
	}
}
