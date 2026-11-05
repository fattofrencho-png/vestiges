package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestiges.data.VillagerTradesDef;
import dev.vestiges.villager.TradeRules;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeRulesTest {
	@Test
	void levelBandsGiveTheFiveTiers() {
		int[][] cases = {{1, 1}, {9, 1}, {10, 2}, {24, 2}, {25, 3}, {49, 3}, {50, 4}, {74, 4}, {75, 5}, {100, 5}};
		for (int[] c : cases) {
			assertEquals(c[1], TradeRules.tierFor(c[0]), "level " + c[0]);
		}
		assertEquals(10, TradeRules.levelFor(2));
		assertEquals(75, TradeRules.levelFor(5));
	}

	@Test
	void stockByKind() {
		assertEquals(12, TradeRules.stock("n"));
		assertEquals(3, TradeRules.stock("e"));
		assertEquals(1, TradeRules.stock("l"));
	}

	@Test
	void trimDiscountNeverGoesBelowOneOrPastHalf() {
		assertEquals(10, TradeRules.price(10, 0));
		assertEquals(9, TradeRules.price(10, 10));
		assertEquals(1, TradeRules.price(1, 50));
		assertEquals(5, TradeRules.price(10, 90), "the discount is capped at 50%");
	}

	@Test
	void theTraderChargesMoreAsYouLevelAndRotates() {
		assertEquals(10, TradeRules.traderBase(10, 1));
		assertEquals(30, TradeRules.traderBase(10, 5));
		List<VillagerTradesDef.Offer> offers = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			offers.add(new VillagerTradesDef.Offer("minecraft:item" + i, 1, 3, 1, "n"));
		}
		VillagerTradesDef pool = new VillagerTradesDef("wandering_trader", offers);
		assertEquals(TradeRules.TRADER_OFFERS, TradeRules.traderPick(pool, 7, 10).size());
		assertEquals(TradeRules.traderPick(pool, 7, 9), TradeRules.traderPick(pool, 7, 10), "the same stretch of days gives the same stock");
		assertTrue(!TradeRules.traderPick(pool, 7, 9).equals(TradeRules.traderPick(pool, 7, 9 + TradeRules.TRADER_DAYS)), "a new stretch gives a new stock");
	}
}
