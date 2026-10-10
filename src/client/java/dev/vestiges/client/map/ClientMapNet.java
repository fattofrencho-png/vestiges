package dev.vestiges.client.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.waystone.WaystoneTerminalScreen;
import dev.vestiges.config.ClientConfig;
import dev.vestiges.net.MapPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Client side of map and waystone messages. */
public final class ClientMapNet {
	private static final Gson GSON = new Gson();
	private static final SystemToast.SystemToastId SHARE_TOAST = new SystemToast.SystemToastId(6000L);
	/** The last short message from the server, shown in the map's footer. */
	public static String lastMessage = "";
	public static boolean lastError;

	private ClientMapNet() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MapPayloads.Event.TYPE, (payload, ctx) -> ctx.client().execute(() -> handle(payload)));
	}

	public static void send(String action, JsonObject json) {
		if (ClientPlayNetworking.canSend(MapPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new MapPayloads.Action(action, GSON.toJson(json)));
		}
	}

	private static void handle(MapPayloads.Event e) {
		Minecraft mc = Minecraft.getInstance();
		JsonObject json;
		try {
			json = JsonParser.parseString(e.json()).getAsJsonObject();
		} catch (RuntimeException ex) {
			Vestiges.LOGGER.warn("Vestiges: bad map event {}", e.event());
			return;
		}
		switch (e.event()) {
			case "message" -> {
				lastMessage = json.get("text").getAsString();
				lastError = json.get("error").getAsBoolean();
				if (mc.player != null) {
					mc.player.sendSystemMessage(Component.literal(lastMessage).withStyle(lastError ? ChatFormatting.RED : ChatFormatting.GREEN));
				}
			}
			case "shared" -> {
				ClientConfig cfg = ClientConfigStore.get();
				String from = json.get("from").getAsString();
				if ("off".equals(cfg.shareMode) || cfg.blockedPlayers.contains(from)) {
					return;
				}
				ClientWaypoints.receiveShared(from, json.get("waypoint"));
				SystemToast.add(mc.gui.toastManager(), SHARE_TOAST, Component.literal("Waypoint shared"),
					Component.literal(from + " shared a place with you. Open the map (M) to add it."));
			}
			case "waystones" -> ClientWaypoints.receiveWaystones(json.getAsJsonArray("waystones"));
			case "waystone_terminal" -> mc.setScreenAndShow(new WaystoneTerminalScreen(json));
			default -> Vestiges.LOGGER.debug("Vestiges: unknown map event {}", e.event());
		}
	}
}
