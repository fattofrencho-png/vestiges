package dev.vestiges.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * What one job sells, loaded from {@code data/<namespace>/vestiges/villager_trades/<job>.json}. Offers are unlocked by the player's level band
 * ({@code tier} 1 to 5) and paid in emeralds. {@code kind}: n normal (stock 12 a day), e enchanted-grade (3), l luxury (1). Prices are drafts for the
 * balance pass. The job {@code wandering_trader} is the Wandering Trader's pool: a few offers picked at random, rotating every few days.
 */
public record VillagerTradesDef(String job, List<Offer> offers) {
	public record Offer(String item, int count, int price, int tier, String kind) {
		public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("item").forGetter(Offer::item),
			Codec.INT.optionalFieldOf("count", 1).forGetter(Offer::count),
			Codec.INT.fieldOf("price").forGetter(Offer::price),
			Codec.INT.optionalFieldOf("tier", 1).forGetter(Offer::tier),
			Codec.STRING.optionalFieldOf("kind", "n").forGetter(Offer::kind)
		).apply(i, Offer::new));
	}

	public static final Codec<VillagerTradesDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("job").forGetter(VillagerTradesDef::job),
		Offer.CODEC.listOf().fieldOf("offers").forGetter(VillagerTradesDef::offers)
	).apply(i, VillagerTradesDef::new));
}
