package dev.vestiges.client.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.Vestiges;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.gear.GearService;
import dev.vestiges.net.TreePayloads;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.PlayerTree;
import dev.vestiges.tree.StatEngine;
import dev.vestiges.tree.TreeDefs;
import dev.vestiges.tree.TreeHooks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The client's copy of the passive trees: the content the server sent (wheels and ascendancies) and the player's own tree state. It also
 * computes the player's stats locally, for the few effects the client must know too (mining speed, crossbow reload).
 */
public final class ClientTree {
	private static final Map<String, TreeDefs.Wheel> WHEELS = new HashMap<>();
	private static final List<TreeDefs.Ascendancy> ASCENDANCIES = new ArrayList<>();
	private static JsonObject state = new JsonObject();
	private static PlayerTree tree = PlayerTree.EMPTY;
	private static PlayerStats stats = PlayerStats.NONE;
	private static boolean enabled;
	/** The server's gear rules (rarity, ladder, emerald costs) are on. */
	private static boolean gear;
	private static String message = "";
	private static long messageAt;
	private static long thickHideReadyAt;
	private static long thickHideCooldown = 1;

	private ClientTree() {
	}

	public static void register() {
		// The client's own stats: its tree plus its trims (for cost previews and mining speed).
		TreeHooks.clientStats = p -> {
			if (p != Minecraft.getInstance().player) {
				return PlayerStats.NONE;
			}
			if (!gear) {
				return stats;
			}
			List<ItemStack> armor = new ArrayList<>();
			for (EquipmentSlot slot : GearService.ARMOR) {
				armor.add(p.getItemBySlot(slot));
			}
			return PlayerStats.merge(stats, GearService.trimStats(armor));
		};
		GearLadder.clientOn = p -> gear && p == Minecraft.getInstance().player;
		// With the gear rules on, the vanilla XP bar mirrors the Vestiges level.
		GearLadder.clientLevel = p -> gear ? p.experienceLevel : Integer.MAX_VALUE;
		ClientPlayNetworking.registerGlobalReceiver(TreePayloads.Defs.TYPE, (payload, ctx) -> ctx.client().execute(() -> onDefs(payload.kind(), payload.json())));
		ClientPlayNetworking.registerGlobalReceiver(TreePayloads.State.TYPE, (payload, ctx) -> ctx.client().execute(() -> onState(payload.json())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			WHEELS.clear();
			ASCENDANCIES.clear();
			state = new JsonObject();
			tree = PlayerTree.EMPTY;
			stats = PlayerStats.NONE;
			enabled = false;
			gear = false;
			thickHideReadyAt = 0;
		}));
	}

	private static void onDefs(String kind, String json) {
		try {
			switch (kind) {
				case "clear" -> {
					WHEELS.clear();
					ASCENDANCIES.clear();
				}
				case "wheel" -> TreeDefs.Wheel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result()
					.ifPresent(w -> WHEELS.put(w.wheel(), w));
				case "ascendancies" -> {
					ASCENDANCIES.clear();
					JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
					for (JsonElement e : arr) {
						TreeDefs.Ascendancy.CODEC.parse(JsonOps.INSTANCE, e).result().ifPresent(ASCENDANCIES::add);
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Vestiges.LOGGER.error("Vestiges: bad tree data ({})", kind, e);
		}
		recompute();
	}

	private static void onState(String json) {
		try {
			state = JsonParser.parseString(json).getAsJsonObject();
			enabled = state.get("enabled").getAsBoolean();
			gear = state.has("gear") && state.get("gear").getAsBoolean();
			tree = PlayerTree.CODEC.parse(JsonOps.INSTANCE, state.get("tree")).result().orElse(PlayerTree.EMPTY);
			String msg = state.get("message").getAsString();
			if (!msg.isEmpty()) {
				message = msg;
				messageAt = System.currentTimeMillis();
			}
		} catch (RuntimeException e) {
			Vestiges.LOGGER.error("Vestiges: bad tree state", e);
		}
		recompute();
	}

	private static void recompute() {
		stats = enabled ? StatEngine.compute(tree, WHEELS::get, ClientTree::ascendancy) : PlayerStats.NONE;
		if (Minecraft.getInstance().gui.screen() instanceof TreeScreen screen) {
			screen.onData();
		}
	}

	public static void send(String op, String wheel, String node, int value) {
		if (ClientPlayNetworking.canSend(TreePayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new TreePayloads.Action(op, wheel, node, value));
		}
	}

	public static void request() {
		send("request", "", "", 0);
	}

	/** The server says Thick Hide was just used; it is ready again after the cooldown. */
	public static void onThickHide(JsonObject json) {
		thickHideCooldown = Math.max(1, json.get("cooldown").getAsLong());
		Minecraft mc = Minecraft.getInstance();
		thickHideReadyAt = (mc.level != null ? mc.level.getGameTime() : 0) + thickHideCooldown;
	}

	/** 0 = just used, 1 = ready. */
	public static float thickHideCharge(long gameTime) {
		long left = thickHideReadyAt - gameTime;
		return left <= 0 ? 1f : 1f - left / (float) thickHideCooldown;
	}

	public static TreeDefs.Wheel wheel(String id) {
		return WHEELS.get(id);
	}

	public static List<TreeDefs.Ascendancy> ascendancies() {
		return ASCENDANCIES;
	}

	public static TreeDefs.Ascendancy ascendancy(String id) {
		for (TreeDefs.Ascendancy a : ASCENDANCIES) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}

	public static JsonObject state() {
		return state;
	}

	public static PlayerTree tree() {
		return tree;
	}

	public static PlayerStats stats() {
		return stats;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static boolean loaded() {
		return state.has("tree");
	}

	/** The last server message (errors start with "!") while it is fresh, else "". */
	public static String message() {
		return System.currentTimeMillis() - messageAt < 6000 ? message : "";
	}
}
