package dev.vestiges.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The villagers a player has talked to, saved on the player and kept on death. Keyed by the villager's UUID. A met villager keeps what the player
 * saw: the profile, the job, and where and when they last met. Tracking comes later, for recruited villagers only.
 */
public record VillagerLibrary(Map<String, Met> met) {
	public static final VillagerLibrary EMPTY = new VillagerLibrary(Map.of());
	public static final int CAP = 300;

	public record Met(VillagerProfile profile, String job, String dim, int x, int y, int z, long day, long order) {
		public static final Codec<Met> CODEC = RecordCodecBuilder.create(i -> i.group(
			VillagerProfile.CODEC.fieldOf("profile").forGetter(Met::profile),
			Codec.STRING.fieldOf("job").forGetter(Met::job),
			Codec.STRING.fieldOf("dim").forGetter(Met::dim),
			Codec.INT.fieldOf("x").forGetter(Met::x),
			Codec.INT.fieldOf("y").forGetter(Met::y),
			Codec.INT.fieldOf("z").forGetter(Met::z),
			Codec.LONG.fieldOf("day").forGetter(Met::day),
			Codec.LONG.optionalFieldOf("order", 0L).forGetter(Met::order)
		).apply(i, Met::new));
	}

	public static final Codec<VillagerLibrary> CODEC = Codec.unboundedMap(Codec.STRING, Met.CODEC).xmap(VillagerLibrary::new, VillagerLibrary::met);

	/** Records a meeting (new or repeated); the oldest entries go first once the library is full. */
	public VillagerLibrary with(String id, Met entry) {
		Map<String, Met> m = new HashMap<>(met);
		m.put(id, entry);
		while (m.size() > CAP) {
			String oldest = null;
			for (Map.Entry<String, Met> e : m.entrySet()) {
				if (oldest == null || e.getValue().order() < m.get(oldest).order()) {
					oldest = e.getKey();
				}
			}
			m.remove(oldest);
		}
		return new VillagerLibrary(Map.copyOf(m));
	}

	/** Newest meeting first. */
	public List<Map.Entry<String, Met>> recent() {
		List<Map.Entry<String, Met>> list = new ArrayList<>(met.entrySet());
		list.sort((a, b) -> Long.compare(b.getValue().order(), a.getValue().order()));
		return list;
	}
}
