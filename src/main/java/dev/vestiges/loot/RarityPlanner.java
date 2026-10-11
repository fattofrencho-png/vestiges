package dev.vestiges.loot;

import dev.vestiges.gear.Rarity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

/**
 * Picks enchantment levels so an item lands in a chosen rarity (M3b: the rarity is rolled first, then the item is made to match). Pure Java so
 * it is unit-tested; {@link LootGear} turns the result into real enchantments. Scores follow {@link Rarity}: material points plus points per
 * enchant level. Levels stay at IV or below (table level) up to Epic, V for Legendary, and Relic carries one VI.
 */
public final class RarityPlanner {
	/** An enchantment the item can take: its id and its rarity points per level. */
	public record Option(String id, int points) {
	}

	private RarityPlanner() {
	}

	public static int low(Rarity.Tier t) {
		return switch (t) {
			case COMMON -> 0;
			case UNCOMMON -> 20;
			case SUPERIOR -> 45;
			case RARE -> 80;
			case EPIC -> 130;
			case LEGENDARY -> 230;
			case RELIC -> 130;
		};
	}

	public static int high(Rarity.Tier t) {
		return switch (t) {
			case COMMON -> 19;
			case UNCOMMON -> 44;
			case SUPERIOR -> 79;
			case RARE -> 129;
			case EPIC -> 229;
			case LEGENDARY, RELIC -> Integer.MAX_VALUE;
		};
	}

	private static int cap(Rarity.Tier t) {
		return t == Rarity.Tier.LEGENDARY || t == Rarity.Tier.RELIC ? 5 : 4;
	}

	/**
	 * Enchant levels that put an item with {@code material} points into {@code target}, or null when it cannot get there. {@code needOne}: at
	 * least one enchantment (books). Stops at a random point inside the band, so scores spread out.
	 */
	public static Map<String, Integer> plan(Rarity.Tier target, int material, List<Option> options, BiPredicate<String, String> compatible,
											boolean needOne, Random random) {
		int lo = low(target);
		int hi = high(target);
		int cap = cap(target);
		if (material > hi) {
			return null;
		}
		Map<String, Integer> levels = new LinkedHashMap<>();
		Map<String, Integer> points = new LinkedHashMap<>();
		for (Option o : options) {
			points.put(o.id(), o.points());
		}
		long score = material;
		String relic = null;
		if (target == Rarity.Tier.RELIC) {
			if (options.isEmpty()) {
				return null;
			}
			Option o = options.get(random.nextInt(options.size()));
			relic = o.id();
			levels.put(relic, 6);
			score += 6L * o.points();
		}
		for (int i = 0; i < 64; i++) {
			boolean inBand = score >= lo && (!needOne || !levels.isEmpty());
			if (inBand && random.nextBoolean()) {
				break;
			}
			List<String> raise = new ArrayList<>();
			for (Map.Entry<String, Integer> e : levels.entrySet()) {
				if (!e.getKey().equals(relic) && e.getValue() < cap && score + points.get(e.getKey()) <= hi) {
					raise.add(e.getKey());
				}
			}
			List<String> add = new ArrayList<>();
			for (Option o : options) {
				if (!levels.containsKey(o.id()) && score + o.points() <= hi && levels.keySet().stream().allMatch(k -> compatible.test(k, o.id()))) {
					add.add(o.id());
				}
			}
			if (raise.isEmpty() && add.isEmpty()) {
				break;
			}
			boolean doRaise = !raise.isEmpty() && (add.isEmpty() || random.nextBoolean());
			String pick = doRaise ? raise.get(random.nextInt(raise.size())) : add.get(random.nextInt(add.size()));
			levels.merge(pick, 1, Integer::sum);
			score += points.get(pick);
		}
		boolean ok = score >= lo && score <= hi && (!needOne || !levels.isEmpty());
		return ok ? levels : null;
	}

	/** Rolls a rarity from odds in % (Common to Relic). */
	public static Rarity.Tier roll(List<Double> odds, double r) {
		double total = 0;
		for (double o : odds) {
			total += o;
		}
		double x = r * total;
		Rarity.Tier[] tiers = Rarity.Tier.values();
		for (int i = 0; i < odds.size() && i < tiers.length; i++) {
			x -= odds.get(i);
			if (x < 0) {
				return tiers[i];
			}
		}
		return Rarity.Tier.COMMON;
	}
}
