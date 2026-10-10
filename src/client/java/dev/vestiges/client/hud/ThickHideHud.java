package dev.vestiges.client.hud;

import dev.vestiges.client.tree.ClientTree;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Thick Hide's ready icon: a small shield right of the hotbar, bright when the next hit will be softened, filling up while it recharges. */
public final class ThickHideHud implements HudElement {
	/** Made on first draw: item stacks can't exist while the game is still starting up. */
	private ItemStack shield;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !ClientTree.stats().has("thick_hide")) {
			return;
		}
		if (shield == null) {
			shield = new ItemStack(Items.SHIELD);
		}
		int x = g.guiWidth() / 2 + 91 + 8;
		int y = g.guiHeight() - 20;
		float charge = ClientTree.thickHideCharge(mc.level.getGameTime());
		g.item(shield, x, y);
		if (charge < 1f) {
			int covered = (int) (16 * (1f - charge));
			g.fill(x, y, x + 16, y + covered, 0xAA000000);
		}
	}
}
