package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.client.camera.IsoCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dungeon camera test: inside the camera zone the view floats above and behind the character, looking down (eased in and out). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract float xRot();

	@Shadow
	public abstract float yRot();

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void vestiges$isoView(float partialTicks, CallbackInfo ci) {
		float blend = IsoCamera.tickBlend();
		if (blend <= 0.0f || entity == null) {
			return;
		}
		Vec3 pivot = new Vec3(Mth.lerp((double) partialTicks, entity.xo, entity.getX()),
			Mth.lerp((double) partialTicks, entity.yo, entity.getY()) + 1.0,
			Mth.lerp((double) partialTicks, entity.zo, entity.getZ()));
		Vec3 target = IsoCamera.position(pivot);
		setRotation(Mth.rotLerp(blend, yRot(), IsoCamera.YAW), Mth.lerp(blend, xRot(), IsoCamera.PITCH));
		setPosition(position.lerp(target, blend));
		detached = true;
	}

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float vestiges$isoFov(float original) {
		return Mth.lerp(IsoCamera.eased(), original, IsoCamera.FOV);
	}
}
