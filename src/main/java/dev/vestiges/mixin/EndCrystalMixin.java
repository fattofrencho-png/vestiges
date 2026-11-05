package dev.vestiges.mixin;

import dev.vestiges.dragon.DragonService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player who hurts an end crystal in a dragon fight counts as a fighter (rewarded when the dragon dies). */
@Mixin(EndCrystal.class)
public abstract class EndCrystalMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void vestiges$credit(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		DragonService.credit(level, source.getEntity());
	}
}
