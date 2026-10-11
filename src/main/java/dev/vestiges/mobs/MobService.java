package dev.vestiges.mobs;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.MobLevels;
import dev.vestiges.level.XpSources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Monster levels, sizes and elites (M3a, approved). Every monster gets its level once, when it first appears: 75% of nearby players' level
 * plus the layer bonus, capped per layer (elites may go over). Health and damage follow level, size and rank; elites get 1-3 powers.
 */
public final class MobService {
	public static final AttachmentType<MobData> DATA = AttachmentRegistry.create(Vestiges.id("mob"),
		builder -> builder.persistent(MobData.CODEC).syncWith(MobData.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static final List<String> POWERS = List.of("swift", "brute", "armored", "burning", "shove", "shadow", "caller", "volatile", "venom",
		"chilling", "mending", "spiked", "withering");

	private static final Identifier LEVEL_HEALTH = Vestiges.id("mob_level");
	private static final Identifier SIZE = Vestiges.id("mob_size");
	private static final Identifier SWIFT = Vestiges.id("power_swift");
	private static final Identifier ARMORED = Vestiges.id("power_armored");
	private static final Identifier ARMORED_KB = Vestiges.id("power_armored_knockback");
	/** Loaded elites, for powers that tick (Shadow, Mending, Burning). */
	private static final Set<Mob> ELITES = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	/** Layer subtitle: the layer each player was last told about, and a new one waiting to be seen 3 checks in a row. */
	private record LayerWatch(MobLevels.Layer shown, net.minecraft.resources.ResourceKey<Level> dimension, MobLevels.Layer pending, int count) {
	}

	private static final java.util.Map<java.util.UUID, LayerWatch> LAYERS = new java.util.HashMap<>();
	/** Set while Caller's helpers spawn: they take this level and never roll a rank. */
	private static int helperLevel;

	private MobService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.MOB_SCALING);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && scales(mob)) {
				MobData data = mob.getAttached(DATA);
				if (data == null && enabled()) {
					assign(mob, level);
				} else if (data != null && data.elite()) {
					ELITES.add(mob);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				ELITES.remove(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ELITES.clear();
			LAYERS.clear();
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAYERS.remove(handler.player.getUUID()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MobService::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(MobService::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(MobService::tick);
	}

	/** Monsters scale (neutral ones too); animals, villagers, golems, pets and the vanilla bosses do not. */
	public static boolean scales(Mob mob) {
		return mob instanceof Enemy && !(mob instanceof WitherBoss) && !(mob instanceof Warden) && !(mob instanceof ElderGuardian)
			&& !(mob instanceof EnderDragon);
	}

	public static MobData data(Entity entity) {
		return entity instanceof Mob mob ? mob.getAttached(DATA) : null;
	}

	// ---- giving a mob its level --------------------------------------------------------------------------------------

	private static void assign(Mob mob, ServerLevel level) {
		ServerConfig.Numbers n = ConfigStore.server().numbers;
		RandomSource random = mob.getRandom();
		BlockPos pos = mob.blockPosition();
		MobLevels.Layer layer = XpSources.layerAt(level, pos);
		int raw = MobLevels.raw(nearbyPlayerLevel(level, mob.position()), layer, n.mobLevelShare, n.cavesLevelBonus, n.deepslateLevelBonus);
		int cap = MobLevels.cap(layer, n.surfaceLevelCap, n.cavesLevelCap, n.deepslateLevelCap);
		int rank = MobData.NORMAL;
		int lvl;
		if (helperLevel > 0) {
			lvl = helperLevel;
		} else {
			if (ConfigStore.server().enabled(VestigesSystem.ELITES)) {
				rank = rollRank(random, layer);
			}
			lvl = Math.max(1, Math.min(raw, cap + MobLevels.overCap(rank)));
		}
		boolean deep = layer == MobLevels.Layer.DEEPSLATE || level.dimension() == Level.NETHER;
		MobData data = new MobData(lvl, rank, rollPowers(random, rank, deep), babySize(mob, level, rollSize(random)), false);
		apply(mob, data);
	}

	/** Average level of players within 64 blocks; with nobody that close, the nearest player in this world; else 1. */
	private static double nearbyPlayerLevel(ServerLevel level, Vec3 pos) {
		double sum = 0;
		int count = 0;
		ServerPlayer nearest = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double d = p.position().distanceToSqr(pos);
			if (d <= 64 * 64) {
				sum += LevelService.get(p).level();
				count++;
			}
			if (d < best) {
				best = d;
				nearest = p;
			}
		}
		if (count > 0) {
			return sum / count;
		}
		return nearest != null ? LevelService.get(nearest).level() : 1;
	}

	/** Spawn rates (approved): Warlord 0.2% (deepslate), Elite 1% (caves and deeper), Champion 3% (anywhere). */
	private static int rollRank(RandomSource random, MobLevels.Layer layer) {
		double r = random.nextDouble();
		if (layer == MobLevels.Layer.DEEPSLATE && r < 0.002) {
			return MobData.WARLORD;
		}
		if (layer != MobLevels.Layer.SURFACE && r < 0.012) {
			return MobData.ELITE;
		}
		return r < 0.042 ? MobData.CHAMPION : MobData.NORMAL;
	}

	private static List<String> rollPowers(RandomSource random, int rank, boolean deep) {
		if (rank == MobData.NORMAL) {
			return List.of();
		}
		List<String> pool = new ArrayList<>(POWERS);
		if (!deep) {
			pool.remove("withering");
		}
		List<String> out = new ArrayList<>();
		for (int i = 0; i < rank && !pool.isEmpty(); i++) {
			out.add(pool.remove(random.nextInt(pool.size())));
		}
		return List.copyOf(out);
	}

	/** Size: a bell curve, most 90-110%, rarely 80% or 125%. */
	private static float rollSize(RandomSource random) {
		double s = 1.0 + random.nextGaussian() * 0.065;
		return (float) Math.max(0.8, Math.min(1.25, s));
	}

	/** Babies inherit their size from the nearest grown mob of the same kind (bred hoglins, a zombie's baby nearby). */
	private static float babySize(Mob mob, ServerLevel level, float rolled) {
		if (!mob.isBaby()) {
			return rolled;
		}
		Mob parent = null;
		double best = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(6),
			m -> m != mob && m.getType() == mob.getType() && !m.isBaby() && m.getAttached(DATA) != null)) {
			double d = m.distanceToSqr(mob);
			if (d < best) {
				best = d;
				parent = m;
			}
		}
		return parent != null ? parent.getAttached(DATA).size() : rolled;
	}

	/** Health multiplier: +15% per level, times size and rank strength. */
	public static double healthMultiplier(MobData d) {
		return (1 + 0.15 * d.level()) * d.size() * d.strength();
	}

	/** Damage multiplier for everything this mob deals: +6% per level, times size, rank strength and Brute. */
	public static double damageMultiplier(LivingEntity attacker) {
		MobData d = data(attacker);
		if (d == null || !enabled()) {
			return 1.0;
		}
		return (1 + 0.06 * d.level()) * d.size() * d.strength() * (d.has("brute") ? 1.4 : 1.0);
	}

	/** Gives the mob its data and the matching attribute modifiers (permanent ones, saved with the mob, so they stay after a reload). */
	public static void apply(Mob mob, MobData d) {
		permanent(mob, Attributes.MAX_HEALTH, LEVEL_HEALTH, healthMultiplier(d) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		permanent(mob, Attributes.SCALE, SIZE, d.size() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		permanent(mob, Attributes.MOVEMENT_SPEED, SWIFT, d.has("swift") ? 0.3 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		permanent(mob, Attributes.ARMOR, ARMORED, d.has("armored") ? 10 : 0, AttributeModifier.Operation.ADD_VALUE);
		permanent(mob, Attributes.KNOCKBACK_RESISTANCE, ARMORED_KB, d.has("armored") ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
		mob.setHealth(mob.getMaxHealth());
		mob.setAttached(DATA, d);
		if (d.elite()) {
			ELITES.add(mob);
		} else {
			ELITES.remove(mob);
		}
	}

	/** Admin tool: gives a mob a rank (new powers) and optionally a level; its size stays. */
	public static void setRank(Mob mob, int rank, int level) {
		MobData old = mob.getAttached(DATA);
		boolean deep = XpSources.layerAt((ServerLevel) mob.level(), mob.blockPosition()) == MobLevels.Layer.DEEPSLATE
			|| mob.level().dimension() == Level.NETHER;
		int lvl = level > 0 ? level : old != null ? old.level() : 1;
		float size = old != null ? old.size() : rollSize(mob.getRandom());
		apply(mob, new MobData(lvl, rank, rollPowers(mob.getRandom(), rank, deep), size, false));
	}

	private static void permanent(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
								  double value, AttributeModifier.Operation op) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance inst = mob.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		if (value == 0) {
			inst.removeModifier(id);
		} else {
			inst.addOrReplacePermanentModifier(new AttributeModifier(id, value, op));
		}
	}

	// ---- powers ------------------------------------------------------------------------------------------------------

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (!enabled() || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		// Powers of the attacker, on a hit that landed.
		if (!blocked && taken > 0 && source.getEntity() instanceof Mob attacker && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
			MobData a = data(attacker);
			if (a != null && a.elite()) {
				if (a.has("burning")) {
					victim.igniteForSeconds(3);
				}
				if (a.has("venom")) {
					victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), attacker);
				}
				if (a.has("chilling")) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), attacker);
				}
				if (a.has("withering")) {
					victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), attacker);
				}
				if (a.has("shove") && source.getDirectEntity() == attacker) {
					Vec3 away = victim.position().subtract(attacker.position()).multiply(1, 0, 1);
					if (away.lengthSqr() > 1.0E-4) {
						away = away.normalize().scale(1.4);
						victim.push(away.x, 0.45, away.z);
						if (victim instanceof ServerPlayer sp) {
							sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
						}
					}
				}
			}
		}
		// Powers of the victim.
		MobData v = data(victim);
		if (v == null || !v.elite() || !(victim instanceof Mob mob)) {
			return;
		}
		if (v.has("spiked") && taken > 0 && !source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() instanceof LivingEntity hitter
			&& source.getDirectEntity() == hitter && hitter != victim) {
			hitter.hurtServer(level, level.damageSources().thorns(victim), taken * 0.2f);
		}
		if (v.has("caller") && !v.called() && victim.isAlive() && victim.getHealth() <= victim.getMaxHealth() / 2) {
			mob.setAttached(DATA, v.withCalled());
			callHelpers(mob, level, v.level());
		}
	}

	private static void callHelpers(Mob caller, ServerLevel level, int lvl) {
		helperLevel = lvl;
		try {
			for (int i = 0; i < 2; i++) {
				BlockPos pos = caller.blockPosition();
				for (int tries = 0; tries < 4; tries++) {
					BlockPos p = pos.offset(caller.getRandom().nextInt(5) - 2, 0, caller.getRandom().nextInt(5) - 2);
					if (level.noCollision(caller.getType().getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
						pos = p;
						break;
					}
				}
				Entity helper = caller.getType().spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
				if (helper instanceof Mob m && caller.getTarget() != null) {
					m.setTarget(caller.getTarget());
				}
			}
		} finally {
			helperLevel = 0;
		}
	}

	private static void onDeath(LivingEntity dead, DamageSource source) {
		MobData d = data(dead);
		if (d != null && d.has("volatile") && enabled() && dead.level() instanceof ServerLevel level) {
			// Explodes when it dies, without breaking blocks; the blast grows with its level.
			level.explode(dead, dead.getX(), dead.getY(0.5), dead.getZ(), volatilePower(d.level()), Level.ExplosionInteraction.NONE);
		}
	}

	/** Volatile's blast: power 2 at level 1, 4 at level 50, at most 5 (a creeper is 3). */
	public static float volatilePower(int level) {
		return Math.min(5.0f, 2.0f + level / 25.0f);
	}

	private static void tick(MinecraftServer server) {
		if (!enabled()) {
			return;
		}
		if (server.getTickCount() % 20 == 0) {
			watchLayers(server);
		}
		if (ELITES.isEmpty()) {
			return;
		}
		boolean second = server.getTickCount() % 20 == 0;
		for (Mob mob : new ArrayList<>(ELITES)) {
			if (!mob.isAlive()) {
				ELITES.remove(mob);
				continue;
			}
			MobData d = mob.getAttached(DATA);
			if (d == null) {
				continue;
			}
			if (d.has("burning")) {
				if (mob.isOnFire()) {
					mob.clearFire();
				}
				if (!mob.hasEffect(MobEffects.FIRE_RESISTANCE)) {
					mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
				}
			}
			if (d.has("shadow") && mob.tickCount % 300 == 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0));
			}
			if (second && d.has("mending") && mob.getHealth() < mob.getMaxHealth()) {
				mob.heal(mob.getMaxHealth() * 0.02f);
			}
		}
	}

	/** Tells a player "Caves: mobs up to level 40" when they settle in a new layer (not on joining). */
	private static void watchLayers(MinecraftServer server) {
		ServerConfig.Numbers n = ConfigStore.server().numbers;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) p.level();
			MobLevels.Layer now = XpSources.layerAt(level, p.blockPosition());
			LayerWatch w = LAYERS.get(p.getUUID());
			if (w == null) {
				LAYERS.put(p.getUUID(), new LayerWatch(now, level.dimension(), now, 0));
				continue;
			}
			boolean newDimension = w.dimension() != level.dimension();
			if (!newDimension && now == w.shown()) {
				if (w.count() != 0) {
					LAYERS.put(p.getUUID(), new LayerWatch(w.shown(), w.dimension(), now, 0));
				}
				continue;
			}
			int count = now == w.pending() ? w.count() + 1 : 1;
			if (count >= 3 || newDimension) {
				LAYERS.put(p.getUUID(), new LayerWatch(now, level.dimension(), now, 0));
				com.google.gson.JsonObject json = new com.google.gson.JsonObject();
				json.addProperty("name", layerName(level, now));
				json.addProperty("cap", MobLevels.cap(now, n.surfaceLevelCap, n.cavesLevelCap, n.deepslateLevelCap));
				LevelService.notice(p, "layer", json);
			} else {
				LAYERS.put(p.getUUID(), new LayerWatch(w.shown(), w.dimension(), now, count));
			}
		}
	}

	private static String layerName(ServerLevel level, MobLevels.Layer layer) {
		if (level.dimension() == Level.NETHER) {
			return "The Nether";
		}
		if (level.dimension() == Level.END) {
			return "The End";
		}
		return switch (layer) {
			case SURFACE -> "Surface";
			case CAVES -> "Caves";
			case DEEPSLATE -> "Deepslate";
		};
	}

	/** Burning elites ignore fire; used by the damage pipeline. */
	public static boolean immune(LivingEntity target, DamageSource source) {
		MobData d = data(target);
		return d != null && enabled() && d.has("burning") && source.is(DamageTypeTags.IS_FIRE);
	}

	/** Kill XP multiplier by rank (Champion x2, Elite x3, Warlord x5). */
	public static double xpMultiplier(MobData d) {
		return d == null ? 1.0 : d.strength();
	}

}
