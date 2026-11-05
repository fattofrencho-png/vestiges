package dev.vestiges.villager;

import dev.vestiges.data.VillagerTradesDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The numbers of villager trading, kept apart from the game so they can be tested. All values are drafts for the balance pass. */
public final class TradeRules {
	/** The first level of each price tier: 1-9, 10-24, 25-49, 50-74, 75-100. */
	public static final int[] TIER_FROM = {1, 10, 25, 50, 75};
	/** How many offers the Wandering Trader shows, and how many days a stock lasts. */
	public static final int TRADER_OFFERS = 6;
	public static final int TRADER_DAYS = 3;

	private TradeRules() {
	}

	public static int tierFor(int level) {
		int tier = 1;
		for (int i = 0; i < TIER_FROM.length; i++) {
			if (level >= TIER_FROM[i]) {
				tier = i + 1;
			}
		}
		return tier;
	}

	public static int levelFor(int tier) {
		return TIER_FROM[Math.max(1, Math.min(TIER_FROM.length, tier)) - 1];
	}

	/** Units a player can buy of one offer per day: 12 normal, 3 enchanted-grade, 1 luxury. */
	public static int stock(String kind) {
		return switch (kind) {
			case "e" -> 3;
			case "l" -> 1;
			default -> 12;
		};
	}

	/** The price after the emerald-trim discount (a percent, at most 50), never below 1. */
	public static int price(int base, double discountPercent) {
		double off = Math.max(0, Math.min(50, discountPercent)) / 100.0;
		return Math.max(1, (int) Math.round(base * (1.0 - off)));
	}

	/** The Wandering Trader charges more as you level: +50% of the base per tier above the first. */
	public static int traderBase(int base, int tier) {
		return (int) Math.round(base * (1.0 + 0.5 * (Math.max(1, tier) - 1)));
	}

	/** The trader's offers for this stretch of days: the same ones for everyone, a new pick every {@link #TRADER_DAYS} days. */
	public static List<VillagerTradesDef.Offer> traderPick(VillagerTradesDef pool, long traderSeed, long day) {
		List<VillagerTradesDef.Offer> all = new ArrayList<>(pool.offers());
		Collections.shuffle(all, new Random(traderSeed * 31 + day / TRADER_DAYS));
		return List.copyOf(all.subList(0, Math.min(TRADER_OFFERS, all.size())));
	}
}
