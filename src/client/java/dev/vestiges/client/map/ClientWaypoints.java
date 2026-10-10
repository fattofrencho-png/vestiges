package dev.vestiges.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.vestiges.Vestiges;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Waypoints of the current world (saved next to its map), the "Shared with you" inbox, and the waystones the server says you may use.
 * Death markers are waypoints of type "death"; only the last 5 are kept.
 */
public final class ClientWaypoints {
	public static final int[] PALETTE = {0xFFE6C36A, 0xFF61C48A, 0xFF5AA9E6, 0xFFE2634F, 0xFFB98AE6, 0xFFE6E6E6, 0xFF4BB98A, 0xFFE68AC0};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_DEATHS = 5;

	public static final class Waypoint {
		public String id = UUID.randomUUID().toString();
		public String name = "Waypoint";
		public String dimension = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public int color = PALETTE[0];
		public String type = "user";
		/** Set on death markers that point at a grave: the grave id and when it crumbles (game time). */
		public int graveId;
		public long expires;

		public JsonObject toJson() {
			return GSON.toJsonTree(this).getAsJsonObject();
		}

		static Waypoint fromJson(JsonElement e) {
			Waypoint w = GSON.fromJson(e, Waypoint.class);
			w.id = UUID.randomUUID().toString();
			w.type = "user";
			w.graveId = 0;
			w.expires = 0;
			return w;
		}
	}

	public record Shared(String from, Waypoint waypoint) {
	}

	public record WaystoneMarker(int id, String name, String dimension, int x, int y, int z, boolean mine, String owner) {
	}

	private static List<Waypoint> waypoints = new ArrayList<>();
	private static final List<Shared> INBOX = new ArrayList<>();
	private static List<WaystoneMarker> waystones = List.of();
	/** Graves the server listed before this world's waypoints were loaded. */
	private static JsonArray pendingGraves;

	private ClientWaypoints() {
	}

	public static List<Waypoint> all() {
		return waypoints;
	}

	public static List<Shared> inbox() {
		return INBOX;
	}

	public static List<WaystoneMarker> waystones() {
		return waystones;
	}

	private static Path file() {
		Path dir = MapStore.dataDir();
		return dir == null ? null : dir.resolve("waypoints.json");
	}

	public static void load() {
		waypoints = new ArrayList<>();
		INBOX.clear();
		waystones = List.of();
		Path f = file();
		if (f != null && Files.exists(f)) {
			try {
				List<Waypoint> list = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() { }.getType());
				if (list != null) {
					waypoints = new ArrayList<>(list);
				}
			} catch (Exception e) {
				Vestiges.LOGGER.warn("Vestiges map: could not read waypoints", e);
			}
		}
		if (pendingGraves != null) {
			JsonArray g = pendingGraves;
			pendingGraves = null;
			syncGraves(g);
		}
	}

	public static void save() {
		Path f = file();
		if (f == null) {
			return;
		}
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(waypoints), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Vestiges.LOGGER.warn("Vestiges map: could not save waypoints", e);
		}
	}

	public static Waypoint add(String name, String dimension, int x, int y, int z, int color, String type) {
		Waypoint w = new Waypoint();
		w.name = name;
		w.dimension = dimension;
		w.x = x;
		w.y = y;
		w.z = z;
		w.color = color;
		w.type = type;
		waypoints.add(w);
		save();
		return w;
	}

	public static void addDeath(String dimension, int x, int y, int z) {
		for (Waypoint w : waypoints) {
			if ("death".equals(w.type) && w.dimension.equals(dimension) && Math.abs(w.x - x) <= 4 && Math.abs(w.z - z) <= 4 && Math.abs(w.y - y) <= 16) {
				return; // The grave's own marker is already here.
			}
		}
		add("Death", dimension, x, y, z, 0xFFE2634F, "death");
		List<Waypoint> deaths = waypoints.stream().filter(w -> "death".equals(w.type)).toList();
		if (deaths.size() > MAX_DEATHS) {
			waypoints.removeAll(deaths.subList(0, deaths.size() - MAX_DEATHS));
			save();
		}
	}

	public static void remove(Waypoint w) {
		waypoints.remove(w);
		save();
	}

	public static int nextColor() {
		return PALETTE[waypoints.size() % PALETTE.length];
	}

	/** A grave was made: link it to the death marker just dropped there, or add one. */
	public static void addGrave(JsonObject g) {
		int id = g.get("id").getAsInt();
		String dim = g.get("dimension").getAsString();
		int x = g.get("x").getAsInt();
		int y = g.get("y").getAsInt();
		int z = g.get("z").getAsInt();
		long expires = g.get("expires").getAsLong();
		for (Waypoint w : waypoints) {
			if (w.graveId == id) {
				w.expires = expires;
				save();
				return;
			}
		}
		Waypoint near = null;
		for (Waypoint w : waypoints) {
			if ("death".equals(w.type) && w.graveId == 0 && w.dimension.equals(dim) && Math.abs(w.x - x) <= 4 && Math.abs(w.z - z) <= 4
				&& Math.abs(w.y - y) <= 16) {
				near = w;
			}
		}
		if (near == null) {
			near = add("Death", dim, x, y, z, 0xFFE2634F, "death");
		}
		near.graveId = id;
		near.expires = expires;
		near.x = x;
		near.y = y;
		near.z = z;
		save();
	}

	/** A grave was collected or crumbled: its marker goes away. */
	public static void removeGrave(int id) {
		if (waypoints.removeIf(w -> w.graveId == id)) {
			save();
		}
	}

	/** On join: keep markers only for graves that still exist, and add any missing ones. */
	public static void syncGraves(JsonArray graves) {
		if (MapStore.dataDir() == null) {
			pendingGraves = graves;
			return;
		}
		java.util.Set<Integer> alive = new java.util.HashSet<>();
		for (JsonElement e : graves) {
			alive.add(e.getAsJsonObject().get("id").getAsInt());
		}
		if (waypoints.removeIf(w -> w.graveId != 0 && !alive.contains(w.graveId))) {
			save();
		}
		for (JsonElement e : graves) {
			addGrave(e.getAsJsonObject());
		}
	}

	/** "Death (2d 5h left)" for grave markers, the plain name otherwise. */
	public static String label(Waypoint w, long gameTime) {
		if (w.graveId == 0 || w.expires <= 0) {
			return w.name;
		}
		long left = Math.max(0, w.expires - gameTime);
		long days = left / 24000L;
		long hours = (left % 24000L) / 1000L;
		return w.name + " (" + (days > 0 ? days + "d " : "") + hours + "h left)";
	}

	static void receiveShared(String from, JsonElement waypoint) {
		INBOX.add(new Shared(from, Waypoint.fromJson(waypoint)));
	}

	public static void accept(Shared s) {
		INBOX.remove(s);
		waypoints.add(s.waypoint());
		save();
	}

	public static void decline(Shared s) {
		INBOX.remove(s);
	}

	static void receiveWaystones(JsonArray arr) {
		List<WaystoneMarker> list = new ArrayList<>();
		for (JsonElement e : arr) {
			JsonObject o = e.getAsJsonObject();
			list.add(new WaystoneMarker(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("dimension").getAsString(),
				o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("mine").getAsBoolean(), o.get("owner").getAsString()));
		}
		waystones = List.copyOf(list);
	}
}
