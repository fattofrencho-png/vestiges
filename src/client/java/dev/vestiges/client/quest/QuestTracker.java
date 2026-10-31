package dev.vestiges.client.quest;

import com.google.gson.JsonObject;
import dev.vestiges.client.ClientConfigStore;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The quest you picked, small and on a faint background so it blocks little of the view. It moves to any corner in Settings. */
public final class QuestTracker implements HudElement {
	private static final int WIDTH = 120;
	private static final int MARGIN = 4;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		JsonObject t = ClientQuests.tracked();
		if (mc.player == null || mc.gui.screen() != null || !t.has("name") || !ClientConfigStore.get().questTracker) {
			return;
		}
		Font font = mc.font;
		int have = t.get("have").getAsInt();
		int need = t.get("need").getAsInt();
		String count = need > 1 ? "  " + have + "/" + need : "";
		List<FormattedCharSequence> lines = font.split(Component.literal(t.get("text").getAsString() + count), WIDTH);
		int h = 12 + lines.size() * 9;
		String corner = ClientConfigStore.get().questTrackerCorner;
		int screenW = g.guiWidth();
		int screenH = g.guiHeight();
		int x = corner.endsWith("left") ? MARGIN : screenW - WIDTH - MARGIN - 4;
		int y = corner.startsWith("top") ? MARGIN : screenH - h - MARGIN;
		g.fill(x, y, x + WIDTH + 4, y + h, 0x30000000);
		g.text(font, t.get("name").getAsString(), x + 2, y + 2, 0xFFE6C36A, true);
		int ly = y + 12;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, x + 2, ly, 0xFFFFFFFF, true);
			ly += 9;
		}
	}
}
