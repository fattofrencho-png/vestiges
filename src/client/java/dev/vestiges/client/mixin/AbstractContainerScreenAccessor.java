package dev.vestiges.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the inventory screen grow to fit the rack panel, and read where it is drawn. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Mutable
	@Accessor("imageWidth")
	void vestiges$setImageWidth(int width);

	@Accessor("leftPos")
	int vestiges$leftPos();

	@Accessor("topPos")
	int vestiges$topPos();
}
