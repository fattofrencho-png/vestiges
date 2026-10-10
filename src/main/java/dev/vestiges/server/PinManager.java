package dev.vestiges.server;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.waypoints.Waypoint;

/**
 * "Go there": pins one target per player on the vanilla Locator Bar, by sending the player a tracked waypoint of their own. The pin clears
 * itself when the player arrives (within 6 blocks), changes dimension, or presses "Clear" on the map.
 */
public final class PinManager {
	private record Pin(String dimension, BlockPos pos) {
	}

	private static final Map<UUID, Pin> PINS = new HashMap<>();
	private static final double ARRIVE_SQ = 6 * 6;

	private PinManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0 || PINS.isEmpty()) {
				return;
			}
			for (UUID id : Map.copyOf(PINS).keySet()) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				Pin pin = PINS.get(id);
				if (p == null) {
					PINS.remove(id);
					continue;
				}
				if (!p.level().dimension().identifier().toString().equals(pin.dimension())) {
					unpin(p, false);
				} else if (p.blockPosition().distSqr(pin.pos()) <= ARRIVE_SQ) {
					unpin(p, false);
					MapNet.message(p, "You have arrived.", false);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PINS.remove(handler.player.getUUID()));
	}

	static void pin(ServerPlayer player, JsonObject json) {
		String dim = json.get("dimension").getAsString();
		if (!player.level().dimension().identifier().toString().equals(dim)) {
			MapNet.message(player, "That place is in another dimension.", true);
			return;
		}
		BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
		int color = json.has("color") ? json.get("color").getAsInt() : 0xFFFFFF;
		if (PINS.containsKey(player.getUUID())) {
			player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(pinId(player)));
		}
		Waypoint.Icon icon = new Waypoint.Icon();
		icon.color = Optional.of(color & 0xFFFFFF);
		player.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(pinId(player), icon, pos));
		PINS.put(player.getUUID(), new Pin(dim, pos));
		String label = json.has("label") ? json.get("label").getAsString() : "your target";
		MapNet.message(player, "Pinned " + label + " on your Locator Bar.", false);
	}

	static void unpin(ServerPlayer player, boolean tell) {
		boolean had = PINS.remove(player.getUUID()) != null;
		if (had) {
			player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(pinId(player)));
		}
		if (tell) {
			MapNet.message(player, had ? "Pin cleared from your Locator Bar." : "There was no pin to clear.", false);
		}
	}

	private static UUID pinId(ServerPlayer player) {
		return UUID.nameUUIDFromBytes(("vestiges-pin:" + player.getUUID()).getBytes(StandardCharsets.UTF_8));
	}
}
