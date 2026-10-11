package dev.vestiges.mixin;

import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlot;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryLayout;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player's inventory menu gets the extra slots (after vanilla's 46, so vanilla's slot numbers never move). Shift-click sends tools,
 * weapons, arrows, belt items, elytra, totems and ender chests to their rack slot, and rack items back to the inventory.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractCraftingMenu {
	/** Index of the first extra slot; 0 until the constructor hook sets it (vanilla's slots come first, so it is never 0 once set). */
	@Unique
	private int vestiges$first;

	protected InventoryMenuMixin(MenuType<?> type, int containerId, int width, int height) {
		super(type, containerId, width, height);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void vestiges$addSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		ExtraSlots extra = ((ExtraHolder) owner).vestiges$slots();
		vestiges$first = slots.size();
		for (int i = 0; i < ExtraSlots.SIZE; i++) {
			int[] p = InventoryLayout.pos(i);
			addSlot(new ExtraSlot(extra, i, p[0], p[1]));
		}
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void vestiges$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (vestiges$first <= 0 || index < 0 || index >= slots.size()) {
			return;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return;
		}
		ItemStack stack = slot.getItem();
		ItemStack before = stack.copy();
		if (index >= vestiges$first) {
			// From a rack: into the inventory, then the hotbar.
			if (!moveItemStackTo(stack, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, false)) {
				cir.setReturnValue(ItemStack.EMPTY);
				return;
			}
		} else if (index >= InventoryMenu.INV_SLOT_START && index < InventoryMenu.USE_ROW_SLOT_END) {
			int home = ExtraSlots.homeFor(stack);
			if (home < 0) {
				return;
			}
			int target = vestiges$first + home;
			Slot to = slots.get(target);
			boolean room = !to.hasItem() || (ItemStack.isSameItemSameComponents(to.getItem(), stack) && to.getItem().getCount() < to.getMaxStackSize(stack));
			if (!room || !moveItemStackTo(stack, target, target + 1, false)) {
				return;
			}
		} else {
			return;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		// Nothing more to move from this slot: returning empty stops vanilla's repeat loop.
		cir.setReturnValue(stack.isEmpty() ? before : ItemStack.EMPTY);
	}
}
