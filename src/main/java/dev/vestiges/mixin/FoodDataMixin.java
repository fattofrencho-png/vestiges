package dev.vestiges.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Natural regeneration from food: the tree's regeneration speed and Second Wind. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private float vestiges$regen(float heal, @Local(argsOnly = true) ServerPlayer player) {
		return TreeHooks.naturalRegen(player, heal);
	}
}
