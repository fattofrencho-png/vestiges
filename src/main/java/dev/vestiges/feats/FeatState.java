package dev.vestiges.feats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's feats (M5): which are done, how many times each repeatable feat paid, the counts feats read that nothing else keeps (villagers
 * met, raids won, the strongest Bad Omen beaten), and rewards owed by systems that come later. Kept on death. Immutable.
 */
public record FeatState(Set<String> done, Map<String, Integer> repeats, int villagers, int raids, int bestOmen, List<String> owed) {
	public static final FeatState EMPTY = new FeatState(Set.of(), Map.of(), 0, 0, 0, List.of());

	public static final Codec<FeatState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.listOf().xmap(l -> (Set<String>) new HashSet<>(l), s -> List.copyOf(s)).optionalFieldOf("done", Set.of()).forGetter(FeatState::done),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("repeats", Map.of()).forGetter(FeatState::repeats),
		Codec.INT.optionalFieldOf("villagers", 0).forGetter(FeatState::villagers),
		Codec.INT.optionalFieldOf("raids", 0).forGetter(FeatState::raids),
		Codec.INT.optionalFieldOf("best_omen", 0).forGetter(FeatState::bestOmen),
		Codec.STRING.listOf().optionalFieldOf("owed", List.of()).forGetter(FeatState::owed)
	).apply(i, FeatState::new));

	public FeatState withDone(String id, boolean on) {
		Set<String> s = new HashSet<>(done);
		if (on) {
			s.add(id);
		} else {
			s.remove(id);
		}
		return new FeatState(Set.copyOf(s), repeats, villagers, raids, bestOmen, owed);
	}

	public FeatState withRepeat(String id, int times) {
		Map<String, Integer> m = new HashMap<>(repeats);
		m.put(id, times);
		return new FeatState(done, Map.copyOf(m), villagers, raids, bestOmen, owed);
	}

	public FeatState withVillagers(int n) {
		return new FeatState(done, repeats, n, raids, bestOmen, owed);
	}

	public FeatState withRaid(int omen) {
		return new FeatState(done, repeats, villagers, raids + 1, Math.max(bestOmen, omen), owed);
	}

	public FeatState withOwed(String reward) {
		List<String> l = new ArrayList<>(owed);
		if (!l.contains(reward)) {
			l.add(reward);
		}
		return new FeatState(done, repeats, villagers, raids, bestOmen, List.copyOf(l));
	}
}
