package dev.vestiges.gear;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Rarity (approved in Batch 3): a score from the base material plus enchant points per level, in 7 tiers. Any level VI enchant makes the item
 * Relic (deep brown with a white outline), with one star per VI enchant. A property of the item itself, never of its type: a wooden pickaxe can out-rank a netherite one.
 */
public final class Rarity {
	public enum Tier {
		COMMON("Common", 0xFFFFFF), UNCOMMON("Uncommon", 0x8C8C8C), SUPERIOR("Superior", 0x73CCFF), RARE("Rare", 0x264DE6),
		EPIC("Epic", 0xFFB81A), LEGENDARY("Legendary", 0xFF2626), RELIC("Relic", 0x5C3317);

		public final String label;
		public final int color;

		Tier(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	public record Result(Tier tier, int score, int stars) {
	}

	private static final Set<String> MAJOR = Set.of("sharpness", "power", "protection", "efficiency", "fortune", "looting", "mending", "silk_touch",
		"infinity");
	private static final Set<String> MINOR = Set.of("smite", "bane_of_arthropods", "knockback", "punch");

	private Rarity() {
	}

	/** Base material: wood or leather 0 · stone, gold, chainmail or copper 10 · iron 20 · diamond 30 · netherite 40. */
	public static int materialPoints(String itemPath) {
		if (itemPath.startsWith("netherite_")) {
			return 40;
		}
		if (itemPath.startsWith("diamond_")) {
			return 30;
		}
		if (itemPath.startsWith("iron_")) {
			return 20;
		}
		if (itemPath.startsWith("stone_") || itemPath.startsWith("golden_") || itemPath.startsWith("chainmail_") || itemPath.startsWith("copper_")) {
			return 10;
		}
		// Gear without a material counts like the gear tier it sits in on the ladder.
		return switch (itemPath) {
			case "bow", "crossbow", "shield" -> 10;
			case "turtle_helmet" -> 20;
			case "trident", "mace" -> 30;
			case "elytra" -> 40;
			default -> 0;
		};
	}

	/** Per level: major 12, normal 8, minor 5; curses -20. */
	public static int enchantPoints(String enchantPath, boolean curse) {
		if (curse) {
			return -20;
		}
		return MAJOR.contains(enchantPath) ? 12 : MINOR.contains(enchantPath) ? 5 : 8;
	}

	/** Bands: 0-19 Common · 20-44 Uncommon · 45-79 Superior · 80-129 Rare · 130-229 Epic · 230+ Legendary; any VI = Relic. */
	public static Result score(int material, Map<String, Integer> enchants, Set<String> curses) {
		int score = material;
		int stars = 0;
		for (Map.Entry<String, Integer> e : enchants.entrySet()) {
			boolean curse = curses.contains(e.getKey());
			score += enchantPoints(e.getKey(), curse) * e.getValue();
			if (!curse && e.getValue() >= 6) {
				stars++;
			}
		}
		Tier tier;
		if (stars > 0) {
			tier = Tier.RELIC;
		} else if (score >= 230) {
			tier = Tier.LEGENDARY;
		} else if (score >= 130) {
			tier = Tier.EPIC;
		} else if (score >= 80) {
			tier = Tier.RARE;
		} else if (score >= 45) {
			tier = Tier.SUPERIOR;
		} else if (score >= 20) {
			tier = Tier.UNCOMMON;
		} else {
			tier = Tier.COMMON;
		}
		return new Result(tier, score, stars);
	}

	/** Rarity of a real item (books use their stored enchantments). */
	public static Result of(ItemStack stack) {
		ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		Map<String, Integer> levels = new HashMap<>();
		Set<String> curses = new HashSet<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
			String path = e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			levels.merge(path, e.getIntValue(), Integer::sum);
			if (e.getKey().is(EnchantmentTags.CURSE)) {
				curses.add(path);
			}
		}
		return score(materialPoints(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()), levels, curses);
	}

	/** Gear and enchanted things get a rarity; plain items (dirt, food...) do not. */
	public static boolean applies(ItemStack stack) {
		return !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty() || GearLadder.isGear(stack);
	}
}
