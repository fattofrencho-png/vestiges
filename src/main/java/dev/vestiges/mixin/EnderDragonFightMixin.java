package dev.vestiges.mixin;

import dev.vestiges.dragon.DragonMoves;
import dev.vestiges.dragon.DragonService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The End fight: a summoning whose summoner is below the gate level is stopped (a safety net behind the crystal item's own check), and the
 * moment vanilla marks the dragon as killed is where Vestiges counts the kill and rewards the fighters.
 */
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {
	@Inject(method = "tryRespawn", at = @At("HEAD"), cancellable = true)
	private void vestiges$gate(CallbackInfo ci) {
		if (!DragonService.allowRespawn((EnderDragonFight) (Object) this)) {
			ci.cancel();
		}
	}

	/** A pillar crystal is destroyed (not one of the four put down to summon): Dhars lets the last three grow back. */
	@Inject(method = "onCrystalDestroyed", at = @At("HEAD"))
	private void vestiges$crystal(EndCrystal crystal, DamageSource source, CallbackInfo ci) {
		if (((EnderDragonFightAccessor) this).vestiges$stage() == null) {
			DragonMoves.crystalDestroyed((EnderDragonFight) (Object) this, crystal);
		}
	}

	@Inject(method = "setDragonKilled", at = @At("HEAD"))
	private void vestiges$killed(EnderDragon dragon, CallbackInfo ci) {
		DragonService.onKilled((EnderDragonFight) (Object) this, dragon);
	}
}
