package dev.vestiges.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.inventory.StackRules;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Durability: tool and armor wear stats change how much a use costs (before Unbreaking rolls, as vanilla does). Bigger stacks, when switched on. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true)
	private int vestiges$wear(int amount, @Local(argsOnly = true) ServerPlayer player) {
		return player != null ? TreeHooks.durability(player, (ItemStack) (Object) this, amount) : amount;
	}

	/** Vanilla keeps this as a default method on {@code ItemInstance}, so the stack overrides it here. */
	public int getMaxStackSize() {
		ItemStack self = (ItemStack) (Object) this;
		return StackRules.max(self, self.getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
	}
}
