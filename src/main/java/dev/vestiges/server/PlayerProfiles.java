package dev.vestiges.server;

import dev.vestiges.ModAttachments;
import dev.vestiges.PlayerProfile;
import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Gives every player a Vestiges profile the first time they join a world. */
public final class PlayerProfiles {
	private PlayerProfiles() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (player.getAttached(ModAttachments.PROFILE) == null) {
				player.setAttached(ModAttachments.PROFILE, new PlayerProfile(PlayerProfile.CURRENT_SCHEMA, server.overworld().getGameTime()));
				Vestiges.LOGGER.info("Vestiges: new profile for {}", player.getName().getString());
			}
		});
	}

	public static PlayerProfile get(ServerPlayer player) {
		PlayerProfile p = player.getAttached(ModAttachments.PROFILE);
		return p != null ? p : new PlayerProfile(PlayerProfile.CURRENT_SCHEMA, 0L);
	}
}
