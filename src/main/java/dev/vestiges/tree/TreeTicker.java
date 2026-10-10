package dev.vestiges.tree;

import dev.vestiges.Vestiges;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Tree effects that depend on what you are doing right now, checked every 5 ticks: sprinting, standing still, the item in hand, sneaking,
 * blocking, riding, walking on paths. Each one is a vanilla attribute modifier switched on and off, so the client sees it at once.
 */
public final class TreeTicker {
	private static final int PERIOD = 5;
	private static final AttributeModifier.Operation BASE = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
	private static final AttributeModifier.Operation TOTAL = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
	private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;

	private static final Identifier SPRINT = Vestiges.id("cond/sprint");
	private static final Identifier RUNNERS_HIGH = Vestiges.id("cond/runners_high");
	private static final Identifier TRAIL = Vestiges.id("cond/trail");
	private static final Identifier WEAPON_SPEED = Vestiges.id("cond/weapon_speed");
	private static final Identifier FORTRESS = Vestiges.id("cond/fortress");
	private static final Identifier MACE_ARMOR = Vestiges.id("cond/mace_armor");
	private static final Identifier NO_KNOCKBACK = Vestiges.id("cond/no_knockback");
	private static final Identifier ENTITY_REACH = Vestiges.id("cond/entity_reach");
	private static final Identifier BLOCK_REACH = Vestiges.id("cond/block_reach");
	private static final Identifier MOUNT_SPEED = Vestiges.id("mount_speed");
	private static final Identifier MOUNT_JUMP = Vestiges.id("mount_jump");

