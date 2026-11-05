package dev.vestiges.inventory;

/**
 * Where the extra slots sit in the survival inventory screen (layout A, approved): elytra and the second offhand above vanilla's offhand,
 * crafting table, ender chest and totem under the crafting grid, and a right-hand panel with the tool rack, the weapon rack plus arrows, the
 * tool belt and the Bank. Coordinates use the vanilla inventory window's frame (it is 176 wide; the panel starts at 180).
 */
public final class InventoryLayout {
	public static final int PANEL_X = 180;
	public static final int PANEL_W = 84;
	public static final int WIDTH = PANEL_X + PANEL_W;
	public static final int BANK_X = 206;
	public static final int BANK_Y = 142;
	public static final int TOOLS_Y = 20;
	public static final int WEAPONS_Y = 66;
	public static final int BELT_Y = 118;
	/** The Sort button, under the crafting result. */
	public static final int SORT_X = 157;
	public static final int SORT_Y = 66;
	public static final int SORT_SIZE = 11;
	/** The 3x3 grid and its result, while a crafting table is in its slot. */
	public static final int GRID_X = 98;
	public static final int GRID_Y = 7;
	public static final int GRID_STEP = 17;
	public static final int RESULT_X = 154;
	public static final int RESULT_Y = 24;
	/** The recipe book's width and the gap between it and the window. */
	public static final int BOOK_W = 147;
	public static final int BOOK_GAP = 2;
	/** The narrowest screen that fits the book (with its tabs, 30 wide) beside the whole window. */
	public static final int BOOK_MIN_SCREEN = WIDTH + BOOK_W + BOOK_GAP + 30;

	private InventoryLayout() {
	}

	/** Left edge of the inventory window: the window alone, or the book and the window together, are centred on the screen. */
	public static int windowLeft(int screenWidth, boolean bookOpen) {
		return bookOpen ? (screenWidth - WIDTH - BOOK_W - BOOK_GAP) / 2 + BOOK_W + BOOK_GAP : (screenWidth - WIDTH) / 2;
	}

	/**
	 * The screen width to tell the recipe book, which draws itself at (width - 147) / 2 - 86: chosen so it lands right beside the window.
	 */
	public static int bookScreenWidth(int screenWidth) {
		return screenWidth - WIDTH - BOOK_GAP + 2 * 86;
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
			case ExtraSlots.TABLE -> new int[] {98, 62};
			case ExtraSlots.ENDER -> new int[] {115, 62};
			case ExtraSlots.TOTEM -> new int[] {132, 62};
			default -> new int[] {-2000, -2000};
		};
	}
}
