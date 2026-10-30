package dev.vestiges.settlement;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The earned markers (approved): each is a vanilla block with a fixed radius baked in. Bells found settlements (radius from renown); Banners
 * name an area; Ward Lanterns make a no-spawn pocket; the Gold Block loads its chunk and the 8 around it. Setting up = minutes without fighting.
 */
public enum MarkerType {
	BELL("bell", Items.BELL, "Settlement Bell", 0, 3, 10 * 60 * 20),
	BANNER16("banner16", Items.BANNER.white(), "Banner (radius 16)", 16, 20, 0),
	BANNER48("banner48", Items.BANNER.white(), "Great Banner (radius 48)", 48, 20, 0),
	WARD6("ward6", Items.LANTERN, "Ward Lantern (radius 6)", 6, 10, 5 * 60 * 20),
	WARD12("ward12", Items.LANTERN, "Ward Lantern (radius 12)", 12, 10, 5 * 60 * 20),
	WARD30("ward30", Items.LANTERN, "Ward Lantern (radius 30)", 30, 10, 5 * 60 * 20),
	GOLD_BLOCK("gold_block", Items.GOLD_BLOCK, "Gold Block (chunk loader)", 24, 3, 0);

	public final String id;
	public final Item item;
	public final String label;
	public final int radius;
	/** Most a player can have active at once (Banners share one limit, so do Ward Lanterns). */
	public final int limit;
	public final int setupTicks;

	MarkerType(String id, Item item, String label, int radius, int limit, int setupTicks) {
		this.id = id;
		this.item = item;
		this.label = label;
		this.radius = radius;
		this.limit = limit;
		this.setupTicks = setupTicks;
	}

	public boolean banner() {
		return this == BANNER16 || this == BANNER48;
	}

	public boolean ward() {
		return this == WARD6 || this == WARD12 || this == WARD30;
	}

	/** Banners count together, Ward Lanterns count together. */
	public String family() {
		return banner() ? "banner" : ward() ? "ward" : id;
	}

	public static MarkerType byId(String id) {
		for (MarkerType t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}
}
