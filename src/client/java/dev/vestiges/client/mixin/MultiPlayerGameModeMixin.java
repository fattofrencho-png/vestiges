package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ClientInventory;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Auto-tool: picks the right rack tool just before the server hears that you started breaking. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void vestiges$autoTool(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		ClientInventory.beforeBreaking(pos);
	}
}
