package dev.vestiges.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Every feature a world can switch on or off, and the two it can't (the core). The id is what the config file and {@code /vestiges config}
 * use; label and help are shown on the welcome page.
 */
public enum VestigesSystem {
	INVENTORY("inventory", "New inventory", Category.CORE, F.BUILT, "The new window: racks, tool belt, Bank and the extra slots.\n\nEverything else is built on this, so it stays on."),
	MAP("map", "Map", Category.CORE, F.BUILT, "Minimap, world map, waypoints, layers and markers.\n\nEverything else is built on this, so it stays on."),

	CHARACTER("character", "Levels and XP", Category.CHARACTER, F.IN_CORE | F.BUILT, "Levels 1-100 and Paragon, percentage hearts and the XP readout.\n\nOff: vanilla hearts and vanilla XP."),
	PASSIVE_TREE("passive_tree", "Passive trees", Category.CHARACTER, F.IN_CORE | F.BUILT, "The Fight and Craft wheels and the ascendancies.", "character"),
	GEAR("gear", "Gear rarity", Category.CHARACTER, F.IN_CORE | F.BUILT, "Rarity colours, enchant costs, the gear ladder and trim stats."),
	ENCHANT_LEVELS("enchant_levels", "Enchant levels I-VI", Category.LOOT, F.IN_CORE | F.BUILT | F.CREATION_ONLY, "All enchants rescaled to levels I to VI.\n\nOnly chosen now. Items keep their levels, so it can't change once the world exists."),

	MOB_SCALING("mob_scaling", "Mob levels", Category.MOBS, F.IN_CORE | F.BUILT, "Monsters level up with you and with depth.\n\nOff: vanilla monsters."),
	ELITES("elites", "Elites", Category.MOBS, F.IN_CORE | F.BUILT, "Champions, Elites and Warlords, with their powers.", "mob_scaling"),
	LOOT("loot", "Banded loot", Category.LOOT, F.IN_CORE | F.BUILT, "Chest and mob loot scale with your level, and gear has rarity.\n\nOff: vanilla loot.", "enchant_levels"),
	PER_PLAYER_LOOT("per_player_loot", "Per-player chests", Category.LOOT, F.IN_CORE | F.BUILT, "Each player gets their own loot from a natural chest."),

	CONTINENTS("continents", "Continent of Trenec", Category.WORLD, F.BUILT | F.CREATION_ONLY | F.OFF_BY_DEFAULT, "A world made for the story. You start on the continent of Trenec, with four more continents across the sea, real rivers, taller mountains, bigger caves and the capital Dilecta. Emerald ore only exists at a few rich mines.\n\nOff: vanilla terrain, where the places in the story may not match the land.\n\nOnly chosen now. It is the same as picking Vestiges Vanilla under World Type, on the World tab."),
	QUESTS("quests", "Quests", Category.WORLD, F.IN_CORE | F.BUILT, "Optional quests: guide quests that teach the mod, side quests from villagers, and notes you find. Rewards never include power you can't get elsewhere."),
	CODEX("codex", "Codex", Category.WORLD, F.IN_CORE | F.BUILT, "Blocks, Bestiary, Places and Items."),
	FEATS("feats", "Feats", Category.WORLD, F.IN_CORE | F.BUILT, "Goals that earn Bells, Banners, Ward Lanterns and more."),
	SETTLEMENTS("settlements", "Settlements", Category.WORLD, F.IN_CORE | F.BUILT, "Bells, renown, wards, banners and land protection.", "feats"),
	WAYSTONES("waystones", "Waystones", Category.WORLD, F.IN_CORE | F.BUILT, "Earned fast travel between lodestones, paid in emeralds.", "feats"),
	VILLAGERS("villagers", "Villagers", Category.WORLD, F.IN_CORE | F.BUILT, "Every villager gets a name and a story, and the library remembers each one you meet.\n\nOff: villagers stay as vanilla made them. Recruiting comes in a later update."),
	TRADES("trades", "Trade system", Category.WORLD, F.IN_CORE | F.BUILT, "Right-click opens the villager screen. Villagers sell for emeralds, and what they sell depends on your level.\n\nOff: vanilla trading.", "villagers"),
	WORKERS("workers", "Workers", Category.WORLD, 0, "Jobs that make any item in your settlement.", "settlements", "villagers"),
	DUNGEONS("dungeons", "Dungeons", Category.WORLD, F.IN_CORE, "Portal runs and the Gate tree."),
	EVENTS("events", "World events", Category.WORLD, 0, "Blood Moon, caravans, shrines and more."),
	NETHER_POCKETS("nether_pockets", "Nether pockets", Category.WORLD, 0, "Rare Nether caves in deepslate."),
	DRAGONS("dragons", "24 dragons", Category.WORLD, F.IN_CORE, "The End, the Vestige and the story's ending."),
	BLUEPRINTS("blueprints", "Blueprints", Category.WORLD, 0, "Save builds and fill in ghost copies."),

