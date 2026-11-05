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
	/** Features switched off only because something they need is off (they return with it). */
	private static final java.util.List<String> REMEMBERED = new java.util.ArrayList<>();
	private static final java.util.List<Land> LANDS = new java.util.ArrayList<>();
	private static String atlas = "";

	private ClientFeatures() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FeaturePayloads.State.TYPE, (payload, ctx) -> ctx.client().execute(() -> receive(ctx.client(), payload.json())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SYSTEMS.clear();
			LANDS.clear();
			atlas = "";
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
		REMEMBERED.clear();
		if (root.has("remembered")) {
			root.getAsJsonArray("remembered").forEach(e -> REMEMBERED.add(e.getAsString()));
		}
		LANDS.clear();
		atlas = root.has("atlas") ? root.get("atlas").getAsString() : "";
		if (root.has("lands")) {
			root.getAsJsonArray("lands").forEach(e -> {
				JsonObject o = e.getAsJsonObject();
				LANDS.add(new Land(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt()));
			});
		}
		operator = root.get("operator").getAsBoolean();
		sleep = root.get("sleep").getAsInt();
		if (root.get("prompt").getAsBoolean() && mc.gui.screen() == null) {
			mc.setScreenAndShow(new FeaturesScreen(null, true));
		}
	}

	public static boolean on(VestigesSystem system) {
		return system.core() || SYSTEMS.getOrDefault(system.id, system.onByDefault());
	}

	/** A continent's name and centre. */
	public record Land(String name, int x, int z) {
	}

	/** The continents of this world, empty in a vanilla world (the map then shows no continent names). */
	public static java.util.List<Land> lands() {
		return LANDS;
	}

	/** Where a block is: a continent's name, "an island", "the open sea", or null in a world without continents. */
	public static String placeAt(double x, double z) {
		int w = dev.vestiges.world.WorldAtlas.gridWidth();
		int h = dev.vestiges.world.WorldAtlas.gridHeight();
		if (atlas.length() != w * h) {
			return null;
		}
		int ix = (int) Math.floor((x - dev.vestiges.world.GlobeMap.X_MIN) / dev.vestiges.world.WorldAtlas.GRID_CELL);
		int iz = (int) Math.floor((z - dev.vestiges.world.GlobeMap.Z_MIN) / dev.vestiges.world.WorldAtlas.GRID_CELL);
		if (x < dev.vestiges.world.GlobeMap.X_MIN || z < dev.vestiges.world.GlobeMap.Z_MIN || x >= dev.vestiges.world.GlobeMap.X_MAX || z >= dev.vestiges.world.GlobeMap.Z_MAX) {
			return "the open sea";
		}
		ix = Math.min(ix, w - 1);
		iz = Math.min(iz, h - 1);
		char c = atlas.charAt(iz * w + ix);
		if (c == '.') {
			return "the open sea";
		}
		if (c == 'i') {
			return "an island";
		}
		int i = c - '0';
		return i >= 0 && i < LANDS.size() ? LANDS.get(i).name() : null;
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
		c.remembered.addAll(REMEMBERED);
		return c;
	}

	public static void send(String op, String value) {
		if (ClientPlayNetworking.canSend(FeaturePayloads.Change.TYPE)) {
			ClientPlayNetworking.send(new FeaturePayloads.Change(op, value));
		}
	}
}
