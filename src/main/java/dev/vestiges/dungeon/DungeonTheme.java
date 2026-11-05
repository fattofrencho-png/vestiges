package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * One dungeon theme, loaded from {@code data/<namespace>/vestiges/dungeon_themes/<id>.json}. {@code unlock} is empty when the theme is open from the
 * start, or {@code feat:<id>} when a feat opens it (the End theme waits for the Dragonslayer feat). {@code palette} lists the block ids the rooms are
 * built from (one is picked at random for each block, so a repeated id counts double), and {@code mobs} the entity ids the fighting rooms spawn.
 * Bosses and caches join this record as their slices are built.
 */
public record DungeonTheme(String name, String color, String unlock, Palette palette, List<String> mobs) {
	/** The blocks of a theme: floor, wall, ceiling, pillar, ceiling light and the gate that closes a door. */
	public record Palette(List<String> floor, List<String> wall, List<String> ceiling, List<String> pillar, List<String> light, List<String> gate) {
		public static final Palette DEFAULT = new Palette(List.of("minecraft:stone_bricks"), List.of("minecraft:stone_bricks"), List.of("minecraft:stone_bricks"),
			List.of("minecraft:stone_bricks"), List.of("minecraft:glowstone"), List.of("minecraft:iron_bars"));

		public static final Codec<Palette> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("floor", DEFAULT.floor()).forGetter(Palette::floor),
			Codec.STRING.listOf().optionalFieldOf("wall", DEFAULT.wall()).forGetter(Palette::wall),
			Codec.STRING.listOf().optionalFieldOf("ceiling", DEFAULT.ceiling()).forGetter(Palette::ceiling),
			Codec.STRING.listOf().optionalFieldOf("pillar", DEFAULT.pillar()).forGetter(Palette::pillar),
			Codec.STRING.listOf().optionalFieldOf("light", DEFAULT.light()).forGetter(Palette::light),
			Codec.STRING.listOf().optionalFieldOf("gate", DEFAULT.gate()).forGetter(Palette::gate)
		).apply(i, Palette::new));
	}

	public static final Codec<DungeonTheme> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(DungeonTheme::name),
		Codec.STRING.optionalFieldOf("color", "white").forGetter(DungeonTheme::color),
		Codec.STRING.optionalFieldOf("unlock", "").forGetter(DungeonTheme::unlock),
		Palette.CODEC.optionalFieldOf("palette", Palette.DEFAULT).forGetter(DungeonTheme::palette),
		Codec.STRING.listOf().optionalFieldOf("mobs", List.of("minecraft:zombie")).forGetter(DungeonTheme::mobs)
	).apply(i, DungeonTheme::new));

	public DungeonTheme(String name, String color, String unlock) {
		this(name, color, unlock, Palette.DEFAULT, List.of("minecraft:zombie"));
	}

	public boolean openFromStart() {
		return unlock.isEmpty();
	}

	/** The feat that opens this theme, or an empty string. */
	public String unlockFeat() {
		return unlock.startsWith("feat:") ? unlock.substring(5) : "";
	}
}
