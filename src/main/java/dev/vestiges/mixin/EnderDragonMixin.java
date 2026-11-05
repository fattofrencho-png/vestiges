package dev.vestiges.mixin;

import dev.vestiges.dragon.DragonService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dragon removed with /kill gives no kill and no rewards: the mark remembers it before the death plays out. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
	@Inject(method = "kill", at = @At("HEAD"))
	private void vestiges$byCommand(ServerLevel level, CallbackInfo ci) {
		DragonService.killedByCommand((EnderDragon) (Object) this);
	}
}
