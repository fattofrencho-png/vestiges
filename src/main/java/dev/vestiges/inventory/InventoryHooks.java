package dev.vestiges.inventory;

import net.minecraft.world.entity.player.Player;

/** Small bridges between common code and the client. */
public final class InventoryHooks {
	/** Client: tell the server the player went back to the vanilla hotbar (set by the client). */
	public static volatile Runnable onClientBackToHotbar = () -> {
	};

	private InventoryHooks() {
	}

	public static ExtraSlots slots(Player player) {
		return ((ExtraHolder) player).vestiges$slots();
	}

	public static Selection selection(Player player) {
		return (Selection) player.getInventory();
	}
}
