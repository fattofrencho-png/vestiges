package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dyed box opened from your inventory tints its screen. */
@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerBoxScreenMixin {
	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void vestiges$tint(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (ClientInventory.shulkerOpen() && ClientInventory.shulkerColor() != 0) {
			AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
			g.fill(s.vestiges$leftPos(), s.vestiges$topPos(), s.vestiges$leftPos() + 176, s.vestiges$topPos() + 166, 0x40000000 | ClientInventory.shulkerColor());
		}
	}
}
