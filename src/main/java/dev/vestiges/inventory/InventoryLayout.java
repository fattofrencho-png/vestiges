package dev.vestiges.inventory;

/**
 * Where the extra slots sit in the survival inventory screen (layout A, approved): elytra and the second offhand above vanilla's offhand,
 * ender chest and totem under the crafting grid (the Bank is drawn next to them), and a right-hand panel with the tool rack, the weapon rack
 * plus arrows, and the tool belt. Coordinates use the vanilla inventory window's frame (it is 176 wide; the panel starts at 180).
 */
public final class InventoryLayout {
	public static final int PANEL_X = 180;
	public static final int PANEL_W = 84;
	public static final int WIDTH = PANEL_X + PANEL_W;
	public static final int BANK_X = 134;
	public static final int BANK_Y = 62;
	public static final int TOOLS_Y = 20;
	public static final int WEAPONS_Y = 66;
	public static final int BELT_Y = 118;

	private InventoryLayout() {
	}

	/** Item position (top-left of the 16x16 item) of an extra slot. */
	public static int[] pos(int slot) {
		int px = PANEL_X + 4;
		if (slot >= ExtraSlots.TOOLS && slot < ExtraSlots.TOOLS + ExtraSlots.TOOL_COUNT) {
			int i = slot - ExtraSlots.TOOLS;
			return new int[] {px + (i % 4) * 18, TOOLS_Y + (i / 4) * 18};
		}
		if (slot >= ExtraSlots.WEAPONS && slot < ExtraSlots.WEAPONS + ExtraSlots.WEAPON_COUNT) {
			int i = slot - ExtraSlots.WEAPONS;
			return new int[] {px + (i % 4) * 18, WEAPONS_Y + (i / 4) * 18};
		}
		if (slot >= ExtraSlots.BELT && slot < ExtraSlots.BELT + ExtraSlots.BELT_COUNT) {
			return new int[] {px + (slot - ExtraSlots.BELT) * 18, BELT_Y};
		}
		return switch (slot) {
			case ExtraSlots.ARROWS -> new int[] {px + 3 * 18, WEAPONS_Y + 18};
			case ExtraSlots.ELYTRA -> new int[] {77, 8};
			case ExtraSlots.OFFHAND2 -> new int[] {77, 44};
			case ExtraSlots.ENDER -> new int[] {98, 62};
			case ExtraSlots.TOTEM -> new int[] {116, 62};
			default -> new int[] {-2000, -2000};
		};
	}
}
