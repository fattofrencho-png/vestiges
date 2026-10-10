package dev.vestiges.client.map;

import dev.vestiges.map.MapMath;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

/**
 * All map regions of the current world, kept in memory up to a limit (least recently used are saved and freed) and saved under
 * {@code .minecraft/vestiges/maps/<world>/<dimension>/<layer>/r.<x>.<z>.png}. One folder per single-player save or server address.
 */
public final class MapStore {
	private static final int MAX_LOADED = 48;
	/** Bumped when the map colours change, so old tiles are not mixed with new ones. */
	private static final String MAP_VERSION = "v2";
	private static final Map<String, MapRegion> LOADED = new LinkedHashMap<>(64, 0.75f, true);
	private static final Set<String> MISSING = new HashSet<>();
	private static Path worldDir;
	private static Path dataDir;

	private MapStore() {
	}

	/** Called when joining a world: picks the folder for this save or server. */
	public static void open() {
		close();
		Minecraft mc = Minecraft.getInstance();
		String key;
		IntegratedServer sp = mc.getSingleplayerServer();
		if (sp != null) {
			Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			key = "sp_" + (root.getFileName() != null ? root.getFileName().toString() : "world");
		} else if (mc.getCurrentServer() != null) {
			key = "mp_" + mc.getCurrentServer().ip;
		} else {
			key = "unknown";
		}
		Path maps = mc.gameDirectory.toPath().resolve("vestiges").resolve("maps");
		dataDir = maps.resolve(sanitize(key));
		worldDir = maps.resolve(MAP_VERSION).resolve(sanitize(key));
	}

	/** Unversioned folder for this world's waypoints, so they survive map-colour updates. */
	public static Path dataDir() {
		return dataDir;
	}

	public static Path worldDir() {
		return worldDir;
	}

	public static void close() {
		for (MapRegion r : LOADED.values()) {
			r.close();
		}
		LOADED.clear();
		MISSING.clear();
	}

	/** After leaving a world: no folder until the next join (so early server notices wait for it). */
	public static void forget() {
		worldDir = null;
		dataDir = null;
	}

	public static void saveAll() {
		for (MapRegion r : LOADED.values()) {
			r.save();
		}
	}

	/** The region for writing (created when new). */
	static MapRegion forWrite(Identifier dimension, String layer, int rx, int rz) {
		return get(dimension, layer, rx, rz, true);
	}

	/** The region for drawing, or null when nothing was ever mapped there. */
	static MapRegion forRead(Identifier dimension, String layer, int rx, int rz) {
		return get(dimension, layer, rx, rz, false);
	}

	private static MapRegion get(Identifier dimension, String layer, int rx, int rz, boolean create) {
		if (worldDir == null) {
			return null;
		}
		String key = dimension + "|" + layer + "|" + rx + "|" + rz;
		MapRegion r = LOADED.get(key);
		if (r != null) {
			return r;
		}
		if (!create && MISSING.contains(key)) {
			return null;
		}
		Path file = worldDir.resolve(sanitize(dimension.toString())).resolve(layer).resolve("r." + rx + "." + rz + ".png");
		r = MapRegion.load(file, create);
		if (r == null) {
			MISSING.add(key);
			return null;
		}
		MISSING.remove(key);
		LOADED.put(key, r);
		evict();
		return r;
	}

	private static void evict() {
		Iterator<Map.Entry<String, MapRegion>> it = LOADED.entrySet().iterator();
		while (LOADED.size() > MAX_LOADED && it.hasNext()) {
			Map.Entry<String, MapRegion> e = it.next();
			e.getValue().close();
			it.remove();
		}
	}

	static void setPixel(Identifier dimension, String layer, int x, int z, int argb) {
		MapRegion r = forWrite(dimension, layer, MapMath.region(x), MapMath.region(z));
		if (r != null) {
			r.set(MapMath.local(x), MapMath.local(z), argb);
		}
	}

	static String sanitize(String s) {
		return s.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
