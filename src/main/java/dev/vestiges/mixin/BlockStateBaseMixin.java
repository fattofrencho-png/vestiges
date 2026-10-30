package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.tree.BuildingNodes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M5: faster breaking of blocks you placed, and Rebuilder's instant break in your claimed areas (both sides). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	@ModifyReturnValue(method = "getDestroyProgress", at = @At("RETURN"))
	private float vestiges$ownBlocks(float progress, Player player, BlockGetter level, BlockPos pos) {
		return BuildingNodes.destroyProgress(player, level, pos, progress);
	}
}
