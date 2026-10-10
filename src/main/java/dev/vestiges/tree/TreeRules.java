package dev.vestiges.tree;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure tree rules (unit-tested): point budgets, refund cost, adjacency and "stays connected" checks. */
public final class TreeRules {
	private TreeRules() {
	}

	/** Fight wheel: 1 point per level. */
	public static int fightPoints(int level) {
		return Math.max(0, level);
	}

	/** Craft wheel: 1 point every 2 levels, plus the Codex bonus points (Codex arrives in M4). */
	public static int craftPoints(int level, int codexPoints) {
		return Math.max(0, level / 2) + Math.max(0, codexPoints);
	}

	/** Refunding one node, or switching a choice: 1 emerald + 1 per 10 levels. */
	public static int refundCost(int level) {
		return 1 + Math.max(0, level) / 10;
	}

	public static Map<String, Set<String>> adjacency(Collection<List<String>> edges) {
		Map<String, Set<String>> adj = new HashMap<>();
		for (List<String> e : edges) {
			if (e.size() < 2) {
				continue;
			}
			adj.computeIfAbsent(e.get(0), k -> new HashSet<>()).add(e.get(1));
			adj.computeIfAbsent(e.get(1), k -> new HashSet<>()).add(e.get(0));
		}
		return adj;
	}

	/** A node can be taken when it touches the start or an already taken node. */
	public static boolean canReach(String node, String startId, Set<String> taken, Map<String, Set<String>> adj) {
		for (String n : adj.getOrDefault(node, Set.of())) {
			if (n.equals(startId) || taken.contains(n)) {
				return true;
			}
		}
		return false;
	}

	/** After removing {@code node}, are all remaining taken nodes still linked to the start? */
	public static boolean stillConnected(String node, String startId, Set<String> taken, Map<String, Set<String>> adj) {
		Set<String> remaining = new HashSet<>(taken);
		remaining.remove(node);
		Set<String> seen = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(startId);
		seen.add(startId);
		while (!queue.isEmpty()) {
			String cur = queue.poll();
			for (String n : adj.getOrDefault(cur, Set.of())) {
				if (remaining.contains(n) && seen.add(n)) {
					queue.add(n);
				}
			}
		}
		for (String r : remaining) {
			if (!seen.contains(r)) {
				return false;
			}
		}
		return true;
	}
}
