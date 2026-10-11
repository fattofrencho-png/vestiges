package dev.vestiges.inventory;

/**
 * Added to the player's Inventory: which extra hotbar slot is selected (none, Tool, Weapon or Belt) and the position picked in each rack.
 * When one is selected, the main hand is that rack item (the vanilla hotbar index stays where it was).
 */
public interface Selection {
	int NONE = 0;
	int TOOL = 1;
	int WEAPON = 2;
	int BELT = 3;

	int vestiges$extra();

	/** Selects an extra slot (or NONE) without telling the other side. */
	void vestiges$setExtra(int kind);

	int vestiges$rackIndex(int kind);

	void vestiges$setRackIndex(int kind, int index);

	/** True when the second offhand slot is the active offhand (Alt + Scroll). */
	boolean vestiges$offhandB();

	void vestiges$setOffhandB(boolean active);

	/** The extra-slot index of what the main hand holds now, or -1 for the vanilla hotbar. */
	default int vestiges$heldExtraSlot() {
		int kind = vestiges$extra();
		int start = ExtraSlots.rackStart(kind);
		return start < 0 ? -1 : start + vestiges$rackIndex(kind);
	}
}
