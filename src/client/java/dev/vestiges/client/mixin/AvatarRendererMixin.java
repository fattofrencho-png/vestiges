package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.client.inventory.ElytraState;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players' render state learns about their elytra slot (yours from your own slots, others' from the server). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void vestiges$elytraSlot(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		ItemStack elytra = ItemStack.EMPTY;
		if (entity instanceof Player player && ClientInventory.on()) {
			elytra = player == Minecraft.getInstance().player ? InventoryHooks.slots(player).getItem(ExtraSlots.ELYTRA) : ClientInventory.elytraOf(player.getId());
		}
		((ElytraState) state).vestiges$setElytra(elytra);
	}
}
