package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One dungeon theme, loaded from {@code data/<namespace>/vestiges/dungeon_themes/<id>.json}. {@code unlock} is empty when the theme is open from the
 * start, or {@code feat:<id>} when a feat opens it (the End theme waits for the Dragonslayer feat). Rooms, mobs, bosses and caches join this record
 * as their slices are built.
 */
public record DungeonTheme(String name, String color, String unlock) {
	public static final Codec<DungeonTheme> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(DungeonTheme::name),
		Codec.STRING.optionalFieldOf("color", "white").forGetter(DungeonTheme::color),
		Codec.STRING.optionalFieldOf("unlock", "").forGetter(DungeonTheme::unlock)
	).apply(i, DungeonTheme::new));

	public boolean openFromStart() {
		return unlock.isEmpty();
	}

	/** The feat that opens this theme, or an empty string. */
	public String unlockFeat() {
		return unlock.startsWith("feat:") ? unlock.substring(5) : "";
	}
}
