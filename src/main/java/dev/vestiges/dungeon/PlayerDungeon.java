package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What one player has earned from dungeons: whether the one-time elytra of the End theme has been handed out. */
public record PlayerDungeon(boolean elytra) {
	public static final PlayerDungeon EMPTY = new PlayerDungeon(false);
	public static final Codec<PlayerDungeon> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("elytra", false).forGetter(PlayerDungeon::elytra)
	).apply(i, PlayerDungeon::new));

	public PlayerDungeon withElytra() {
		return new PlayerDungeon(true);
	}
}
