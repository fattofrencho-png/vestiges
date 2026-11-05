package dev.vestiges.mixin;

import dev.vestiges.inventory.StackRules;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anvils and grindstones use up everything in an input slot, so an enchanted book goes in one at a time. */
@Mixin(Slot.class)
public abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
	private void vestiges$oneBook(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack.is(Items.ENCHANTED_BOOK) && StackRules.isSingleInput(container)) {
			cir.setReturnValue(1);
		}
	}
}
