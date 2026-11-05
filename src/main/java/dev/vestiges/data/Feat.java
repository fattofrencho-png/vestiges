package dev.vestiges.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import java.util.List;

/**
 * One feat, loaded from {@code data/<namespace>/vestiges/feats/<id>.json}. {@code requirement} and {@code reward} are the shown text; the checks
 * are {@code goals} (all must be met), or for repeatable feats a {@code counter} that pays once every {@code every}. {@code item} is the marker
 * (or waystone) handed out; {@code owed} names a reward of a later system (kept until it arrives); {@code later} names the system a feat waits
 * for (it cannot be earned yet).
 */
public record Feat(String chain, int order, String name, String requirement, String reward, int xp, boolean repeatable, List<Goal> goals,
				   String item, String owed, String later, String counter, int every) {
	/** One check: a kind ("level", "biomes", "codex", "elites", "villagers", "beds", "renown", "renown_two", "raids", "raid_omen", "dragon_best") and an amount. */
	public record Goal(String type, int amount) {
		public static final Codec<Goal> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(Goal::type),
			Codec.INT.fieldOf("amount").forGetter(Goal::amount)
		).apply(i, Goal::new));
	}

	public static final Codec<Feat> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("chain").forGetter(Feat::chain),
		Codec.INT.optionalFieldOf("order", 0).forGetter(Feat::order),
		Codec.STRING.fieldOf("name").forGetter(Feat::name),
		Codec.STRING.fieldOf("requirement").forGetter(Feat::requirement),
		Codec.STRING.fieldOf("reward").forGetter(Feat::reward),
		Codec.INT.optionalFieldOf("xp", 0).forGetter(Feat::xp),
		Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(Feat::repeatable),
		Goal.CODEC.listOf().optionalFieldOf("goals", List.of()).forGetter(Feat::goals),
		Codec.STRING.optionalFieldOf("item", "").forGetter(Feat::item),
		Codec.STRING.optionalFieldOf("owed", "").forGetter(Feat::owed),
		Codec.STRING.optionalFieldOf("later", "").forGetter(Feat::later),
		Codec.STRING.optionalFieldOf("counter", "").forGetter(Feat::counter),
		Codec.INT.optionalFieldOf("every", 0).forGetter(Feat::every)
	).apply(i, Feat::new));

	/** True while a feature this feat counts on or rewards is switched off in this world: it can't be earned then. */
	public boolean blockedIn(ServerConfig config) {
		VestigesSystem reward = item.isEmpty() ? null : "waystone".equals(item) ? VestigesSystem.WAYSTONES : "dungeon_key".equals(item) ? VestigesSystem.DUNGEONS : VestigesSystem.SETTLEMENTS;
		return off(config, reward) || off(config, system(counter)) || goals.stream().anyMatch(g -> off(config, system(g.type())));
	}

	private static boolean off(ServerConfig config, VestigesSystem system) {
		return system != null && !config.enabled(system);
	}

	private static VestigesSystem system(String type) {
		return switch (type) {
			case "level" -> VestigesSystem.CHARACTER;
			case "codex" -> VestigesSystem.CODEX;
			case "elites" -> VestigesSystem.ELITES;
			case "dragon_best" -> VestigesSystem.DRAGONS;
			case "renown", "renown_two", "renown_levels" -> VestigesSystem.SETTLEMENTS;
			default -> null;
		};
	}
}
