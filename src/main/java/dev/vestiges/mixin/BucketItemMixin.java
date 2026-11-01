package dev.vestiges.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emptying one bucket from a stack: vanilla swaps the whole stack for one empty bucket, which would lose the rest. */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "getEmptySuccessItem", at = @At("HEAD"), cancellable = true)
	private static void vestiges$keepStack(ItemStack stack, Player player, CallbackInfoReturnable<ItemStack> cir) {
		if (stack.getCount() > 1 && !player.hasInfiniteMaterials()) {
			stack.shrink(1);
			if (!player.level().isClientSide()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET), net.minecraft.util.Prediction.PREDICTED);
			}
			cir.setReturnValue(stack);
		}
	}
}
