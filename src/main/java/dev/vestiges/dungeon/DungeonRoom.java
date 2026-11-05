package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * One room template, loaded from {@code data/<namespace>/vestiges/dungeon_rooms/<id>.json}. {@code role} says where it can stand in a run (start,
 * combat, lieutenant, boss, reward), {@code size} is the side of its square floor, and {@code shape} names the code-built layout (hall, arena,
 * pillars, pit, split). A {@code structure} (a vanilla structure file the author hands in) replaces the built layout and uses the marker blocks:
 * gold block = mob spawn, chest = cache, lodestone = boss spot, iron door = door slot.
 */
public record DungeonRoom(String theme, String role, String shape, int size, String structure) {
	public static final List<String> ROLES = List.of("start", "combat", "lieutenant", "boss", "reward");
	public static final List<String> SHAPES = List.of("hall", "arena", "pillars", "pit", "split");

	public static final Codec<DungeonRoom> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("theme").forGetter(DungeonRoom::theme),
		Codec.STRING.fieldOf("role").forGetter(DungeonRoom::role),
		Codec.STRING.optionalFieldOf("shape", "arena").forGetter(DungeonRoom::shape),
		Codec.INT.optionalFieldOf("size", 32).forGetter(DungeonRoom::size),
		Codec.STRING.optionalFieldOf("structure", "").forGetter(DungeonRoom::structure)
	).apply(i, DungeonRoom::new));
}
