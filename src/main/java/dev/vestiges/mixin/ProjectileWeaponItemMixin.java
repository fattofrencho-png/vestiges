package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Bows and crossbows: projectile speed, and the chance to keep the arrow. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {
	@ModifyVariable(method = "shoot", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float vestiges$velocity(float velocity, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
		return TreeHooks.projectileVelocity(shooter, velocity);
	}

	@ModifyExpressionValue(method = "useAmmo", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processAmmoUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)I"))
	private static int vestiges$arrowSave(int used, @Local(argsOnly = true) LivingEntity shooter) {
		return TreeHooks.ammoUse(shooter, used);
	}
}
