package dev.vestiges.dragon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.Vec3;

/**
 * The signature moves of the first dragons, run every tick for the dragon that stands in the End. A dragon uses the moves its data file lists:
 * {@code fire_ring} (Belaz), {@code wing_shove} (Morst) and {@code crystal_regrow} (Dhars); {@code phantoms} and {@code shade} follow in
 * {@link DragonMoves2}. Nothing here places or removes a block except the regrown crystals, so the arena stays as it was.
 */
public final class DragonMoves {
	private static boolean wasSitting;
	private static Vec3 ringCenter = Vec3.ZERO;
	private static long ringUntil = -1;
	private static long shoveAt = -1;
	private static long shoveCooldownUntil;
	private static Vec3 shoveHeading = Vec3.ZERO;
	private static final List<Vec3> DESTROYED = new ArrayList<>();
	private static long regrowAt = -1;
	private static int waves;

	/** Counters and the last values, for the automatic checks. */
	public static int rings;
	public static int shoves;
	public static int regrows;
	public static Vec3 lastShove = Vec3.ZERO;
	/** For the automatic checks: shorter waits (-1 = the real ones). */
	public static int regrowDelayOverride = -1;
	public static int regrowWarnOverride = -1;

	private DragonMoves() {
	}

	/** A new fight (or the end of one): nothing is pending any more. */
	public static void reset() {
		wasSitting = false;
		ringUntil = -1;
		shoveAt = -1;
		shoveCooldownUntil = 0;
		DESTROYED.clear();
		regrowAt = -1;
		waves = 0;
		DragonMoves2.reset();
	}

	public static void tick(MinecraftServer server) {
		if (!DragonService.enabled()) {
			return;
		}
		ServerLevel end = server.getLevel(Level.END);
		EnderDragon dragon = end == null ? null : DragonService.live(end);
		if (dragon == null) {
			return;
		}
		DragonDef def = DragonService.def(DragonService.mark(dragon).number());
		if (def == null) {
			return;
		}
		long now = end.getGameTime();
		boolean sitting = dragon.getPhaseManager().getCurrentPhase().isSitting();
		if (def.has("fire_ring")) {
			ring(end, dragon, def, now, sitting);
		}
		if (def.has("wing_shove")) {
			shove(end, dragon, now, sitting);
		}
		if (def.has("crystal_regrow")) {
			regrow(end, now);
		}
		DragonMoves2.tick(end, dragon, def, now, sitting, wasSitting);
		wasSitting = sitting;
	}

	// ---- Belaz: the ring of fire --------------------------------------------------------------------------------------

	private static void ring(ServerLevel end, EnderDragon dragon, DragonDef def, long now, boolean sitting) {
		if (sitting && !wasSitting) {
			ringCenter = dragon.position();
			ringUntil = now + MoveRules.RING_TICKS;
			rings++;
			end.playSound(null, ringCenter.x, ringCenter.y, ringCenter.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 4.0f, 0.6f);
		}
		if (now >= ringUntil) {
			return;
		}
		if (now % 4 == 0) {
			double mid = (MoveRules.RING_INNER + MoveRules.RING_OUTER) / 2;
			for (int i = 0; i < 24; i++) {
				double a = i * Math.PI * 2 / 24 + now * 0.05;
				end.sendParticles(ParticleTypes.FLAME, ringCenter.x + Math.cos(a) * mid, ringCenter.y + 0.3, ringCenter.z + Math.sin(a) * mid, 1, 1.2, 0.1, 1.2, 0.01);
			}
		}
		if (now % MoveRules.RING_HIT_EVERY != 0) {
			return;
		}
		float hit = (float) (MoveRules.RING_DAMAGE * def.damage());
		for (ServerPlayer p : end.players()) {
			if (!p.isSpectator() && p.isAlive() && MoveRules.inRing(p.getX() - ringCenter.x, p.getY() - ringCenter.y, p.getZ() - ringCenter.z)) {
				p.igniteForSeconds(MoveRules.RING_BURN_SECONDS);
				p.hurtServer(end, end.damageSources().inFire(), hit);
			}
		}
	}

	// ---- Morst: the wing shove ----------------------------------------------------------------------------------------

