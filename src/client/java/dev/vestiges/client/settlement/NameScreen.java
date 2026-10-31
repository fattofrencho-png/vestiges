package dev.vestiges.client.settlement;

import dev.vestiges.client.UiTheme;
import dev.vestiges.net.SettlementPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Naming a settlement (once its Bell is set up) or a Banner's area (once placed). The name is final, as agreed. */
public final class NameScreen extends Screen {
	private final String kind;
	private final String id;
	private EditBox box;

	public NameScreen(String kind, String id, String title) {
		super(Component.literal(title));
		this.kind = kind;
		this.id = id;
	}

	@Override
	protected void init() {
		int w = Math.min(220, width - 40);
		box = new EditBox(font, (width - w) / 2, height / 2 - 10, w, 20, Component.literal("Name"));
		box.setMaxLength(32);
		addRenderableWidget(box);
		setInitialFocus(box);
		addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> confirm()).bounds(width / 2 - 104, height / 2 + 18, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Later"), b -> onClose()).bounds(width / 2 + 4, height / 2 + 18, 100, 20).build());
	}

	private void confirm() {
		String name = box.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		if (ClientPlayNetworking.canSend(SettlementPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new SettlementPayloads.Action("settlement".equals(kind) ? "name_settlement" : "name_banner", id, name));
		}
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiTheme.paintBackground(g, width, height);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, getTitle(), (width - font.width(getTitle())) / 2, height / 2 - 40, UiTheme.head(), UiTheme.shadow());
		String hint = "You can't rename it later. (Later: right-click it again to name it.)";
		g.text(font, hint, (width - font.width(hint)) / 2, height / 2 - 26, UiTheme.muted(), UiTheme.shadow());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
