package dev.vestiges.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.net.VestigesPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** The last snapshot the server sent this client. Screens read it and ask for a fresh one when they open. */
public final class ClientData {
	private static JsonObject snapshot = new JsonObject();
	private static boolean loaded;

	private ClientData() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			snapshot = new JsonObject();
			loaded = false;
		});
		ClientPlayNetworking.registerGlobalReceiver(VestigesPayloads.HubData.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
			try {
				snapshot = JsonParser.parseString(payload.json()).getAsJsonObject();
				loaded = true;
			} catch (RuntimeException e) {
				Vestiges.LOGGER.error("Vestiges: bad hub snapshot", e);
			}
			if (Minecraft.getInstance().gui.screen() instanceof HubScreen screen) {
				screen.onData();
			}
		}));
	}

	public static void request(String section) {
		if (ClientPlayNetworking.canSend(VestigesPayloads.HubRequest.TYPE)) {
			ClientPlayNetworking.send(new VestigesPayloads.HubRequest(section));
		}
	}

	public static JsonObject snapshot() {
		return snapshot;
	}

	public static boolean loaded() {
		return loaded;
	}
}
