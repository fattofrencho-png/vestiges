package dev.vestiges.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One feat, loaded from {@code data/<namespace>/vestiges/feats/<id>.json}. In M0 the requirement and reward are display text; M5 replaces them
 * with structured checks and real reward items, keeping these fields as the shown descriptions.
 */
public record Feat(String chain, int order, String name, String requirement, String reward, int xp, boolean repeatable) {
	public static final Codec<Feat> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("chain").forGetter(Feat::chain),
		Codec.INT.optionalFieldOf("order", 0).forGetter(Feat::order),
		Codec.STRING.fieldOf("name").forGetter(Feat::name),
		Codec.STRING.fieldOf("requirement").forGetter(Feat::requirement),
		Codec.STRING.fieldOf("reward").forGetter(Feat::reward),
		Codec.INT.optionalFieldOf("xp", 0).forGetter(Feat::xp),
		Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(Feat::repeatable)
	).apply(i, Feat::new));
}
