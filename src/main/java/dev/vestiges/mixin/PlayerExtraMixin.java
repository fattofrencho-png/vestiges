package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.Selection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every player gets the extra slots (saved with the player), the Bank and the ender chest page names. The elytra slot lets you glide, and
 * bows and crossbows draw from the arrows slot.
 */
@Mixin(Player.class)
public abstract class PlayerExtraMixin implements ExtraHolder {
	/** Created on first use: the inventory menu asks for it while the player is still being built, and mixin field initialisers can't be
	 * relied on to have run by then. */
	@Unique
	private ExtraSlots vestiges$slots;
	@Unique
	private long vestiges$bank;
	@Unique
	private List<String> vestiges$pages;

	@Override
	public ExtraSlots vestiges$slots() {
		if (vestiges$slots == null) {
			vestiges$slots = new ExtraSlots();
		}
		return vestiges$slots;
	}

	@Override
	public long vestiges$bank() {
		return vestiges$bank;
	}

	@Override
	public void vestiges$setBank(long emeralds) {
		vestiges$bank = Math.max(0, emeralds);
	}

	@Override
	public List<String> vestiges$pageNames() {
		return vestiges$pages != null ? vestiges$pages : List.of();
	}

	@Override
	public void vestiges$setPageNames(List<String> names) {
		vestiges$pages = List.copyOf(names);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void vestiges$save(ValueOutput out, CallbackInfo ci) {
		out.store("vestiges_slots", ExtraSlots.CODEC, vestiges$slots().toList());
		out.putLong("vestiges_bank", vestiges$bank);
		out.store("vestiges_pages", Codec.STRING.listOf(), vestiges$pageNames());
		Selection sel = (Selection) ((Player) (Object) this).getInventory();
		out.store("vestiges_racks", Codec.INT.listOf(), List.of(sel.vestiges$rackIndex(Selection.TOOL), sel.vestiges$rackIndex(Selection.WEAPON),
			sel.vestiges$rackIndex(Selection.BELT)));
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void vestiges$load(ValueInput in, CallbackInfo ci) {
		in.read("vestiges_slots", ExtraSlots.CODEC).ifPresent(list -> vestiges$slots().fromList(list));
		vestiges$bank = Math.max(0, in.getLongOr("vestiges_bank", 0L));
		vestiges$pages = in.read("vestiges_pages", Codec.STRING.listOf()).map(List::copyOf).orElse(List.of());
		Selection sel = (Selection) ((Player) (Object) this).getInventory();
		in.read("vestiges_racks", Codec.INT.listOf()).ifPresent(r -> {
			for (int i = 0; i < Math.min(3, r.size()); i++) {
				sel.vestiges$setRackIndex(Selection.TOOL + i, r.get(i));
			}
		});
	}

	/** The elytra slot works like wearing the elytra (same rules as vanilla: not on the ground, riding, levitating or flying). */
	@ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
	private boolean vestiges$elytraSlot(boolean vanilla) {
		if (vanilla) {
			return true;
		}
		Player self = (Player) (Object) this;
		ItemStack elytra = vestiges$slots().getItem(ExtraSlots.ELYTRA);
		return !elytra.isEmpty() && LivingEntity.canGlideUsing(elytra, EquipmentSlot.CHEST) && !self.getAbilities().flying && !self.onGround()
			&& !self.isPassenger() && !self.hasEffect(MobEffects.LEVITATION);
	}

	/** Quiver: after a projectile held in the other hand, bows and crossbows use the arrows slot. */
	@WrapOperation(method = "getProjectile", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ProjectileWeaponItem;getHeldProjectile(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$quiver(LivingEntity entity, Predicate<ItemStack> supported, Operation<ItemStack> original) {
		ItemStack held = original.call(entity, supported);
		if (!held.isEmpty()) {
			return held;
		}
		ItemStack arrows = vestiges$slots().getItem(ExtraSlots.ARROWS);
		return !arrows.isEmpty() && supported.test(arrows) ? arrows : held;
	}
}
