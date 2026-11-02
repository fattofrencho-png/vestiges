package dev.vestiges.inventory;

import com.google.gson.JsonObject;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * A shulker box opened from your own inventory. Its contents live in the item: every change is written straight back to that exact stack. The menu
 * stays valid only while that stack is still in its slot, and the slot is locked while open, so the box can't be moved or duplicated.
 */
public final class ShulkerSession extends SimpleContainer {
	private static final int SIZE = 27;
	private static final int MAX_NAME = 35;
	private final ServerPlayer player;
	private final int inventorySlot;
	private final ItemStack box;

	private ShulkerSession(ServerPlayer player, int inventorySlot, ItemStack box) {
		super(SIZE);
		this.player = player;
		this.inventorySlot = inventorySlot;
		this.box = box;
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		for (int i = 0; i < SIZE; i++) {
			super.setItem(i, items.get(i));
		}
	}

	private static ShulkerBoxBlock block(ItemStack stack) {
		return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock s ? s : null;
	}

	/** {@code menuSlot} is the slot in the player's inventory menu: the main inventory or the hotbar. */
	public static void open(ServerPlayer player, int menuSlot) {
		if (!ConfigStore.server().enabled(VestigesSystem.SHULKERS) || player.containerMenu != player.inventoryMenu
			|| menuSlot < InventoryMenu.INV_SLOT_START || menuSlot >= InventoryMenu.USE_ROW_SLOT_END || !player.containerMenu.getCarried().isEmpty()) {
			return;
		}
		Slot slot = player.inventoryMenu.getSlot(menuSlot);
		ItemStack stack = slot.getItem();
		if (block(stack) == null || stack.getCount() != 1) {
			return;
		}
		ShulkerSession session = new ShulkerSession(player, slot.getContainerSlot(), stack);
		session.tell(true);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> session.menu(id, inv), stack.getHoverName()));
	}

	private ShulkerBoxMenu menu(int id, Inventory inventory) {
		ShulkerBoxMenu menu = new ShulkerBoxMenu(id, inventory, this);
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot s = menu.slots.get(i);
			if (s.container == inventory && s.getContainerSlot() == inventorySlot) {
				Slot locked = new Slot(inventory, inventorySlot, s.x, s.y) {
					@Override
					public boolean mayPickup(Player player) {
						return false;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}
				};
				locked.index = i;
				menu.slots.set(i, locked);
			}
		}
		return menu;
	}

	public static void rename(ServerPlayer player, String name) {
		if (player.containerMenu instanceof ShulkerBoxMenu menu) {
			for (Slot s : menu.slots) {
				if (s.container instanceof ShulkerSession session) {
					String clean = name.strip();
					clean = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
					if (clean.isEmpty()) {
						session.box.remove(DataComponents.CUSTOM_NAME);
					} else {
						session.box.set(DataComponents.CUSTOM_NAME, Component.literal(clean));
					}
					return;
				}
			}
		}
	}

	private void tell(boolean open) {
		JsonObject o = new JsonObject();
		o.addProperty("open", open);
		DyeColor color = block(box) != null ? block(box).getColor() : null;
		o.addProperty("color", color == null ? 0 : color.getTextureDiffuseColor() & 0xFFFFFF);
		o.addProperty("name", box.has(DataComponents.CUSTOM_NAME) ? box.getHoverName().getString() : "");
		LevelService.notice(player, "shulker_session", o);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
	}

	@Override
	public boolean stillValid(Player p) {
		return p == player && p.getInventory().getItem(inventorySlot) == box;
	}

	@Override
	public void stopOpen(ContainerUser user) {
		super.stopOpen(user);
		tell(false);
	}
}
