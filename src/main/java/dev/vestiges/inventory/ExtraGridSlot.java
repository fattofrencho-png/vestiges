package dev.vestiges.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One of the 5 crafting grid slots that only exist while a crafting table sits in its slot. Hidden and closed without one. */
public final class ExtraGridSlot extends Slot {
	private final ExtraSlots extra;

	public ExtraGridSlot(Container grid, int index, int x, int y, ExtraSlots extra) {
		super(grid, index, x, y);
		this.extra = extra;
	}

	@Override
	public boolean isActive() {
		return extra.hasTable();
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return extra.hasTable();
	}
}
