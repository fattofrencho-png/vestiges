package dev.vestiges.client.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.InventoryLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Drawing and clicks for the survival inventory's new parts: slot frames, the rack panel, the Bank and the ender chest slot. */
public final class InventoryPanel {
	private static final int LABEL = 0xFF404040;
	/** Made on first use: item stacks can't exist while the game is starting. */
	private static ItemStack emerald;

	private InventoryPanel() {
	}

	/** A vanilla-looking slot frame around the 16x16 item spot at (x, y). */
	private static void slotFrame(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
		g.fill(x - 1, y - 1, x + 17, y, 0xFF373737);
		g.fill(x - 1, y - 1, x, y + 17, 0xFF373737);
		g.fill(x - 1, y + 16, x + 17, y + 17, 0xFFFFFFFF);
		g.fill(x + 16, y - 1, x + 17, y + 17, 0xFFFFFFFF);
	}

	public static void background(GuiGraphicsExtractor g, int left, int top) {
		// The right-hand panel, in the same grey as the vanilla window.
		int x0 = left + InventoryLayout.PANEL_X;
		int x1 = x0 + InventoryLayout.PANEL_W;
		int y0 = top;
		int y1 = top + 166;
		g.fill(x0, y0, x1, y1, 0xFF000000);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
		g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
		g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
		g.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
		g.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
		for (int i = 0; i < ExtraSlots.SIZE; i++) {
			int[] p = InventoryLayout.pos(i);
			slotFrame(g, left + p[0], top + p[1]);
		}
		slotFrame(g, left + InventoryLayout.BANK_X, top + InventoryLayout.BANK_Y);
		// The offhand slot that is in your hand now (Alt + Scroll switches).
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			int[] active = InventoryHooks.selection(player).vestiges$offhandB() ? InventoryLayout.pos(ExtraSlots.OFFHAND2) : new int[] {77, 62};
			g.outline(left + active[0] - 2, top + active[1] - 2, 20, 20, 0xFFFFFFFF);
		}
		if (emerald == null) {
			emerald = new ItemStack(Items.EMERALD);
		}
		g.item(emerald, left + InventoryLayout.BANK_X, top + InventoryLayout.BANK_Y);
	}

	/** Drawn with the screen's labels (already moved to the window's corner). */
	public static void labels(GuiGraphicsExtractor g) {
		Font font = Minecraft.getInstance().font;
		int px = InventoryLayout.PANEL_X + 4;
		g.text(font, "Tools", px, InventoryLayout.TOOLS_Y - 10, LABEL, false);
		g.text(font, "Weapons", px, InventoryLayout.WEAPONS_Y - 10, LABEL, false);
		g.text(font, "Tool belt", px, InventoryLayout.BELT_Y - 10, LABEL, false);
		Player p = Minecraft.getInstance().player;
		if (p != null) {
			String total = shortCount(((ExtraHolder) p).vestiges$bank());
			int x = InventoryLayout.BANK_X + 17 - font.width(total);
			g.text(font, total, x, InventoryLayout.BANK_Y + 9, 0xFFFFFFFF, true);
		}
	}

	/** 1284 -> "1.2k" so it fits on the slot. */
	static String shortCount(long n) {
		if (n < 1000) {
			return Long.toString(n);
		}
		if (n < 1_000_000) {
			return String.format(Locale.ROOT, n < 10_000 ? "%.1fk" : "%.0fk", n / 1000.0);
		}
		return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
	}

	private static boolean over(int left, int top, int[] p, double mx, double my) {
		return mx >= left + p[0] - 1 && mx < left + p[0] + 17 && my >= top + p[1] - 1 && my < top + p[1] + 17;
	}

	public static void tooltips(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || !p.containerMenu.getCarried().isEmpty()) {
			return;
		}
		if (over(left, top, new int[] {InventoryLayout.BANK_X, InventoryLayout.BANK_Y}, mouseX, mouseY)) {
			long bank = ((ExtraHolder) p).vestiges$bank();
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal("Bank: " + String.format(Locale.ROOT, "%,d", bank) + (bank == 1 ? " emerald" : " emeralds")).withStyle(ChatFormatting.GREEN));
			lines.add(Component.literal("Drop emeralds here to keep them safe.").withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal("Left-click: take 64 · Right-click: take 1").withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal("Pays waystone trips, anvil costs and tree refunds.").withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.literal("Kept when you die.").withStyle(ChatFormatting.DARK_GRAY));
			g.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
			return;
		}
		ExtraSlots slots = InventoryHooks.slots(p);
		for (int i = 0; i < ExtraSlots.SIZE; i++) {
			if (over(left, top, InventoryLayout.pos(i), mouseX, mouseY)) {
				if (i == ExtraSlots.ENDER && !slots.getItem(i).isEmpty()) {
					return;
				}
				if (slots.getItem(i).isEmpty()) {
					List<Component> lines = new ArrayList<>();
					lines.add(Component.literal(ExtraSlots.NAMES[i] + " slot").withStyle(ChatFormatting.GRAY));
					String hint = hint(i);
					if (!hint.isEmpty()) {
						lines.add(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
					}
					g.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
				}
				return;
			}
		}
	}

	private static String hint(int slot) {
		return switch (slot) {
			case ExtraSlots.ENDER -> "Put an ender chest here, then right-click it to open your ender chest.";
			case ExtraSlots.TOTEM -> "A totem here saves you, without holding it.";
			case ExtraSlots.ELYTRA -> "Glide with it while wearing a chestplate.";
			case ExtraSlots.OFFHAND2 -> "Alt + Scroll picks which offhand slot is in your hand (white outline).";
			case ExtraSlots.ARROWS -> "Bows and crossbows shoot these first.";
			case ExtraSlots.BELT -> "On the belt: N/E/S/W around the minimap.";
			case ExtraSlots.BELT + 1 -> "On the belt: the time of day under the minimap.";
			case ExtraSlots.BELT + 2 -> "On the belt: shows the minimap.";
			case ExtraSlots.BELT + 3 -> "On the belt: hold the zoom key to zoom.";
			default -> "";
		};
	}

	/** Bank clicks and right-click on a filled ender chest slot. Returns true when handled. */
	public static boolean click(int left, int top, MouseButtonEvent event) {
		Player p = Minecraft.getInstance().player;
		if (p == null || !ClientInventory.on()) {
			return false;
		}
		ItemStack carried = p.containerMenu.getCarried();
		if (over(left, top, new int[] {InventoryLayout.BANK_X, InventoryLayout.BANK_Y}, event.x(), event.y())) {
			if (carried.is(Items.EMERALD)) {
				ClientInventory.send("bank_deposit", 0, 0, "");
			} else if (carried.isEmpty() || carried.is(Items.EMERALD)) {
				ClientInventory.send("bank_withdraw", 0, event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 64, "");
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && carried.isEmpty()
			&& over(left, top, InventoryLayout.pos(ExtraSlots.ENDER), event.x(), event.y())
			&& InventoryHooks.slots(p).getItem(ExtraSlots.ENDER).is(Items.ENDER_CHEST)) {
			ClientInventory.send("ender_open", 0, 0, "");
			return true;
		}
		return false;
	}
}
