package dev.vestiges.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A world's settings, saved in its folder as {@code vestiges/settings.json}; {@code config/vestiges/server.json} is the starting point for new
 * worlds. Pure data, no Minecraft types, so it is unit-tested directly. Missing systems take their default and the core is always on; the numbers
 * are the agreed defaults from the design log.
 */
public final class ServerConfig {
	public int schema = 1;
	public Map<String, Boolean> systems = new LinkedHashMap<>();
	public Numbers numbers = new Numbers();
	/** A dedicated server's new world: the first operator to join gets the welcome page. */
	public boolean setupPending;

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
		/** Multiplies every level XP award (1.0 = the agreed balance). */
		public double xpMultiplier = 1.0;
		/** Minecraft days before an uncollected grave turns into a skull and keeps only the most valuable item. */
		public int graveDays = 3;
		/** Share of players who must sleep to skip the night while the Sleep percentage feature is on. */
		public int sleepPercent = 50;
		/** Stackable items stack this high while the Bigger stacks feature is on: 64, or 99 by option. */
		public int stackLimit = 64;
		/** Potions, stews, filled buckets, enchanted books, and empty minecarts and boats stack this high while the Bigger stacks feature is on. */
		public int brewStack = 16;
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
			ordered.put(s.id, s.core() || (v == null ? s.onByDefault() : v));
		}
		systems = ordered;
		return this;
	}

	public boolean enabled(VestigesSystem system) {
		if (system.core()) {
			return true;
		}
		Boolean v = systems.get(system.id);
		return v == null ? system.onByDefault() : v;
	}

	public void set(VestigesSystem system, boolean on) {
		systems.put(system.id, system.core() || on);
	}

	/** Switches one feature and keeps the rest valid: turning on also turns on what it needs, turning off also turns off what needs it. */
	public void setWithNeeds(VestigesSystem system, boolean on) {
		if (system.core() || enabled(system) == on) {
			return;
		}
		set(system, on);
		for (VestigesSystem s : VestigesSystem.values()) {
			if (on && system.needs().contains(s)) {
				setWithNeeds(s, true);
			}
			if (!on && s.needs().contains(system)) {
				setWithNeeds(s, false);
			}
		}
	}

	public void applyPreset(VestigesSystem.Preset preset) {
		for (VestigesSystem s : VestigesSystem.values()) {
			set(s, s.inPreset(preset));
		}
	}

	/** The preset these switches match, or null when they are a custom choice. */
	public VestigesSystem.Preset preset() {
		for (VestigesSystem.Preset p : VestigesSystem.Preset.values()) {
			boolean same = true;
			for (VestigesSystem s : VestigesSystem.values()) {
				same &= enabled(s) == s.inPreset(p);
			}
			if (same) {
				return p;
			}
		}
		return null;
	}
}
