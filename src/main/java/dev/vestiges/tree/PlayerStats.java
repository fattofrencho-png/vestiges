package dev.vestiges.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The summed effect of a player's tree: stat totals and the mechanics (with their parameters) they own. */
public final class PlayerStats {
	public static final PlayerStats NONE = new PlayerStats();

	private final Map<String, Double> stats = new HashMap<>();
	private final Map<String, List<TreeDefs.Effect>> mechanics = new HashMap<>();

	/** Adds to a stat (gear and trims use this; the tree adds whole effects). */
	public void addStat(String stat, double value) {
		stats.merge(stat, value, Double::sum);
	}

	/** Everything from both, summed. Returns NONE when both are NONE. */
	public static PlayerStats merge(PlayerStats a, PlayerStats b) {
		if (b == NONE) {
			return a;
		}
		if (a == NONE) {
			return b;
		}
		PlayerStats out = new PlayerStats();
		for (PlayerStats p : new PlayerStats[] {a, b}) {
			p.stats.forEach(out::addStat);
			p.mechanics.forEach((k, v) -> out.mechanics.computeIfAbsent(k, x -> new ArrayList<>()).addAll(v));
		}
		return out;
	}

	void add(TreeDefs.Effect e) {
		e.stat().ifPresent(s -> stats.merge(s, e.value(), Double::sum));
		e.mechanic().ifPresent(m -> mechanics.computeIfAbsent(m, k -> new ArrayList<>()).add(e));
	}

	/** Total of a stat (0 when absent). Percent stats are in percent: 3 means 3%. */
	public double get(String stat) {
		Double v = stats.get(stat);
		return v != null ? v : 0.0;
	}

	/** 1 + stat/100, for percent multipliers. */
	public double mult(String stat) {
		return 1.0 + get(stat) / 100.0;
	}

	public boolean has(String mechanic) {
		return mechanics.containsKey(mechanic);
	}

	/** Every instance of a mechanic (several nodes can give the same one). */
	public List<TreeDefs.Effect> all(String mechanic) {
		return mechanics.getOrDefault(mechanic, List.of());
	}

	/** Sum of one parameter across every instance of a mechanic. */
	public double sum(String mechanic, String param) {
		double s = 0;
		for (TreeDefs.Effect e : all(mechanic)) {
			s += e.param(param, 0);
		}
		return s;
	}

	public Map<String, Double> stats() {
		return stats;
	}
}
