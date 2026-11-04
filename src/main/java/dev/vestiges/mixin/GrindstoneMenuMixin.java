package dev.vestiges.mixin;

import dev.vestiges.gear.BrokenGear;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A broken item can't be repaired by merging two copies at the grindstone (its material at the anvil is the only repair). */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenNoMerge(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
		if (!first.isEmpty() && !second.isEmpty() && (BrokenGear.isBroken(first) || BrokenGear.isBroken(second))) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
