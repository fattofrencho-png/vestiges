package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The anvil shows its price in emeralds, and "Too Expensive!" moves with the tree's anvil limit. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@ModifyConstant(method = "extractLabels", constant = @Constant(intValue = 40))
	private int vestiges$limit(int vanilla) {
		Player player = Minecraft.getInstance().player;
		return player != null && GearService.on(player) ? EnchantScale.anvilLimit(player) : vanilla;
	}

	@WrapOperation(method = "extractLabels", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent vestiges$price(String key, Object[] args, Operation<MutableComponent> original) {
		Player player = Minecraft.getInstance().player;
		if ("container.repair.cost".equals(key) && player != null && GearService.on(player) && args.length > 0 && args[0] instanceof Integer cost) {
			int price = EnchantScale.anvilEmeralds(player, cost);
			return Component.literal("Cost: " + price + (price == 1 ? " emerald" : " emeralds"));
		}
		return original.call(key, args);
	}
}
