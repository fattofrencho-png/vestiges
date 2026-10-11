package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.farming.FarmingService;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.StatEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M4: the tree's food nodes: food restores more hunger, and more saturation. */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@WrapOperation(method = "onConsume", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void vestiges$heartierFood(FoodData data, FoodProperties food, Operation<Void> original, Level level, LivingEntity entity, ItemStack stack,
									   Consumable consumable) {
		if (entity instanceof ServerPlayer player && FarmingService.enabled()) {
			PlayerStats s = StatEngine.get(player);
			double more = s.get("food_restore");
			double sat = s.get("saturation");
			if (more != 0 || sat != 0) {
				food = new FoodProperties(Math.max(0, (int) Math.round(food.nutrition() * (1 + more / 100.0))),
					(float) Math.max(0, food.saturation() * (1 + sat / 100.0)), food.canAlwaysEat());
			}
		}
		original.call(data, food);
	}
}
