package dev.vestiges.mixin;

import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.RawEquipment;
import dev.vestiges.inventory.Selection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The offhand rack: when the second offhand slot is the active one (Alt + Scroll), the offhand holds that item (using it, rendering it)
 * while the vanilla offhand slot keeps its own item. The inventory reads the real slot (raw), so nothing is ever shown twice.
 */
@Mixin(PlayerEquipment.class)
public abstract class PlayerEquipmentMixin implements RawEquipment {
	@Shadow
	@Final
	private Player player;

	@Unique
	private boolean vestiges$raw;

	@Override
	public void vestiges$setRaw(boolean raw) {
		vestiges$raw = raw;
	}

	@Unique
	private boolean vestiges$lightHand(EquipmentSlot slot) {
		if (vestiges$raw || slot != EquipmentSlot.OFFHAND || player == null) {
			return false;
		}
		Inventory inv = player.getInventory();
		return inv != null && ((Selection) inv).vestiges$offhandB();
	}

	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void vestiges$getLight(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
		if (vestiges$lightHand(slot)) {
			cir.setReturnValue(InventoryHooks.slots(player).getItem(ExtraSlots.OFFHAND2));
		}
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void vestiges$setLight(EquipmentSlot slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!vestiges$lightHand(slot)) {
			return;
		}
		ExtraSlots slots = InventoryHooks.slots(player);
		ItemStack old = slots.getItem(ExtraSlots.OFFHAND2);
		if (ExtraSlots.accepts(ExtraSlots.OFFHAND2, stack)) {
			slots.setItem(ExtraSlots.OFFHAND2, stack);
		} else if (!player.getInventory().add(stack) && !player.level().isClientSide()) {
			ItemEntity drop = player.createItemStackToDrop(stack, false, true);
			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}
		cir.setReturnValue(old);
	}
}
