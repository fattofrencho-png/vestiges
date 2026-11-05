package dev.vestiges.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Mending, reworked (design log, 2026-11-03): it no longer repairs from XP. It is a one-time safety net: gear that would break instead stays at
 * 1 durability in a <b>broken state</b> (does nothing) and loses the enchant. Repairing it past 1 durability (anvil, with its material) ends the
 * state. The state is a flag in the item's custom data, valid only while the item is still at 1 durability.
 */
public final class BrokenGear {
	private static final String TAG = "vestiges_broken";

	private BrokenGear() {
	}

	public static boolean isBroken(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG, false);
	}

	public static boolean hasMending(ItemStack stack) {
		return stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(Enchantments.MENDING));
	}

	/** Called when {@code damage} (the new total) would break the stack: true when Mending caught it, so the stack must not break. */
	public static boolean tryCatch(ItemStack stack, int damage, ServerPlayer player) {
		if (damage < stack.getMaxDamage() || !hasMending(stack)) {
			return false;
		}
		stack.setDamageValue(stack.getMaxDamage() - 1);
		EnchantmentHelper.updateEnchantments(stack, m -> m.removeIf(h -> h.is(Enchantments.MENDING)));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, (CompoundTag t) -> t.putBoolean(TAG, true));
		if (player != null) {
			player.sendSystemMessage(Component.literal("Mending held your " + stack.getHoverName().getString() + ", but it is broken. Repair it at an anvil.")
				.withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		}
		return true;
	}

	/** Once, when a Mending item drops to the last tenth of its durability (at least 3 points): Mending will hold it one time. */
	public static void warn(ItemStack stack, int damage, ServerPlayer player) {
		int max = stack.getMaxDamage();
		int limit = Math.max(3, max / 10);
		if (player == null || max - damage > limit || max - stack.getDamageValue() <= limit || max - damage <= 0 || !hasMending(stack)) {
			return;
		}
		player.sendSystemMessage(Component.literal("Your " + stack.getHoverName().getString() + " is about to give out. Mending will save it once.")
			.withStyle(ChatFormatting.GOLD), true);
		player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** Called after the durability changed: a repaired item leaves the broken state. */
	public static void clearIfRepaired(ItemStack stack) {
		if (stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage() - 1 && stack.get(DataComponents.CUSTOM_DATA) != null) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, (CompoundTag t) -> t.remove(TAG));
		}
	}
}
