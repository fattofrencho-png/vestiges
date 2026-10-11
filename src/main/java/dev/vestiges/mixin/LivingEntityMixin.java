package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import java.util.List;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.tree.TreeCombat;
import dev.vestiges.tree.TreeHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

	// ---- Elytra slot: gliding wears the elytra in the extra slot when no worn glider is found. ----

	@WrapOperation(method = "updateFallFlying", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/Util;getRandom(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/lang/Object;"))
	private Object vestiges$gliderSlot(List<?> slots, RandomSource random, Operation<Object> original) {
		return slots.isEmpty() ? null : original.call(slots, random);
	}

	@WrapOperation(method = "updateFallFlying", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$gliderItem(LivingEntity self, EquipmentSlot slot, Operation<ItemStack> original) {
		if (slot == null) {
			return self instanceof Player p ? InventoryHooks.slots(p).getItem(ExtraSlots.ELYTRA) : ItemStack.EMPTY;
		}
		return original.call(self, slot);
	}

	@WrapOperation(method = "updateFallFlying", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
	private void vestiges$gliderWear(ItemStack stack, int amount, LivingEntity self, EquipmentSlot slot, Operation<Void> original) {
		if (slot != null) {
			original.call(stack, amount, self, slot);
		} else if (self instanceof ServerPlayer player && !stack.isEmpty()) {
			stack.hurtAndBreak(amount, player.level(), player, item -> {
			});
		}
	}

	/** Totem slot: when neither hand holds a totem, the one in the totem slot saves you. */
	@WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$totemSlot(LivingEntity self, InteractionHand hand, Operation<ItemStack> original) {
		ItemStack inHand = original.call(self, hand);
		if (hand == InteractionHand.OFF_HAND && self instanceof Player p && !inHand.has(DataComponents.DEATH_PROTECTION)
			&& !self.getItemInHand(InteractionHand.MAIN_HAND).has(DataComponents.DEATH_PROTECTION)) {
			ItemStack totem = InventoryHooks.slots(p).getItem(ExtraSlots.TOTEM);
			if (totem.has(DataComponents.DEATH_PROTECTION)) {
				return totem;
			}
		}
		return inHand;
	}

	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float vestiges$healing(float amount) {
		if ((Object) this instanceof ServerPlayer player) {
			return TreeHooks.healing(player, amount);
		}
		return amount;
	}
}
