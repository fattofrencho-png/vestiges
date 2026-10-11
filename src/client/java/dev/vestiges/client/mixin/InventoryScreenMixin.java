package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.client.inventory.InventoryPanel;
import dev.vestiges.inventory.InventoryLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The survival inventory (layout A): the window grows to the right for the rack panel, the new slot frames and the Bank are drawn, and the
 * recipe book button moves under the crafting result so the ender chest, totem and Bank row stays free.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void vestiges$wider(Player player, CallbackInfo ci) {
		if (ClientInventory.on()) {
			((AbstractContainerScreenAccessor) this).vestiges$setImageWidth(InventoryLayout.WIDTH);
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void vestiges$panel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (ClientInventory.on()) {
			AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
			InventoryPanel.background(g, s.vestiges$leftPos(), s.vestiges$topPos());
		}
	}

	@Inject(method = "extractLabels", at = @At("TAIL"))
	private void vestiges$labels(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if (ClientInventory.on()) {
			InventoryPanel.labels(g);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void vestiges$tooltips(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (ClientInventory.on()) {
			AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
			InventoryPanel.tooltips(g, s.vestiges$leftPos(), s.vestiges$topPos(), mouseX, mouseY);
		}
	}

	@ModifyReturnValue(method = "getRecipeBookButtonPosition", at = @At("RETURN"))
	private ScreenPosition vestiges$recipeButton(ScreenPosition vanilla) {
		if (!ClientInventory.on()) {
			return vanilla;
		}
		AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
		return new ScreenPosition(s.vestiges$leftPos() + InventoryLayout.PANEL_X + 4, s.vestiges$topPos() + 142);
	}
}
