package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.tree.TreeCombat;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Damage and healing in one place: everything a living entity takes passes {@link TreeCombat#modifyDamage} (level bonus, tree damage, the
 * victim's reductions); shield blocks feed Riposte; heals on players get "healing received".
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float vestiges$damage(float amount, @Local(argsOnly = true) DamageSource source) {
		return TreeCombat.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	/** Shield blocks: a shield above your level blocks only half (gear ladder); a block feeds Riposte. */
	@ModifyReturnValue(method = "applyItemBlocking", at = @At("RETURN"))
	private float vestiges$shieldBlock(float blocked, ServerLevel level, DamageSource source, float amount) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (blocked > 0 && self instanceof ServerPlayer player && GearLadder.tooHigh(player, player.getItemBlockingWith())) {
			blocked *= 0.5f;
		}
		TreeCombat.onShieldBlock(self, blocked);
		return blocked;
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float vestiges$healing(float amount) {
		if ((Object) this instanceof ServerPlayer player) {
			return TreeHooks.healing(player, amount);
		}
		return amount;
	}
}
