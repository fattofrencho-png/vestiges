package dev.vestiges.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M4: remembers who is using bone meal, so the crop can read their bone meal node. */
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"))
	private void vestiges$start(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		dev.vestiges.farming.FarmingService.BONEMEAL_USER.set(context.getPlayer() instanceof ServerPlayer sp ? sp : null);
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	private void vestiges$end(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		dev.vestiges.farming.FarmingService.BONEMEAL_USER.remove();
	}
}
