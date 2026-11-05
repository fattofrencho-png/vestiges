package dev.vestiges.mixin;

import dev.vestiges.world.EmeraldSites;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
	private void vestiges$emeraldSites(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
		EmeraldSites.decorate(level, (ChunkGenerator) (Object) this, chunk);
	}
}