	/** What the ticker remembers about each player between checks. */
	private static final class State {
		long sprintSince = -1;
		Vec3 lastPos;
		long climbTick = -1000;
		double climbY;
		LivingEntity mount;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private TreeTicker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TreeTicker::tick);
	}

	/** Called when a player's tree changes, leaves or respawns: start fresh and drop the modifiers this ticker added. */
	static void forget(ServerPlayer player) {
		State st = STATES.remove(player.getUUID());
		if (st != null && st.mount != null) {
			clearMount(st.mount);
		}
		TreeCombat.forget(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		TreeCombat.tick(server);
		UtilityPowers.tick(server);
		long t = server.getTickCount();
		if (t % PERIOD != 0) {
			return;
		}
		STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		boolean on = TreeService.enabled();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
			PlayerStats s = p.isAlive() ? StatEngine.get(p) : PlayerStats.NONE;
			update(p, s, st, t);
			if (on && t % 40 == 0 && p.level().dimension() == Level.OVERWORLD && p.getY() < 0 && TreeService.get(p).counter("deepslate") == 0) {
				p.setAttached(TreeService.TREE, TreeService.get(p).setCounter("deepslate", 1));
			}
		}
	}

	private static void update(ServerPlayer p, PlayerStats s, State st, long t) {
		ItemStack hand = p.getMainHandItem();
		boolean mace = hand.getItem() instanceof MaceItem;

		// Speed.
		boolean sprinting = p.isSprinting();
		if (sprinting && st.sprintSince < 0) {
			st.sprintSince = t;
		} else if (!sprinting) {
			st.sprintSince = -1;
		}
		set(p, Attributes.MOVEMENT_SPEED, SPRINT, sprinting ? s.get("sprint_speed") / 100.0 : 0, BASE);
		double high = 0;
		if (sprinting) {
			for (TreeDefs.Effect e : s.all("runners_high")) {
				if (t - st.sprintSince >= e.param("after", 3) * 20) {
					high += e.param("speed", 10) / 100.0;
				}
			}
		}
		set(p, Attributes.MOVEMENT_SPEED, RUNNERS_HIGH, high, BASE);
		double trail = 0;
		if (s.has("trailmaker")) {
			BlockPos at = p.blockPosition();
			if (p.level().getBlockState(at).is(Blocks.DIRT_PATH) || p.level().getBlockState(at.below()).is(Blocks.DIRT_PATH)) {
				trail = s.sum("trailmaker", "speed") / 100.0;
			}
		}
		set(p, Attributes.MOVEMENT_SPEED, TRAIL, trail, BASE);

		// Weapon attack speed.
		double weaponSpeed = 0;
		if (hand.is(ItemTags.SWORDS)) {
			weaponSpeed = s.get("sword_attack_speed");
		} else if (hand.is(ItemTags.AXES)) {
			weaponSpeed = s.get("axe_attack_speed");
		} else if (hand.is(ItemTags.SPEARS)) {
			weaponSpeed = s.get("spear_attack_speed");
		}
		set(p, Attributes.ATTACK_SPEED, WEAPON_SPEED, weaponSpeed / 100.0, BASE);

		// Armor and knockback.
		Vec3 pos = p.position();
		boolean still = st.lastPos != null && pos.distanceToSqr(st.lastPos) < 1.0E-4;
		st.lastPos = pos;
		set(p, Attributes.ARMOR, FORTRESS, still ? s.sum("fortress", "armor") / 100.0 : 0, TOTAL);
		double maceArmor = 0;
		if (mace) {
			maceArmor = s.sum("stoneheart", "armor");
			for (TreeDefs.Effect e : s.all("held_armor")) {
				if (e.param("item", 0) == 0) {
					maceArmor += e.param("armor", 5);
				}
			}
		}
		set(p, Attributes.ARMOR, MACE_ARMOR, maceArmor / 100.0, TOTAL);
		boolean noKnockback = (mace && s.has("stoneheart")) || (s.has("unshakable") && (p.isBlocking() || p.isShiftKeyDown()));
		set(p, Attributes.KNOCKBACK_RESISTANCE, NO_KNOCKBACK, noKnockback ? 1.0 : 0, ADD);

		// Reach.
		double entityReach = hand.is(ItemTags.SPEARS) ? s.sum("spear_reach", "blocks") : 0;
		double blockReach = hand.getItem() instanceof BlockItem ? s.sum("builder_reach", "blocks") : 0;
		if (p.isShiftKeyDown()) {
			entityReach += s.sum("sneak_reach", "blocks");
			blockReach += s.sum("sneak_reach", "blocks");
		}
		set(p, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH, entityReach, ADD);
		set(p, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH, blockReach, ADD);

		// Mounts: the ridden animal gets the speed and jump bonuses while you ride it.
		Entity vehicle = p.getControlledVehicle();
		LivingEntity mount = vehicle instanceof LivingEntity living ? living : null;
		if (st.mount != null && st.mount != mount) {
			clearMount(st.mount);
		}
		st.mount = mount;
		if (mount != null) {
			set(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, s.get("mount_speed") / 100.0, BASE);
			set(mount, Attributes.JUMP_STRENGTH, MOUNT_JUMP, s.get("mount_jump") / 100.0, BASE);
		}

		// Climbing (for Steady Footing).
		if (p.onClimbable()) {
			st.climbTick = t;
			st.climbY = p.getY();
		}

		// Ever-Mending: regeneration keeps going on a low (but not empty) hunger bar.
		if (s.has("ever_mending") && t % 80 == 0 && p.getHealth() < p.getMaxHealth()) {
			int food = p.getFoodData().getFoodLevel();
			ServerLevel level = (ServerLevel) p.level();
			if (food > 0 && food < 18 && level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
				p.heal(1.0f);
				p.causeFoodExhaustion(6.0f);
			}
		}
	}

	private static void set(LivingEntity e, Holder<Attribute> attribute, Identifier id, double value, AttributeModifier.Operation op) {
		StatEngine.setModifier(e, attribute, id, value, op);
	}

	private static void clearMount(LivingEntity mount) {
		set(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED, 0, BASE);
		set(mount, Attributes.JUMP_STRENGTH, MOUNT_JUMP, 0, BASE);
	}

	/** Steady Footing: a fall that started on a ladder, vine or scaffolding, within the allowed height, does no damage. */
	static boolean safeClimbFall(ServerPlayer player, PlayerStats s) {
		if (!s.has("steady_footing")) {
			return false;
		}
		State st = STATES.get(player.getUUID());
		if (st == null || player.level().getServer().getTickCount() - st.climbTick > 60) {
			return false;
		}
		double blocks = 0;
		for (TreeDefs.Effect e : s.all("steady_footing")) {
			blocks = Math.max(blocks, e.param("blocks", 10));
		}
		return st.climbY - player.getY() <= blocks + 0.5;
	}
}
