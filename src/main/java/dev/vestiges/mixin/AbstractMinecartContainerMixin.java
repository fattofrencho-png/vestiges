package dev.vestiges.mixin;

import dev.vestiges.loot.PersonalLoot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Per-player chests (M3b): mineshaft chest minecarts give each player their own copy too. */
@Mixin(AbstractMinecartContainer.class)
public abstract class AbstractMinecartContainerMixin {
	@Inject(method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
		at = @At("HEAD"), cancellable = true)
	private void vestiges$personalLoot(int id, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
		AbstractContainerMenu menu = PersonalLoot.open(this, id, inventory, player);
		if (menu != null) {
			cir.setReturnValue(menu);
		}
	}
}
