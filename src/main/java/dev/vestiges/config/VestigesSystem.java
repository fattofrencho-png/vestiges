package dev.vestiges.config;

/** Every system a server can switch off. The id is what the config file and {@code /vestiges config} use. */
public enum VestigesSystem {
	CHARACTER("character", "Levels and character stats"),
	PASSIVE_TREE("passive_tree", "Fight and Craft wheels, ascendancy"),
	INVENTORY("inventory", "New slots, racks and the bank"),
	MOB_SCALING("mob_scaling", "Mob levels by layer"),
	ELITES("elites", "Champions, Elites and Warlords"),
	LOOT("loot", "Rarity and banded loot"),
	CODEX("codex", "Block Codex and Bestiary"),
	FARMING("farming", "Farming quality of life"),
	FEATS("feats", "Feats and earned markers"),
	SETTLEMENTS("settlements", "Bells, renown, wards and banners"),
	VILLAGERS("villagers", "Villager library and screen"),
	TRADES("trades", "Vestiges trade system"),
	WORKERS("workers", "Worker jobs"),
	DUNGEONS("dungeons", "Portal dungeons and the Gate tree"),
	EVENTS("events", "World events"),
	NETHER_POCKETS("nether_pockets", "Nether pockets in deepslate"),
	DRAGONS("dragons", "The 24 dragons and the Vestige"),
	BLUEPRINTS("blueprints", "Blueprints and ghost building"),
	MAP("map", "World map and waystones");

	public final String id;
	public final String label;

	VestigesSystem(String id, String label) {
		this.id = id;
		this.label = label;
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
