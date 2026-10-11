package dev.vestiges.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/**
 * One meal buff (Batch 3, approved), from {@code data/<ns>/vestiges/meals/*.json}: the food, how long it lasts, the stats it gives (same keys as
 * the passive trees) and the text shown on the food's tooltip and the HUD.
 */
public record MealDef(String item, int minutes, Map<String, Double> stats, String text) {
	public static final Codec<MealDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("item").forGetter(MealDef::item),
		Codec.INT.fieldOf("minutes").forGetter(MealDef::minutes),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("stats").forGetter(MealDef::stats),
		Codec.STRING.fieldOf("text").forGetter(MealDef::text)
	).apply(i, MealDef::new));
}