	private static void shove(ServerLevel end, EnderDragon dragon, long now, boolean sitting) {
		if (shoveAt >= 0) {
			// The warning is running; sparks and a growl show where the wings will sweep.
			if (now % 4 == 0) {
				end.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY(), dragon.getZ(), 8, 3.0, 1.0, 3.0, 0.02);
			}
			if (now >= shoveAt) {
				doShove(end, dragon);
				shoveAt = -1;
				shoveCooldownUntil = now + MoveRules.SHOVE_COOLDOWN_TICKS;
			}
			return;
		}
		if (sitting || now < shoveCooldownUntil || now % 10 != 0) {
			return;
		}
		for (ServerPlayer p : end.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceTo(dragon) <= MoveRules.SHOVE_NEAR) {
				Vec3 move = dragon.getDeltaMovement();
				shoveHeading = new Vec3(move.x, 0, move.z);
				shoveAt = now + MoveRules.SHOVE_WARN_TICKS;
				end.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0f, 0.7f);
				return;
			}
		}
	}

	private static void doShove(ServerLevel end, EnderDragon dragon) {
		Vec3 heading = shoveHeading;
		if (heading.horizontalDistanceSqr() < 1e-6) {
			Vec3 look = dragon.getViewVector(1.0f);
			heading = new Vec3(look.x, 0, look.z);
		}
		for (ServerPlayer p : end.players()) {
			if (p.isSpectator() || !p.isAlive() || p.distanceTo(dragon) > MoveRules.SHOVE_REACH) {
				continue;
			}
			double[] s = MoveRules.sideways(heading.x, heading.z, p.getX() - dragon.getX(), p.getZ() - dragon.getZ());
			Vec3 push = new Vec3(s[0] * MoveRules.SHOVE_SPEED, MoveRules.SHOVE_LIFT, s[1] * MoveRules.SHOVE_SPEED);
			p.setDeltaMovement(p.getDeltaMovement().add(push));
			p.needsSync = true;
			lastShove = push;
		}
		shoves++;
		end.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 6.0f, 0.5f);
	}

	// ---- Dhars: crystals that grow back -------------------------------------------------------------------------------

	/** A pillar crystal was destroyed in a fight with a dragon that regrows them: remember where, and start the clock if it is not running. */
	public static void crystalDestroyed(EnderDragonFight fight, EndCrystal crystal) {
		if (!DragonService.enabled() || !(crystal.level() instanceof ServerLevel end)) {
			return;
		}
		EnderDragon dragon = DragonService.live(end);
		DragonDef def = dragon == null ? null : DragonService.def(DragonService.mark(dragon).number());
		if (def == null || !def.has("crystal_regrow")) {
			return;
		}
		DESTROYED.add(crystal.position());
		if (regrowAt < 0 && MoveRules.wavesLeft(waves)) {
			regrowAt = end.getGameTime() + (regrowDelayOverride >= 0 ? regrowDelayOverride : MoveRules.REGROW_DELAY_TICKS);
		}
	}

	private static void regrow(ServerLevel end, long now) {
		if (regrowAt < 0) {
			return;
		}
		int warn = regrowWarnOverride >= 0 ? regrowWarnOverride : MoveRules.REGROW_WARN_TICKS;
		List<Vec3> coming = MoveRules.last(DESTROYED, MoveRules.REGROW_COUNT);
		if (now >= regrowAt - warn && now % 5 == 0) {
			for (Vec3 at : coming) {
				end.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 12, 0.6, 0.8, 0.6, 0.1);
			}
			if (now % 20 == 0 && !coming.isEmpty()) {
				end.playSound(null, coming.get(0).x, coming.get(0).y, coming.get(0).z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 4.0f, 1.4f);
			}
		}
		if (now < regrowAt) {
			return;
		}
		for (Vec3 at : new ArrayList<>(coming)) {
			EndCrystal crystal = new EndCrystal(end, at.x, at.y, at.z);
			crystal.setShowBottom(false);
			end.addFreshEntity(crystal);
			DESTROYED.remove(at);
			regrows++;
		}
		waves++;
		regrowAt = -1;
		if (!DESTROYED.isEmpty() && MoveRules.wavesLeft(waves)) {
			regrowAt = now + (regrowDelayOverride >= 0 ? regrowDelayOverride : MoveRules.REGROW_DELAY_TICKS);
		}
	}

	public static int wavesRun() {
		return waves;
	}
}
