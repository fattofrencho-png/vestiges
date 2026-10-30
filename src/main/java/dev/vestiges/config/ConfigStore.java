package dev.vestiges.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.vestiges.Vestiges;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads and saves the settings. Each world keeps its own file; {@code server.json} is only where new worlds start from. A broken file is kept
 * aside as {@code .broken} and replaced by defaults, never silently overwritten.
 */
public final class ConfigStore {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ServerConfig server = new ServerConfig().normalize();
	/** The open world's file, or null while none is open. */
	private static Path worldFile;
	/** What the welcome page chose for the world about to be created (single player: the screen and the server share this class). */
	private static ServerConfig pendingWorld;
	private static boolean newWorld;
	private static boolean chosenAtCreation;

	private ConfigStore() {
	}

	public static ServerConfig server() {
		return server;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(Vestiges.MOD_ID);
	}

	/** The starting point for a new world. */
	public static ServerConfig defaults() {
		return read(dir().resolve("server.json"));
	}

	public static void setPendingWorld(ServerConfig choice) {
		pendingWorld = choice;
	}

	/** True when the welcome page chose this world's settings just now. */
	public static boolean chosenAtCreation() {
		return chosenAtCreation;
	}

	/** True when the open world got its settings file just now, so this is its first start with Vestiges' settings. */
	public static boolean newWorld() {
		return newWorld;
	}

	/** Opens a world's settings. A world with no file yet takes the welcome page's choice, or else the defaults (older worlds stay as they were). */
	public static void openWorld(Path file) {
		worldFile = file;
		newWorld = !Files.exists(file);
		chosenAtCreation = newWorld && pendingWorld != null;
		if (newWorld) {
			write(dir().resolve("server.json"), defaults());
			server = pendingWorld != null ? pendingWorld : defaults();
		} else {
			server = read(file);
		}
		pendingWorld = null;
		saveServer();
		logSystems();
	}

	public static void closeWorld() {
		worldFile = null;
		newWorld = false;
	}

	public static void loadServer() {
		server = worldFile != null ? read(worldFile) : defaults();
		saveServer();
		logSystems();
	}

	private static void logSystems() {
		Vestiges.LOGGER.info("Vestiges config loaded: {} of {} systems on", server.systems.values().stream().filter(b -> b).count(), server.systems.size());
	}

	public static void saveServer() {
		write(worldFile != null ? worldFile : dir().resolve("server.json"), server);
	}

	static ServerConfig read(Path file) {
		if (!Files.exists(file)) {
			return new ServerConfig().normalize();
		}
		try {
			ServerConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerConfig.class);
			return (c == null ? new ServerConfig() : c).normalize();
		} catch (IOException | JsonParseException e) {
			Vestiges.LOGGER.error("Vestiges: could not read {}, using defaults (old file kept as .broken)", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Keeping the broken file is a courtesy; defaults still load.
			}
			return new ServerConfig().normalize();
		}
	}

	static void write(Path file, ServerConfig c) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Vestiges.LOGGER.error("Vestiges: could not save {}", file, e);
		}
	}
}
