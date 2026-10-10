package dev.vestiges.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.client.hud.XpHud;
import dev.vestiges.client.map.ClientWaypoints;
import dev.vestiges.net.VestigesPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Handles the server's small HUD notices: XP gains, level-ups, Paragon, and grave markers. */
public final class ClientNotices {
	private ClientNotices() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VestigesPayloads.Notice.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
			JsonObject json;
			try {
				json = JsonParser.parseString(payload.json()).getAsJsonObject();
			} catch (RuntimeException e) {
				Vestiges.LOGGER.warn("Vestiges: bad notice {}", payload.kind());
				return;
			}
			switch (payload.kind()) {
				case "xp" -> XpHud.gain(json.get("amount").getAsLong());
				case "levelup" -> XpHud.levelUp(json.get("level").getAsInt(), json.get("paragon").getAsInt());
				case "level" -> XpHud.setParagon(json.get("paragon").getAsInt());
				case "grave" -> ClientWaypoints.addGrave(json);
				case "grave_removed" -> ClientWaypoints.removeGrave(json.get("id").getAsInt());
				case "graves" -> ClientWaypoints.syncGraves(json.getAsJsonArray("graves"));
				case "thick_hide" -> dev.vestiges.client.tree.ClientTree.onThickHide(json);
				case "discovery" -> XpHud.discovery(json.get("key").getAsString(), json.get("xp").getAsLong());
				default -> Vestiges.LOGGER.debug("Vestiges: unknown notice {}", payload.kind());
			}
		}));
	}
}
