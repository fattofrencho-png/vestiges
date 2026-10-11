package dev.vestiges.inventory;

/** Added to PlayerEquipment: while raw, the offhand is the real vanilla slot (inventory screen access), not the second offhand slot. */
public interface RawEquipment {
	void vestiges$setRaw(boolean raw);
}
