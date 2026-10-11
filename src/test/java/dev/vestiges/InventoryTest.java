package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryLayout;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The M2 slot positions (approved layout A). What each slot accepts is checked in game: 26.3 only gives items their data when a world
 * loads, so item stacks can't be built in a unit test.
 */
class InventoryTest {
	@Test
	void slotsNeverOverlap() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < ExtraSlots.SIZE; i++) {
			int[] p = InventoryLayout.pos(i);
			assertTrue(seen.add(p[0] + "," + p[1]), "slot " + i + " overlaps another");
			assertTrue(p[0] >= 0 && p[0] + 16 <= InventoryLayout.WIDTH, "slot " + i + " outside the window");
		}
		assertFalse(seen.contains(InventoryLayout.BANK_X + "," + InventoryLayout.BANK_Y), "the Bank sits on its own spot");
	}
}
