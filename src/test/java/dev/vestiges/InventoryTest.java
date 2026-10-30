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

	@Test
	void windowAndRecipeBookStayCentred() {
		int screen = 853;
		int alone = dev.vestiges.inventory.InventoryLayout.windowLeft(screen, false);
		org.junit.jupiter.api.Assertions.assertEquals(screen / 2.0, alone + dev.vestiges.inventory.InventoryLayout.WIDTH / 2.0, 1.0);

		int left = dev.vestiges.inventory.InventoryLayout.windowLeft(screen, true);
		// The book draws itself at (width - 147) / 2 - 86, here with the width the mixin gives it.
		int bookLeft = (dev.vestiges.inventory.InventoryLayout.bookScreenWidth(screen) - 147) / 2 - 86;
		org.junit.jupiter.api.Assertions.assertEquals(left - dev.vestiges.inventory.InventoryLayout.BOOK_GAP - 147, bookLeft, 1.0, "the book touches the window");
		org.junit.jupiter.api.Assertions.assertEquals(screen / 2.0, (bookLeft + left + dev.vestiges.inventory.InventoryLayout.WIDTH) / 2.0, 1.0, "both together are centred");
	}
}
