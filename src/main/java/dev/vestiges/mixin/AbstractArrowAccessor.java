package dev.vestiges.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Splinter Bolt add pierce to crossbow bolts. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setPierceLevel")
	void vestiges$setPierceLevel(byte level);
}