	FARMING("farming", "Farming help", Category.COMFORT, F.IN_CORE | F.BUILT, "Right-click harvest, no trampling, growth info and meal buffs."),
	SORTING("sorting", "Sorting and hotbar sets", Category.COMFORT, F.COMFORT | F.BUILT, "Sort buttons, move all, and three saved hotbars you switch between with a key."),
	STACKS("stacks", "Bigger stacks", Category.COMFORT, F.COMFORT | F.BUILT, "Everything stackable goes to 64, or 99 with the option in Settings, World. Potions, stews, filled buckets, enchanted books, and empty minecarts and boats stack to 16."),
	SHAPES("shapes", "Ctrl + Alt shapes", Category.COMFORT, F.COMFORT, "Scroll an item to swap it for related shapes, at the normal recipe cost. You can undo."),
	SHULKERS("shulkers", "Open shulker boxes", Category.COMFORT, F.COMFORT | F.BUILT, "Right-click a box in your inventory to open it."),
	SLEEP("sleep", "Sleep percentage", Category.COMFORT, F.BUILT | F.OFF_BY_DEFAULT, "Fewer players need to sleep to skip the night. Half by default; operators can change it in Settings, World."),
	GRAVES("graves", "Gravestones", Category.COMFORT, F.IN_CORE | F.BUILT, "When you die, your things rest in a gravestone where you fell, and only you can collect them.\n\nOff: vanilla, your things drop where you died."),
	GRAVE_TIMER("grave_timer", "Gravestone timer", Category.COMFORT, F.IN_CORE | F.BUILT, "The gravestone crumbles after a number of days (set in Settings, World), leaving a skull with your most valuable item.\n\nOff: the gravestone stays until you collect it.", "graves"),
	GRIEFING("griefing", "No mob griefing", Category.COMFORT, F.BUILT | F.OFF_BY_DEFAULT, "No creeper holes, no endermen moving blocks. This is vanilla's mobGriefing rule, so villagers can't farm while it is on."),
	PEEK("peek", "Peek", Category.COMFORT, F.COMFORT, "See inside containers you could open, without opening them."),
	FREECAM("freecam", "Freecam", Category.COMFORT, F.OFF_BY_DEFAULT, "Free flying camera, through walls too.\n\nIn survival it works like x-ray, so turn it off where that matters.");

	/** Groups on the welcome page. */
	public enum Category {
		CORE("Foundation"), CHARACTER("Character"), LOOT("Loot and enchants"), MOBS("Mobs"), WORLD("World"), COMFORT("Comfort");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	/** Ready-made choices on the welcome page. Each one includes the one before it. */
	public enum Preset {
		CORE("RPG", "The RPG: levels, trees, gear, mobs, loot, Codex, feats, settlements, villagers, dungeons and dragons."),
		COMFORT("Comfort", "Core plus quality of life: sorting, bigger stacks, shapes, shulker boxes and peek."),
		FULL("Full RPG", "Comfort plus workers, world events, Nether pockets and blueprints.");

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
		static final int IN_CORE = 16;
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
		return core() || onByDefault() && firstPreset().ordinal() <= preset.ordinal();
	}

	/** The smallest preset that has this feature. */
	private Preset firstPreset() {
		return (flags & F.IN_CORE) != 0 ? Preset.CORE : (flags & F.COMFORT) != 0 ? Preset.COMFORT : Preset.FULL;
	}

	/** The features this one can't work without. */
	public List<VestigesSystem> needs() {
		List<VestigesSystem> out = new ArrayList<>();
		for (String n : needs) {
			out.add(byId(n));
		}
		return out;
	}

	/** The feature this one is shown under: the first one it needs in the same group, or null when it stands alone. */
	public VestigesSystem parent() {
		for (String n : needs) {
			VestigesSystem p = byId(n);
			if (p.category == category) {
				return p;
			}
		}
		return null;
	}

	/** How many levels deep it is shown under its parents. */
	public int depth() {
		int d = 0;
		for (VestigesSystem p = parent(); p != null; p = p.parent()) {
			d++;
		}
		return d;
	}

	private static final List<String> SECTIONS = List.of("Inventory", "In the world", "Server rules");

	/** The heading a stand-alone switch is listed under inside its group (Comfort only), or null. */
	public String section() {
		return switch (id) {
			case "sorting", "stacks", "shapes", "shulkers", "peek" -> "Inventory";
			case "farming", "freecam", "graves" -> "In the world";
			case "sleep", "griefing" -> "Server rules";
			default -> null;
		};
	}

	/** A group's switches in the order they are listed: sections together, each parent followed by the features that need it. */
	public static List<VestigesSystem> listed(Category category) {
		List<VestigesSystem> roots = new ArrayList<>();
		for (VestigesSystem s : values()) {
			if (s.category == category && s.parent() == null) {
				roots.add(s);
			}
		}
		roots.sort(java.util.Comparator.comparingInt(s -> s.section() == null ? -1 : SECTIONS.indexOf(s.section())));
		List<VestigesSystem> out = new ArrayList<>();
		for (VestigesSystem r : roots) {
			addWithChildren(r, out);
		}
		return out;
	}

	private static void addWithChildren(VestigesSystem s, List<VestigesSystem> out) {
		out.add(s);
		for (VestigesSystem c : values()) {
			if (c.parent() == s) {
				addWithChildren(c, out);
			}
		}
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
