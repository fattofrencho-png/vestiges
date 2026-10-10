package dev.vestiges.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.net.MapPayloads;
import dev.vestiges.waystone.WaystoneService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Receives map and waystone actions from clients and sends events back. */
public final class MapNet {
	static final Gson GSON = new Gson();

	private MapNet() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(MapPayloads.Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));
	}

	private static void handle(ServerPlayer player, MapPayloads.Action a) {
		JsonObject json;
		try {
			json = a.json().isEmpty() ? new JsonObject() : JsonParser.parseString(a.json()).getAsJsonObject();
		} catch (RuntimeException e) {
			Vestiges.LOGGER.warn("Vestiges: bad map action '{}' from {}", a.action(), player.getName().getString());
			return;
		}
		switch (a.action()) {
			case "pin" -> PinManager.pin(player, json);
			case "unpin" -> PinManager.unpin(player, true);
			case "share" -> share(player, json);
			case "waystone_travel" -> WaystoneService.travel(player, json.get("from").getAsInt(), json.get("to").getAsInt());
			case "waystone_rename" -> WaystoneService.rename(player, json.get("id").getAsInt(), json.get("name").getAsString());
			case "waystone_share" -> WaystoneService.share(player, json.get("id").getAsInt(), json.get("player").getAsString());
			default -> Vestiges.LOGGER.warn("Vestiges: unknown map action '{}'", a.action());
		}
	}

	/** Relays a waypoint to another online player. Their client decides (ask, auto-add, off or blocked). */
	private static void share(ServerPlayer from, JsonObject json) {
		String targetName = json.has("target") ? json.get("target").getAsString() : "";
		ServerPlayer target = from.level().getServer().getPlayerList().getPlayerByName(targetName);
		if (target == null || target == from) {
			message(from, targetName.isEmpty() ? "Type a player name to share with." : targetName + " is not online.", true);
			return;
		}
		JsonObject out = new JsonObject();
		out.addProperty("from", from.getName().getString());
		out.add("waypoint", json.get("waypoint"));
		send(target, "shared", out);
		message(from, "Shared with " + target.getName().getString() + ".", false);
	}

	public static void send(ServerPlayer player, String event, JsonObject json) {
		ServerPlayNetworking.send(player, new MapPayloads.Event(event, GSON.toJson(json)));
	}

	/** A short line shown on the player's screen by the map or terminal (and in chat). */
	public static void message(ServerPlayer player, String text, boolean error) {
		JsonObject o = new JsonObject();
		o.addProperty("text", text);
		o.addProperty("error", error);
		send(player, "message", o);
	}
}
