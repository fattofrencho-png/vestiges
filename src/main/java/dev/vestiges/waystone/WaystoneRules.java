package dev.vestiges.waystone;

/** The waystone numbers, kept free of Minecraft types so they are unit-tested. */
public final class WaystoneRules {
	/** Travel is refused within this many ticks of hitting or being hit (10 seconds). */
	public static final int COMBAT_TICKS = 200;
	/** How close (blocks) a player must stand to the waystone they travel from. */
	public static final double USE_RANGE = 6.0;
	public static final int MAX_NAME = 32;

	private WaystoneRules() {
	}

	/** 1 emerald per {@code blocksPerEmerald} blocks of straight-line distance, never less than 1. A bad (zero or negative) setting falls back to 250. */
	public static int cost(double distance, int blocksPerEmerald) {
		int per = blocksPerEmerald > 0 ? blocksPerEmerald : 250;
		return Math.max(1, (int) (distance / per));
	}

	public static boolean inCombat(int nowTick, int lastHurtByTick, boolean hasAttacker, int lastHurtMobTick) {
		boolean hurt = hasAttacker && lastHurtByTick > 0 && nowTick - lastHurtByTick < COMBAT_TICKS;
		boolean hit = lastHurtMobTick > 0 && nowTick - lastHurtMobTick < COMBAT_TICKS;
		return hurt || hit;
	}

	/** Trims and shortens a player-chosen name; blank becomes the fallback. */
	public static String cleanName(String raw, String fallback) {
		String s = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
		if (s.isEmpty()) {
			return fallback;
		}
		return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
	}
}
