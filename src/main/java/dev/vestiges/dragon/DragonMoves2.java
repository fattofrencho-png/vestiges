package dev.vestiges.dragon;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * The moves of dragons 5 and 6. {@code phantoms} (Ilvig): every time it perches, and every 30 seconds it keeps perching, two phantoms join the
 * fight, never more than five alive; they burn away when the dragon is gone. {@code shade} (Durhos): in flight it flickers out of sight but a faint
 * smoke always marks where it is; when it perches it is plain to see and the light of the End dims (Darkness) for everyone near.
 */
public final class DragonMoves2 {
	/** The tag every phantom of the swarm carries. */
	public static final String SWARM_TAG = "vestiges_swarm";

	private static long swarmNextAt = -1;
	private static boolean shadeHidden;

	/** Counters, for the automatic checks. */
	public static int swarmed;
	public static int hidden;
	public static int darkened;
	/** For the automatic checks: ticks between two swarms while perching (-1 = the real 30 seconds). */
	public static int swarmEveryOverride = -1;

	private DragonMoves2() {
	}

	static void reset() {
		swarmNextAt = -1;
		shadeHidden = false;
	}

	static void tick(ServerLevel end, EnderDragon dragon, DragonDef def, long now, boolean sitting, boolean wasSitting) {
		if (def.has("phantoms")) {
			phantoms(end, dragon, now, sitting);
		}
		if (def.has("shade")) {
			shade(end, dragon, now, sitting, wasSitting);
		}
	}

	// ---- Ilvig: the swarm --------------------------------------------------------------------------------------------

	/** The phantoms of the swarm that are alive in the End. */
	public static List<? extends Phantom> swarm(ServerLevel end) {
		return end.getEntities(EntityTypeTest.forClass(Phantom.class), p -> p.entityTags().contains(SWARM_TAG));
	}

	private static void phantoms(ServerLevel end, EnderDragon dragon, long now, boolean sitting) {
		if (!sitting) {
			swarmNextAt = -1;
			return;
		}
		if (swarmNextAt >= 0 && now < swarmNextAt) {
			return;
		}
		swarmNextAt = now + (swarmEveryOverride >= 0 ? swarmEveryOverride : MoveRules.SWARM_EVERY_TICKS);
		int n = MoveRules.swarmToSpawn(swarm(end).size());
		RandomSource random = end.getRandom();
		EntityType<?> phantomType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:phantom")).orElse(null);
		for (int i = 0; i < n; i++) {
			if (phantomType == null || !(phantomType.create(end, EntitySpawnReason.EVENT) instanceof Phantom phantom)) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 5 + random.nextDouble() * 5;
			phantom.snapTo(dragon.getX() + Math.cos(angle) * radius, dragon.getY() + 4 + random.nextDouble() * 4, dragon.getZ() + Math.sin(angle) * radius,
				(float) (random.nextDouble() * 360), 0f);
			phantom.addTag(SWARM_TAG);
			phantom.setPersistenceRequired();
			ServerPlayer target = end.getNearestPlayer(phantom, 80.0) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
			if (target != null) {
				phantom.setTarget(target);
			}
			end.addFreshEntity(phantom);
			swarmed++;
		}
		if (n > 0) {
			end.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 6.0f, 0.6f);
		}
	}

	/** The swarm burns away when the dragon is gone (killed, lost or reset). */
	public static void clearSwarm(ServerLevel end) {
		for (Phantom p : swarm(end)) {
			end.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
			p.discard();
		}
	}

	// ---- Durhos: the shade -------------------------------------------------------------------------------------------

	private static void shade(ServerLevel end, EnderDragon dragon, long now, boolean sitting, boolean wasSitting) {
		boolean hide = !sitting && MoveRules.shadeHidden(now);
		if (hide != shadeHidden) {
			shadeHidden = hide;
			dragon.setInvisible(hide);
			if (hide) {
				hidden++;
			}
		}
		if (hide && now % 4 == 0) {
			// Always a faint shadow: smoke around the body and the head.
			end.sendParticles(ParticleTypes.SMOKE, dragon.getX(), dragon.getY() + 1.5, dragon.getZ(), 6, 3.0, 1.0, 3.0, 0.005);
			end.sendParticles(ParticleTypes.SMOKE, dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), 3, 0.8, 0.5, 0.8, 0.005);
		}
		// The light dims the moment it lands, and stays dim while it sits.
		if (sitting && (!wasSitting || now % 40 == 0)) {
			for (ServerPlayer p : end.players()) {
				if (!p.isSpectator() && p.distanceTo(dragon) <= MoveRules.DARKNESS_RADIUS) {
					p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, false));
					darkened++;
				}
			}
		}
	}

	/** The dragon is gone or no longer a shade: it is visible again. */
	public static void finish(EnderDragon dragon) {
		dragon.setInvisible(false);
		shadeHidden = false;
	}
}
