package dev.vestiges.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;

/**
 * Hearts as a percentage (agreed): always 10 vanilla hearts, each worth 10% of max health, with the exact number beside them. Keeps the
 * vanilla looks for poison, wither, freezing and hardcore, blinks at low health, and shows absorption as a gold "+N".
 */
public final class HeartsHud implements HudElement {
	private static final int HEARTS = 10;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || mc.level == null) {
			return;
		}
		float max = Math.max(1f, player.getMaxHealth());
		float hp = Math.max(0f, Math.min(max, player.getHealth()));
		int halves = (int) Math.ceil(hp / max * HEARTS * 2);
		if (hp > 0 && halves == 0) {
			halves = 1;
		}
		boolean hardcore = mc.level.getLevelData().isHardcore();
		String kind = player.hasEffect(MobEffects.POISON) ? "poisoned_" : player.hasEffect(MobEffects.WITHER) ? "withered_" : player.isFullyFrozen() ? "frozen_" : "";
		String hc = hardcore ? "hardcore_" : "";
		Identifier container = sprite("container" + (hardcore ? "_hardcore" : ""));
		Identifier full = sprite(kind + hc + "full");
		Identifier half = sprite(kind + hc + "half");

		int x0 = g.guiWidth() / 2 - 91;
		int y0 = g.guiHeight() - 39;
		boolean low = hp <= max * 0.2f;
		int ticks = player.tickCount;
		for (int i = 0; i < HEARTS; i++) {
			int x = x0 + i * 8;
			int y = y0;
			if (low) {
				y += (ticks + i * 7) % 4 == 0 ? -1 : 0;
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, 9, 9);
			if (halves >= i * 2 + 2) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, full, x, y, 9, 9);
			} else if (halves == i * 2 + 1) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, half, x, y, 9, 9);
			}
		}

		Font font = mc.font;
		String text = (int) Math.ceil(hp) + "/" + Math.round(max);
		int tx = x0 - font.width(text) - 3;
		g.text(font, text, tx, y0 + 1, low ? 0xFFFF5555 : 0xFFFF8080, true);
		float abs = player.getAbsorptionAmount();
		if (abs > 0) {
			String a = "+" + (int) Math.ceil(abs);
			g.text(font, a, tx - font.width(a) - 3, y0 + 1, 0xFFFFD34D, true);
		}
	}

	private static Identifier sprite(String name) {
		return Identifier.withDefaultNamespace("hud/heart/" + name);
	}
}
