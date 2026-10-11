package dev.vestiges.client.hud;

import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Level feedback: a "+42 XP" pop-up above the right end of the XP bar (gains close together add up), a "Level 12!" banner on level-up, and
 * "Paragon 12" beside the XP bar after level 100.
 */
public final class XpHud implements HudElement {
	private static final long POPUP_MS = 2200;
	private static final long MERGE_MS = 1200;
	private static final long BANNER_MS = 3500;

	private static long popupAmount;
	private static long popupStart;
	private static long lastGain;
	private static String bannerText = "";
	private static long bannerStart;
	private static int paragon;
	private static final long TOAST_MS = 2600;
	/** Discovery toasts waiting their turn: title, subtitle, XP. */
	private static final ArrayDeque<Object[]> TOASTS = new ArrayDeque<>();
	private static long toastStart;
	private static final long LAYER_MS = 3500;
	private static String layerText = "";
	private static long layerStart;

	public static void gain(long amount) {
		long now = System.currentTimeMillis();
		if (now - lastGain > MERGE_MS) {
			popupAmount = 0;
		}
		popupAmount += amount;
		popupStart = now;
		lastGain = now;
	}

	public static void levelUp(int level, int newParagon) {
		paragon = newParagon;
		bannerText = level >= 100 && newParagon > 0 ? "Paragon " + newParagon + "!" : "Level " + level + "!";
		bannerStart = System.currentTimeMillis();
	}

	/** Entering a new layer: "Caves: mobs up to level 40". */
	public static void layer(String name, int cap) {
		layerText = name + ": mobs up to level " + cap;
		layerStart = System.currentTimeMillis();
	}

	public static void setParagon(int value) {
		paragon = value;
	}

	/** A first visit: "biome:minecraft:plains", "structure:minecraft:village_plains", "dim:minecraft:the_nether"... */
	public static void discovery(String key, long xp) {
		String[] parts = key.split(":", 2);
		String id = parts.length > 1 ? parts[1] : key;
		int colon = id.indexOf(':');
		String ns = colon >= 0 ? id.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		Component title;
		Component sub;
		switch (parts[0]) {
			case "biome" -> {
				title = Component.literal("New biome discovered");
				sub = Component.translatable("biome." + ns + "." + path);
			}
			case "structure" -> {
				title = Component.literal("New structure discovered");
				sub = Component.literal(pretty(path));
			}
			case "dim" -> {
				title = Component.literal(path.equals("the_end") ? "You entered the End" : path.equals("the_nether") ? "You entered the Nether" : "New dimension discovered");
				sub = Component.empty();
			}
			default -> {
				title = Component.literal("Discovery");
				sub = Component.empty();
			}
		}
		if (TOASTS.isEmpty()) {
			toastStart = System.currentTimeMillis();
		}
		TOASTS.add(new Object[] {title, sub, xp});
	}

	private static String pretty(String path) {
		StringBuilder sb = new StringBuilder();
		for (String w : path.replace('/', '_').split("_")) {
			if (!w.isEmpty()) {
				sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
			}
		}
		return sb.toString();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
			return;
		}
		Font font = mc.font;
		long now = System.currentTimeMillis();
		int barY = g.guiHeight() - 29;
		if (now - popupStart < POPUP_MS && popupAmount > 0) {
			float t = (now - popupStart) / (float) POPUP_MS;
			int alpha = (int) (255 * Math.min(1f, (1f - t) * 2f));
			String s = "+" + popupAmount + " XP";
			// Drawn 1.5x so it reads at a glance, above the Tool and Weapon slots, right where the XP bar ends.
			float scale = 1.5f;
			int y = g.guiHeight() - 22 - 16 - (int) (t * 6);
			g.pose().pushMatrix();
			g.pose().translate(ExtraHotbarHud.rightStart(g, mc.player), y);
			g.pose().scale(scale, scale);
			g.text(font, s, 0, 0, (Math.max(8, alpha) << 24) | 0x80FF20, true);
			g.pose().popMatrix();
		}
		if (paragon > 0) {
			String p = "Paragon " + paragon;
			g.text(font, p, g.guiWidth() / 2 + 94, barY - 1, 0xFFE6C36A, true);
		}
		drawToast(g, font, now);
		if (now - layerStart < LAYER_MS && !layerText.isEmpty()) {
			float t = (now - layerStart) / (float) LAYER_MS;
			int alpha = Math.max(8, (int) (255 * Math.min(1f, Math.min(t * 6f, (1f - t) * 3f))));
			g.pose().pushMatrix();
			g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f + 24);
			g.pose().scale(1.25f, 1.25f);
			g.text(font, layerText, -font.width(layerText) / 2, 0, (alpha << 24) | 0xD8D0C0, true);
			g.pose().popMatrix();
		}
		if (now - bannerStart < BANNER_MS && !bannerText.isEmpty()) {
			float t = (now - bannerStart) / (float) BANNER_MS;
			int alpha = (int) (255 * Math.min(1f, (1f - t) * 3f));
			g.pose().pushMatrix();
			g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 4f);
			g.pose().scale(2f, 2f);
			g.text(font, bannerText, -font.width(bannerText) / 2, 0, (Math.max(8, alpha) << 24) | 0xE6C36A, true);
			g.pose().popMatrix();
		}
	}

	/** The discovery toast: a quick title with the XP, and the place's name under it, just above the crosshair. */
	private static void drawToast(GuiGraphicsExtractor g, Font font, long now) {
		if (TOASTS.isEmpty()) {
			return;
		}
		if (now - toastStart >= TOAST_MS) {
			TOASTS.poll();
			toastStart = now;
			if (TOASTS.isEmpty()) {
				return;
			}
		}
		Object[] toast = TOASTS.peek();
		float t = (now - toastStart) / (float) TOAST_MS;
		int alpha = Math.max(8, (int) (255 * Math.min(1f, Math.min(t * 8f, (1f - t) * 4f))));
		Component title = (Component) toast[0];
		Component sub = (Component) toast[1];
		String xp = "  +" + toast[2] + " XP";
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 - 44;
		float scale = 1.5f;
		int w = font.width(title) + font.width(xp);
		g.pose().pushMatrix();
		g.pose().translate(cx - w * scale / 2f, y);
		g.pose().scale(scale, scale);
		g.text(font, title, 0, 0, (alpha << 24) | 0xE6C36A, true);
		g.text(font, xp, font.width(title), 0, (alpha << 24) | 0x80FF20, true);
		g.pose().popMatrix();
		if (!sub.getString().isEmpty()) {
			g.text(font, sub, cx - font.width(sub) / 2, y + 16, (alpha << 24) | 0xDDDDDD, true);
		}
	}
}
