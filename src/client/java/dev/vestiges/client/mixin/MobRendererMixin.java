package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.client.mobs.ClientMobs;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Health bars (M3a): a mob shows its name tag (with the bar) for 5 s after you hit it, elites while you can see them. */
@Mixin(MobRenderer.class)
public abstract class MobRendererMixin {
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/Mob;D)Z", at = @At("RETURN"))
	private boolean vestiges$healthBar(boolean original, Mob mob, double distanceSq) {
		return original || ClientMobs.showBar(mob);
	}
}
