package dev.vestiges.client.mixin;

import dev.vestiges.inventory.ExtraSlot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Creative mode keeps the vanilla inventory: the extra slots are moved out of sight instead of landing on the hotbar row. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	private static final String WRAPPER = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V";

	@ModifyArg(method = "selectTab", at = @At(value = "INVOKE", target = WRAPPER), index = 2)
	private int vestiges$hideX(Slot slot, int index, int x, int y) {
		return slot instanceof ExtraSlot ? -2000 : x;
	}

	@ModifyArg(method = "selectTab", at = @At(value = "INVOKE", target = WRAPPER), index = 3)
	private int vestiges$hideY(Slot slot, int index, int x, int y) {
		return slot instanceof ExtraSlot ? -2000 : y;
	}
}
