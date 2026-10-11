package dev.vestiges.mixin;

import dev.vestiges.farming.MealService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M4: each cake slice starts the cake's meal buff. */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
	@Inject(method = "eat", at = @At("RETURN"))
	private static void vestiges$cakeMeal(LevelAccessor level, BlockPos pos, BlockState state, Player player,
										  CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && player instanceof ServerPlayer sp) {
			MealService.ate(sp, Items.CAKE);
		}
	}
}
