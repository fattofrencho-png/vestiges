package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crossbow reload speed (both sides, so the charge animation matches). */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
	private static int vestiges$reload(int ticks, ItemStack stack, LivingEntity entity) {
		return TreeHooks.chargeDuration(entity, ticks);
	}
}
