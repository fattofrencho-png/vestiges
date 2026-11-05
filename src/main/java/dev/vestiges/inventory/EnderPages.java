package dev.vestiges.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The extra ender chest pages of one player: page 1 is the vanilla ender chest (so ender chest blocks and everything else that knows it keep
 * working), pages 2 to 25 are the 27-slot containers kept here. A page is unlocked by a dragon kill; the items of a page stay in it even when the
 * page cannot be opened (the Dragons switch off). Saved with the player and kept through death, like the ender chest itself.
 */
public final class EnderPages {
	public static final int SLOTS = 27;

	private final SimpleContainer[] pages = new SimpleContainer[InventoryService.PAGES];

	/** One saved page: its index and its 27 slots. */
	public record Saved(int page, List<ItemStack> items) {
		public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("page").forGetter(Saved::page),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Saved::items)
		).apply(i, Saved::new));
	}

	public static final Codec<List<Saved>> CODEC = Saved.CODEC.listOf();

	/** The container of page {@code page} (1 to 24; 0 is the vanilla ender chest and is not kept here). */
	public SimpleContainer container(int page) {
		if (page < 1 || page >= pages.length) {
			throw new IllegalArgumentException("page " + page);
		}
		if (pages[page] == null) {
			pages[page] = new SimpleContainer(SLOTS);
		}
		return pages[page];
	}

	public boolean owns(Container c) {
		for (SimpleContainer p : pages) {
			if (p == c) {
				return true;
			}
		}
		return false;
	}

	/** Only the pages that hold something. */
	public List<Saved> toList() {
		List<Saved> out = new ArrayList<>();
		for (int page = 1; page < pages.length; page++) {
			SimpleContainer c = pages[page];
			if (c != null && !c.isEmpty()) {
				List<ItemStack> items = new ArrayList<>();
				for (int i = 0; i < SLOTS; i++) {
					items.add(c.getItem(i).copy());
				}
				out.add(new Saved(page, items));
			}
		}
		return out;
	}

	public void fromList(List<Saved> saved) {
		for (int page = 1; page < pages.length; page++) {
			pages[page] = null;
		}
		for (Saved s : saved) {
			if (s.page() >= 1 && s.page() < pages.length) {
				SimpleContainer c = container(s.page());
				for (int i = 0; i < Math.min(SLOTS, s.items().size()); i++) {
					c.setItem(i, s.items().get(i).copy());
				}
			}
		}
	}

	public void copyFrom(EnderPages other) {
		fromList(other.toList());
	}
}
