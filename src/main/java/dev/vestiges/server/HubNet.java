package dev.vestiges.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.PlayerProfile;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.DataType;
import dev.vestiges.data.Feat;
import dev.vestiges.data.VestigesData;
import dev.vestiges.net.VestigesPayloads;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Server side of the hub: answers snapshot requests. Each later system adds its own section to {@link #snapshot}. */
public final class HubNet {
	private static final Gson GSON = new Gson();

	private HubNet() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(VestigesPayloads.HubRequest.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> send(ctx.player())));
	}

	public static void send(ServerPlayer player) {
		ServerPlayNetworking.send(player, new VestigesPayloads.HubData(GSON.toJson(snapshot(player))));
	}

	static JsonObject snapshot(ServerPlayer player) {
		JsonObject root = new JsonObject();
		root.addProperty("version", version());
		root.addProperty("operator", Permissions.isOperator(player));

		ServerConfig config = ConfigStore.server();
		JsonArray systems = new JsonArray();
		for (VestigesSystem s : VestigesSystem.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", s.id);
			o.addProperty("label", s.label);
			o.addProperty("on", config.enabled(s));
			systems.add(o);
		}
		root.add("systems", systems);

		JsonArray feats = new JsonArray();
		for (Map.Entry<Identifier, Feat> e : VestigesData.FEATS.all().entrySet()) {
			Feat f = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("id", e.getKey().toString());
			o.addProperty("chain", f.chain());
			o.addProperty("order", f.order());
			o.addProperty("name", f.name());
			o.addProperty("requirement", f.requirement());
			o.addProperty("reward", f.reward());
			o.addProperty("xp", f.xp());
			o.addProperty("repeatable", f.repeatable());
			feats.add(o);
		}
		root.add("feats", feats);

		JsonObject data = new JsonObject();
		for (DataType<?> type : VestigesData.ALL) {
			data.addProperty(type.name(), type.all().size());
		}
		root.add("data", data);

		dev.vestiges.level.PlayerLevel lvl = dev.vestiges.level.LevelService.get(player);
		JsonObject level = new JsonObject();
		level.addProperty("level", lvl.level());
		level.addProperty("xp", lvl.xp());
		level.addProperty("need", lvl.need());
		level.addProperty("paragon", lvl.paragon());
		level.addProperty("bonusHealth", dev.vestiges.level.LevelCurve.bonusHealth(lvl.level(), lvl.paragon()));
		level.addProperty("damagePct", Math.round((dev.vestiges.level.LevelCurve.damageMultiplier(lvl.level(), lvl.paragon()) - 1) * 1000) / 10.0);
		level.addProperty("discoveries", lvl.firsts().size());
		root.add("level", level);

		PlayerProfile profile = PlayerProfiles.get(player);
		JsonObject p = new JsonObject();
		p.addProperty("name", player.getName().getString());
		p.addProperty("firstSeen", profile.firstSeen());
		root.add("profile", p);
		return root;
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(Vestiges.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}
}
