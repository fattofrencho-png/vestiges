package dev.vestiges.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Stops a modified client from lagging the server by asking for big snapshots many times a second. Normal play never gets near this. */
public final class RequestLimit {
	/** 5 answers a second for each kind of request. */
	private static final int TICKS = 4;
	private static final Map<UUID, Map<String, Integer>> LAST = new HashMap<>();

	private RequestLimit() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
	}

	public static boolean allow(ServerPlayer player, String kind) {
		int now = player.level().getServer().getTickCount();
		Map<String, Integer> last = LAST.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		Integer before = last.get(kind);
		if (before != null && now >= before && now - before < TICKS) {
			return false;
		}
		last.put(kind, now);
		return true;
	}
}
