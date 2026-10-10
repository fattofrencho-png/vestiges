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

/** Loads and saves the server config. A broken file is kept aside as {@code server.json.broken} and replaced by defaults, never silently lost. */
public final class ConfigStore {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ServerConfig server = new ServerConfig().normalize();

	private ConfigStore() {
	}

	public static ServerConfig server() {
		return server;
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(Vestiges.MOD_ID);
	}

	public static void loadServer() {
		server = read(dir().resolve("server.json"));
		saveServer();
		Vestiges.LOGGER.info("Vestiges config loaded: {} of {} systems on", server.systems.values().stream().filter(b -> b).count(), server.systems.size());
	}

	public static void saveServer() {
		write(dir().resolve("server.json"), server);
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
