package dev.vestiges.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;

/**
 * One quest, loaded from {@code data/<namespace>/vestiges/quests/<id>.json}. {@code kind} is guide, side, note or story. {@code start} is how it
 * begins: tab (the Quests tab, after the one before it in its chain), villager (sneak + right-click a villager with that {@code job}), note (a
 * note found in the world) or command (test quests only).
 */
public record QuestDef(String kind, String chain, int order, String name, String giver, String intro, List<Step> steps, Reward reward, String start, String job) {
	/**
	 * One step. {@code targets} are the possible subjects (one is picked when the quest starts; "{target}" in the text shows it). From comeback
	 * {@code eliteFrom}, a kill step asks for elites. Choice steps have {@code choices} instead of a goal.
	 */
	public record Step(String type, List<String> targets, int amount, String text, int eliteFrom, List<Choice> choices) {
		public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(Step::type),
			Codec.STRING.listOf().optionalFieldOf("targets", List.of()).forGetter(Step::targets),
			Codec.INT.optionalFieldOf("amount", 1).forGetter(Step::amount),
			Codec.STRING.fieldOf("text").forGetter(Step::text),
			Codec.INT.optionalFieldOf("elite_from", 0).forGetter(Step::eliteFrom),
			Choice.CODEC.listOf().optionalFieldOf("choices", List.of()).forGetter(Step::choices)
		).apply(i, Step::new));
	}

	/** One option of a choice step: its label, the line shown after picking it, and the reputation it gives (rivals follow by {@code QuestRules}). */
	public record Choice(String label, String after, Map<String, Integer> reputation) {
		public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("label").forGetter(Choice::label),
			Codec.STRING.optionalFieldOf("after", "").forGetter(Choice::after),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("reputation", Map.of()).forGetter(Choice::reputation)
		).apply(i, Choice::new));
	}

	/**
	 * Flat {@code xp} plus {@code share} of what the player's level needs; {@code items} are written as "minecraft:bread:8". {@code gear} is
	 * "common", "uncommon" or "superior": one piece of gear of that rarity in the player's loot band. {@code refunds} are free tree-point refunds
	 * (1-5). Gear, items and refunds are paid on the first finish only.
	 */
	public record Reward(int xp, double share, int emeralds, List<String> items, String title, String gear, int refunds) {
		public static final Reward NONE = new Reward(0, 0, 0, List.of(), "", "", 0);
		public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Reward::xp),
			Codec.DOUBLE.optionalFieldOf("share", 0.0).forGetter(Reward::share),
			Codec.INT.optionalFieldOf("emeralds", 0).forGetter(Reward::emeralds),
			Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Reward::items),
			Codec.STRING.optionalFieldOf("title", "").forGetter(Reward::title),
			Codec.STRING.optionalFieldOf("gear", "").forGetter(Reward::gear),
			Codec.INT.optionalFieldOf("refunds", 0).forGetter(Reward::refunds)
		).apply(i, Reward::new));
	}

	public static final Codec<QuestDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("kind").forGetter(QuestDef::kind),
		Codec.STRING.optionalFieldOf("chain", "").forGetter(QuestDef::chain),
		Codec.INT.optionalFieldOf("order", 0).forGetter(QuestDef::order),
		Codec.STRING.fieldOf("name").forGetter(QuestDef::name),
		Codec.STRING.optionalFieldOf("giver", "").forGetter(QuestDef::giver),
		Codec.STRING.optionalFieldOf("intro", "").forGetter(QuestDef::intro),
		Step.CODEC.listOf().fieldOf("steps").forGetter(QuestDef::steps),
		Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(QuestDef::reward),
		Codec.STRING.optionalFieldOf("start", "tab").forGetter(QuestDef::start),
		Codec.STRING.optionalFieldOf("job", "").forGetter(QuestDef::job)
	).apply(i, QuestDef::new));

	public boolean side() {
		return "side".equals(kind);
	}
}
