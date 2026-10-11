package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Number keys: Ctrl + 1-8 jumps to a tool rack position, Alt + 1-6 to a weapon rack position; plain numbers stay vanilla. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void vestiges$rackKeys(Inventory inventory, int slot, Operation<Void> original) {
		if (!ClientInventory.onNumberKey(inventory, slot)) {
			original.call(inventory, slot);
		}
	}
}
