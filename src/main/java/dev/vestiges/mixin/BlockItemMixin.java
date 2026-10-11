package dev.vestiges.mixin;

import dev.vestiges.codex.PlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M4: blocks placed by players are remembered, so breaking them never counts for the Codex. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
	private void vestiges$rememberPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getLevel() instanceof ServerLevel level && context.getPlayer() != null) {
			PlacedBlocks.markPlaced(level, context.getClickedPos());
		}
	}
}
