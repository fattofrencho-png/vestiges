package dev.vestiges.client.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.net.CodexPayloads;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The client's copy of the Codex (M4): the last snapshot from the server, plus what is known for the look-at "New" tag. */
public final class ClientCodex {
	/** One entry as the screen shows it; names, icons and lines are blank until earned. */
	public record Entry(String id, String section, String name, String cls, Item icon, int stage, int max, int count, int need, String label,
						String line1, String line2, String later, List<Map.Entry<String, Boolean>> unlocks) {
		public boolean known() {
			return stage > 0;
		}

		public boolean mastered() {
			return stage > 0 && stage >= max;
		}
	}

	private static List<Entry> entries = List.of();
	private static Map<String, String> index = Map.of();
	private static final Set<String> KNOWN = new HashSet<>();
	private static boolean loaded;
	private static boolean enabled = true;
	private static int discovered;
	private static int total;
	private static int points;
	private static int nextPoint;
	private static int version;

	private ClientCodex() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CodexPayloads.Data.TYPE, (payload, ctx) -> ctx.client().execute(() -> load(payload.json())));
	}

	public static void request() {
		if (ClientPlayNetworking.canSend(CodexPayloads.Request.TYPE)) {
			ClientPlayNetworking.send(new CodexPayloads.Request(""));
		}
	}

	public static void reset() {
		entries = List.of();
		index = Map.of();
		KNOWN.clear();
		loaded = false;
	}

	private static void load(String json) {
		JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			Vestiges.LOGGER.warn("Vestiges: bad Codex snapshot");
			return;
		}
		enabled = root.get("enabled").getAsBoolean();
		discovered = root.get("discovered").getAsInt();
		total = root.get("total").getAsInt();
		points = root.get("points").getAsInt();
		nextPoint = root.get("nextPoint").getAsInt();
		List<Entry> list = new ArrayList<>();
		for (JsonElement el : root.getAsJsonArray("entries")) {
			JsonObject o = el.getAsJsonObject();
			List<Map.Entry<String, Boolean>> unlocks = new ArrayList<>();
			for (JsonElement u : o.getAsJsonArray("unlocks")) {
				JsonArray a = u.getAsJsonArray();
				unlocks.add(Map.entry(a.get(0).getAsString(), a.get(1).getAsBoolean()));
			}
			list.add(new Entry(o.get("id").getAsString(), o.get("section").getAsString(), o.get("name").getAsString(), o.get("cls").getAsString(),
				item(o.get("icon").getAsString()), o.get("stage").getAsInt(), o.get("max").getAsInt(), o.get("count").getAsInt(),
				o.get("need").getAsInt(), o.get("label").getAsString(), o.get("line1").getAsString(), o.get("line2").getAsString(),
				o.get("later").getAsString(), List.copyOf(unlocks)));
		}
		entries = Collections.unmodifiableList(list);
		Map<String, String> idx = new HashMap<>();
		root.getAsJsonObject("index").entrySet().forEach(e -> idx.put(e.getKey(), e.getValue().getAsString()));
		index = idx;
		KNOWN.clear();
		root.getAsJsonArray("known").forEach(e -> KNOWN.add(e.getAsString()));
		loaded = true;
		version++;
	}

	private static Item item(String id) {
		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BARRIER);
	}

	/** A Codex step from the server: a toast, and the entry counts as known from now on. */
	public static void onStep(JsonObject o) {
		KNOWN.add(o.get("entry").getAsString());
		Minecraft.getInstance().gui.toastManager().addToast(new CodexToast(item(o.get("icon").getAsString()), o.get("name").getAsString(),
			o.get("label").getAsString(), o.get("xp").getAsLong()));
		if (Minecraft.getInstance().gui.screen() instanceof CodexScreen) {
			request();
		}
	}

	public static void onPoint() {
		Minecraft.getInstance().gui.toastManager().addToast(new CodexToast(Items.WRITABLE_BOOK, "Codex bonus point", "+1 Craft point", 0));
	}

	/** For the look-at "New" tag: null when this block or mob has no entry, else whether it is still undiscovered. */
	public static Boolean isNew(Identifier gameId) {
		String entry = index.get(gameId.toString());
		return entry == null ? null : !KNOWN.contains(entry);
	}

	public static List<Entry> entries() {
		return entries;
	}

	public static boolean loaded() {
		return loaded;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static int discovered() {
		return discovered;
	}

	public static int total() {
		return total;
	}

	public static int points() {
		return points;
	}

	public static int nextPoint() {
		return nextPoint;
	}

	public static int version() {
		return version;
	}
}
