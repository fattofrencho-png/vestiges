package dev.vestiges.mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What Vestiges gives a monster when it first appears (M3, approved): its level, its rank (0 normal, 1 Champion, 2 Elite, 3 Warlord), its
 * powers, its size, and whether Caller already called. Saved with the mob and sent to clients (look-at card, health bar, glow).
 */
public record MobData(int level, int rank, List<String> powers, float size, boolean called) {
	public static final int NORMAL = 0;
	public static final int CHAMPION = 1;
	public static final int ELITE = 2;
	public static final int WARLORD = 3;

	public static final Codec<MobData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("level").forGetter(MobData::level),
		Codec.INT.optionalFieldOf("rank", 0).forGetter(MobData::rank),
		Codec.STRING.listOf().optionalFieldOf("powers", List.of()).forGetter(MobData::powers),
		Codec.FLOAT.optionalFieldOf("size", 1.0f).forGetter(MobData::size),
		Codec.BOOL.optionalFieldOf("called", false).forGetter(MobData::called)
	).apply(i, MobData::new));

	public static final StreamCodec<ByteBuf, MobData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, MobData::level,
		ByteBufCodecs.VAR_INT, MobData::rank,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), MobData::powers,
		ByteBufCodecs.FLOAT, MobData::size,
		ByteBufCodecs.BOOL, MobData::called,
		MobData::new);

	public boolean has(String power) {
		return powers.contains(power);
	}

	public boolean elite() {
		return rank > NORMAL;
	}

	/** Strength by rank (approved): Champion x2, Elite x3, Warlord x5. */
	public double strength() {
		return switch (rank) {
			case CHAMPION -> 2;
			case ELITE -> 3;
			case WARLORD -> 5;
			default -> 1;
		};
	}

	public MobData withCalled() {
		return new MobData(level, rank, powers, size, true);
	}
}
