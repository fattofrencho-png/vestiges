package dev.vestiges.mixin;

import dev.vestiges.inventory.StackRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the grindstone's inputs for {@link SlotMixin}. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Shadow
	@Final
	private Container repairSlots;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void vestiges$mark(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		StackRules.markSingleInput(repairSlots);
	}
}
