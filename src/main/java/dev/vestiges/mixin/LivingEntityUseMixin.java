package dev.vestiges.mixin;

import dev.vestiges.farming.MealService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M4: finishing a meal starts its buff. The food is read before eating, since eating may use it up. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUseMixin {
	@Shadow
	protected ItemStack useItem;

	@Unique
	private Item vestiges$eating;

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void vestiges$beforeEat(CallbackInfo ci) {
		vestiges$eating = (Object) this instanceof ServerPlayer && useItem != null && !useItem.isEmpty() ? useItem.getItem() : null;
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void vestiges$afterEat(CallbackInfo ci) {
		Item item = vestiges$eating;
		vestiges$eating = null;
		if (item != null && (Object) this instanceof ServerPlayer player) {
			MealService.ate(player, item);
		}
	}
}
