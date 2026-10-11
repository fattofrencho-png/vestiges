package dev.vestiges.mixin;

import dev.vestiges.mobs.ArmorFormula;
import dev.vestiges.mobs.MobService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The agreed armor formula (Batch 3, switched on with mob levels in M3): blocked = armor / (armor + 20), at most 85%; toughness counts as half
 * an armor point. Breach still lowers how much armor helps, as in vanilla.
 */
@Mixin(CombatRules.class)
public abstract class CombatRulesMixin {
	@Inject(method = "getDamageAfterAbsorb", at = @At("HEAD"), cancellable = true)
	private static void vestiges$armor(LivingEntity entity, float damage, DamageSource source, float armor, float toughness,
									   CallbackInfoReturnable<Float> cir) {
		if (!MobService.enabled()) {
			return;
		}
		float blocked = ArmorFormula.blocked(armor, toughness);
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && entity.level() instanceof ServerLevel level) {
			blocked = Math.max(0.0f, Math.min(1.0f, EnchantmentHelper.modifyArmorEffectiveness(level, weapon, entity, source, blocked)));
		}
		cir.setReturnValue(damage * (1.0f - blocked));
	}
}
