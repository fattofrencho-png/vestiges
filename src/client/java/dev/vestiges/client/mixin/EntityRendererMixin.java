package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.mobs.ClientMobs;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M3a: the name tag becomes "name  Lv N  bar" while the health bar shows, and a spotted elite's glow takes its rank colour. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
	private Component vestiges$nameTag(Component original, Entity entity) {
		if (entity instanceof Mob mob && ClientMobs.showBar(mob)) {
			return ClientMobs.nameTag(mob);
		}
		return original;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int vestiges$glowColor(Entity entity, Operation<Integer> original) {
		return ClientMobs.outlineColor(entity, original.call(entity));
	}
}
