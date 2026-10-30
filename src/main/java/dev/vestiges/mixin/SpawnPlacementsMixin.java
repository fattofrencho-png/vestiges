package dev.vestiges.mixin;

import dev.vestiges.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M5: no natural monster spawns (or patrols) inside active settlements and Ward Lanterns, at full height. Spawners still work. */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static void vestiges$pacified(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random,
										  CallbackInfoReturnable<Boolean> cir) {
		if (type.getCategory() == MobCategory.MONSTER && (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.PATROL)
			&& SettlementService.pacified(level.getLevel(), pos)) {
			cir.setReturnValue(false);
		}
	}
}
