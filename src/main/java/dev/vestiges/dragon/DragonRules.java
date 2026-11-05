package dev.vestiges.dragon;

import dev.vestiges.level.LevelCurve;

/**
 * The numbers of the dragon fights that the design fixed, as pure maths (unit-tested). They are all drafts for the balance pass. The data files
 * carry the per-dragon multipliers; the formulas here are what those multipliers follow, and what a test checks them against.
 */
public final class DragonRules {
	/** The vanilla dragon's health. */
	public static final double VANILLA_HEALTH = 200.0;
	/** One hit can never take more than this share of the dragon's maximum health (bed and TNT bursts in the End). */
	public static final double HIT_CAP = 0.08;
	/** Players count for the health up to this many (the arena scales to a party of four). */
	public static final int MAX_PARTY = 4;
	/** Players this close to the portal count for the health, and make a fight "occupied". */
	public static final double PARTY_RADIUS = 100.0;
	/** Players this close to the portal when it dies, and who hurt it, are rewarded. */
	public static final double REWARD_RADIUS = 150.0;
	/** An arena with no player in it for this long (2 minutes) resets the fight. */
	public static final int EMPTY_RESET_TICKS = 2 * 60 * 20;
	/** After a kill the 10 pillars and their crystals come back over this long (60 seconds). */
	public static final int REPAIR_TICKS = 60 * 20;
	/** A player this far under the portal is out in the void, beyond the dragon's reach. */
	public static final int VOID_DEPTH = 40;
	/** Levels the summoner needs for each dragon number. */
	public static final int LEVELS_PER_DRAGON = 4;

	private DragonRules() {
	}

	/** Health multiplier for dragon {@code n}: +40% for every dragon after the first. */
	public static double healthMultiplier(int n) {
		return 1.0 + 0.4 * (Math.max(1, n) - 1);
	}

	/** Damage multiplier for dragon {@code n}: +20% for every dragon after the first. */
	public static double damageMultiplier(int n) {
		return 1.0 + 0.2 * (Math.max(1, n) - 1);
	}

	/** +50% health for every player beyond the first, up to four players. */
	public static double partyFactor(int players) {
		return 1.0 + 0.5 * (Math.min(Math.max(1, players), MAX_PARTY) - 1);
	}

	/** Maximum health of a dragon with a data multiplier, for this many players. */
	public static double maxHealth(double dataMultiplier, int players) {
		return VANILLA_HEALTH * dataMultiplier * partyFactor(players);
	}

	/** The damage a hit does after the cap. */
	public static float capHit(float amount, double maxHealth) {
		return (float) Math.min(amount, HIT_CAP * maxHealth);
	}

	/** The summoner's level for dragon {@code n}: 4 × N (dragon 1 has no gate, see {@link DragonDef#gate()}). */
	public static int gateFor(int n) {
		return n <= 1 ? 0 : LEVELS_PER_DRAGON * n;
	}

	public static boolean gateOpen(int summonerLevel, int gate) {
		return summonerLevel >= gate;
	}

	/** The dragon the next summoning calls: the world's kill count plus one. */
	public static int nextNumber(int kills) {
		return Math.max(0, kills) + 1;
	}

	/** XP for a kill: {@code levels} level-ups' worth at the dragon's level. */
	public static long xpFor(int level, double levels) {
		return Math.round(LevelCurve.need(Math.max(1, level)) * levels);
	}

	/** Is this player rewarded: they hurt the dragon or its crystals and stand within the reward radius of the portal when it dies. */
	public static boolean rewarded(boolean hurtIt, double distanceToPortal) {
		return hurtIt && distanceToPortal <= REWARD_RADIUS;
	}

	/** Ender chest pages: one to start, one more for every dragon the player has been rewarded for. */
	public static int pages(int dragonsDone, int total) {
		return Math.min(total, 1 + Math.max(0, dragonsDone));
	}
}
