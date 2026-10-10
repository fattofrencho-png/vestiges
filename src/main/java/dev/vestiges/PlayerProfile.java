package dev.vestiges;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What a player carries (Fabric attachment): the data version and when Vestiges first saw them, as a game-time tick. */
public record PlayerProfile(int schema, long firstSeen) {
	public static final int CURRENT_SCHEMA = 1;

	public static final Codec<PlayerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("schema", CURRENT_SCHEMA).forGetter(PlayerProfile::schema),
		Codec.LONG.optionalFieldOf("firstSeen", 0L).forGetter(PlayerProfile::firstSeen)
	).apply(i, PlayerProfile::new));
}
