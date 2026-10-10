package dev.vestiges.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ClientConfig;
import dev.vestiges.config.ConfigStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves {@code config/vestiges/client.json}. */
public final class ClientConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig config = new ClientConfig().normalize();

	private ClientConfigStore() {
	}

	public static ClientConfig get() {
		return config;
	}

	private static Path file() {
		return ConfigStore.dir().resolve("client.json");
	}

	public static void load() {
		Path f = file();
		if (Files.exists(f)) {
			try {
				ClientConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ClientConfig.class);
				config = (c == null ? new ClientConfig() : c).normalize();
			} catch (IOException | JsonParseException e) {
				Vestiges.LOGGER.error("Vestiges: could not read {}, using defaults", f, e);
				config = new ClientConfig().normalize();
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Vestiges.LOGGER.error("Vestiges: could not save client config", e);
		}
	}
}
