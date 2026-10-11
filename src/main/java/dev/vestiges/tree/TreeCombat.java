package dev.vestiges.tree;

import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.level.LevelService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;

/**
 * Tree effects in fights. All damage passes {@link #modifyDamage} once (from LivingEntity.hurtServer): the level bonus, the player's "+X%
 * damage" stats added together, then each mechanic as its own multiplier (crits, Executioner, Momentum...), then the victim's own reductions.
 */
public final class TreeCombat {
	private static final Identifier SUNDER = Vestiges.id("sunder");
	private static final String PIERCE_TAG = "vestiges_pierce";

	private record Momentum(UUID target, int count, long tick) {
	}

	private record Mark(long until, double bonus) {
	}

	private record Debuff(ResourceKey<Level> dimension, long until) {
	}

	private static final Map<UUID, Momentum> MOMENTUM = new HashMap<>();
	private static final Map<UUID, Long> RIPOSTE_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> THICK_HIDE_READY = new HashMap<>();
	private static final Map<UUID, Long> IRON_WILL_READY = new HashMap<>();
	private static final Map<UUID, Long> SECOND_WIND_READY = new HashMap<>();
	private static final Map<UUID, Long> SECOND_WIND_UNTIL = new HashMap<>();
	private static final Map<UUID, Mark> MARKED = new HashMap<>();
	private static final Map<UUID, Debuff> SUNDERED = new HashMap<>();
	private static long now;

