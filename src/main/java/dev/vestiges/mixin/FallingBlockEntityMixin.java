package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.codex.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** M4: placed sand or gravel that falls stays player-placed where it lands. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
	@Inject(method = "fall", at = @At("RETURN"))
	private static void vestiges$leavePlaced(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
		if (level instanceof ServerLevel server && cir.getReturnValue() != null && PlacedBlocks.take(server, pos)) {
			cir.getReturnValue().setAttached(PlacedBlocks.FALLING, true);
		}
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean vestiges$landPlaced(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state);
		FallingBlockEntity self = (FallingBlockEntity) (Object) this;
		if (placed && level instanceof ServerLevel server && Boolean.TRUE.equals(self.getAttached(PlacedBlocks.FALLING))) {
			PlacedBlocks.mark(server, pos);
		}
		return placed;
	}
}
