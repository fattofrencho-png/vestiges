package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.inventory.InventoryLayout;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The survival inventory is wider than vanilla's: the window (and the recipe book beside it) stay centred, and the book never covers it. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@Shadow
	private boolean widthTooNarrow;

	private boolean vestiges$wide() {
		return (Object) this instanceof InventoryScreen && ClientInventory.on();
	}

	@ModifyConstant(method = "init", constant = @Constant(intValue = 379))
	private int vestiges$narrowBelow(int vanilla) {
		return vestiges$wide() ? InventoryLayout.BOOK_MIN_SCREEN : vanilla;
	}

	@ModifyArg(method = "init", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;init(IILnet/minecraft/client/Minecraft;Z)V"), index = 0)
	private int vestiges$bookBesideWindow(int width) {
		return vestiges$wide() && !widthTooNarrow ? InventoryLayout.bookScreenWidth(width) : width;
	}

	@WrapOperation(method = {"init", "lambda$initButton$0"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I"))
	private int vestiges$centred(RecipeBookComponent<?> book, int width, int imageWidth, Operation<Integer> original) {
		return vestiges$wide() ? InventoryLayout.windowLeft(width, book.isVisible() && !widthTooNarrow) : original.call(book, width, imageWidth);
	}
}
