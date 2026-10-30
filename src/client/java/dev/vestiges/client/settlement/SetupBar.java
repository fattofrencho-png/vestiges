package dev.vestiges.client.settlement;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The setting-up bar (M5, approved): "Setting up Settlement Bell  6:12" above the action bar while the owner is near. */
public final class SetupBar implements HudElement {
	private static String label = "";
	private static int left;
	private static int total = 1;
	private static boolean fight;
	private static long at;

	public static void on(JsonObject o) {
		label = o.get("label").getAsString();
		left = o.get("left").getAsInt();
		total = Math.max(1, o.get("total").getAsInt());
		fight = o.get("fight").getAsBoolean();
		at = System.currentTimeMillis();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (System.currentTimeMillis() - at > 2500 || mc.player == null || mc.gui.screen() != null || left <= 0) {
			return;
		}
		Font font = mc.font;
		int seconds = left / 20;
		String text = label + "  " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)
			+ (fight ? "  (a fight restarted it)" : "");
		int w = 182;
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - 84;
		g.text(font, text, (g.guiWidth() - font.width(text)) / 2, y - 10, fight ? 0xFFFF8080 : 0xFFE6C36A, true);
		g.fill(x, y, x + w, y + 4, 0xFF2A2F33);
		g.fill(x, y, x + (int) (w * (1 - left / (double) total)), y + 4, 0xFFE6C36A);
	}
}
