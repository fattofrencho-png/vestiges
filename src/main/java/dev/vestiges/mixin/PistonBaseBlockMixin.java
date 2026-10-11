package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.vestiges.codex.PlacedBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** M4: a player-placed block pushed by a piston stays player-placed at its new spot. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
	@ModifyExpressionValue(method = "moveBlocks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;"))
	private List<BlockPos> vestiges$movePlaced(List<BlockPos> toPush, Level level, BlockPos pistonPos, Direction direction, boolean extending) {
		if (level instanceof ServerLevel server) {
			Direction move = extending ? direction : direction.getOpposite();
			List<BlockPos> moved = new ArrayList<>();
			for (BlockPos p : toPush) {
				if (PlacedBlocks.take(server, p)) {
					moved.add(p.relative(move));
				}
			}
			moved.forEach(p -> PlacedBlocks.mark(server, p));
		}
		return toPush;
	}
}
