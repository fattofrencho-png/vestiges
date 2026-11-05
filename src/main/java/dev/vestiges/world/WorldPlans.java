package dev.vestiges.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.Vestiges;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Which {@link WorldPlan} is in use. A new world takes the plan from its data packs and keeps a copy in {@code <world>/vestiges/world_plan.json}. From
 * then on that copy is used, so a mod update or a changed pack can't shift the land of a world already explored.
 */
public final class WorldPlans {
	private static final String PATH = "vestiges/world_plan/trenec.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final WorldPlan BUILT_IN = builtIn();
	private static volatile WorldPlan current = BUILT_IN;
	private static volatile boolean active;

	private WorldPlans() {
	}

	public static WorldPlan current() {
		return current;
	}

	/** True only while a world made with a Vestiges world type is running. */
	public static boolean active() {
		return active;
	}

	/** Read from the world's saved dimensions, because this runs before the levels exist. */
	private static boolean usesPlan(MinecraftServer server) {
		LevelStem stem = server.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
		return stem != null && WorldAtlas.isPlanned(stem.generator());
	}

	public static void register() {
		// Before the levels are created, because their terrain reads the plan.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			current = load(server);
			active = usesPlan(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(WorldPlans::keepCopy);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			current = BUILT_IN;
			active = false;
		});
	}

	private static Path copyOf(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(Vestiges.MOD_ID).resolve("world_plan.json");
	}

	private static WorldPlan load(MinecraftServer server) {
		try {
			Path copy = copyOf(server);
			if (Files.exists(copy)) {
				return read(Files.newBufferedReader(copy, StandardCharsets.UTF_8));
			}
			var resource = server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(Vestiges.MOD_ID, PATH));
			return resource.isPresent() ? read(resource.get().openAsReader()) : BUILT_IN;
		} catch (IOException e) {
			throw new IllegalStateException("Vestiges: can't read the world plan", e);
		}
	}

	/** Vanilla worlds never use a plan, so they get no copy. */
	private static void keepCopy(MinecraftServer server) {
		Path copy = copyOf(server);
		if (!WorldAtlas.active(server) || Files.exists(copy)) {
			return;
		}
		try {
			Files.createDirectories(copy.getParent());
			Files.writeString(copy, write(current), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Vestiges.LOGGER.error("Vestiges: can't save the world plan", e);
		}
	}

	public static WorldPlan read(Reader reader) throws IOException {
		try (reader) {
			return WorldPlan.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow(IllegalStateException::new);
		}
	}

	public static String write(WorldPlan plan) {
		JsonElement json = WorldPlan.CODEC.encodeStart(JsonOps.INSTANCE, plan).getOrThrow(IllegalStateException::new);
		return GSON.toJson(json);
	}

	private static WorldPlan builtIn() {
		try (InputStream in = WorldPlans.class.getResourceAsStream("/data/vestiges/" + PATH)) {
			return read(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Vestiges: the built-in world plan is missing", e);
		}
	}
}
