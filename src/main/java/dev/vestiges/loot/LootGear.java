package dev.vestiges.loot;

import dev.vestiges.gear.Rarity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Builds found gear and books (M3b, approved): the rarity is rolled from the Batch 3 odds for the loot level, then a base is picked from RPG
 * Craft's ladder for that band and enchanted to land exactly in that rarity. Elites never drop Common; the Warlord's book is always Relic.
 */
public final class LootGear {
	private LootGear() {
	}

	public static ItemStack build(ServerLevel level, RandomSource random, int lootLevel, boolean book, Rarity.Tier min, Rarity.Tier forced) {
		LootRules rules = LootRules.get();
		Rarity.Tier tier = forced != null ? forced : RarityPlanner.roll(rules.row(lootLevel).odds(), random.nextDouble());
		if (min != null && tier.ordinal() < min.ordinal()) {
			tier = min;
		}
		Random rnd = new Random(random.nextLong());
		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> pool = new ArrayList<>();
		for (Holder<Enchantment> h : registry.getTagOrEmpty(EnchantmentTags.ON_RANDOM_LOOT)) {
			if (!h.is(EnchantmentTags.CURSE)) {
				pool.add(h);
			}
		}
		// Step down one rarity at a time if nothing can reach it (a shield cannot be Legendary); Common always works.
		for (int t = tier.ordinal(); t >= 0; t--) {
			Rarity.Tier target = Rarity.Tier.values()[t];
			ItemStack out = book ? makeBook(target, pool, rnd) : makeGear(target, rules, rules.band(lootLevel), pool, rnd);
			if (out != null) {
				return out;
			}
		}
		return book ? new ItemStack(Items.BOOK) : new ItemStack(Items.WOODEN_SWORD);
	}

	private static ItemStack makeBook(Rarity.Tier target, List<Holder<Enchantment>> pool, Random rnd) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		return enchant(stack, target, 0, pool, true, rnd) ? stack : null;
	}

	private static ItemStack makeGear(Rarity.Tier target, LootRules rules, int band, List<Holder<Enchantment>> pool, Random rnd) {
		List<Item> items = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		for (LootRules.Base base : rules.ladder()) {
			int w = base.weightAt(band);
			if (w <= 0) {
				continue;
			}
			for (Map.Entry<String, Double> e : base.items().entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item != null && Rarity.materialPoints(id.getPath()) <= RarityPlanner.high(target)) {
					items.add(item);
					weights.add(Math.max(1, (int) (w * e.getValue())));
				}
			}
		}
		for (int tries = 0; tries < 8 && !items.isEmpty(); tries++) {
			int total = weights.stream().mapToInt(Integer::intValue).sum();
			int x = rnd.nextInt(total);
			int i = 0;
			while (x >= weights.get(i)) {
				x -= weights.get(i++);
			}
			Item item = items.get(i);
			ItemStack stack = new ItemStack(item);
			int material = Rarity.materialPoints(BuiltInRegistries.ITEM.getKey(item).getPath());
			if (enchant(stack, target, material, pool, false, rnd)) {
				return stack;
			}
			items.remove(i);
			weights.remove(i);
		}
		return null;
	}

	/** Plans and applies enchantments; false if this item cannot reach the target rarity. */
	private static boolean enchant(ItemStack stack, Rarity.Tier target, int material, List<Holder<Enchantment>> pool, boolean book, Random rnd) {
		Map<String, Holder<Enchantment>> byId = new HashMap<>();
		List<RarityPlanner.Option> options = new ArrayList<>();
		for (Holder<Enchantment> h : pool) {
			if (book || h.value().canEnchant(stack)) {
				String id = h.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
				if (id != null) {
					byId.put(id, h);
					options.add(new RarityPlanner.Option(id, Rarity.enchantPoints(Identifier.parse(id).getPath(), false)));
				}
			}
		}
		Map<String, Integer> plan = RarityPlanner.plan(target, material, options,
			(a, b) -> Enchantment.areCompatible(byId.get(a), byId.get(b)), book, rnd);
		if (plan == null) {
			return false;
		}
		EnchantmentHelper.updateEnchantments(stack, m -> plan.forEach((id, lvl) -> m.set(byId.get(id), lvl)));
		return true;
	}
}
