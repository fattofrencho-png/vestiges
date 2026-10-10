package dev.vestiges.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's level: level (1..100), XP progress inside the current level, Paragon count, and the one-time discoveries already rewarded
 * ("biome:minecraft:plains", "structure:minecraft:mansion", "dim:minecraft:the_nether"). Immutable; changes make a new record.
 */
public record PlayerLevel(int level, long xp, int paragon, Set<String> firsts) {
	public static final PlayerLevel START = new PlayerLevel(1, 0, 0, Set.of());

	public static final Codec<PlayerLevel> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerLevel::level),
		Codec.LONG.optionalFieldOf("xp", 0L).forGetter(PlayerLevel::xp),
		Codec.INT.optionalFieldOf("paragon", 0).forGetter(PlayerLevel::paragon),
		Codec.STRING.listOf().xmap(l -> (Set<String>) new HashSet<>(l), s -> List.copyOf(s)).optionalFieldOf("firsts", Set.of()).forGetter(PlayerLevel::firsts)
	).apply(i, PlayerLevel::new));

	public PlayerLevel withXp(int newLevel, long newXp, int newParagon) {
		return new PlayerLevel(newLevel, newXp, newParagon, firsts);
	}

	public PlayerLevel withFirst(String key) {
		Set<String> s = new HashSet<>(firsts);
		s.add(key);
		return new PlayerLevel(level, xp, paragon, Set.copyOf(s));
	}

	public long need() {
		return LevelCurve.need(level);
	}

	/** Applies {@code amount} XP, rolling over into new levels and then Paragon levels. */
	public PlayerLevel addXp(long amount) {
		int l = level;
		int p = paragon;
		long x = xp + Math.max(0, amount);
		while (x >= LevelCurve.need(l)) {
			x -= LevelCurve.need(l);
			if (l < LevelCurve.MAX_LEVEL) {
				l++;
			} else {
				p++;
			}
		}
		return new PlayerLevel(l, x, p, firsts);
	}
}
