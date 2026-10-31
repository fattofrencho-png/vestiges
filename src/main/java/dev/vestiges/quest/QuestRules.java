package dev.vestiges.quest;

import dev.vestiges.config.VestigesSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The quest numbers and reputation rules, without any game classes. All draft numbers until the quest list is reviewed. */
public final class QuestRules {
	public static final int MAX_COMEBACK = 5;
	public static final long COMEBACK_DAYS = 7;
	public static final int MAX_SIDE_ACTIVE = 5;
	public static final double COMEBACK_SHARE = 0.10;
	public static final int SHARE_RADIUS = 32;
	public static final int MAX_REP = 100;
	/** Each group's rivals: they lose half of what the group gains. */
	public static final Map<String, Set<String>> RIVALS = Map.of(
		"bright_path", Set.of("vitera", "promise"),
		"primacy", Set.of("vitera", "promise"),
		"promise", Set.of("vitera", "bright_path", "primacy"),
		"vitera", Set.of("bright_path", "primacy", "promise"));
	/** Standing needed for each rank title, lowest first. */
	public static final int[] RANK_AT = {20, 40, 60, 80};
	public static final Map<String, List<String>> RANKS = Map.of(
		"bright_path", List.of("Spark", "Candle", "Lantern", "Beacon"),
		"primacy", List.of("Seeker", "Initiate", "Keeper", "Warden"),
		"promise", List.of("Listener", "Believer", "Reader", "Shepherd"));
	public static final Map<String, String> GROUP_NAMES = Map.of(
		"bright_path", "the Bright Path",
		"primacy", "the Primacy",
		"promise", "the Promise",
		"vitera", "the Vitera");

	/** Every step type the game can run. Escorts come with villagers (M6). */
	public static final Set<String> STEP_TYPES = Set.of("enchant", "breed", "fish", "craft", "use", "kill", "mine", "harvest", "structure", "biome", "new_biome", "waypoint",
		"go_there", "waystone_trip", "talk", "level", "codex", "fight_points", "craft_points", "rarity", "wear", "rack_tool", "rack_weapon", "hold_tool", "hold_weapon", "belt",
		"bank", "ascended", "familiar", "bestiary", "feat", "renown", "named_settlement", "ward", "bring", "choice");

	private QuestRules() {
	}

	/** The feature a step type can't work without, or null. A quest with such a step isn't offered while that feature is off. */
	public static VestigesSystem needs(String stepType) {
		return switch (stepType) {
			case "level" -> VestigesSystem.CHARACTER;
			case "fight_points", "craft_points", "ascended" -> VestigesSystem.PASSIVE_TREE;
			case "rarity" -> VestigesSystem.GEAR;
			case "codex", "familiar", "bestiary" -> VestigesSystem.CODEX;
			case "harvest" -> VestigesSystem.FARMING;
			case "feat" -> VestigesSystem.FEATS;
			case "renown", "named_settlement", "ward" -> VestigesSystem.SETTLEMENTS;
			case "waystone_trip" -> VestigesSystem.WAYSTONES;
			case "new_biome", "structure" -> VestigesSystem.CHARACTER;
			default -> null;
		};
	}

	/** A goal on comeback {@code step} (1 = the first time): 50% more per comeback. */
	public static int goal(int base, int step) {
		return (int) Math.ceil(base * (1 + 0.5 * (Math.min(step, MAX_COMEBACK) - 1)));
	}

	/** The XP a quest pays: flat plus a share of what the level needs. Side quests pay the small share from their first comeback. */
	public static long xp(int flat, double share, long levelNeed, boolean side, int step) {
		double used = side && step > 1 ? COMEBACK_SHARE : share;
		return side && step > 1 ? Math.round(used * levelNeed) : flat + Math.round(used * levelNeed);
	}

	public static int emeralds(int base, boolean side, int step) {
		return side && step > 1 ? Math.max(1, Math.min(3, base / 4)) : base;
	}

	/** Whether a finished side quest may be offered again. */
	public static boolean canReturn(long finishedDay, long today) {
		return today - finishedDay >= COMEBACK_DAYS;
	}

	/** Standing after a choice: its changes, plus rivals losing half of each gain unless the choice names them. Always within the scale. */
	public static Map<String, Integer> applyChoice(Map<String, Integer> standing, Map<String, Integer> changes) {
		Map<String, Integer> out = new HashMap<>(standing);
		for (Map.Entry<String, Integer> e : changes.entrySet()) {
			out.merge(e.getKey(), e.getValue(), Integer::sum);
			if (e.getValue() > 0) {
				for (String rival : RIVALS.getOrDefault(e.getKey(), Set.of())) {
					if (!changes.containsKey(rival)) {
						out.merge(rival, -e.getValue() / 2, Integer::sum);
					}
				}
			}
		}
		out.replaceAll((k, v) -> Math.max(-MAX_REP, Math.min(MAX_REP, v)));
		return out;
	}

	public static String band(int standing) {
		if (standing >= 60) {
			return "Trusted";
		}
		if (standing >= 20) {
			return "Known";
		}
		if (standing > -20) {
			return "Unknown";
		}
		return standing > -60 ? "Distrusted" : "Hunted";
	}

	/** The rank title a standing has earned with a group, or "" when it has none. */
	public static String rank(String group, int standing) {
		List<String> names = RANKS.get(group);
		if (names == null) {
			return "";
		}
		String best = "";
		for (int i = 0; i < RANK_AT.length; i++) {
			if (standing >= RANK_AT[i]) {
				best = names.get(i) + " of " + GROUP_NAMES.get(group);
			}
		}
		return best;
	}
}
