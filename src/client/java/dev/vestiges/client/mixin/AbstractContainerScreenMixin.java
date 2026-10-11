package dev.vestiges.client.mixin;

import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.client.inventory.InventoryPanel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Container screens: clicks on the Bank and the ender chest slot in the survival inventory, and the ender chest's page list on the right
 * (page 1 can be renamed; the other pages show which dragon unlocks them; a Close button).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Unique
	private EditBox vestiges$rename;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean vestiges$isEnderChest() {
		return (Object) this instanceof ContainerScreen && getTitle().getContents() instanceof TranslatableContents t
			&& "container.enderchest".equals(t.getKey()) && ClientInventory.on();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void vestiges$bankAndEnder(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof InventoryScreen) {
			AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
			if (InventoryPanel.click(s.vestiges$leftPos(), s.vestiges$topPos(), event)) {
				cir.setReturnValue(true);
			}
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void vestiges$pageWidgets(CallbackInfo ci) {
		if (!vestiges$isEnderChest()) {
			return;
		}
		AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
		int x = s.vestiges$leftPos() + 176 + 4;
		int y = s.vestiges$topPos();
		vestiges$rename = new EditBox(font, x + 4, y + 112, 92, 16, Component.literal("Page name"));
		vestiges$rename.setMaxLength(24);
		vestiges$rename.setValue(ClientInventory.pageName(0));
		addRenderableWidget(vestiges$rename);
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> ClientInventory.send("page_rename", 0, 0, vestiges$rename.getValue()))
			.bounds(x + 4, y + 130, 92, 16).build());
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x + 4, y + 148, 92, 16).build());
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void vestiges$pageList(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!vestiges$isEnderChest()) {
			return;
		}
		AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
		int x = s.vestiges$leftPos() + 176 + 4;
		int y = s.vestiges$topPos();
		g.fill(x, y, x + 100, y + 108, 0xC0101010);
		g.outline(x, y, 100, 108, 0xFF555555);
		g.text(font, "Pages", x + 4, y + 4, 0xFFE6C36A, false);
		int row = y + 16;
		g.fill(x + 2, row - 2, x + 98, row + 9, 0x40FFFFFF);
		g.text(font, font.plainSubstrByWidth("1 · " + ClientInventory.pageName(0), 92), x + 4, row, 0xFFFFFFFF, false);
		row += 11;
		for (int page = 2; page <= ClientInventory.totalPages() && row < y + 104; page++) {
			boolean open = page <= ClientInventory.unlockedPages();
			String text = page + " · " + (open ? ClientInventory.pageName(page - 1) : "dragon " + (page - 1));
			g.text(font, font.plainSubstrByWidth(text, 92), x + 4, row, open ? 0xFFFFFFFF : 0xFF777777, false);
			row += 9;
		}
		if (mouseX >= x && mouseX < x + 100 && mouseY >= y + 26 && mouseY < y + 108) {
			g.setComponentTooltipForNextFrame(font, java.util.List.of(Component.literal("One more page for each dragon you defeat.").withStyle(ChatFormatting.GRAY)),
				mouseX, mouseY);
		}
	}
}
