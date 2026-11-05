package dev.vestiges.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vestiges.inventory.StackRules;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks the inputs of anvils and smithing tables for {@link SlotMixin}. */
@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
	@ModifyReturnValue(method = "createContainer", at = @At("RETURN"))
	private SimpleContainer vestiges$mark(SimpleContainer container) {
		StackRules.markSingleInput(container);
		return container;
	}
}
