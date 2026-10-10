package dev.vestiges.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The passive tree data read from {@code data/<ns>/vestiges/trees/*.json} (one file per wheel) and {@code .../ascendancies/*.json}. Built from
 * the approved wheels by the generator; servers can change it with a datapack. Effects are either a stat (summed per player) or a named
 * mechanic with parameters (handled in code).
 */
public final class TreeDefs {
	private TreeDefs() {
	}

	public record Effect(Optional<String> stat, Optional<String> mechanic, double value, Map<String, Double> params) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("stat").forGetter(Effect::stat),
			Codec.STRING.optionalFieldOf("mechanic").forGetter(Effect::mechanic),
			Codec.DOUBLE.optionalFieldOf("value", 0.0).forGetter(Effect::value),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(Effect::params)
		).apply(i, Effect::new));

		public double param(String key, double fallback) {
			Double v = params.get(key);
			return v != null ? v : fallback;
		}
	}

	public record Option(String name, String text, List<Effect> effects) {
		public static final Codec<Option> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Option::name),
			Codec.STRING.fieldOf("text").forGetter(Option::text),
			Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Option::effects)
		).apply(i, Option::new));
	}

	public record Node(String id, String type, Optional<String> region, String name, String text, double x, double y, List<Effect> effects,
					   List<Option> choices, Optional<String> rival, Optional<Integer> start) {
		public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Node::id),
			Codec.STRING.fieldOf("type").forGetter(Node::type),
			Codec.STRING.optionalFieldOf("region").forGetter(Node::region),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Node::name),
			Codec.STRING.optionalFieldOf("text", "").forGetter(Node::text),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Node::x),
			Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(Node::y),
			Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Node::effects),
			Option.CODEC.listOf().optionalFieldOf("choices", List.of()).forGetter(Node::choices),
			Codec.STRING.optionalFieldOf("rival").forGetter(Node::rival),
			Codec.INT.optionalFieldOf("start").forGetter(Node::start)
		).apply(i, Node::new));

		public boolean isStart() {
			return start.isPresent();
		}

		/** The effects that apply for the chosen option (choice nodes) or the fixed effects. */
		public List<Effect> effectsFor(int choice) {
			if (choices.isEmpty()) {
				return effects;
			}
			return choices.get(Math.max(0, Math.min(choices.size() - 1, choice))).effects();
		}
	}

	public record Wheel(String wheel, String name, List<Node> nodes, List<List<String>> edges) {
		public static final Codec<Wheel> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("wheel").forGetter(Wheel::wheel),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Wheel::name),
			Node.CODEC.listOf().fieldOf("nodes").forGetter(Wheel::nodes),
			Codec.STRING.listOf().listOf().optionalFieldOf("edges", List.of()).forGetter(Wheel::edges)
		).apply(i, Wheel::new));
	}

	public record AscNode(String id, String type, String name, String text, List<Effect> effects) {
		public static final Codec<AscNode> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(AscNode::id),
			Codec.STRING.fieldOf("type").forGetter(AscNode::type),
			Codec.STRING.fieldOf("name").forGetter(AscNode::name),
			Codec.STRING.optionalFieldOf("text", "").forGetter(AscNode::text),
			Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(AscNode::effects)
		).apply(i, AscNode::new));
	}

	public record Branch(String name, List<AscNode> nodes) {
		public static final Codec<Branch> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Branch::name),
			AscNode.CODEC.listOf().fieldOf("nodes").forGetter(Branch::nodes)
		).apply(i, Branch::new));
	}

	public record Step(String counter, long goal, String text) {
		public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("counter").forGetter(Step::counter),
			Codec.LONG.fieldOf("goal").forGetter(Step::goal),
			Codec.STRING.fieldOf("text").forGetter(Step::text)
		).apply(i, Step::new));
	}

	public record Ascendancy(String id, String name, String kit, List<Branch> branches, List<Step> second) {
		public static final Codec<Ascendancy> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Ascendancy::id),
			Codec.STRING.fieldOf("name").forGetter(Ascendancy::name),
			Codec.STRING.optionalFieldOf("kit", "").forGetter(Ascendancy::kit),
			Branch.CODEC.listOf().fieldOf("branches").forGetter(Ascendancy::branches),
			Step.CODEC.listOf().optionalFieldOf("second", List.of()).forGetter(Ascendancy::second)
		).apply(i, Ascendancy::new));
	}
}
