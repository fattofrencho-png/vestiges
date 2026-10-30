package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Number keys: Ctrl + 1-8 jumps to a tool rack position, Alt + 1-6 to a weapon rack position; plain numbers stay vanilla. Elites glow for a
 * while after you first spot them (M3a).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void vestiges$rackKeys(Inventory inventory, int slot, Operation<Void> original) {
		if (!ClientInventory.onNumberKey(inventory, slot)) {
			original.call(inventory, slot);
		}
	}

	@Shadow
	private int rightClickDelay;

	@org.spongepowered.asm.mixin.injection.Inject(method = "startUseItem", at = @At("RETURN"))
	private void vestiges$placingSpeed(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (rightClickDelay == 4 && mc.player != null && (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem
			|| mc.player.getOffhandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) {
			rightClickDelay = dev.vestiges.tree.BuildingNodes.placeDelay(mc.player, rightClickDelay);
		}
	}

	@ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
	private boolean vestiges$eliteGlow(boolean original, Entity entity) {
		return original || dev.vestiges.client.mobs.ClientMobs.glowing(entity);
	}
}
