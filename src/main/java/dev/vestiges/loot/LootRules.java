package dev.vestiges.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import java.util.List;
import java.util.Map;

/**
 * The loot numbers Java needs (M3b), from {@code data/vestiges/vestiges/loot_rules/default.json} (written by tools/build_loot.py together with
 * the loot tables, so both always agree): the 11 loot bands, the Batch 3 rarity odds by loot level, and RPG Craft's gear ladder.
 */
public record LootRules(List<List<Integer>> bands, List<Row> rarity, List<Base> ladder) {
	/** Rarity odds (%, Common to Relic) for loot levels up to {@code maxLevel}. */
	public record Row(int maxLevel, List<Double> odds) {
		public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("max_level").forGetter(Row::maxLevel),
			Codec.DOUBLE.listOf().fieldOf("odds").forGetter(Row::odds)
		).apply(i, Row::new));
	}

	/** One rung of the gear ladder: when it starts, peaks and how common it is at its peak; items with a weight multiplier. */
	public record Base(int start, int peak, int weight, Map<String, Double> items) {
		public static final Codec<Base> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("start").forGetter(Base::start),
			Codec.INT.fieldOf("peak").forGetter(Base::peak),
			Codec.INT.fieldOf("weight").forGetter(Base::weight),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("items").forGetter(Base::items)
		).apply(i, Base::new));

		/** RPG Craft's curve: 10% → 100% from start to peak, then -28% per band, never below 6% of the peak. */
		public int weightAt(int band) {
			if (band < start) {
				return 0;
			}
			int full = weight * 100;
			if (band <= peak) {
				double prog = peak == start ? 0 : (band - start) / (double) (peak - start);
				return Math.max(1, (int) (full * (0.10 + 0.90 * prog)));
			}
			return Math.max(1, (int) (full * Math.max(0.06, Math.pow(0.72, band - peak))));
		}
	}

	public static final Codec<LootRules> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.listOf().listOf().fieldOf("bands").forGetter(LootRules::bands),
		Row.CODEC.listOf().fieldOf("rarity").forGetter(LootRules::rarity),
		Base.CODEC.listOf().fieldOf("ladder").forGetter(LootRules::ladder)
	).apply(i, LootRules::new));

	/** Used only if the data file is missing: the agreed bands and odds, and no ladder (gear then falls back to an iron sword). */
	public static final LootRules FALLBACK = new LootRules(
		List.of(List.of(1, 4), List.of(5, 9), List.of(10, 14), List.of(15, 19), List.of(20, 29), List.of(30, 39), List.of(40, 49),
			List.of(50, 64), List.of(65, 79), List.of(80, 89), List.of(90, 100)),
		List.of(new Row(10, List.of(60.0, 35.0, 4.9, 0.1, 0.0, 0.0, 0.0)), new Row(20, List.of(35.0, 44.0, 17.0, 3.5, 0.4, 0.08, 0.02)),
			new Row(35, List.of(15.0, 39.0, 32.0, 12.0, 1.7, 0.25, 0.05)), new Row(10000, List.of(5.0, 24.0, 40.0, 25.0, 5.4, 0.5, 0.1))),
		List.of());

	public static LootRules get() {
		LootRules r = VestigesData.LOOT_RULES.get(Vestiges.id("default"));
		return r != null ? r : FALLBACK;
	}

	/** Band 1-11 for a loot level (below the first band = 1, above the last = the last). */
	public int band(int lootLevel) {
		for (int b = 0; b < bands.size(); b++) {
			if (lootLevel <= bands.get(b).get(1)) {
				return b + 1;
			}
		}
		return bands.size();
	}

	public Row row(int lootLevel) {
		for (Row r : rarity) {
			if (lootLevel <= r.maxLevel()) {
				return r;
			}
		}
		return rarity.get(rarity.size() - 1);
	}
}
