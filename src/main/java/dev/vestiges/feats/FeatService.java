package dev.vestiges.feats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.Feat;
import dev.vestiges.data.VestigesData;
import dev.vestiges.level.LevelService;
import dev.vestiges.server.MapNet;
import dev.vestiges.settlement.MarkerItems;
import dev.vestiges.settlement.MarkerType;
import dev.vestiges.settlement.SettlementService;
import dev.vestiges.tree.TreeService;
import dev.vestiges.waystone.WaystoneItems;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Feats (M5, approved): checked every two seconds; each completed feat gives its item, a toast and XP. Repeatable feats pay once per step of
 * their counter. Feats waiting for a later system show "Comes with ..." and cannot be earned yet, and neither can feats whose feature is off in this world; rewards of later systems are kept as owed.
 */
public final class FeatService {
	public static final AttachmentType<FeatState> STATE = AttachmentRegistry.create(Vestiges.id("feats"),
		b -> b.persistent(FeatState.CODEC).copyOnDeath());

	private FeatService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.FEATS);
	}

	public static FeatState get(ServerPlayer p) {
		return p.getAttachedOrElse(STATE, FeatState.EMPTY);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FeatService::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0 || !enabled()) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isSpectator()) {
				check(p);
			}
		}
	}

	/** Awards at most one feat (or one repeat) per check, so several at once arrive one after another. */
	public static void check(ServerPlayer p) {
		FeatState st = get(p);
		for (Map.Entry<Identifier, Feat> e : VestigesData.FEATS.all().entrySet()) {
			String id = e.getKey().getPath();
			Feat f = e.getValue();
			if (!f.later().isEmpty() || f.blockedIn(ConfigStore.server())) {
				continue;
			}
			if (!f.counter().isEmpty() && f.every() > 0) {
				int times = value(p, st, f.counter()) / f.every();
				int given = st.repeats().getOrDefault(id, 0);
				if (times > given) {
					p.setAttached(STATE, st.withRepeat(id, given + 1));
					award(p, f);
					return;
				}
			} else if (!st.done().contains(id) && !f.goals().isEmpty() && f.goals().stream().allMatch(g -> met(p, st, g))) {
				p.setAttached(STATE, st.withDone(id, true));
				award(p, f);
				return;
			}
		}
	}

	private static boolean met(ServerPlayer p, FeatState st, Feat.Goal g) {
		return "renown_two".equals(g.type()) ? SettlementService.ownedAtLevel(p, g.amount()) >= 2 : value(p, st, g.type()) >= g.amount();
	}

	/** The current count behind a goal or a repeat counter. */
	static int value(ServerPlayer p, FeatState st, String type) {
		return switch (type) {
			case "level" -> LevelService.get(p).level();
			case "biomes" -> (int) LevelService.get(p).firsts().stream().filter(k -> k.startsWith("biome:")).count();
			case "codex" -> dev.vestiges.codex.CodexService.discovered(p);
			case "elites" -> (int) TreeService.get(p).counter("elites");
			case "villagers" -> st.villagers();
			case "beds" -> SettlementService.placedBedsNear(p, 24);
			case "renown" -> SettlementService.bestOwnedLevel(p);
			case "renown_levels" -> SettlementService.levelsGained(p);
			case "raids" -> st.raids();
			case "raid_omen" -> st.bestOmen();
			default -> 0;
		};
	}

	private static String label(String type) {
		return switch (type) {
			case "level" -> "Level";
			case "biomes" -> "Biomes visited";
			case "codex" -> "Codex entries";
			case "elites" -> "Elites killed";
			case "villagers" -> "Villagers met";
			case "beds" -> "Beds near you";
			case "renown" -> "Settlement renown level";
			case "renown_two" -> "Settlements at renown level";
			case "raids" -> "Raids defended";
			case "raid_omen" -> "Raid beaten at Bad Omen level";
			default -> type;
		};
	}

	private static void award(ServerPlayer p, Feat f) {
		ItemStack item = reward(f.item());
		if (!item.isEmpty() && !p.getInventory().add(item)) {
			ItemEntity drop = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), item);
			drop.setTarget(p.getUUID());
			p.level().addFreshEntity(drop);
		}
		if (!f.owed().isEmpty()) {
			p.setAttached(STATE, get(p).withOwed(f.owed()));
		}
		long xp = f.xp() > 0 ? LevelService.addXp(p, f.xp(), "feat") : 0;
		JsonObject o = new JsonObject();
		o.addProperty("name", f.name());
		o.addProperty("reward", f.reward());
		o.addProperty("xp", xp);
		o.addProperty("icon", icon(f));
		LevelService.notice(p, "feat", o);
		MapNet.message(p, "Feat: " + f.name() + "  →  " + f.reward(), false);
	}

	public static ItemStack reward(String item) {
		if ("waystone".equals(item)) {
			return WaystoneItems.create(1);
		}
		MarkerType t = MarkerType.byId(item);
		return t == null ? ItemStack.EMPTY : MarkerItems.create(t);
	}

	/** The icon the feat screen and toast show. */
	public static String icon(Feat f) {
		if ("waystone".equals(f.item())) {
			return "minecraft:lodestone";
		}
		MarkerType t = MarkerType.byId(f.item());
		if (t != null) {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(t.item).toString();
		}
		return switch (f.chain()) {
			case "dragonslayer" -> "minecraft:dragon_head";
			case "gatekeeper" -> "minecraft:trial_key";
			case "explorer" -> f.owed().isEmpty() ? "minecraft:paper" : "minecraft:map";
			default -> "minecraft:book";
		};
	}

	public static void metVillager(ServerPlayer p) {
		FeatState st = get(p);
		if (st.villagers() == 0) {
			p.setAttached(STATE, st.withVillagers(1));
		}
	}

	public static void raidWon(ServerPlayer p, int omen) {
		p.setAttached(STATE, get(p).withRaid(omen));
	}

	/** Admin: completes a feat (with its reward) or takes it back. */
	public static boolean grant(ServerPlayer p, String id, boolean on) {
		Feat f = VestigesData.FEATS.get(Vestiges.id(id));
		if (f == null) {
			return false;
		}
		FeatState st = get(p);
		if (on && !st.done().contains(id)) {
			p.setAttached(STATE, st.withDone(id, true));
			award(p, f);
		} else if (!on) {
			p.setAttached(STATE, st.withDone(id, false).withRepeat(id, 0));
		}
		return true;
	}

	/** Progress for the feats screen: done, times paid, and each goal's count. */
	public static JsonObject progress(ServerPlayer p, String id, Feat f) {
		FeatState st = get(p);
		JsonObject o = new JsonObject();
		o.addProperty("done", st.done().contains(id));
		o.addProperty("times", st.repeats().getOrDefault(id, 0));
		boolean off = f.later().isEmpty() && f.blockedIn(ConfigStore.server());
		o.addProperty("later", off ? "off" : f.later());
		o.addProperty("off", off);
		o.addProperty("icon", icon(f));
		JsonArray goals = new JsonArray();
		if (!f.counter().isEmpty() && f.every() > 0) {
			int v = value(p, st, f.counter());
			JsonObject g = new JsonObject();
			g.addProperty("label", label(f.counter()));
			g.addProperty("have", v % f.every());
			g.addProperty("need", f.every());
			goals.add(g);
		}
		for (Feat.Goal goal : f.goals()) {
			JsonObject g = new JsonObject();
			boolean two = "renown_two".equals(goal.type());
			g.addProperty("label", label(goal.type()) + (two ? " " + goal.amount() : ""));
			g.addProperty("have", two ? SettlementService.ownedAtLevel(p, goal.amount()) : value(p, st, goal.type()));
			g.addProperty("need", two ? 2 : goal.amount());
			goals.add(g);
		}
		o.add("goals", goals);
		return o;
	}
}