	private TreeCombat() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(TreeCombat::onKill);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && entity.tickCount == 0) {
				onArrowShot(arrow);
			}
		});
	}

	/** Splinter Bolt: crossbow bolts pierce more enemies. Tagged so a bolt never gets it twice (e.g. when its chunk reloads). */
	private static void onArrowShot(AbstractArrow arrow) {
		if (!(arrow.getOwner() instanceof ServerPlayer owner) || !TreeService.enabled() || arrow.entityTags().contains(PIERCE_TAG)) {
			return;
		}
		ItemStack weapon = arrow.getWeaponItem();
		PlayerStats s = StatEngine.get(owner);
		if (weapon == null || !(weapon.getItem() instanceof CrossbowItem) || !s.has("pierce")) {
			return;
		}
		arrow.addTag(PIERCE_TAG);
		int extra = (int) s.sum("pierce", "extra");
		((dev.vestiges.mixin.AbstractArrowAccessor) arrow).vestiges$setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + extra));
	}

	private static long now(ServerLevel level) {
		now = level.getServer().getTickCount();
		return now;
	}

	// ---- the damage pipeline ---------------------------------------------------------------------------------------

	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (!(target.level() instanceof ServerLevel level) || amount <= 0) {
			return amount;
		}
		long t = now(level);
		float out = amount;
		if (dev.vestiges.mobs.MobService.immune(target, source)) {
			return 0;
		}
		// Mobs hit harder by level, size, rank and Brute (not Spiked's reflected damage, not a Volatile mob's death blast).
		if (source.getEntity() instanceof LivingEntity mobAttacker && !(mobAttacker instanceof net.minecraft.world.entity.player.Player)
			&& !mobAttacker.isDeadOrDying() && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
			out *= (float) dev.vestiges.mobs.MobService.damageMultiplier(mobAttacker);
		}
		boolean tree = !target.isInvulnerableTo(level, source);
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
			out *= (float) LevelService.damageMultiplier(attacker);
			if (tree) {
				out = outgoing(attacker, target, source, out, level, t);
			}
		}
		if (!tree) {
			return out;
		}
		Mark mark = MARKED.get(target.getUUID());
		if (mark != null) {
			if (mark.until() > t) {
				out *= (float) (1 + mark.bonus() / 100.0);
			} else {
				MARKED.remove(target.getUUID());
			}
		}
		if (target instanceof ServerPlayer victim) {
			out = incoming(victim, source, out, t);
		} else if (target.getControllingPassenger() instanceof ServerPlayer rider && source.is(DamageTypeTags.IS_FALL)) {
			PlayerStats s = StatEngine.get(rider);
			if (s.has("mount_fall")) {
				out *= (float) Math.max(0, 1 - s.sum("mount_fall", "reduce") / 100.0);
			}
		}
		return out;
	}

	private static float outgoing(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount, ServerLevel level, long t) {
		Entity direct = source.getDirectEntity();
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = !projectile && direct == attacker;
		AbstractArrow arrow = direct instanceof AbstractArrow a ? a : null;
		ItemStack weapon = arrow != null ? arrow.getWeaponItem() : attacker.getMainHandItem();
		if (weapon == null) {
			weapon = ItemStack.EMPTY;
		}
		// Gear ladder: a weapon above your level deals half damage.
		if ((melee || arrow != null) && GearLadder.tooHigh(attacker, weapon)) {
			amount *= 0.5f;
		}
		PlayerStats s = StatEngine.get(attacker);
		if (s == PlayerStats.NONE) {
			return amount;
		}
		boolean bow = weapon.getItem() instanceof BowItem;
		boolean crossbow = weapon.getItem() instanceof CrossbowItem;
		boolean trident = direct instanceof ThrownTrident || (melee && weapon.is(Items.TRIDENT));
		boolean sword = melee && weapon.is(ItemTags.SWORDS);

		// "+X% damage" stats add up ("damage" = every hit, e.g. amethyst trims).
		double pct = s.get("damage");
		if (melee) {
			pct += s.get("melee_damage");
			if (sword) {
				pct += s.get("sword_damage");
			}
			if (weapon.is(ItemTags.AXES)) {
				pct += s.get("axe_damage");
			}
			if (weapon.getItem() instanceof MaceItem) {
				pct += s.get("mace_damage");
			}
			if (weapon.is(ItemTags.SPEARS)) {
				pct += s.get("spear_damage");
			}
		}
		if (source.is(DamageTypes.MACE_SMASH)) {
			pct += s.get("smash_damage");
		}
		if (projectile) {
			pct += s.get("projectile_damage");
			if (bow) {
				pct += s.get("bow_damage");
			}
			if (crossbow) {
				pct += s.get("crossbow_damage");
			}
		}
		if (trident) {
			pct += s.get("trident_damage");
		}
		double mult = Math.max(0, 1 + pct / 100.0);

		// Each mechanic multiplies on its own.
		double critChance = melee ? s.get("crit_chance") + (sword ? s.get("sword_crit") : 0) : projectile ? s.get("projectile_crit") : 0;
		if (critChance > 0 && attacker.getRandom().nextDouble() < critChance / 100.0) {
			mult *= 1.5 + s.get("crit_damage") / 100.0;
			dev.vestiges.mobs.CombatFeedback.markCrit(attacker, target);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		for (TreeDefs.Effect e : s.all("executioner")) {
			if (target.getHealth() < target.getMaxHealth() * e.param("below", 30) / 100.0) {
				mult *= 1 + e.param("bonus", 40) / 100.0;
			}
		}
		if (projectile) {
			double dist = attacker.distanceTo(target);
			for (TreeDefs.Effect e : s.all("sniper")) {
				if (dist > e.param("distance", 20)) {
					mult *= 1 + e.param("bonus", 25) / 100.0;
				}
			}
			for (TreeDefs.Effect e : s.all("skirmisher")) {
				if (dist <= e.param("distance", 8)) {
					mult *= 1 + e.param("bonus", 25) / 100.0;
				}
			}
			if (bow && arrow != null && arrow.isCritArrow()) {
				for (TreeDefs.Effect e : s.all("full_draw_bonus")) {
					mult *= 1 + e.param("damage", 20) / 100.0;
				}
			}
		}
		if (trident && attacker.isInWaterOrRain()) {
			for (TreeDefs.Effect e : s.all("tide_caller")) {
				mult *= 1 + e.param("bonus", 15) / 100.0;
			}
		}
		if (melee && s.has("momentum")) {
			TreeDefs.Effect e = s.all("momentum").get(0);
			Momentum m = MOMENTUM.get(attacker.getUUID());
			int count = m != null && m.target().equals(target.getUUID()) && t - m.tick() <= e.param("reset", 3) * 20 ? m.count() + 1 : 1;
			MOMENTUM.put(attacker.getUUID(), new Momentum(target.getUUID(), count, t));
			mult *= 1 + Math.min(e.param("max", 20), e.param("per", 4) * (count - 1)) / 100.0;
		}
		Long riposte = RIPOSTE_UNTIL.remove(attacker.getUUID());
		if (riposte != null && riposte >= t && s.has("riposte")) {
			mult *= 1 + s.all("riposte").get(0).param("bonus", 30) / 100.0;
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
		}

		// Effects on the target.
		if (melee && s.has("sunder")) {
			TreeDefs.Effect e = s.all("sunder").get(0);
			StatEngine.setModifier(target, Attributes.ARMOR, SUNDER, -e.param("armor", 10) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			SUNDERED.put(target.getUUID(), new Debuff(level.dimension(), t + (long) (e.param("seconds", 5) * 20)));
		}
		if (projectile) {
			for (TreeDefs.Effect e : s.all("hunters_mark")) {
				int ticks = (int) (e.param("seconds", 10) * 20);
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), attacker);
				MARKED.put(target.getUUID(), new Mark(t + ticks, e.param("bonus", 10)));
			}
			for (TreeDefs.Effect e : s.all("slow_on_hit")) {
				boolean rightWeapon = e.param("weapon", 0) == 1 ? crossbow : bow;
				boolean drawn = e.param("full_draw", 0) == 0 || (arrow != null && arrow.isCritArrow());
				if (rightWeapon && drawn) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (e.param("seconds", 2) * 20), (int) e.param("amplifier", 1)), attacker);
				}
			}
		}
		if (source.is(DamageTypes.MACE_SMASH)) {
			for (TreeDefs.Effect e : s.all("aftershock")) {
				double r = e.param("radius", 3);
				int ticks = (int) (e.param("seconds", 3) * 20);
				for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
					x -> x == target || (x instanceof Enemy && x.isAlive()))) {
					near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), attacker);
				}
			}
		}

		float out = (float) (amount * mult);
		if (s.has("blood_pact") && attacker.getHealth() < attacker.getMaxHealth()) {
			attacker.heal((float) (out * s.sum("blood_pact", "pct") / 100.0));
		}
		return out;
	}

	private static float incoming(ServerPlayer victim, DamageSource source, float amount, long t) {
		PlayerStats s = StatEngine.get(victim);
		if (s == PlayerStats.NONE) {
			return amount;
		}
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		if (source.is(DamageTypeTags.IS_FALL) && TreeTicker.safeClimbFall(victim, s)) {
			return 0;
		}
		double m = 1;
		if (projectile) {
			m *= s.mult("projectile_taken");
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			m *= s.mult("explosion_taken");
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			m *= s.mult("fire_taken");
		}
		if (!projectile && source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()) {
			m *= s.mult("melee_taken");
		}
		for (TreeDefs.Effect e : s.all("last_stand")) {
			if (victim.getHealth() < victim.getMaxHealth() * e.param("below", 25) / 100.0) {
				m *= 1 - e.param("reduce", 20) / 100.0;
			}
		}
		// Thick Hide: only real hits (from something), and not during the hit cooldown where vanilla ignores most damage anyway.
		if (s.has("thick_hide") && source.getEntity() != null && victim.getInvulnerableTime() <= 10) {
			TreeDefs.Effect e = s.all("thick_hide").get(0);
			long ready = THICK_HIDE_READY.getOrDefault(victim.getUUID(), 0L);
			if (t >= ready) {
				m *= 1 - e.param("reduce", 50) / 100.0;
				int cooldown = (int) (e.param("seconds", 10) * 20);
				THICK_HIDE_READY.put(victim.getUUID(), t + cooldown);
				JsonObject o = new JsonObject();
				o.addProperty("cooldown", cooldown);
				LevelService.notice(victim, "thick_hide", o);
			}
		}
		float out = (float) (amount * Math.max(0.05, m));
		if (s.has("iron_will")) {
			TreeDefs.Effect e = s.all("iron_will").get(0);
			if (out > victim.getHealth() * e.param("threshold", 25) / 100.0 && t >= IRON_WILL_READY.getOrDefault(victim.getUUID(), 0L)) {
				victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) (e.param("seconds", 4) * 20), 0));
				IRON_WILL_READY.put(victim.getUUID(), t + (long) (e.param("cooldown", 20) * 20));
			}
		}
		if (s.has("second_wind")) {
			TreeDefs.Effect e = s.all("second_wind").get(0);
			boolean low = victim.getHealth() - out < victim.getMaxHealth() * e.param("below", 30) / 100.0;
			if (low && victim.getHealth() - out > 0 && t >= SECOND_WIND_READY.getOrDefault(victim.getUUID(), 0L)) {
				SECOND_WIND_UNTIL.put(victim.getUUID(), t + (long) (e.param("seconds", 5) * 20));
				SECOND_WIND_READY.put(victim.getUUID(), t + (long) (e.param("cooldown", 60) * 20));
			}
		}
		return out;
	}

	/** Called when a shield blocks part of a hit (LivingEntity.applyItemBlocking). */
	public static void onShieldBlock(LivingEntity blocker, float blocked) {
		if (blocked <= 0 || !(blocker instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PlayerStats s = StatEngine.get(player);
		if (s.has("riposte")) {
			RIPOSTE_UNTIL.put(player.getUUID(), now(level) + (long) (s.all("riposte").get(0).param("seconds", 3) * 20));
		}
	}

	static boolean secondWindActive(ServerPlayer player) {
		Long until = SECOND_WIND_UNTIL.get(player.getUUID());
		return until != null && until >= player.level().getServer().getTickCount();
	}

	private static void onKill(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || dead == killer || !TreeService.enabled()) {
			return;
		}
		boolean melee = !source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() == killer;
		if (melee && killer.getMainHandItem().is(ItemTags.SWORDS)) {
			PlayerStats s = StatEngine.get(killer);
			if (s.has("grim_harvest")) {
				killer.heal((float) (killer.getMaxHealth() * s.sum("grim_harvest", "pct") / 100.0));
			}
		}
		MARKED.remove(dead.getUUID());
		SUNDERED.remove(dead.getUUID());
	}

	/** Ends Sunder debuffs and forgets old marks (from the tree ticker). */
	static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		Iterator<Map.Entry<UUID, Debuff>> it = SUNDERED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Debuff> e = it.next();
			if (e.getValue().until() > t) {
				continue;
			}
			ServerLevel level = server.getLevel(e.getValue().dimension());
			if (level != null && level.getEntity(e.getKey()) instanceof LivingEntity living) {
				StatEngine.setModifier(living, Attributes.ARMOR, SUNDER, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
			it.remove();
		}
		if (t % 200 == 0) {
			MARKED.values().removeIf(m -> m.until() <= t);
			MOMENTUM.values().removeIf(m -> t - m.tick() > 200);
			RIPOSTE_UNTIL.values().removeIf(u -> u < t);
		}
	}

	static void forget(UUID player) {
		MOMENTUM.remove(player);
		RIPOSTE_UNTIL.remove(player);
		THICK_HIDE_READY.remove(player);
		IRON_WILL_READY.remove(player);
		SECOND_WIND_READY.remove(player);
		SECOND_WIND_UNTIL.remove(player);
	}
}
