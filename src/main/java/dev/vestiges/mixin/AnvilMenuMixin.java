package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearService;
import dev.vestiges.server.Emeralds;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.TreeHooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The anvil (approved for M1c): it costs emeralds instead of levels (the same number), "Too Expensive!" starts at 40 plus the tree's bonus,
 * tree nodes slow the repair penalty, and two IV enchants combine into V (never into VI, which is found only; a VI is always kept).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;

	protected AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
		super(type, containerId, inventory, access, slots);
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void vestiges$canPay(Player p, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		if (!GearService.on(p) || p.hasInfiniteMaterials()) {
			return;
		}
		int c = cost.get();
		cir.setReturnValue(c > 0 && Emeralds.countWithBank(p) >= EnchantScale.anvilEmeralds(p, c));
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void vestiges$pay(Player p, ItemStack stack, CallbackInfo ci) {
		if (p instanceof ServerPlayer sp && GearService.enabled() && !sp.hasInfiniteMaterials() && cost.get() > 0) {
			Emeralds.payWithBank(sp, EnchantScale.anvilEmeralds(sp, cost.get()));
		}
	}

	@ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
	private int vestiges$limit(int vanilla) {
		return GearService.on(player) ? EnchantScale.anvilLimit(player) : vanilla;
	}

	@ModifyConstant(method = "createResult", constant = @Constant(intValue = 39))
	private int vestiges$renameCap(int vanilla) {
		return GearService.on(player) ? EnchantScale.anvilLimit(player) - 1 : vanilla;
	}

	/** Repair penalty: Runic Memory keeps book + book combines free of it; "-X% repair penalty growth" sometimes skips the increase. */
	@WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;calculateIncreasedRepairCost(I)I"))
	private int vestiges$penalty(int old, Operation<Integer> original) {
		if (GearService.on(player)) {
			PlayerStats s = TreeHooks.stats(player);
			if (s.has("runic_memory") && inputSlots.getItem(0).is(Items.ENCHANTED_BOOK) && inputSlots.getItem(1).is(Items.ENCHANTED_BOOK)) {
				return old;
			}
			double skip = -s.get("anvil_penalty") / 100.0;
			if (skip > 0 && player.getRandom().nextDouble() < skip) {
				return old;
			}
		}
		return original.call(old);
	}

	/** IV + IV = V. Vanilla stops at the max level (IV), so the result is raised here; a VI on either side is kept. */
	@Inject(method = "createResult", at = @At("TAIL"))
	private void vestiges$combineToFive(CallbackInfo ci) {
		ItemStack result = resultSlots.getItem(0);
		ItemStack right = inputSlots.getItem(1);
		if (result.isEmpty() || right.isEmpty()) {
			return;
		}
		ItemEnchantments left = EnchantmentHelper.getEnchantmentsForCrafting(inputSlots.getItem(0));
		ItemEnchantments added = EnchantmentHelper.getEnchantmentsForCrafting(right);
		ItemEnchantments now = EnchantmentHelper.getEnchantmentsForCrafting(result);
		for (Object2IntMap.Entry<Holder<Enchantment>> e : now.entrySet()) {
			Holder<Enchantment> h = e.getKey();
			int l = left.getLevel(h);
			int r = added.getLevel(h);
			if (r == 0 || !EnchantScale.scaled(h)) {
				continue;
			}
			int expected = l == r ? l + 1 : Math.max(l, r);
			expected = Math.min(expected, Math.max(EnchantScale.TABLE_MAX + 1, Math.max(l, r)));
			if (e.getIntValue() < expected) {
				int level = expected;
				EnchantmentHelper.updateEnchantments(result, m -> m.set(h, level));
			}
		}
	}
}
