package dev.vestiges.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's Codex (M4), saved on the player and kept on death. Keyed by entry id ("blocks/stone"):
 * {@code counts} blocks broken / kills and breedings / items obtained; {@code stages} the stage reached; {@code spots} the chunks walked
 * (biomes), structure starts or feature positions found (places); {@code held} how many of each item entry the inventory held at the last
 * look, so only new ones count. Immutable: every change makes a new record.
 */
public record CodexProgress(Map<String, Integer> counts, Map<String, Integer> stages, Map<String, List<Long>> spots, Map<String, Integer> held) {
	public static final CodexProgress EMPTY = new CodexProgress(Map.of(), Map.of(), Map.of(), Map.of());

	public static final Codec<CodexProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(CodexProgress::counts),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stages", Map.of()).forGetter(CodexProgress::stages),
		Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("spots", Map.of()).forGetter(CodexProgress::spots),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("held", Map.of()).forGetter(CodexProgress::held)
	).apply(i, CodexProgress::new));

	public int count(String id) {
		return counts.getOrDefault(id, 0);
	}

	public int stage(String id) {
		return stages.getOrDefault(id, 0);
	}

	public List<Long> spots(String id) {
		return spots.getOrDefault(id, List.of());
	}

	public int discovered() {
		int n = 0;
		for (int s : stages.values()) {
			if (s > 0) {
				n++;
			}
		}
		return n;
	}

	public CodexProgress withCount(String id, int value) {
		Map<String, Integer> m = new HashMap<>(counts);
		m.put(id, value);
		return new CodexProgress(m, stages, spots, held);
	}

	public CodexProgress withStage(String id, int value) {
		Map<String, Integer> m = new HashMap<>(stages);
		if (value <= 0) {
			m.remove(id);
		} else {
			m.put(id, value);
		}
		return new CodexProgress(counts, m, spots, held);
	}

	public CodexProgress withSpot(String id, long spot) {
		Map<String, List<Long>> m = new HashMap<>(spots);
		List<Long> list = new ArrayList<>(m.getOrDefault(id, List.of()));
		list.add(spot);
		m.put(id, List.copyOf(list));
		return new CodexProgress(counts, stages, m, held);
	}

	public CodexProgress withHeld(Map<String, Integer> value) {
		return new CodexProgress(counts, stages, spots, Map.copyOf(value));
	}
}
