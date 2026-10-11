package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.grave.GraveService;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gravestones (just before vanilla scatters the inventory on death, after totems and Curse of Vanishing, the items go into a grave), tree
 * mining speeds (both sides, so the client breaks at the same pace as the server) and hunger drain.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "dropEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V"))
	private void vestiges$grave(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			GraveService.createGrave(player);
		}
	}

	@Inject(method = "crit", at = @At("HEAD"))
	private void vestiges$markCrit(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			dev.vestiges.mobs.CombatFeedback.markCrit(player, target);
		}
	}

	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float vestiges$breakSpeed(float speed, BlockState state) {
		return TreeHooks.breakSpeed((Player) (Object) this, state, speed);
	}

	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float vestiges$exhaustion(float amount) {
		if ((Object) this instanceof ServerPlayer player) {
			return TreeHooks.exhaustion(player, amount);
		}
		return amount;
	}
}
