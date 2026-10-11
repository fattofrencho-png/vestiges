package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** With the recipe book open, the survival inventory is placed as if it had vanilla's width, so the book sits beside it instead of over it. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@ModifyArg(method = {"init", "lambda$initButton$0"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I"), index = 1)
	private int vestiges$vanillaWidth(int imageWidth) {
		return (Object) this instanceof InventoryScreen && ClientInventory.on() ? 176 : imageWidth;
	}
}
