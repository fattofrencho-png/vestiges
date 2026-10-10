package dev.vestiges.tree;

import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small tree effects called from mixins. Some run on both sides (mining speed, crossbow reload), so the client must agree with the server:
 * on a server player we read {@link StatEngine}, on the client the local player's own copy (set by the client in {@link #clientStats}).
 */
public final class TreeHooks {
	/** The local player's stats on the client (set by ClientTree). */
	public static volatile Function<Player, PlayerStats> clientStats = p -> PlayerStats.NONE;

	private TreeHooks() {
	}

	public static PlayerStats stats(LivingEntity entity) {
		if (entity instanceof ServerPlayer sp) {
			return StatEngine.get(sp);
		}
		if (entity instanceof Player p && p.level().isClientSide()) {
			return clientStats.apply(p);
		}
		return PlayerStats.NONE;
	}

	/** Rounds a fractional amount up or down at random, so small percentages still count on average. */
	static int randomRound(double value, RandomSource random) {
		int whole = (int) Math.floor(value);
		return whole + (random.nextDouble() < value - whole ? 1 : 0);
	}

	// ---- mining speed (both sides) ---------------------------------------------------------------------------------

	public static float breakSpeed(Player player, BlockState state, float speed) {
		ItemStack tool = player.getMainHandItem();
		if (speed > 0 && dev.vestiges.gear.GearLadder.tooHigh(player, tool)) {
			speed *= 0.5f;
		}
		PlayerStats s = stats(player);
		if (s == PlayerStats.NONE || speed <= 0) {
			return speed;
		}
		double pct = 0;
		if (tool.is(ItemTags.AXES) && state.is(BlockTags.MINEABLE_WITH_AXE)) {
			pct += s.get("chopping_speed");
		}
		if (tool.is(ItemTags.SHOVELS) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			pct += s.get("shovel_speed");
		}
		if (player.getY() < 0) {
			pct += s.get("deep_mining_speed");
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)) {
			pct += s.get("stone_mining_speed");
		}
		if (s.has("shearmaster") && tool.is(Items.SHEARS) && (state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB))) {
			pct += s.sum("shearmaster", "speed");
		}
		return pct == 0 ? speed : (float) (speed * Math.max(0.1, 1 + pct / 100.0));
	}

	// ---- bows and crossbows ----------------------------------------------------------------------------------------

	/** Crossbow reload time in ticks after reload speed (both sides, so the charge bar matches). */
	public static int chargeDuration(LivingEntity entity, int ticks) {
		double m = stats(entity).mult("reload_speed");
		return m == 1.0 ? ticks : Math.max(1, (int) Math.round(ticks / Math.max(0.1, m)));
	}

	/** Bow draw: the server counts the held time faster, so full power comes sooner. */
	public static int drawTicks(LivingEntity entity, int ticks) {
		double m = stats(entity).mult("draw_speed");
		return m == 1.0 ? ticks : (int) Math.round(ticks * m);
	}

	public static float projectileVelocity(LivingEntity shooter, float velocity) {
		return (float) (velocity * Math.max(0.1, stats(shooter).mult("projectile_speed")));
	}

	/** Chance to keep the arrow (it flies but can't be picked up, like Infinity). */
	public static int ammoUse(LivingEntity shooter, int used) {
		if (used <= 0) {
			return used;
		}
		double chance = stats(shooter).get("arrow_save") / 100.0;
		return chance > 0 && shooter.getRandom().nextDouble() < chance ? 0 : used;
	}

	// ---- server-only ---------------------------------------------------------------------------------------------

	/** Durability loss after tool and armor wear stats. */
	public static int durability(ServerPlayer player, ItemStack stack, int amount) {
		PlayerStats s = StatEngine.get(player);
		if (s == PlayerStats.NONE || amount <= 0) {
			return amount;
		}
		double m = 1;
		if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)) {
			m *= s.mult("armor_wear");
		} else if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.HOES) || stack.is(Items.SHEARS)) {
			m *= s.mult("tool_wear");
		}
		if (stack.is(ItemTags.SHOVELS)) {
			m *= s.mult("shovel_wear");
		}
		if ((stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)) && s.has("gentle_hands")) {
			m *= Math.max(0, 1 - s.sum("gentle_hands", "reduce") / 100.0);
		}
		return m == 1 ? amount : randomRound(amount * Math.max(0, m), player.getRandom());
	}

	/** Hunger drain: general, while sprinting, and none while sprint-swimming with the Sea Legs keystone. */
	public static float exhaustion(ServerPlayer player, float amount) {
		PlayerStats s = StatEngine.get(player);
		if (s == PlayerStats.NONE || amount <= 0) {
			return amount;
		}
		if (player.isSwimming() && s.has("swim_no_hunger")) {
			return 0;
		}
		double m = s.mult("hunger_drain");
		if (player.isSprinting()) {
			m *= s.mult("sprint_hunger");
		}
		return (float) (amount * Math.max(0, m));
	}

	/** Natural regeneration (from food): regen speed, and x3 during Second Wind. */
	public static float naturalRegen(ServerPlayer player, float heal) {
		PlayerStats s = StatEngine.get(player);
		if (s == PlayerStats.NONE) {
			return heal;
		}
		double m = Math.max(0, s.mult("regen_speed"));
		if (TreeCombat.secondWindActive(player)) {
			m *= 3;
		}
		return (float) (heal * m);
	}

	/** Every heal the player receives. */
	public static float healing(ServerPlayer player, float heal) {
		PlayerStats s = StatEngine.get(player);
		return s == PlayerStats.NONE ? heal : (float) (heal * Math.max(0, s.mult("healing_received")));
	}

	/** Level XP after the XP stats ("kill", "ore", "discovery"...). */
	public static long xp(ServerPlayer player, long amount, String reason) {
		PlayerStats s = StatEngine.get(player);
		if (s == PlayerStats.NONE) {
			return amount;
		}
		double m = s.mult("xp_gain");
		switch (reason) {
			case "ore" -> m *= s.mult("ore_xp");
			case "discovery" -> m *= s.mult("discovery_xp");
			default -> {
			}
		}
		return Math.max(1, Math.round(amount * m));
	}
}
