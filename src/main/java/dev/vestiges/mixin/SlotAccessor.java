package dev.vestiges.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Moves a slot on screen: the crafting grid changes shape with the crafting table slot. */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void vestiges$setX(int x);

	@Mutable
	@Accessor("y")
	void vestiges$setY(int y);
}
