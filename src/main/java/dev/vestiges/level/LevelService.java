package dev.vestiges.level;

import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.net.VestigesPayloads;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Owns the player's level. The vanilla XP bar is a mirror of it (level number and progress); vanilla XP orbs no longer feed it (they only
 * repair through Mending). Every gain goes through {@link #addXp}, which also tells the client for the "+XP" pop-up.
 */
public final class LevelService {
	public static final AttachmentType<PlayerLevel> LEVEL = AttachmentRegistry.create(Vestiges.id("level"),
		builder -> builder.persistent(PlayerLevel.CODEC).copyOnDeath());
	private static final Identifier HEALTH_MODIFIER = Vestiges.id("level_health");
	/** True while Vestiges itself writes the vanilla XP fields, so the XP mixins let it through. */
	public static boolean syncing;

	private LevelService() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> refresh(handler.player)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				long lost = applyDeathPenalty(player);
				if (lost > 0) {
					PlayerLevel l = get(player);
					player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You lost " + lost + " XP, but you keep level " + l.level() + ".")
						.withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
		});
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.CHARACTER);
	}

	public static PlayerLevel get(ServerPlayer player) {
		PlayerLevel l = player.getAttached(LEVEL);
		return l != null ? l : PlayerLevel.START;
	}

	public static void set(ServerPlayer player, PlayerLevel level) {
		player.setAttached(LEVEL, level);
		refresh(player);
	}

	/** Adds XP (already scaled by its source), applies the tree and server multipliers, handles level-ups and tells the client. Returns the XP given. */
	public static long addXp(ServerPlayer player, long amount, String reason) {
		if (!enabled() || amount <= 0 || player.isSpectator()) {
			return 0;
		}
		long scaled = Math.max(1, Math.round(dev.vestiges.tree.TreeHooks.xp(player, amount, reason) * ConfigStore.server().numbers.xpMultiplier));
		PlayerLevel before = get(player);
		PlayerLevel after = before.addXp(scaled);
		player.setAttached(LEVEL, after);
		JsonObject xp = new JsonObject();
		xp.addProperty("amount", scaled);
		xp.addProperty("reason", reason);
		notice(player, "xp", xp);
		if (after.level() != before.level() || after.paragon() != before.paragon()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);
			JsonObject up = new JsonObject();
			up.addProperty("level", after.level());
			up.addProperty("paragon", after.paragon());
			notice(player, "levelup", up);
			refresh(player);
		} else {
			syncBar(player, after);
		}
		return scaled;
	}

	/** Re-applies stats (max health) and re-syncs the XP bar, e.g. on join, respawn or a level change. */
	public static void refresh(ServerPlayer player) {
		PlayerLevel l = get(player);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			double bonus = enabled() ? LevelCurve.bonusHealth(l.level(), l.paragon()) : 0;
			health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
		syncBar(player, l);
		JsonObject info = new JsonObject();
		info.addProperty("level", l.level());
		info.addProperty("paragon", l.paragon());
		notice(player, "level", info);
	}

	/** Mirrors the level into the vanilla XP bar (level number + progress). */
	static void syncBar(ServerPlayer player, PlayerLevel l) {
		if (!enabled()) {
			return;
		}
		syncing = true;
		try {
			player.experienceLevel = l.level();
			player.experienceProgress = (float) Math.min(0.999, l.xp() / (double) Math.max(1, l.need()));
			player.totalExperience = (int) Math.min(Integer.MAX_VALUE, LevelCurve.totalTo(l.level()) + l.xp());
			player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
		} finally {
			syncing = false;
		}
	}

	/** Death penalty: part of the current level's progress, never a level. Returns the XP lost. */
	public static long applyDeathPenalty(ServerPlayer player) {
		if (!enabled()) {
			return 0;
		}
		PlayerLevel l = get(player);
		long loss = LevelCurve.deathLoss(l.level(), l.xp());
		if (loss > 0) {
			player.setAttached(LEVEL, l.withXp(l.level(), l.xp() - loss, l.paragon()));
		}
		return loss;
	}

	/** Damage multiplier for everything this player deals (levels + Paragon). */
	public static double damageMultiplier(ServerPlayer player) {
		if (!enabled()) {
			return 1.0;
		}
		PlayerLevel l = get(player);
		return LevelCurve.damageMultiplier(l.level(), l.paragon());
	}

	public static void notice(ServerPlayer player, String kind, JsonObject json) {
		if (ServerPlayNetworking.canSend(player, VestigesPayloads.Notice.TYPE)) {
			ServerPlayNetworking.send(player, new VestigesPayloads.Notice(kind, json.toString()));
		}
	}
}
