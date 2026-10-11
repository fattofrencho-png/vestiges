package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the belt, Tool or Weapon slot is selected, the vanilla hotbar draws no selection frame (ours is drawn on the extra slot). */
@Mixin(Hud.class)
public abstract class HudMixin {
	@WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void vestiges$hideSelection(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && InventoryHooks.selection(mc.player).vestiges$extra() != Selection.NONE) {
			return;
		}
		original.call(g, pipeline, sprite, x, y, w, h);
	}
}
