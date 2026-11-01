package dev.vestiges.mixin;

import dev.vestiges.inventory.CraftGrid;
import dev.vestiges.inventory.ExtraGridSlot;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlot;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryLayout;
import java.util.List;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player's inventory menu gets the extra slots (after vanilla's 46, so vanilla's slot numbers never move). Shift-click sends tools,
 * weapons, arrows, belt items, elytra, totems and ender chests to their rack slot, and rack items back to the inventory.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractCraftingMenu implements CraftGrid {
	/** Container positions of vanilla's slots 1-4 inside the 3x3 grid, and of the 5 slots a crafting table adds. */
	@Unique
	private static final int[] VANILLA_GRID = {0, 1, 3, 4};
	@Unique
	private static final int[] TABLE_GRID = {2, 5, 6, 7, 8};

	/** Index of the first extra slot; 0 until the constructor hook sets it (vanilla's slots come first, so it is never 0 once set). */
	@Unique
	private int vestiges$first;

	protected InventoryMenuMixin(MenuType<?> type, int containerId, int width, int height) {
		super(type, containerId, width, height);
	}

	/** The grid's container is always 3x3, so the 2x2 uses only its top-left corner. */
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractCraftingMenu;<init>(Lnet/minecraft/world/inventory/MenuType;III)V"), index = 2)
	private static int vestiges$gridWidth(int width) {
		return 3;
	}

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractCraftingMenu;<init>(Lnet/minecraft/world/inventory/MenuType;III)V"), index = 3)
	private static int vestiges$gridHeight(int height) {
		return 3;
	}

	/** Vanilla's four grid slots keep their numbers (1-4) and their 2x2 places. */
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/InventoryMenu;addCraftingGridSlots(II)V"))
	private void vestiges$vanillaGrid(InventoryMenu menu, int x, int y) {
		for (int i = 0; i < VANILLA_GRID.length; i++) {
			addSlot(new Slot(craftSlots, VANILLA_GRID[i], x + (i % 2) * 18, y + (i / 2) * 18));
		}
	}

	@Unique
	private boolean vestiges$table() {
		return ((ExtraHolder) owner()).vestiges$slots().hasTable();
	}

	@Override
	public int getGridWidth() {
		return vestiges$table() ? 3 : 2;
	}

	@Override
	public int getGridHeight() {
		return vestiges$table() ? 3 : 2;
	}

	/** The recipe book fills these in row order, so with a table they are all 9 in grid order. */
	@Inject(method = "getInputGridSlots", at = @At("HEAD"), cancellable = true)
	private void vestiges$inputs(CallbackInfoReturnable<List<Slot>> cir) {
		if (vestiges$first > 0 && vestiges$table()) {
			Slot[] grid = new Slot[9];
			for (int i = 0; i < VANILLA_GRID.length; i++) {
				grid[VANILLA_GRID[i]] = slots.get(InventoryMenu.CRAFT_SLOT_START + i);
			}
			for (int i = 0; i < TABLE_GRID.length; i++) {
				grid[TABLE_GRID[i]] = slots.get(vestiges$first + ExtraSlots.SIZE + i);
			}
			cir.setReturnValue(List.of(grid));
		}
	}

	@Override
	public void vestiges$layoutGrid() {
		boolean table = vestiges$table();
		((SlotAccessor) slots.get(InventoryMenu.RESULT_SLOT)).vestiges$setX(table ? InventoryLayout.RESULT_X : 154);
		((SlotAccessor) slots.get(InventoryMenu.RESULT_SLOT)).vestiges$setY(table ? InventoryLayout.RESULT_Y : 28);
		for (int i = 0; i < VANILLA_GRID.length; i++) {
			SlotAccessor s = (SlotAccessor) slots.get(InventoryMenu.CRAFT_SLOT_START + i);
			s.vestiges$setX(table ? InventoryLayout.GRID_X + (i % 2) * InventoryLayout.GRID_STEP : 98 + (i % 2) * 18);
			s.vestiges$setY(table ? InventoryLayout.GRID_Y + (i / 2) * InventoryLayout.GRID_STEP : 18 + (i / 2) * 18);
		}
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void vestiges$addSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		ExtraSlots extra = ((ExtraHolder) owner).vestiges$slots();
		vestiges$first = slots.size();
		for (int i = 0; i < ExtraSlots.SIZE; i++) {
			int[] p = InventoryLayout.pos(i);
			addSlot(new ExtraSlot(extra, i, p[0], p[1]));
		}
		for (int index : TABLE_GRID) {
			addSlot(new ExtraGridSlot(craftSlots, index, InventoryLayout.GRID_X + (index % 3) * InventoryLayout.GRID_STEP, InventoryLayout.GRID_Y + (index / 3) * InventoryLayout.GRID_STEP, extra));
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
