package dev.vestiges.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's tree choices: per wheel the chosen start, taken nodes and choice picks; the ascendancy, how many times they ascended and its
 * taken nodes; the counters behind the ascension checklists; and whether utility powers are on (key V). Immutable.
 */
public record PlayerTree(WheelState fight, WheelState craft, String ascendancy, int ascensions, Set<String> ascTaken, Map<String, Long> counters,
						 boolean utilityOn) {
	public static final PlayerTree EMPTY = new PlayerTree(WheelState.EMPTY, WheelState.EMPTY, "", 0, Set.of(), Map.of(), true);

	private static <T> Codec<Set<T>> setOf(Codec<T> c) {
		return c.listOf().xmap(l -> (Set<T>) new HashSet<>(l), s -> List.copyOf(s));
	}

	public record WheelState(int start, Set<String> taken, Map<String, Integer> choices) {
		public static final WheelState EMPTY = new WheelState(-1, Set.of(), Map.of());
		public static final Codec<WheelState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("start", -1).forGetter(WheelState::start),
			setOf(Codec.STRING).optionalFieldOf("taken", Set.of()).forGetter(WheelState::taken),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("choices", Map.of()).forGetter(WheelState::choices)
		).apply(i, WheelState::new));

		public WheelState withStart(int s) {
			return new WheelState(s, taken, choices);
		}

		public WheelState take(String id, Integer choice) {
			Set<String> t = new HashSet<>(taken);
			t.add(id);
			Map<String, Integer> c = new HashMap<>(choices);
			if (choice != null) {
				c.put(id, choice);
			}
			return new WheelState(start, Set.copyOf(t), Map.copyOf(c));
		}

		public WheelState drop(String id) {
			Set<String> t = new HashSet<>(taken);
			t.remove(id);
			Map<String, Integer> c = new HashMap<>(choices);
			c.remove(id);
			return new WheelState(start, Set.copyOf(t), Map.copyOf(c));
		}

		public WheelState choose(String id, int choice) {
			Map<String, Integer> c = new HashMap<>(choices);
			c.put(id, choice);
			return new WheelState(start, taken, Map.copyOf(c));
		}
	}

	public static final Codec<PlayerTree> CODEC = RecordCodecBuilder.create(i -> i.group(
		WheelState.CODEC.optionalFieldOf("fight", WheelState.EMPTY).forGetter(PlayerTree::fight),
		WheelState.CODEC.optionalFieldOf("craft", WheelState.EMPTY).forGetter(PlayerTree::craft),
		Codec.STRING.optionalFieldOf("ascendancy", "").forGetter(PlayerTree::ascendancy),
		Codec.INT.optionalFieldOf("ascensions", 0).forGetter(PlayerTree::ascensions),
		setOf(Codec.STRING).optionalFieldOf("ascTaken", Set.of()).forGetter(PlayerTree::ascTaken),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("counters", Map.of()).forGetter(PlayerTree::counters),
		Codec.BOOL.optionalFieldOf("utilityOn", true).forGetter(PlayerTree::utilityOn)
	).apply(i, PlayerTree::new));

	public WheelState wheel(String id) {
		return "craft".equals(id) ? craft : fight;
	}

	public PlayerTree withWheel(String id, WheelState state) {
		return "craft".equals(id)
			? new PlayerTree(fight, state, ascendancy, ascensions, ascTaken, counters, utilityOn)
			: new PlayerTree(state, craft, ascendancy, ascensions, ascTaken, counters, utilityOn);
	}

	public PlayerTree withAscension(String asc, int count, Set<String> taken) {
		return new PlayerTree(fight, craft, asc, count, Set.copyOf(taken), counters, utilityOn);
	}

	public PlayerTree withUtility(boolean on) {
		return new PlayerTree(fight, craft, ascendancy, ascensions, ascTaken, counters, on);
	}

	public long counter(String key) {
		Long v = counters.get(key);
		return v != null ? v : 0L;
	}

	public PlayerTree addCounter(String key, long amount) {
		Map<String, Long> m = new HashMap<>(counters);
		m.merge(key, amount, Long::sum);
		return new PlayerTree(fight, craft, ascendancy, ascensions, ascTaken, Map.copyOf(m), utilityOn);
	}

	public PlayerTree setCounter(String key, long value) {
		Map<String, Long> m = new HashMap<>(counters);
		m.put(key, value);
		return new PlayerTree(fight, craft, ascendancy, ascensions, ascTaken, Map.copyOf(m), utilityOn);
	}
}
