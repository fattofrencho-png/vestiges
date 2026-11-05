package dev.vestiges.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.net.FeaturePayloads;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/** Opens each world's settings when it starts, keeps clients told about the switches, and lets operators change them. */
public final class FeatureNet {
	private static final String ENCHANT_PACK_SUFFIX = "enchant_levels";

	private FeatureNet() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server ->
			ConfigStore.openWorld(server.getWorldPath(LevelResource.ROOT).resolve(Vestiges.MOD_ID).resolve("settings.json")));
		ServerLifecycleEvents.SERVER_STARTED.register(FeatureNet::onStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ConfigStore.closeWorld());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.player)));
		ServerPlayNetworking.registerGlobalReceiver(FeaturePayloads.Change.TYPE, (payload, ctx) -> onChange(ctx.player(), payload));
	}

	private static void onStarted(MinecraftServer server) {
		ServerConfig c = ConfigStore.server();
		// The enchant levels live in a data pack, so the world's own pack list is the truth.
		c.set(VestigesSystem.ENCHANT_LEVELS, server.getPackRepository().getSelectedIds().stream().anyMatch(id -> id.endsWith(ENCHANT_PACK_SUFFIX)));
		// So does the generator for its terrain, whichever way the world type was picked.
		c.set(VestigesSystem.CONTINENTS, dev.vestiges.world.WorldAtlas.active(server));
		if (ConfigStore.chosenAtCreation()) {
			applyRules(server);
		}
		if (ConfigStore.newWorld()) {
			c.setupPending = server.isDedicatedServer() && server.overworld().getGameTime() == 0;
		}
		ConfigStore.saveServer();
	}

	/** Changes the settings, saves them and tells everyone. */
	public static void change(MinecraftServer server, Consumer<ServerConfig> change) {
		int sleep = sleepRule();
		boolean griefing = ConfigStore.server().enabled(VestigesSystem.GRIEFING);
		change.accept(ConfigStore.server());
		ConfigStore.saveServer();
		// Only when a switch moved: a rule someone set with /gamerule stays otherwise.
		if (sleep != sleepRule() || griefing != ConfigStore.server().enabled(VestigesSystem.GRIEFING)) {
			applyRules(server);
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p);
		}
	}

	private static int sleepRule() {
		ServerConfig c = ConfigStore.server();
		return c.enabled(VestigesSystem.SLEEP) ? c.numbers.sleepPercent : 100;
	}

	/** Features that are really vanilla game rules. */
	private static void applyRules(MinecraftServer server) {
		ServerConfig c = ConfigStore.server();
		GameRules rules = server.getGameRules();
		rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, sleepRule(), server);
		rules.set(GameRules.MOB_GRIEFING, !c.enabled(VestigesSystem.GRIEFING), server);
	}

	private static boolean mayChange(ServerPlayer p) {
		return Permissions.isOperator(p) || p.level().getServer().isSingleplayerOwner(p.nameAndId());
	}

	private static void send(ServerPlayer p) {
		ServerConfig c = ConfigStore.server();
		JsonObject systems = new JsonObject();
		for (VestigesSystem s : VestigesSystem.values()) {
			systems.addProperty(s.id, c.enabled(s));
		}
		JsonObject root = new JsonObject();
		root.add("systems", systems);
		com.google.gson.JsonArray remembered = new com.google.gson.JsonArray();
		c.remembered.forEach(remembered::add);
		root.add("remembered", remembered);
		root.addProperty("operator", mayChange(p));
		root.addProperty("prompt", c.setupPending && mayChange(p));
		root.addProperty("sleep", sleepRule());
		root.addProperty("brew_stack", c.numbers.brewStack);
		root.addProperty("stack_limit", c.numbers.stackLimit);
		// Continent names and centres for the map; only worlds made with the Vestiges world type have them.
		com.google.gson.JsonArray lands = new com.google.gson.JsonArray();
		if (dev.vestiges.world.WorldAtlas.active(p.level().getServer())) {
			for (dev.vestiges.world.WorldPlan.Land land : dev.vestiges.world.WorldPlans.current().lands()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", land.name());
				o.addProperty("x", land.x());
				o.addProperty("z", land.z());
				lands.add(o);
			}
		}
		root.add("lands", lands);
		if (lands.size() > 0) {
			root.addProperty("atlas", dev.vestiges.world.WorldAtlas.grid(p.level().getServer()));
		}
		if (ServerPlayNetworking.canSend(p, FeaturePayloads.State.TYPE)) {
			ServerPlayNetworking.send(p, new FeaturePayloads.State(root.toString()));
		}
	}

	private static void onChange(ServerPlayer p, FeaturePayloads.Change payload) {
		MinecraftServer server = p.level().getServer();
		if (!mayChange(p)) {
			return;
		}
		try {
			switch (payload.op()) {
				case "apply" -> {
					JsonObject wanted = JsonParser.parseString(payload.value()).getAsJsonObject();
					// Read every value first, so a bad one changes nothing.
					java.util.Map<VestigesSystem, Boolean> values = new java.util.EnumMap<>(VestigesSystem.class);
					for (VestigesSystem s : VestigesSystem.values()) {
						if (wanted.has(s.id) && !s.creationOnly()) {
							values.put(s, wanted.get(s.id).getAsBoolean());
						}
					}
					change(server, c -> {
						values.forEach(c::setWithNeeds);
						c.setupPending = false;
					});
				}
				case "sleep" -> {
					int percent = Math.max(1, Math.min(100, Integer.parseInt(payload.value())));
					change(server, c -> {
						c.numbers.sleepPercent = percent;
						c.set(VestigesSystem.SLEEP, percent < 100);
					});
				}
				case "stack" -> {
					int limit = "99".equals(payload.value()) ? 99 : 64;
					change(server, c -> c.numbers.stackLimit = limit);
				}
				case "done" -> change(server, c -> c.setupPending = false);
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Vestiges.LOGGER.warn("Vestiges: bad settings change '{}' from {}: {}", payload.op(), p.getName().getString(), e.toString());
		}
	}
}
