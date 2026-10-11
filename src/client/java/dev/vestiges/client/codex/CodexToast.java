package dev.vestiges.client.codex;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A small vanilla-style toast (top right) for each Codex step: the entry's icon, its name and the stage reached with the XP. */
public final class CodexToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long SHOW_MS = 3000;

	private final ItemStack icon;
	private final String title;
	private final String sub;
	private long shown;

	public CodexToast(Item icon, String name, String label, long xp) {
		this.icon = new ItemStack(icon);
		this.title = name;
		this.sub = label + (xp > 0 ? "  +" + xp + " XP" : "");
	}

	@Override
	public Visibility getWantedVisibility() {
		return shown >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void update(ToastManager manager, long visibleTime) {
		shown = visibleTime;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleTime) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		if (!icon.isEmpty()) {
			g.item(icon, 8, 8);
		}
		g.text(font, "Codex: " + title, 30, 7, 0xFFE6C36A, false);
		g.text(font, sub, 30, 18, 0xFFFFFFFF, false);
	}
}
