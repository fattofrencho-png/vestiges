package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.farming.FarmingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M4: the tree's crop growth node (a growth step sometimes jumps two stages) and bone meal node (more stages per use). */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
	@WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean vestiges$fasterGrowth(ServerLevel level, BlockPos pos, BlockState grown, int flags, Operation<Boolean> original) {
		CropBlock crop = (CropBlock) (Object) this;
		double pct = FarmingService.cropGrowth(level, pos);
		if (pct > 0 && grown.is(crop) && level.getRandom().nextDouble() < pct / 100.0) {
			grown = crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(grown) + 1));
		}
		return original.call(level, pos, grown, flags);
	}

	@ModifyReturnValue(method = "getBonemealAgeIncrease", at = @At("RETURN"))
	private int vestiges$strongerBonemeal(int stages, Level level) {
		return FarmingService.bonemeal(FarmingService.BONEMEAL_USER.get(), stages, level.getRandom());
	}
}
