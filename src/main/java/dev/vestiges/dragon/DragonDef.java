package dev.vestiges.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * One lore dragon, loaded from {@code data/<namespace>/vestiges/dragons/<id>.json}. {@code number} is its place in the cycle (1 is the dragon already
 * in the End), {@code level} the level the boss bar shows and {@code gate} the level the summoner needs (0 = none). {@code health} and {@code damage}
 * are multipliers on the vanilla dragon (200 health); the party factor comes on top at the fight. {@code moves} lists the signature move ids it uses,
 * its own new move first. Dragons 7 to 24 are only more files of this kind.
 */
public record DragonDef(int number, String name, String title, int level, int gate, double health, double damage, List<String> moves, Rewards rewards) {
	/**
	 * What every qualifying player gets once for this dragon. {@code xpLevels} is how many level-ups' worth of XP at the dragon's level,
	 * {@code feats} the feat ids completed, {@code chapter} the Codex entry of its lore chapter ("lore/belaz"), {@code relic} a guaranteed Relic item.
	 */
	public record Rewards(double xpLevels, int emeralds, boolean book, boolean relic, List<String> feats, String chapter) {
		public static final Rewards NONE = new Rewards(0, 0, false, false, List.of(), "");

		public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("xp_levels", 0.0).forGetter(Rewards::xpLevels),
			Codec.INT.optionalFieldOf("emeralds", 0).forGetter(Rewards::emeralds),
			Codec.BOOL.optionalFieldOf("book", false).forGetter(Rewards::book),
			Codec.BOOL.optionalFieldOf("relic", false).forGetter(Rewards::relic),
			Codec.STRING.listOf().optionalFieldOf("feats", List.of()).forGetter(Rewards::feats),
			Codec.STRING.optionalFieldOf("chapter", "").forGetter(Rewards::chapter)
		).apply(i, Rewards::new));
	}

	public static final Codec<DragonDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("number").forGetter(DragonDef::number),
		Codec.STRING.fieldOf("name").forGetter(DragonDef::name),
		Codec.STRING.optionalFieldOf("title", "").forGetter(DragonDef::title),
		Codec.INT.optionalFieldOf("level", 1).forGetter(DragonDef::level),
		Codec.INT.optionalFieldOf("gate", 0).forGetter(DragonDef::gate),
		Codec.DOUBLE.optionalFieldOf("health", 1.0).forGetter(DragonDef::health),
		Codec.DOUBLE.optionalFieldOf("damage", 1.0).forGetter(DragonDef::damage),
		Codec.STRING.listOf().optionalFieldOf("moves", List.of()).forGetter(DragonDef::moves),
		Rewards.CODEC.optionalFieldOf("rewards", Rewards.NONE).forGetter(DragonDef::rewards)
	).apply(i, DragonDef::new));

	/** The boss bar and name tag text: "Belaz, the Ember · Lv 8". */
	public String display() {
		return (title.isEmpty() ? name : name + ", " + title) + " · Lv " + level;
	}

	public boolean has(String move) {
		return moves.contains(move);
	}
}
