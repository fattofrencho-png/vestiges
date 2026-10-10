package dev.vestiges.waystone;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The Waystone item is a vanilla lodestone with a name, a glint and a Vestiges marker (no new item, per the vanilla-style rule). */
public final class WaystoneItems {
	private static final String TAG = "vestiges_waystone";

	private WaystoneItems() {
	}

	public static ItemStack create(int count) {
		ItemStack stack = new ItemStack(Items.LODESTONE, count);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Waystone").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		return stack;
	}

	public static boolean isWaystone(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.LODESTONE)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG, false);
	}
}
