package dev.vestiges.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vestiges.client.inventory.ElytraState;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wings render from the elytra slot when the chest slot holds no glider (so a chestplate and wings show together). */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;chestEquipment:Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vestiges$wingsFromSlot(HumanoidRenderState state, Operation<ItemStack> original) {
		ItemStack chest = original.call(state);
		ItemStack slot = ((ElytraState) state).vestiges$elytra();
		return !chest.has(DataComponents.GLIDER) && !slot.isEmpty() ? slot : chest;
	}
}
