package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.vestiges.settlement.SettlementService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Land protection: blocks in a settlement are taken out of an explosion's list before it destroys them. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
	private List<BlockPos> vestiges$protectSettlements(List<BlockPos> blocks) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		return SettlementService.explosionTargets(self.level(), self.getIndirectSourceEntity(), blocks);
	}
}
