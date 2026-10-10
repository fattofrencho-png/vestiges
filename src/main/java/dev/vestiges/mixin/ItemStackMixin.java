package dev.vestiges.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Durability: tool and armor wear stats change how much a use costs (before Unbreaking rolls, as vanilla does). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true)
	private int vestiges$wear(int amount, @Local(argsOnly = true) ServerPlayer player) {
		return player != null ? TreeHooks.durability(player, (ItemStack) (Object) this, amount) : amount;
	}
}
