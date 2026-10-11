package dev.vestiges.mixin;

import dev.vestiges.farming.FarmingService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M4: breeding counts for the Bestiary and uses the tree's breeding nodes. The breeder is read first: vanilla clears it while breeding. */
@Mixin(Animal.class)
public abstract class AnimalMixin {
	@Unique
	private ServerPlayer vestiges$breeder;

	@Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
	private void vestiges$findBreeder(ServerLevel level, Animal partner, AgeableMob baby, CallbackInfo ci) {
		ServerPlayer p = ((Animal) (Object) this).getLoveCause();
		vestiges$breeder = p != null ? p : partner.getLoveCause();
	}

	@Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
	private void vestiges$bred(ServerLevel level, Animal partner, AgeableMob baby, CallbackInfo ci) {
		ServerPlayer p = vestiges$breeder;
		vestiges$breeder = null;
		if (p != null) {
			FarmingService.bred(p, (Animal) (Object) this, partner, baby);
		}
	}
}
