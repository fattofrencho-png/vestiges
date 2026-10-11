package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ElytraState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements ElytraState {
	@Unique
	private ItemStack vestiges$elytra;

	@Override
	public ItemStack vestiges$elytra() {
		return vestiges$elytra != null ? vestiges$elytra : ItemStack.EMPTY;
	}

	@Override
	public void vestiges$setElytra(ItemStack stack) {
		vestiges$elytra = stack != null ? stack : ItemStack.EMPTY;
	}
}
