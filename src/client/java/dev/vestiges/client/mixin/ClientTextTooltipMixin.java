package dev.vestiges.client.mixin;

import dev.vestiges.client.hud.GearTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relic tooltip lines: deep brown letters with a thin white outline (tooltips have no outline of their own). */
@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin {
	@Shadow
	@Final
	private FormattedCharSequence text;

	@Inject(method = "extractText", at = @At("HEAD"), cancellable = true)
	private void vestiges$relicOutline(GuiGraphicsExtractor g, Font font, int x, int y, CallbackInfo ci) {
		if (!GearTooltips.isRelic(text)) {
			return;
		}
		FormattedCharSequence white = sink -> text.accept((index, style, codePoint) -> sink.accept(index, style.withColor(0xFFFFFF), codePoint));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					g.text(font, white, x + dx, y + dy, -1, false);
				}
			}
		}
		g.text(font, text, x, y, -1, false);
		ci.cancel();
	}
}
