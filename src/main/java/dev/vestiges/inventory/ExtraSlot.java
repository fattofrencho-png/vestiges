package dev.vestiges.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One of the new inventory slots: takes only its kind of item, with a vanilla background icon when empty. */
public final class ExtraSlot extends Slot {
	public ExtraSlot(ExtraSlots container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return ExtraSlots.accepts(getContainerSlot(), stack);
	}

	@Override
	public int getMaxStackSize() {
		return ExtraSlots.maxStack(getContainerSlot());
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return Math.min(stack.getMaxStackSize(), getMaxStackSize());
	}

	@Override
	public Identifier getNoItemIcon() {
		return ExtraSlots.icon(getContainerSlot());
	}
}
