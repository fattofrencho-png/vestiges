package dev.vestiges.mixin;

import dev.vestiges.gear.GearService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mending, reworked: XP orbs no longer repair gear (Mending is now a one-time safety net, see {@code BrokenGear}). */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
	@Inject(method = "repairPlayerItems", at = @At("HEAD"), cancellable = true)
	private void vestiges$noXpRepair(ServerPlayer player, int amount, CallbackInfoReturnable<Integer> cir) {
		if (GearService.enabled()) {
			cir.setReturnValue(amount);
		}
	}
}
