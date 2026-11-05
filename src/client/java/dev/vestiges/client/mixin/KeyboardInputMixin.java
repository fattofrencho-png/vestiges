package dev.vestiges.client.mixin;

import dev.vestiges.client.camera.IsoCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dungeon camera test: in the camera zone the keys walk along the screen (W = up the screen) and the character turns to face its way. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void vestiges$screenKeys(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !IsoCamera.steering()) {
			return;
		}
		ClientInputAccessor input = (ClientInputAccessor) this;
		Vec2 move = input.vestiges$getMoveVector();
		player.setXRot(0.0f);
		float[] steered = IsoCamera.steer(player, move.x, move.y);
		if (steered != null) {
			input.vestiges$setMoveVector(new Vec2(steered[0], steered[1]));
		}
	}
}
