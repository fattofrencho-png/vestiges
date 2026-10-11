package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.RawEquipment;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.Selection;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Tool, Weapon and Belt hotbar slots: while one is selected, the main hand is the picked rack item (using, dropping, wearing it down
 * all happen on the rack). Choosing a vanilla hotbar slot leaves them. On death, the extra slots drop with the rest when there is no grave.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin implements Selection {
	@Shadow
	@Final
	public Player player;

	@Unique
	private int vestiges$extra;
	@Unique
	private boolean vestiges$offhandB;
	/** Created on first use (mixin field initialisers can't be relied on). */
	@Unique
	private int[] vestiges$racks;

	@Unique
	private int[] vestiges$racks() {
		if (vestiges$racks == null) {
			vestiges$racks = new int[4];
		}
		return vestiges$racks;
	}

	@Override
	public int vestiges$extra() {
		return vestiges$extra;
	}

	@Override
	public void vestiges$setExtra(int kind) {
		vestiges$extra = kind >= Selection.NONE && kind <= Selection.BELT ? kind : Selection.NONE;
	}

	@Override
	public boolean vestiges$offhandB() {
		return vestiges$offhandB;
	}

	@Override
	public void vestiges$setOffhandB(boolean active) {
		vestiges$offhandB = active;
	}

	@Override
	public int vestiges$rackIndex(int kind) {
		return kind >= Selection.TOOL && kind <= Selection.BELT ? vestiges$racks()[kind] : 0;
	}

	@Override
	public void vestiges$setRackIndex(int kind, int index) {
		if (kind >= Selection.TOOL && kind <= Selection.BELT) {
			vestiges$racks()[kind] = Math.floorMod(index, ExtraSlots.rackSize(kind));
		}
	}

	@Inject(method = "getSelectedItem", at = @At("HEAD"), cancellable = true)
	private void vestiges$held(CallbackInfoReturnable<ItemStack> cir) {
		int slot = vestiges$heldExtraSlot();
		if (slot >= 0) {
			cir.setReturnValue(InventoryHooks.slots(player).getItem(slot));
		}
	}

	@Inject(method = "setSelectedItem", at = @At("HEAD"), cancellable = true)
	private void vestiges$setHeld(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		int slot = vestiges$heldExtraSlot();
		if (slot < 0) {
			return;
		}
		ExtraSlots slots = InventoryHooks.slots(player);
		ItemStack old = slots.getItem(slot);
		if (ExtraSlots.accepts(slot, stack)) {
			slots.setItem(slot, stack);
		} else if (!player.getInventory().add(stack) && !player.level().isClientSide()) {
			// Something turned the held item into a different kind: it goes to the inventory (or the ground), the rack keeps its slot.
			ItemEntity drop = player.createItemStackToDrop(stack, false, true);
			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}
		cir.setReturnValue(old);
	}

	@Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
	private void vestiges$dropHeld(boolean all, CallbackInfoReturnable<ItemStack> cir) {
		int slot = vestiges$heldExtraSlot();
		if (slot < 0) {
			return;
		}
		ExtraSlots slots = InventoryHooks.slots(player);
		ItemStack s = slots.getItem(slot);
		cir.setReturnValue(s.isEmpty() ? ItemStack.EMPTY : all ? slots.removeItemNoUpdate(slot) : slots.removeItem(slot, 1));
	}

	@Inject(method = "setSelectedSlot", at = @At("HEAD"))
	private void vestiges$backToHotbar(int index, CallbackInfo ci) {
		if (vestiges$extra != Selection.NONE) {
			vestiges$extra = Selection.NONE;
			if (player.level().isClientSide()) {
				InventoryHooks.onClientBackToHotbar.run();
			}
		}
	}

	@WrapOperation(method = {"getItem", "removeItem"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/EntityEquipment;get(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$rawGet(EntityEquipment equipment, EquipmentSlot slot, Operation<ItemStack> original) {
		if (!(equipment instanceof RawEquipment raw)) {
			return original.call(equipment, slot);
		}
		raw.vestiges$setRaw(true);
		try {
			return original.call(equipment, slot);
		} finally {
			raw.vestiges$setRaw(false);
		}
	}

	@WrapOperation(method = {"setItem", "removeItemNoUpdate"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/EntityEquipment;set(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$rawSet(EntityEquipment equipment, EquipmentSlot slot, ItemStack stack, Operation<ItemStack> original) {
		if (!(equipment instanceof RawEquipment raw)) {
			return original.call(equipment, slot, stack);
		}
		raw.vestiges$setRaw(true);
		try {
			return original.call(equipment, slot, stack);
		} finally {
			raw.vestiges$setRaw(false);
		}
	}

	/** Death without a grave: the new slots drop with everything else. */
	@Inject(method = "dropAll", at = @At("TAIL"))
	private void vestiges$dropExtra(CallbackInfo ci) {
		for (ItemStack s : InventoryHooks.slots(player).drain()) {
			ItemEntity drop = player.createItemStackToDrop(s, true, false);
			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}
	}
}
