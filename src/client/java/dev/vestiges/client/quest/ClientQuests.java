package dev.vestiges.client.quest;

import com.google.gson.JsonObject;
import dev.vestiges.net.QuestPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** What the client keeps about quests: the line the tracker shows. Everything else comes with the hub snapshot. */
public final class ClientQuests {
	private static JsonObject tracked = new JsonObject();

	private ClientQuests() {
	}

	public static void onTrack(JsonObject json) {
		tracked = json;
	}

	public static JsonObject tracked() {
		return tracked;
	}

	public static void onOffer(JsonObject json) {
		Minecraft.getInstance().setScreenAndShow(QuestBookScreen.offer(json));
	}

	public static void send(String op, String id, String arg) {
		if (ClientPlayNetworking.canSend(QuestPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new QuestPayloads.Action(op, id, arg));
		}
	}

	public static void reset() {
		tracked = new JsonObject();
	}
}
