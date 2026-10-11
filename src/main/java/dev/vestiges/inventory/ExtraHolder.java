package dev.vestiges.inventory;

import java.util.List;

/** Added to every Player: the extra slots, the Bank (emeralds), and the ender chest page names. */
public interface ExtraHolder {
	ExtraSlots vestiges$slots();

	long vestiges$bank();

	void vestiges$setBank(long emeralds);

	List<String> vestiges$pageNames();

	void vestiges$setPageNames(List<String> names);
}
