package dev.vestiges.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * The word lists a villager's name and background are made from, loaded from {@code data/<namespace>/vestiges/villager_lore/*.json}. Texts are
 * drafts until the lore chat approves them.
 */
public record VillagerLoreDef(List<String> firstNames, List<String> bynames, List<String> familyTrades, List<Turning> turningPoints, List<String> wants,
							  List<String> traits, List<String> hooks, String birthplaceNear, String birthplaceRegion, String birthplaceFar) {
	/** One turning point of a life: its kind (hardship, journey, ambition, loss, strange), the text, and a byname it can give. */
	public record Turning(String kind, String text, String byname) {
		public static final Codec<Turning> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Turning::kind),
			Codec.STRING.fieldOf("text").forGetter(Turning::text),
			Codec.STRING.optionalFieldOf("byname", "").forGetter(Turning::byname)
		).apply(i, Turning::new));
	}

	public static final Codec<VillagerLoreDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.listOf().fieldOf("first_names").forGetter(VillagerLoreDef::firstNames),
		Codec.STRING.listOf().fieldOf("bynames").forGetter(VillagerLoreDef::bynames),
		Codec.STRING.listOf().fieldOf("family_trades").forGetter(VillagerLoreDef::familyTrades),
		Turning.CODEC.listOf().fieldOf("turning_points").forGetter(VillagerLoreDef::turningPoints),
		Codec.STRING.listOf().fieldOf("wants").forGetter(VillagerLoreDef::wants),
		Codec.STRING.listOf().fieldOf("traits").forGetter(VillagerLoreDef::traits),
		Codec.STRING.listOf().optionalFieldOf("hooks", List.of()).forGetter(VillagerLoreDef::hooks),
		Codec.STRING.optionalFieldOf("birthplace_near", "a village near here").forGetter(VillagerLoreDef::birthplaceNear),
		Codec.STRING.optionalFieldOf("birthplace_region", "a place in {continent}").forGetter(VillagerLoreDef::birthplaceRegion),
		Codec.STRING.optionalFieldOf("birthplace_far", "far across the sea, in {continent}").forGetter(VillagerLoreDef::birthplaceFar)
	).apply(i, VillagerLoreDef::new));
}
