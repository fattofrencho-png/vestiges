package dev.vestiges.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.level.LevelService;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.StatEngine;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Meal buffs (Batch 3 and M4, approved): complex vanilla foods give a small, long buff to stats potions do not cover; one at a time (a new meal
 * replaces the old one); lost on death. The buff counts down only while the player is online. Shown next to the hunger bar.
 */
public final class MealService {
	/** The current meal and how many ticks it has left. */
	public record Buff(String item, int ticks) {
		public static final Codec<Buff> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("item").forGetter(Buff::item),
			Codec.INT.fieldOf("ticks").forGetter(Buff::ticks)
		).apply(i, Buff::new));
	}

	/** Not copied on death: dying loses the buff, like a potion. */
	public static final AttachmentType<Buff> BUFF = AttachmentRegistry.create(Vestiges.id("meal"), b -> b.persistent(Buff.CODEC));

	private MealService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.FARMING);
	}

	public static MealDef def(String item) {
		for (MealDef d : VestigesData.MEALS.all().values()) {
			if (d.item().equals(item)) {
				return d;
			}
		}
		return null;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MealService::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			sendDefs(handler.player);
			send(handler.player);
		}));
	}

	/** Called when a player finishes eating something (or a cake slice). */
	public static void ate(ServerPlayer player, Item item) {
		if (!enabled()) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		MealDef d = def(id);
		if (d == null) {
			return;
		}
		double longer = 1 + StatEngine.get(player).get("meal_duration") / 100.0;
		player.setAttached(BUFF, new Buff(id, (int) Math.round(d.minutes() * 60 * 20 * longer)));
		refresh(player);
	}

	/** The meal's stats, merged into the player's stats by {@link StatEngine}. */
	public static PlayerStats stats(ServerPlayer player) {
		Buff b = player.getAttached(BUFF);
		MealDef d = b == null || !enabled() ? null : def(b.item());
		if (d == null) {
			return PlayerStats.NONE;
		}
		PlayerStats s = new PlayerStats();
		d.stats().forEach(s::addStat);
		return s;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Buff b = p.getAttached(BUFF);
			if (b == null) {
				continue;
			}
			int left = b.ticks() - 20;
			if (left <= 0 || def(b.item()) == null) {
				p.removeAttached(BUFF);
				refresh(p);
			} else {
				p.setAttached(BUFF, new Buff(b.item(), left));
			}
		}
	}

	private static void refresh(ServerPlayer player) {
		StatEngine.invalidate(player);
		StatEngine.apply(player);
		send(player);
	}

	/** Tells the client which meal is active and for how long (it counts down by itself). */
	public static void send(ServerPlayer player) {
		Buff b = player.getAttached(BUFF);
		MealDef d = b == null ? null : def(b.item());
		JsonObject o = new JsonObject();
		o.addProperty("item", d == null ? "" : b.item());
		o.addProperty("seconds", d == null ? 0 : b.ticks() / 20);
		o.addProperty("text", d == null ? "" : d.text());
		LevelService.notice(player, "meal", o);
	}

	/** The meal list, for food tooltips on the client. */
	private static void sendDefs(ServerPlayer player) {
		JsonArray list = new JsonArray();
		for (MealDef d : VestigesData.MEALS.all().values()) {
			JsonObject o = new JsonObject();
			o.addProperty("item", d.item());
			o.addProperty("minutes", d.minutes());
			o.addProperty("text", d.text());
			list.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("meals", list);
		LevelService.notice(player, "meal_defs", root);
	}
}
