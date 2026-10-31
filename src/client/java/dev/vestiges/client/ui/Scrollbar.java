package dev.vestiges.client.ui;

import dev.vestiges.client.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A thin bar that shows there's more to scroll to, and where you are. It only shows; the mouse wheel does the scrolling. */
public final class Scrollbar {
	private static final int WIDTH = 3;
	private static final int MIN_THUMB = 12;

	private Scrollbar() {
	}

	/** Draws nothing when everything already fits ({@code max} is 0). */
	public static void draw(GuiGraphicsExtractor g, int x, int top, int bottom, double scroll, double max) {
		if (max <= 0) {
			return;
		}
		int height = bottom - top;
		int thumb = Math.max(MIN_THUMB, (int) (height * (height / (height + max))));
		int y = top + (int) ((height - thumb) * Math.min(1.0, scroll / max));
		g.fill(x, top, x + WIDTH, bottom, UiTheme.barTrack());
		g.fill(x, y, x + WIDTH, y + thumb, UiTheme.muted());
	}
}
