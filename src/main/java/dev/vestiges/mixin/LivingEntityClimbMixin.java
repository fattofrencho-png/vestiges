package dev.vestiges.mixin;

import dev.vestiges.tree.BuildingNodes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** M5: the tree's climb speed node (ladders, vines, scaffolding). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
	@ModifyConstant(method = "handleRelativeFrictionAndCalculateMovement", constant = @Constant(doubleValue = 0.2))
	private double vestiges$climb(double speed) {
		return BuildingNodes.climb((LivingEntity) (Object) this, speed);
	}
}
