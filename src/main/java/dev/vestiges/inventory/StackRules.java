package dev.vestiges.inventory;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;

/** The Bigger stacks feature: everything stackable goes to the world's limit (64, or 99 by option), a few agreed unstackable items to their own number, and everything else stays at 1. */
public final class StackRules {
	public static final int DEFAULT = 64;
	public static final int BIG = 99;
	/** Anvil, smithing and grindstone inputs, which use up a whole slot. */
	private static final Set<Container> SINGLE_INPUTS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private StackRules() {
	}

	/** The pure rule: {@code vanilla} is the item's own limit. */
	public static int max(int vanilla, boolean brew, int stackLimit, int brewStack) {
		if (vanilla > 1) {
			return stackLimit >= BIG ? BIG : DEFAULT;
		}
		return brew ? Math.max(1, Math.min(BIG, brewStack)) : vanilla;
	}

	public static int max(ItemStack stack, int vanilla) {
		if (stack.isEmpty() || !ConfigStore.server().enabled(VestigesSystem.STACKS)) {
			return vanilla;
		}
		return max(vanilla, vanilla == 1 && isBrew(stack), ConfigStore.server().numbers.stackLimit, ConfigStore.server().numbers.brewStack);
	}

	/** The agreed exceptions: potions, stews, filled buckets, enchanted books, and empty minecarts and boats. */
	private static boolean isBrew(ItemStack stack) {
		return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)
			|| stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.SUSPICIOUS_STEW)
			|| stack.getItem() instanceof BucketItem || stack.getItem() instanceof SolidBucketItem || stack.is(Items.MILK_BUCKET)
			|| stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.MINECART) || stack.is(ItemTags.BOATS);
	}

	public static void markSingleInput(Container container) {
		SINGLE_INPUTS.add(container);
	}

	public static boolean isSingleInput(Container container) {
		return SINGLE_INPUTS.contains(container);
	}
}
