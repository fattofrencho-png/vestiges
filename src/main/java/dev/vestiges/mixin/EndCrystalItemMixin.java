package dev.vestiges.mixin;

import dev.vestiges.dragon.DragonService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The 4th end crystal on the portal rim refuses (and is not used up) when the summoner is below the level the next dragon needs. */
@Mixin(EndCrystalItem.class)
public abstract class EndCrystalItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void vestiges$gate(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
			if (DragonService.refuses(level, player, context.getClickedPos())) {
				cir.setReturnValue(InteractionResult.FAIL);
			}
		}
	}
}
