package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.gear.EnchantScale;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Looting's rare-drop chances (wither skulls, rabbit feet...) see the vanilla-equivalent level. */
@Mixin(LootItemRandomChanceWithEnchantedBonusCondition.class)
public abstract class EnchantedBonusChanceMixin {
	@Shadow
	@Final
	private Holder<Enchantment> enchantment;

	@ModifyExpressionValue(method = "test", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int vestiges$level(int level, @Local(argsOnly = true) LootContext context) {
		return EnchantScale.lootLevel(enchantment, level, context.getRandom());
	}
}
