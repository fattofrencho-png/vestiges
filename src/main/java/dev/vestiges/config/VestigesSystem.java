package dev.vestiges.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Every feature a world can switch on or off, and the two it can't (the core). The id is what the config file and {@code /vestiges config}
 * use; label and help are shown on the welcome page.
 */
public enum VestigesSystem {
	INVENTORY("inventory", "New inventory", Category.CORE, F.BUILT, "The new window: racks, tool belt, Bank and the extra slots.\n\nThis is the core of Vestiges, so it stays on."),
	MAP("map", "Map", Category.CORE, F.BUILT, "Minimap, world map, waypoints, layers and markers.\n\nThis is the core of Vestiges, so it stays on."),

	CHARACTER("character", "Levels and XP", Category.CHARACTER, F.BUILT, "Levels 1-100 and Paragon, percentage hearts and the XP readout.\n\nOff: vanilla hearts and vanilla XP."),
	PASSIVE_TREE("passive_tree", "Passive trees", Category.CHARACTER, F.BUILT, "The Fight and Craft wheels and the ascendancies.", "character"),
	GEAR("gear", "Gear rarity", Category.CHARACTER, F.BUILT, "Rarity colours, enchant costs, the gear ladder and trim stats."),
	ENCHANT_LEVELS("enchant_levels", "Enchant levels I-VI", Category.CHARACTER, F.BUILT | F.CREATION_ONLY, "All enchants rescaled to levels I to VI.\n\nOnly chosen now. Items keep their levels, so it can't change once the world exists."),

	MOB_SCALING("mob_scaling", "Mob levels", Category.MOBS, F.BUILT, "Monsters level up with you and with depth.\n\nOff: vanilla monsters."),
	ELITES("elites", "Elites", Category.MOBS, F.BUILT, "Champions, Elites and Warlords, with their powers.", "mob_scaling"),
	LOOT("loot", "Banded loot", Category.MOBS, F.BUILT, "Chest and mob loot scale with your level, and gear has rarity.\n\nOff: vanilla loot.", "enchant_levels"),
	PER_PLAYER_LOOT("per_player_loot", "Per-player chests", Category.MOBS, F.BUILT, "Each player gets their own loot from a natural chest."),

	CODEX("codex", "Codex", Category.WORLD, F.BUILT, "Blocks, Bestiary, Places and Items."),
	FEATS("feats", "Feats", Category.WORLD, F.BUILT, "Goals that earn Bells, Banners, Ward Lanterns and more."),
	SETTLEMENTS("settlements", "Settlements", Category.WORLD, F.BUILT, "Bells, renown, wards, banners and land protection.", "feats"),
	WAYSTONES("waystones", "Waystones", Category.WORLD, F.BUILT, "Earned fast travel between lodestones, paid in emeralds.", "feats"),
	VILLAGERS("villagers", "Villagers", Category.WORLD, 0, "Library, villager screen and recruiting."),
	TRADES("trades", "Trade system", Category.WORLD, 0, "Our trades replace vanilla trading.", "villagers"),
	WORKERS("workers", "Workers", Category.WORLD, 0, "Jobs that make any item in your settlement.", "villagers", "settlements"),
	DUNGEONS("dungeons", "Dungeons", Category.WORLD, 0, "Portal runs and the Gate tree."),
	EVENTS("events", "World events", Category.WORLD, 0, "Blood Moon, caravans, shrines and more."),
	NETHER_POCKETS("nether_pockets", "Nether pockets", Category.WORLD, 0, "Rare Nether caves in deepslate."),
	DRAGONS("dragons", "24 dragons", Category.WORLD, 0, "The End, the Vestige and the story's ending."),
	BLUEPRINTS("blueprints", "Blueprints", Category.WORLD, 0, "Save builds and fill in ghost copies."),

	FARMING("farming", "Farming help", Category.COMFORT, F.BUILT | F.COMFORT, "Right-click harvest, no trampling, growth info and meal buffs."),
	SORTING("sorting", "Sorting and locks", Category.COMFORT, F.COMFORT, "Sort buttons, move all, locked slots."),
	STACKS("stacks", "Bigger stacks", Category.COMFORT, F.COMFORT, "Stackable items go to 99. Potions and stews stack to 16."),
	SHAPES("shapes", "Ctrl + Alt shapes", Category.COMFORT, F.COMFORT, "Scroll an item to swap it for related shapes, at the normal recipe cost. You can undo."),
	SHULKERS("shulkers", "Open shulker boxes", Category.COMFORT, F.COMFORT, "Right-click a box in your inventory to open it."),
	SLEEP("sleep", "Sleep percentage", Category.COMFORT, F.BUILT | F.OFF_BY_DEFAULT, "Fewer players need to sleep to skip the night. Half by default; operators can change it in Settings, World."),
	GRIEFING("griefing", "No mob griefing", Category.COMFORT, F.BUILT | F.OFF_BY_DEFAULT, "No creeper holes, no endermen moving blocks. This is vanilla's mobGriefing rule, so villagers can't farm while it is on."),
	PEEK("peek", "Peek", Category.COMFORT, F.COMFORT, "See inside containers you could open, without opening them."),
	FREECAM("freecam", "Freecam", Category.COMFORT, F.COMFORT, "Free flying camera, through walls too.\n\nIn survival it works like x-ray, so turn it off where that matters.");

	/** Groups on the welcome page. */
	public enum Category {
		CORE("Core"), CHARACTER("Character"), MOBS("Mobs and loot"), WORLD("World"), COMFORT("Comfort");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	/** Ready-made choices on the welcome page. */
	public enum Preset {
		CORE("Core", "Vanilla, plus the new inventory and map."),
		COMFORT("Comfort", "Core plus quality-of-life. No levels, no RPG."),
		FULL("Full RPG", "Everything.");

		public final String label;
		public final String help;

		Preset(String label, String help) {
			this.label = label;
			this.help = help;
		}
	}

	private static final class F {
		static final int BUILT = 1;
		static final int COMFORT = 2;
		static final int CREATION_ONLY = 4;
		static final int OFF_BY_DEFAULT = 8;
	}

	public final String id;
	public final String label;
	public final Category category;
	public final String help;
	private final int flags;
	private final String[] needs;

	VestigesSystem(String id, String label, Category category, int flags, String help, String... needs) {
		this.id = id;
		this.label = label;
		this.category = category;
		this.flags = flags;
		this.help = help;
		this.needs = needs;
	}

	/** Always on: can't be switched off, in any preset or file. */
	public boolean core() {
		return category == Category.CORE;
	}

	/** False while the feature doesn't exist yet: its switch is kept and shown as "later". */
	public boolean built() {
		return (flags & F.BUILT) != 0;
	}

	/** Can only be chosen when the world is created. */
	public boolean creationOnly() {
		return (flags & F.CREATION_ONLY) != 0;
	}

	public boolean onByDefault() {
		return (flags & F.OFF_BY_DEFAULT) == 0;
	}

	public boolean inPreset(Preset preset) {
		return core() || switch (preset) {
			case CORE -> false;
			case COMFORT -> (flags & F.COMFORT) != 0;
			case FULL -> onByDefault();
		};
	}

	/** The features this one can't work without. */
	public List<VestigesSystem> needs() {
		List<VestigesSystem> out = new ArrayList<>();
		for (String n : needs) {
			out.add(byId(n));
		}
		return out;
	}

	public static VestigesSystem byId(String id) {
		for (VestigesSystem s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}
}
