package dev.vestiges.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.gear.BrokenGear;
import dev.vestiges.gear.GearService;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Durability: tool and armor wear stats change how much a use costs (before Unbreaking rolls, as vanilla does). Also the reworked Mending
 * ({@link BrokenGear}): gear that would break stays at 1 durability, broken, and a broken item does nothing until it is repaired.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true)
	private int vestiges$wear(int amount, @Local(argsOnly = true) ServerPlayer player) {
		return player != null ? TreeHooks.durability(player, (ItemStack) (Object) this, amount) : amount;
	}

	@Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
	private void vestiges$mending(int damage, ServerPlayer player, Consumer<ItemStack> onBreak, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!GearService.enabled()) {
			return;
		}
		if (BrokenGear.isBroken(self) || BrokenGear.tryCatch(self, damage, player)) {
			ci.cancel();
			return;
		}
		BrokenGear.warn(self, damage, player);
	}

	@Inject(method = "setDamageValue", at = @At("TAIL"))
	private void vestiges$repaired(int damage, CallbackInfo ci) {
		BrokenGear.clearIfRepaired((ItemStack) (Object) this);
	}

	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(1.0F);
		}
	}

	@Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenDrops(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenStats(EquipmentSlot slot, java.util.function.BiConsumer<?, ?> action, CallbackInfo ci) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenGroupStats(EquipmentSlotGroup group, org.apache.commons.lang3.function.TriConsumer<?, ?, ?> action, CallbackInfo ci) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void vestiges$brokenUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
