package dev.vestiges.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Saved on a dragon entity once it has been set up as a lore dragon: its {@code number}, the {@code party} size its health was scaled to (it only
 * grows during a fight) and whether it was killed by an operator command (that gives no kill and no rewards).
 */
public record DragonMark(int number, int party, boolean byCommand) {
	public static final Codec<DragonMark> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("number").forGetter(DragonMark::number),
		Codec.INT.optionalFieldOf("party", 1).forGetter(DragonMark::party),
		Codec.BOOL.optionalFieldOf("by_command", false).forGetter(DragonMark::byCommand)
	).apply(i, DragonMark::new));

	public DragonMark withParty(int p) {
		return new DragonMark(number, p, byCommand);
	}

	public DragonMark killedByCommand() {
		return new DragonMark(number, party, true);
	}
}
