package dev.vestiges.client.mixin;

import dev.vestiges.client.features.ClientFeatures;
import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.client.inventory.InventoryPanel;
import dev.vestiges.config.VestigesSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;
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
 * (click an unlocked page to open it, scroll for more; the page you are on can be renamed; the other pages show which dragon unlocks them; a Close
 * button).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Unique
	private EditBox vestiges$rename;
	/** The first page row shown in the ender chest's page list. */
	@Unique
	private int vestiges$pageScroll;
	@Unique
	private static final int VESTIGES_ROWS = 9;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean vestiges$isEnderChest() {
		return (Object) this instanceof ContainerScreen && getTitle().getContents() instanceof TranslatableContents t
			&& "container.enderchest".equals(t.getKey()) && ClientInventory.on();
	}

	@Unique
	private int vestiges$currentPage() {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(" · (\\d+)").matcher(getTitle().getString());
		return m.find() ? Math.max(0, Integer.parseInt(m.group(1)) - 1) : 0;
	}

	/** The page row under the mouse in the page list, or -1. */
	@Unique
	private int vestiges$pageAt(double mouseX, double mouseY) {
		AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
		int x = s.vestiges$leftPos() + 176 + 4;
		int y = s.vestiges$topPos();
		if (mouseX < x || mouseX >= x + 100 || mouseY < y + 14 || mouseY >= y + 14 + VESTIGES_ROWS * 9) {
			return -1;
		}
		int page = vestiges$pageScroll + (int) ((mouseY - (y + 14)) / 9);
		return page < ClientInventory.totalPages() ? page : -1;
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void vestiges$scrollPages(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (vestiges$isEnderChest() && vestiges$pageAt(mouseX, mouseY) >= 0) {
			int max = Math.max(0, ClientInventory.totalPages() - VESTIGES_ROWS);
			vestiges$pageScroll = Math.max(0, Math.min(max, vestiges$pageScroll - (int) Math.signum(scrollY)));
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void vestiges$bankAndEnder(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (vestiges$isEnderChest()) {
			int page = vestiges$pageAt(event.x(), event.y());
			if (page >= 0) {
				if (page < ClientInventory.unlockedPages() && page != vestiges$currentPage()) {
					ClientInventory.send("ender_page", page, 0, "");
				}
				cir.setReturnValue(true);
				return;
			}
		}
		if ((Object) this instanceof InventoryScreen) {
			AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
			if (InventoryPanel.click(s.vestiges$leftPos(), s.vestiges$topPos(), event)) {
				cir.setReturnValue(true);
			}
		}
	}

	/** While the page name box has focus it gets every key except Esc, so E, Q and 1-9 type instead of closing the screen or moving items. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void vestiges$renameKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (vestiges$rename != null && vestiges$rename.isFocused() && !event.isEscape()) {
			vestiges$rename.keyPressed(event);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void vestiges$pageWidgets(CallbackInfo ci) {
		if (((Object) this instanceof ContainerScreen || (Object) this instanceof ShulkerBoxScreen || (Object) this instanceof HopperScreen || (Object) this instanceof DispenserScreen) && ClientFeatures.on(VestigesSystem.SORTING)) {
			AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
			int right = a.vestiges$leftPos() + 176 - 7;
			int by = a.vestiges$topPos() + 3;
			addRenderableWidget(Button.builder(Component.literal("Out"), b -> ClientInventory.send("move_all", 1, 0, "")).bounds(right - 22, by, 22, 12)
				.tooltip(Tooltip.create(Component.literal("Take everything out"))).build());
			addRenderableWidget(Button.builder(Component.literal("In"), b -> ClientInventory.send("move_all", 0, 0, "")).bounds(right - 42, by, 18, 12)
				.tooltip(Tooltip.create(Component.literal("Move your inventory in (not the hotbar)"))).build());
			addRenderableWidget(Button.builder(Component.literal("Sort"), b -> ClientInventory.send("sort", 0, 0, "")).bounds(right - 70, by, 26, 12).build());
		}
		if ((Object) this instanceof ShulkerBoxScreen && ClientInventory.shulkerOpen()) {
			AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
			int bx = a.vestiges$leftPos() + 176 + 4;
			int top = a.vestiges$topPos();
			vestiges$rename = new EditBox(font, bx, top, 92, 16, Component.literal("Box name"));
			vestiges$rename.setMaxLength(35);
			vestiges$rename.setValue(ClientInventory.shulkerName());
			addRenderableWidget(vestiges$rename);
			addRenderableWidget(Button.builder(Component.literal("Rename"), b -> ClientInventory.send("shulker_rename", 0, 0, vestiges$rename.getValue())).bounds(bx, top + 18, 92, 16).build());
		}
		if (!vestiges$isEnderChest()) {
			return;
		}
		AbstractContainerScreenAccessor s = (AbstractContainerScreenAccessor) this;
		int x = s.vestiges$leftPos() + 176 + 4;
		int y = s.vestiges$topPos();
		vestiges$rename = new EditBox(font, x + 4, y + 112, 92, 16, Component.literal("Page name"));
		vestiges$rename.setMaxLength(24);
		vestiges$rename.setValue(ClientInventory.pageName(vestiges$currentPage()));
		addRenderableWidget(vestiges$rename);
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> ClientInventory.send("page_rename", vestiges$currentPage(), 0, vestiges$rename.getValue()))
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
		int current = vestiges$currentPage();
		for (int i = 0; i < VESTIGES_ROWS; i++) {
			int page = vestiges$pageScroll + i;
			if (page >= ClientInventory.totalPages()) {
				break;
			}
			int row = y + 16 + i * 9;
			boolean open = page < ClientInventory.unlockedPages();
			if (page == current) {
				g.fill(x + 2, row - 2, x + 98, row + 8, 0x40FFFFFF);
			} else if (open && mouseX >= x && mouseX < x + 100 && mouseY >= row - 2 && mouseY < row + 7) {
				g.fill(x + 2, row - 2, x + 98, row + 8, 0x20FFFFFF);
			}
			String text = (page + 1) + " · " + (open ? ClientInventory.pageName(page) : "dragon " + page);
			g.text(font, font.plainSubstrByWidth(text, 92), x + 4, row, open ? 0xFFFFFFFF : 0xFF777777, false);
		}
		if (ClientInventory.totalPages() > VESTIGES_ROWS) {
			g.text(font, vestiges$pageScroll > 0 ? "^" : " ", x + 90, y + 4, 0xFF999999, false);
			g.text(font, vestiges$pageScroll + VESTIGES_ROWS < ClientInventory.totalPages() ? "v" : " ", x + 90, y + 98, 0xFF999999, false);
		}
		if (mouseX >= x && mouseX < x + 100 && mouseY >= y + 14 && mouseY < y + 108 && vestiges$pageAt(mouseX, mouseY) >= ClientInventory.unlockedPages()) {
			g.setComponentTooltipForNextFrame(font, java.util.List.of(Component.literal("Opens when you have defeated that dragon.").withStyle(ChatFormatting.GRAY)),
				mouseX, mouseY);
		}
	}
}
