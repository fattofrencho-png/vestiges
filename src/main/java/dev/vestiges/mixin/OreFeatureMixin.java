package dev.vestiges.mixin;

import dev.vestiges.world.EmeraldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OreFeature.class)
public abstract class OreFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void vestiges$noStrayEmeralds(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (EmeraldSites.blocks((OreFeature) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
