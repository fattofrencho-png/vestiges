package dev.vestiges.tree;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which tree effects already work, and for the others which later system turns them on. The tree screen uses this so a node never promises
 * something the game does not do yet ("Not active yet: comes with Gear (M1c)").
 */
public final class TreeLabels {
	private TreeLabels() {
	}

	private static final String CODEX = "the Codex (M4)";
	private static final String FARMING = "farming and food (M4)";
	private static final String BUILDING = "settlements (M5)";
	private static final String LATER = "a later update";

	/** Effects that are not live yet, with the system that brings them. Everything else works today. */
	private static final Map<String, String> PENDING = Map.ofEntries(
		Map.entry("codex_gathering", CODEX), Map.entry("codex_xp", CODEX), Map.entry("lorekeeper", CODEX),
		Map.entry("crop_growth", FARMING), Map.entry("bonemeal", FARMING), Map.entry("baby_growth", FARMING), Map.entry("breeding_cooldown", FARMING),
		Map.entry("meal_duration", FARMING), Map.entry("food_restore", FARMING), Map.entry("saturation", FARMING),
		Map.entry("rebuilder", BUILDING), Map.entry("placing_speed", BUILDING), Map.entry("climb_speed", BUILDING), Map.entry("own_block_break", BUILDING),
		Map.entry("cartographer", LATER), Map.entry("fletcher", LATER), Map.entry("brewing_speed", LATER), Map.entry("wide_splash", LATER),
		Map.entry("potion_duration", LATER), Map.entry("drunk_potion_duration", LATER), Map.entry("potion_healing", LATER),
		Map.entry("parry_window", LATER), Map.entry("shield_raise", LATER), Map.entry("perfect_parry", LATER), Map.entry("shield_disable", LATER),
		Map.entry("trident_return", LATER), Map.entry("trident_throw", LATER), Map.entry("impaler", LATER), Map.entry("scythe_sweep", LATER),
		Map.entry("whirlwind", LATER), Map.entry("smash_radius", LATER), Map.entry("underwater_sight", LATER), Map.entry("vein_reader", LATER));

	/** Utility powers: switched on and off together with the V key. */
	public static final Set<String> UTILITY = Set.of("vein_miner", "lumberjack", "excavator", "earthmover", "reaper", "tiller", "strip_trunk",
		"cluster_harvest", "shearmaster", "trailmaker");

	public static boolean active(TreeDefs.Effect e) {
		return pendingFor(e) == null;
	}

	/** The system that will turn this effect on, or null when it already works. */
	public static String pendingFor(TreeDefs.Effect e) {
		String key = e.stat().orElseGet(() -> e.mechanic().orElse(""));
		return PENDING.get(key);
	}

	/** Every distinct "comes with ..." label among these effects (empty when they all work). */
	public static Set<String> pending(List<TreeDefs.Effect> effects) {
		Set<String> out = new LinkedHashSet<>();
		for (TreeDefs.Effect e : effects) {
			String p = pendingFor(e);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}
}
