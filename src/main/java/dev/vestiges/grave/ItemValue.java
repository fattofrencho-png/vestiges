package dev.vestiges.grave;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Rough emerald value of an item stack: Hearthbook's unit price (bundled {@code vestiges/prices.json}) times the count, plus a bonus per
 * enchantment level. Used to pick the item a decaying grave keeps.
 */
public final class ItemValue {
	private static Map<String, Double> prices;

	private ItemValue() {
	}

	private static Map<String, Double> prices() {
		if (prices == null) {
			Map<String, Double> map = new HashMap<>();
			try (InputStream in = ItemValue.class.getResourceAsStream("/vestiges/prices.json")) {
				if (in != null) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsDouble()));
				}
			} catch (Exception e) {
				Vestiges.LOGGER.warn("Vestiges: could not read prices.json", e);
			}
			prices = map;
		}
		return prices;
	}

	public static double of(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		double unit = prices().getOrDefault(id, 0.05);
		return unit * stack.getCount() + 3.0 * (levels(stack.getEnchantments()) + levels(stack.get(DataComponents.STORED_ENCHANTMENTS)));
	}

	private static int levels(ItemEnchantments e) {
		if (e == null) {
			return 0;
		}
		int sum = 0;
		for (var entry : e.entrySet()) {
			sum += entry.getIntValue();
		}
		return sum;
	}

	/** The single most valuable stack, or empty when there is none. */
	public static ItemStack mostValuable(List<ItemStack> items) {
		ItemStack best = ItemStack.EMPTY;
		double bestValue = -1;
		for (ItemStack s : items) {
			double v = of(s);
			if (v > bestValue) {
				bestValue = v;
				best = s;
			}
		}
		return best;
	}
}
