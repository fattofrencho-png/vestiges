package dev.vestiges.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A tab: looks like a button, and the selected one is lit with a gold label like the welcome page's presets, not greyed out like a button that's
 * switched off. Clicking the selected tab does nothing.
 */
public final class TabButton extends AbstractWidget {
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_ON = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final int GOLD = 0xFFE6C36A;
	private static final int PADDING = 10;

	private final boolean selected;
	private final Runnable onSelect;

	public TabButton(int x, int y, int width, int height, String label, boolean selected, Runnable onSelect) {
		super(x, y, width, height, Component.literal(label));
		this.selected = selected;
		this.onSelect = onSelect;
	}

	/** The width that fits this label. */
	public static int widthFor(Font font, String label) {
		return font.width(label) + PADDING;
	}

	private void select() {
		if (!selected) {
			onSelect.run();
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, selected || isHoveredOrFocused() ? BUTTON_ON : BUTTON, getX(), getY(), getWidth(), getHeight());
		String label = getMessage().getString();
		g.text(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2, selected ? GOLD : 0xFFFFFFFF);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		select();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isSelection()) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			select();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
