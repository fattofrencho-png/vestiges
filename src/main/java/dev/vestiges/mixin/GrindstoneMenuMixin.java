package dev.vestiges.mixin;

import dev.vestiges.gear.BrokenGear;
import dev.vestiges.inventory.StackRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the grindstone's inputs for {@link SlotMixin}. A broken item can't be repaired by merging two copies here (its material at the anvil is
 * the only repair).
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Shadow
	@Final
	private Container repairSlots;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void vestiges$mark(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		StackRules.markSingleInput(repairSlots);
	}

	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenNoMerge(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
		if (!first.isEmpty() && !second.isEmpty() && (BrokenGear.isBroken(first) || BrokenGear.isBroken(second))) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
