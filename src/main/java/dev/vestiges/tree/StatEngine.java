package dev.vestiges.tree;

import dev.vestiges.Vestiges;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Turns a player's tree into stats. Stats that match a vanilla attribute become attribute modifiers (the "boost vanilla features" rule);
 * the rest are read by Vestiges' hooks (combat, healing, hunger, mining speed, drops...). Results are cached until the tree changes.
 */
public final class StatEngine {
	/** How a stat maps onto a vanilla attribute: which one, how, and the scale from the stat's number. */
	private record AttrMap(Holder<Attribute> attribute, AttributeModifier.Operation op, double scale) {
	}

	private static final Map<String, AttrMap> ATTRIBUTES = Map.ofEntries(
		Map.entry("attack_speed", new AttrMap(Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("movement_speed", new AttrMap(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("knockback_resistance", new AttrMap(Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.01)),
		Map.entry("safe_fall", new AttrMap(Attributes.SAFE_FALL_DISTANCE, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("max_health", new AttrMap(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("armor", new AttrMap(Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("toughness", new AttrMap(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("max_health_pct", new AttrMap(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01)),
		Map.entry("armor_pct", new AttrMap(Attributes.ARMOR, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01)),
		Map.entry("toughness_pct", new AttrMap(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.01)),
		Map.entry("burning_time", new AttrMap(Attributes.BURNING_TIME, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("jump_strength", new AttrMap(Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("sweeping_damage", new AttrMap(Attributes.SWEEPING_DAMAGE_RATIO, AttributeModifier.Operation.ADD_VALUE, 0.01)),
		Map.entry("swim_speed", new AttrMap(Attributes.WATER_MOVEMENT_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE, 0.01)),
		Map.entry("block_reach", new AttrMap(Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("entity_reach", new AttrMap(Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("mining_speed", new AttrMap(Attributes.BLOCK_BREAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("underwater_mining", new AttrMap(Attributes.SUBMERGED_MINING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("sneak_speed", new AttrMap(Attributes.SNEAKING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)),
		Map.entry("oxygen_seconds", new AttrMap(Attributes.OXYGEN_BONUS, AttributeModifier.Operation.ADD_VALUE, 1.0 / 15.0)),
		Map.entry("step_height", new AttrMap(Attributes.STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE, 1.0)),
		Map.entry("attack_knockback", new AttrMap(Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, 0.01)),
		Map.entry("explosion_knockback", new AttrMap(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.01)),
		Map.entry("fall_damage", new AttrMap(Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01)));

	private static final Map<UUID, PlayerStats> CACHE = new HashMap<>();

	private StatEngine() {
	}

	/** The player's tree stats plus their gear stats (trims). NONE when neither gives anything. */
	public static PlayerStats get(ServerPlayer player) {
		return CACHE.computeIfAbsent(player.getUUID(), id -> {
			PlayerStats tree = TreeService.enabled() ? compute(TreeService.get(player), TreeService::wheel, TreeService::ascendancy) : PlayerStats.NONE;
			return PlayerStats.merge(tree, dev.vestiges.gear.GearService.stats(player));
		});
	}

	public static void invalidate(ServerPlayer player) {
		CACHE.remove(player.getUUID());
	}

	public static void invalidateAll() {
		CACHE.clear();
	}

	/** Sums every taken node (with its chosen option) of both wheels and the ascendancy. Pure: the client uses it on its own copy. */
	public static PlayerStats compute(PlayerTree tree, Function<String, TreeDefs.Wheel> wheels, Function<String, TreeDefs.Ascendancy> ascendancies) {
		PlayerStats s = new PlayerStats();
		for (String wheelId : List.of("fight", "craft")) {
			TreeDefs.Wheel wheel = wheels.apply(wheelId);
			if (wheel == null) {
				continue;
			}
			PlayerTree.WheelState state = tree.wheel(wheelId);
			for (TreeDefs.Node n : wheel.nodes()) {
				if (state.taken().contains(n.id())) {
					for (TreeDefs.Effect e : n.effectsFor(state.choices().getOrDefault(n.id(), 0))) {
						s.add(e);
					}
				}
			}
		}
		TreeDefs.Ascendancy asc = tree.ascendancy().isEmpty() ? null : ascendancies.apply(tree.ascendancy());
		if (asc != null) {
			for (TreeDefs.Branch b : asc.branches()) {
				for (TreeDefs.AscNode n : b.nodes()) {
					if (tree.ascTaken().contains(n.id())) {
						n.effects().forEach(s::add);
					}
				}
			}
		}
		return s;
	}

	/** True when a stat is one of the vanilla attributes (so it is synced to the client by vanilla). */
	public static boolean isAttribute(String stat) {
		return ATTRIBUTES.containsKey(stat);
	}

	/** Applies the attribute stats as transient modifiers (re-applied on join, respawn and every tree change). */
	public static void apply(ServerPlayer player) {
		PlayerStats s = get(player);
		for (Map.Entry<String, AttrMap> e : ATTRIBUTES.entrySet()) {
			setModifier(player, e.getValue().attribute(), Vestiges.id("tree/" + e.getKey()), s.get(e.getKey()) * e.getValue().scale(), e.getValue().op());
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Adds, updates or (at 0) removes one transient modifier, touching the attribute only when the value really changes. */
	public static void setModifier(net.minecraft.world.entity.LivingEntity entity, Holder<Attribute> attribute, Identifier id, double value,
								   AttributeModifier.Operation op) {
		AttributeInstance inst = entity.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		if (value == 0) {
			if (inst.hasModifier(id)) {
				inst.removeModifier(id);
			}
			return;
		}
		AttributeModifier old = inst.getModifier(id);
		if (old == null || old.amount() != value || old.operation() != op) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, value, op));
		}
	}
}
