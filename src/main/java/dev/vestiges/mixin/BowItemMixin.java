package dev.vestiges.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Bow draw speed: the held time counts faster, so a full-power shot comes sooner. */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@ModifyArg(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
	private int vestiges$draw(int ticks, @Local(argsOnly = true) LivingEntity entity) {
		return TreeHooks.drawTicks(entity, ticks);
	}
}
