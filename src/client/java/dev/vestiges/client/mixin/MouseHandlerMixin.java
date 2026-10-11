package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The mouse wheel also reaches the belt, Tool and Weapon slots (and Ctrl / Alt + wheel move inside the racks). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void vestiges$scroll(Inventory inventory, int next, Operation<Void> original) {
		if (!ClientInventory.onScroll(inventory, next)) {
			original.call(inventory, next);
		}
	}
}
