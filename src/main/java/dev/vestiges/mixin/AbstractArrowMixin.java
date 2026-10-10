package dev.vestiges.mixin;

import dev.vestiges.gear.EnchantScale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Flame I-VI: a burning arrow sets its target on fire for 1 s (I) ... 5 s (IV, vanilla), 7 s (V), 9 s (VI). Arrows lit any other way: 5 s. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	@ModifyArg(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"))
	private float vestiges$flameSeconds(float seconds) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		ItemStack weapon = self.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			return seconds;
		}
		Holder<Enchantment> flame = self.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FLAME);
		int level = EnchantmentHelper.getItemEnchantmentLevel(flame, weapon);
		return level > 0 && EnchantScale.scaled(flame) ? EnchantScale.flameSeconds(level) : seconds;
	}
}
