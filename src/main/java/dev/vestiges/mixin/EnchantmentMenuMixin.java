package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearService;
import dev.vestiges.server.Emeralds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The enchanting table (approved for M1c): it still needs your level (the bar is your Vestiges level), costs lapis plus emeralds (the number
 * shown / 5, rounded up) instead of levels, and its 3rd slot can come out one level higher (IV to V) thanks to arcane blocks or Arcane Insight.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
	@Shadow
	@Final
	private ContainerLevelAccess access;
	@Shadow
	@Final
	private RandomSource random;
	@Shadow
	@Final
	public int[] costs;
	@Shadow
	@Final
	private Container enchantSlots;

	@Unique
	private Player vestiges$player;
	@Unique
	private int vestiges$emeralds;
	@Unique
	private int vestiges$lapis;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void vestiges$rememberPlayer(int containerId, Inventory inventory, ContainerLevelAccess levelAccess, CallbackInfo ci) {
		vestiges$player = inventory.player;
	}

	/** Level V: the list is rolled from the slot's fixed seed, so this extra roll stays the same between the preview and the real enchant. */
	@ModifyReturnValue(method = "getEnchantmentList", at = @At("RETURN"))
	private List<EnchantmentInstance> vestiges$levelFive(List<EnchantmentInstance> list, RegistryAccess registries, ItemStack stack, int slot, int cost) {
		if (slot != 2 || list.isEmpty() || vestiges$player == null || !GearService.on(vestiges$player)) {
			return list;
		}
		int arcane = access.evaluate(EnchantScale::arcaneBlocks).orElse(0);
		double chance = EnchantScale.levelFiveChance(vestiges$player, arcane);
		if (chance <= 0 || random.nextDouble() >= chance) {
			return list;
		}
		List<EnchantmentInstance> out = new ArrayList<>(list.size());
		for (EnchantmentInstance e : list) {
			boolean top = e.level() >= EnchantScale.TABLE_MAX && EnchantScale.scaled(e.enchantment());
			out.add(top ? new EnchantmentInstance(e.enchantment(), EnchantScale.TABLE_MAX + 1) : e);
		}
		return out;
	}

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void vestiges$canPay(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
		vestiges$emeralds = 0;
		if (id < 0 || id >= costs.length || costs[id] <= 0 || player.hasInfiniteMaterials() || !GearService.on(player)) {
			return;
		}
		int price = EnchantScale.tableEmeralds(player, costs[id]);
		if (Emeralds.count(player) < price) {
			cir.setReturnValue(false);
			return;
		}
		// The costs are recomputed (to 0) once the item is enchanted, so the price is kept from here.
		vestiges$emeralds = price;
		vestiges$lapis = id + 1;
	}

	@Inject(method = "clickMenuButton", at = @At("RETURN"))
	private void vestiges$pay(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || vestiges$emeralds <= 0 || !(player instanceof ServerPlayer sp)) {
			return;
		}
		Emeralds.take(sp, vestiges$emeralds);
		int back = EnchantScale.lapisRefund(sp, vestiges$lapis, sp.getRandom());
		if (back > 0) {
			ItemStack lapis = enchantSlots.getItem(1);
			if (lapis.isEmpty()) {
				enchantSlots.setItem(1, new ItemStack(Items.LAPIS_LAZULI, back));
			} else if (lapis.is(Items.LAPIS_LAZULI)) {
				lapis.grow(back);
			} else {
				sp.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, back));
			}
		}
		vestiges$emeralds = 0;
	}
}
