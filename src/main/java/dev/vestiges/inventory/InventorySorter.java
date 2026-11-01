package dev.vestiges.inventory;

import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.mixin.CompoundContainerAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Sort and Move all. The server does all the moving: stacks are only merged and reordered, so nothing is created or lost. The hotbar is never
 * sorted, and an open container is sorted only if a player placed it.
 */
public final class InventorySorter {
	private static final Comparator<ItemStack> ORDER = Comparator.<ItemStack>comparingInt(InventorySorter::group)
		.thenComparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()))
		.thenComparing(s -> s.getHoverName().getString())
		.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

	private InventorySorter() {
	}

	/** Like the creative tabs: blocks, tools, combat, food, then everything else. */
	private static int group(ItemStack s) {
		if (s.getItem() instanceof BlockItem) {
			return 0;
		}
		if (s.has(DataComponents.TOOL)) {
			return 1;
		}
		if (s.has(DataComponents.MAX_DAMAGE) || s.has(DataComponents.EQUIPPABLE)) {
			return 2;
		}
		return s.has(DataComponents.FOOD) ? 3 : 4;
	}

	private static boolean supported(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	/** Your ender chest, or a container block a player placed. Natural loot chests, their personal copies and minecarts don't count. */
	private static boolean placed(ServerPlayer player, Container c) {
		if (c instanceof PlayerEnderChestContainer) {
			return true;
		}
		if (c instanceof CompoundContainerAccessor both) {
			return placed(player, both.vestiges$first()) && placed(player, both.vestiges$second());
		}
		return c instanceof BlockEntity be && PlacedBlocks.isPlaced(player.level(), be.getBlockPos());
	}

	/** The main inventory while no container is open, or the open chest or shulker box. */
	public static void sort(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		List<Slot> slots = new ArrayList<>();
		if (menu == player.inventoryMenu) {
			slots.addAll(menu.slots.subList(InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END));
		} else if (supported(menu)) {
			for (Slot s : menu.slots) {
				if (!(s.container instanceof Inventory)) {
					if (!placed(player, s.container)) {
						player.sendOverlayMessage(Component.literal("You can only sort containers placed by a player."));
						return;
					}
					slots.add(s);
				}
			}
		} else {
			return;
		}
		List<ItemStack> merged = new ArrayList<>();
		for (Slot s : slots) {
			ItemStack rest = s.getItem().copy();
			for (ItemStack m : merged) {
				if (rest.isEmpty()) {
					break;
				}
				if (ItemStack.isSameItemSameComponents(m, rest)) {
					int move = Math.min(rest.getCount(), m.getMaxStackSize() - m.getCount());
					if (move > 0) {
						m.grow(move);
						rest.shrink(move);
					}
				}
			}
			if (!rest.isEmpty()) {
				merged.add(rest);
			}
		}
		merged.sort(ORDER);
		for (int i = 0; i < slots.size(); i++) {
			slots.get(i).set(i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
		}
		menu.broadcastChanges();
	}

	/** Moves everything from the main inventory into the open container, or takes everything out of it, using vanilla's shift-click. */
	public static void moveAll(ServerPlayer player, boolean toContainer) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!supported(menu)) {
			return;
		}
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot s = menu.slots.get(i);
			boolean own = s.container instanceof Inventory;
			boolean main = own && s.getContainerSlot() >= 9 && s.getContainerSlot() < 36;
			if (s.hasItem() && (toContainer ? main : !own)) {
				menu.quickMoveStack(player, i);
			}
		}
		menu.broadcastChanges();
	}
}
