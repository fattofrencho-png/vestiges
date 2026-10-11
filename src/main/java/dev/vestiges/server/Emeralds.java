package dev.vestiges.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Paying with emeralds from the inventory (the Bank slot takes over waystones, repairs and refunds in M2). Creative players pay nothing. */
public final class Emeralds {
	private Emeralds() {
	}

	public static boolean free(ServerPlayer player) {
		return player.hasInfiniteMaterials();
	}

	public static int count(ServerPlayer player) {
		return count((Player) player);
	}

	/** Works on both sides (the client checks before asking the server). */
	public static int count(Player player) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Takes the emeralds if the player has enough (or plays for free). Returns false and takes nothing otherwise. */
	public static boolean pay(ServerPlayer player, int amount) {
		if (free(player) || amount <= 0) {
			return true;
		}
		if (count(player) < amount) {
			return false;
		}
		take(player, amount);
		return true;
	}

	/** Emeralds in the Bank (M2). */
	public static long bank(Player player) {
		return ((dev.vestiges.inventory.ExtraHolder) player).vestiges$bank();
	}

	/** Inventory plus Bank: what waystones, the anvil and tree refunds can use. */
	public static long countWithBank(Player player) {
		return count(player) + bank(player);
	}

	/** Pays from the Bank first, then the inventory (waystones, anvil, tree refunds). Creative pays nothing. */
	public static boolean payWithBank(ServerPlayer player, int amount) {
		if (free(player) || amount <= 0) {
			return true;
		}
		if (countWithBank(player) < amount) {
			return false;
		}
		long fromBank = Math.min(bank(player), amount);
		if (fromBank > 0) {
			((dev.vestiges.inventory.ExtraHolder) player).vestiges$setBank(bank(player) - fromBank);
			dev.vestiges.inventory.InventoryService.sendBank(player);
		}
		take(player, (int) (amount - fromBank));
		return true;
	}

	public static void take(ServerPlayer player, int amount) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				int take = Math.min(amount, s.getCount());
				inv.removeItem(i, take);
				amount -= take;
			}
		}
	}
}
