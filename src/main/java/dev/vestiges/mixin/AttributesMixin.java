package dev.vestiges.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Uncapped attributes (M1/M3): armor and toughness above vanilla's 30 / 20, and health for high-level elites. */
@Mixin(Attributes.class)
public abstract class AttributesMixin {
	@ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/ai/attributes/RangedAttribute;<init>(Ljava/lang/String;DDD)V"), index = 3)
	private static double vestiges$uncap(String descriptionId, double defaultValue, double min, double max) {
		return switch (descriptionId) {
			case "attribute.name.armor", "attribute.name.armor_toughness" -> 1024.0;
			case "attribute.name.max_health" -> 1_000_000.0;
			default -> max;
		};
	}
}
