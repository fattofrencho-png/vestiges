package dev.vestiges.mixin;

import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking a craft result in the player's own inventory with a crafting table in the table slot: the grid_craft quest step. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void vestiges$questGridCraft(Player player, ItemStack stack, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp && sp.containerMenu instanceof InventoryMenu && ((ExtraHolder) sp).vestiges$slots().hasTable()) {
			QuestService.event(sp, "grid_craft", "", 1);
		}
	}
}
