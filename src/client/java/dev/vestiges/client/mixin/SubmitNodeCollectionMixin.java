package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vestiges.client.mobs.ClientMobs;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M3a: a mob's health bar is drawn as a smaller name tag than vanilla names. */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
	@WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void vestiges$smallBar(PoseStack pose, float x, float y, float z, Operation<Void> original, PoseStack poseArg, Vec3 attachment,
								   int offset, Component text, boolean seeThrough, int light, CameraRenderState camera) {
		float s = ClientMobs.isHealthBar(text) ? ClientMobs.BAR_SCALE : 1.0f;
		original.call(pose, x * s, y * s, z * s);
	}
}
