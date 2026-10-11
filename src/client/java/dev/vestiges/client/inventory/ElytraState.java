package dev.vestiges.client.inventory;

import net.minecraft.world.item.ItemStack;

/** Added to humanoid render states: the elytra in the player's elytra slot, so wings render even with a chestplate on. */
public interface ElytraState {
	ItemStack vestiges$elytra();

	void vestiges$setElytra(ItemStack stack);
}
