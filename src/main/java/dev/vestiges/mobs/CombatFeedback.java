package dev.vestiges.mobs;

import dev.vestiges.Vestiges;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Damage numbers (approved): each hit you land sends you the damage dealt, marked gold when it was a critical hit. */
public final class CombatFeedback {
	/** One hit: the entity hit, the damage it took, and whether it was a crit. */
	public record DamageNumber(int entityId, float amount, boolean crit) implements CustomPacketPayload {
		public static final Type<DamageNumber> TYPE = new Type<>(Vestiges.id("damage_number"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DamageNumber> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DamageNumber::entityId,
			ByteBufCodecs.FLOAT, DamageNumber::amount,
			ByteBufCodecs.BOOL, DamageNumber::crit,
			DamageNumber::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Last crit per attacker: target id and server tick (vanilla jump crits and tree crits both count). */
	private record Crit(int target, long tick) {
	}

	/** A hit waiting for the end of the tick: vanilla marks a jump crit only after the damage is dealt. */
	private record Pending(ServerPlayer attacker, int target, float amount) {
	}

	private static final Map<UUID, Crit> CRITS = new HashMap<>();
	private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

	private CombatFeedback() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DamageNumber.TYPE, DamageNumber.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0 && source.getEntity() instanceof ServerPlayer attacker && attacker != victim
				&& ServerPlayNetworking.canSend(attacker, DamageNumber.TYPE)) {
				PENDING.add(new Pending(attacker, victim.getId(), taken));
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (Pending p : PENDING) {
				Crit c = CRITS.get(p.attacker().getUUID());
				boolean crit = c != null && c.target() == p.target() && now - c.tick() <= 1;
				ServerPlayNetworking.send(p.attacker(), new DamageNumber(p.target(), p.amount(), crit));
			}
			PENDING.clear();
			CRITS.values().removeIf(c -> now - c.tick() > 1);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			CRITS.clear();
		});
	}

	public static void markCrit(ServerPlayer attacker, Entity target) {
		CRITS.put(attacker.getUUID(), new Crit(target.getId(), attacker.level().getServer().getTickCount()));
	}
}
