package dev.vestiges.client.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.net.FeaturePayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** What the server says is switched on in this world. Until it has said anything, everything counts as on. */
public final class ClientFeatures {
	private static final Map<String, Boolean> SYSTEMS = new HashMap<>();
	private static boolean operator;
	private static int sleep = 100;
	private static int stackLimit = 64;

	private ClientFeatures() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FeaturePayloads.State.TYPE, (payload, ctx) -> ctx.client().execute(() -> receive(ctx.client(), payload.json())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SYSTEMS.clear();
			operator = false;
		}));
	}

	private static void receive(Minecraft mc, String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonObject systems = root.getAsJsonObject("systems");
		for (VestigesSystem s : VestigesSystem.values()) {
			boolean on = !systems.has(s.id) || systems.get(s.id).getAsBoolean();
			SYSTEMS.put(s.id, on);
			if (mc.getSingleplayerServer() == null) {
				// On a remote server, the shared rules that also run here (tooltips, gear checks) must follow its switches.
				ConfigStore.server().set(s, on);
			}
		}
		if (mc.getSingleplayerServer() == null && root.has("brew_stack")) {
			ConfigStore.server().numbers.brewStack = root.get("brew_stack").getAsInt();
			ConfigStore.server().numbers.stackLimit = root.get("stack_limit").getAsInt();
		}
		stackLimit = root.get("stack_limit").getAsInt();
		operator = root.get("operator").getAsBoolean();
		sleep = root.get("sleep").getAsInt();
		if (root.get("prompt").getAsBoolean() && mc.gui.screen() == null) {
			mc.setScreenAndShow(new FeaturesScreen(null, true));
		}
	}

	public static boolean on(VestigesSystem system) {
		return system.core() || SYSTEMS.getOrDefault(system.id, system.onByDefault());
	}

	public static boolean operator() {
		return operator;
	}

	public static int stackLimit() {
		return stackLimit;
	}

	public static int sleepPercent() {
		return sleep;
	}

	/** A copy of the current switches, to edit on a screen. */
	public static ServerConfig copy() {
		ServerConfig c = new ServerConfig().normalize();
		for (VestigesSystem s : VestigesSystem.values()) {
			c.set(s, on(s));
		}
		return c;
	}

	public static void send(String op, String value) {
		if (ClientPlayNetworking.canSend(FeaturePayloads.Change.TYPE)) {
			ClientPlayNetworking.send(new FeaturePayloads.Change(op, value));
		}
	}
}
