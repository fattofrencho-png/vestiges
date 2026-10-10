package dev.vestiges.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server settings, saved as {@code config/vestiges/server.json}. Pure data, no Minecraft types, so it is unit-tested directly.
 * Unknown or missing systems fall back to "on"; the numbers are the agreed defaults from the design log.
 */
public final class ServerConfig {
	public int schema = 1;
	public Map<String, Boolean> systems = new LinkedHashMap<>();
	public Numbers numbers = new Numbers();

	public static final class Numbers {
		/** Mob level = this share of the average nearby players' level, plus the layer bonus. */
		public double mobLevelShare = 0.75;
		public int cavesLevelBonus = 3;
		public int deepslateLevelBonus = 6;
		public int surfaceLevelCap = 20;
		public int cavesLevelCap = 40;
		public int deepslateLevelCap = 50;
		/** Death costs this share of the current level's XP, rising with level (never drops a level). */
		public int deathXpLossMinPercent = 5;
		public int deathXpLossMaxPercent = 15;
		/** Waystone trips: 1 emerald per this many blocks, minimum 1. */
		public int waystoneBlocksPerEmerald = 250;
	}

	/** Fills in every system the file does not mention, so new systems added in an update default to on. */
	public ServerConfig normalize() {
		if (systems == null) {
			systems = new LinkedHashMap<>();
		}
		if (numbers == null) {
			numbers = new Numbers();
		}
		Map<String, Boolean> ordered = new LinkedHashMap<>();
		for (VestigesSystem s : VestigesSystem.values()) {
			Boolean v = systems.get(s.id);
			ordered.put(s.id, v == null || v);
		}
		systems = ordered;
		return this;
	}

	public boolean enabled(VestigesSystem system) {
		Boolean v = systems.get(system.id);
		return v == null || v;
	}

	public void set(VestigesSystem system, boolean on) {
		systems.put(system.id, on);
	}
}
