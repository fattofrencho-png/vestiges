package dev.vestiges.client.hud;

import dev.vestiges.client.inventory.ClientInventory;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.Selection;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The extra hotbar slots (approved HUD): the belt slot on the left of the offhand, the Tool and Weapon slots on the right of the hotbar,
 * each showing its rack's current item, little dots for the rack positions, and the selection frame when one is selected.
 */
public final class ExtraHotbarHud implements HudElement {
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");

	/** Left edge of the Tool slot frame (the Weapon slot follows it). Other HUD bits sit after it. */
	public static int rightStart(GuiGraphicsExtractor g, Player p) {
		boolean offhandRight = p.getMainArm() == HumanoidArm.LEFT && !p.getOffhandItem().isEmpty();
		return g.guiWidth() / 2 + 91 + (offhandRight ? 29 : 0) + 4;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || p.isSpectator() || !ClientInventory.on()) {
			return;
		}
		ExtraSlots slots = InventoryHooks.slots(p);
		Selection sel = InventoryHooks.selection(p);
		int y = g.guiHeight() - 22;
		int cx = g.guiWidth() / 2;
		// Belt: left of the offhand slot (or of the hotbar when the offhand sits on the right).
		boolean offhandLeft = p.getMainArm() == HumanoidArm.RIGHT && !p.getOffhandItem().isEmpty();
		int beltX = cx - 91 - (offhandLeft ? 29 : 0) - 4 - 22;
		frame(g, beltX, y, 1);
		int rx = rightStart(g, p);
		frame(g, rx, y, 2);
		slot(g, mc, beltX, y, slots.getItem(ExtraSlots.BELT + sel.vestiges$rackIndex(Selection.BELT)));
		slot(g, mc, rx, y, slots.getItem(ExtraSlots.TOOLS + sel.vestiges$rackIndex(Selection.TOOL)));
		slot(g, mc, rx + 20, y, slots.getItem(ExtraSlots.WEAPONS + sel.vestiges$rackIndex(Selection.WEAPON)));
		dots(g, slots, sel, Selection.BELT, beltX + 11, y - 3);
		dots(g, slots, sel, Selection.TOOL, rx + 11, y - 3);
		dots(g, slots, sel, Selection.WEAPON, rx + 31, y - 3);
		int selected = sel.vestiges$extra();
		if (selected != Selection.NONE) {
			int sx = selected == Selection.BELT ? beltX : selected == Selection.TOOL ? rx : rx + 20;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, sx - 1, y - 1, 24, 23);
		}
	}

	/** A hotbar-style frame for {@code count} slots, cut from the vanilla hotbar sprite. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int count) {
		int w = 1 + count * 20;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, x, y, w, 22);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, x + w, y, 1, 22);
	}

	private static void slot(GuiGraphicsExtractor g, Minecraft mc, int x, int y, ItemStack stack) {
		if (!stack.isEmpty()) {
			g.item(stack, x + 3, y + 3);
			g.itemDecorations(mc.font, stack, x + 3, y + 3);
		}
	}

	/** One dot per rack position above the slot: white = in the slot now, grey = filled, dark = empty. */
	private static void dots(GuiGraphicsExtractor g, ExtraSlots slots, Selection sel, int kind, int centerX, int y) {
		int n = ExtraSlots.rackSize(kind);
		int width = n * 2 - 1;
		int x = centerX - width / 2;
		int current = sel.vestiges$rackIndex(kind);
		for (int i = 0; i < n; i++) {
			boolean filled = !slots.getItem(ExtraSlots.rackStart(kind) + i).isEmpty();
			int color = i == current ? 0xFFFFFFFF : filled ? 0xFF9A9A9A : 0x66000000;
			g.fill(x + i * 2, y, x + i * 2 + 1, y + 1, color);
		}
	}
}
