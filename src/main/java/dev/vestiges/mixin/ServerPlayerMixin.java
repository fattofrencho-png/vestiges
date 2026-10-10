package dev.vestiges.mixin;

import dev.vestiges.level.LevelService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The XP bar is the Vestiges level bar: vanilla XP (orbs, /xp) no longer changes it, and enchanting no longer costs levels. Mending still
 * works, because orbs repair gear before they would reach {@code giveExperiencePoints}.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Unique
	private int vestiges$levelBeforeEnchant;

	@Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
	private void vestiges$noVanillaXp(int amount, CallbackInfo ci) {
		if (LevelService.enabled() && !LevelService.syncing) {
			ci.cancel();
		}
	}

	@Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
	private void vestiges$noVanillaLevels(int amount, CallbackInfo ci) {
		if (LevelService.enabled() && !LevelService.syncing) {
			ci.cancel();
		}
	}

	@Inject(method = "onEnchantmentPerformed", at = @At("HEAD"))
	private void vestiges$rememberLevel(ItemStack stack, int cost, CallbackInfo ci) {
		vestiges$levelBeforeEnchant = ((ServerPlayer) (Object) this).experienceLevel;
	}

	@Inject(method = "onEnchantmentPerformed", at = @At("TAIL"))
	private void vestiges$freeEnchanting(ItemStack stack, int cost, CallbackInfo ci) {
		if (LevelService.enabled()) {
			((ServerPlayer) (Object) this).experienceLevel = vestiges$levelBeforeEnchant;
		}
	}
}
