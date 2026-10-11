package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Spyglass on the belt: holding the zoom key zooms like looking through it. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	private float vestiges$beltZoom(float vanilla) {
		if ((Object) this == Minecraft.getInstance().player && ClientInventory.zooming()) {
			return 0.1f;
		}
		return vanilla;
	}
}
