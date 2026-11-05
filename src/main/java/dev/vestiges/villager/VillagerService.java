package dev.vestiges.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.data.VillagerLoreDef;
import dev.vestiges.data.VillagerTradesDef;
import dev.vestiges.level.LevelService;
import dev.vestiges.net.VillagerPayloads;
import dev.vestiges.quest.QuestService;
import dev.vestiges.server.Emeralds;
import dev.vestiges.server.MapNet;
import dev.vestiges.world.WorldAtlas;
import dev.vestiges.world.WorldPlans;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Villagers: every villager has a name and a background (made when it first loads), the player's library remembers who they have met, and right-click
 * opens our own villager screen instead of vanilla trading. Villagers only sell, for emeralds, by the player's level band; the Wandering Trader uses
 * the same screen with a rotating stock. Behind the Villagers switch (identity and library) and its child Trade system (the screen and trades).
 */
public final class VillagerService {
	public static final AttachmentType<VillagerProfile> PROFILE = AttachmentRegistry.create(Vestiges.id("villager"),
		b -> b.persistent(VillagerProfile.CODEC).syncWith(VillagerProfile.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On the player: who they have met. */
	public static final AttachmentType<VillagerLibrary> LIBRARY = AttachmentRegistry.create(Vestiges.id("villager_library"),
		b -> b.persistent(VillagerLibrary.CODEC).copyOnDeath());
	/** On the villager: what each player has bought today. */
	public static final AttachmentType<Stock> STOCK = AttachmentRegistry.create(Vestiges.id("villager_stock"), b -> b.persistent(Stock.CODEC));

	/** Units bought, per player and offer, on one day. */
	public record Sold(long day, int count) {
		static final Codec<Sold> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Sold::day),
			Codec.INT.fieldOf("count").forGetter(Sold::count)
		).apply(i, Sold::new));
	}

	public record Stock(Map<String, Sold> sold) {
		static final Codec<Stock> CODEC = Codec.unboundedMap(Codec.STRING, Sold.CODEC).xmap(Stock::new, Stock::sold);
		static final Stock EMPTY = new Stock(Map.of());

		int bought(String key, long day) {
			Sold s = sold.get(key);
			return s != null && s.day() == day ? s.count() : 0;
		}

		Stock add(String key, long day, int n) {
			Map<String, Sold> m = new HashMap<>();
			sold.forEach((k, v) -> {
				if (v.day() == day) {
					m.put(k, v);
				}
			});
			m.put(key, new Sold(day, bought(key, day) + n));
			return new Stock(Map.copyOf(m));
		}
	}

	private VillagerService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.VILLAGERS);
	}

	/** Whether right-click opens our screen (the Trade system switch) instead of vanilla trading. */
	public static boolean screenOpens() {
		return enabled() && ConfigStore.server().enabled(VestigesSystem.TRADES);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && enabled() && villager.getAttached(PROFILE) == null) {
				assign(villager, level);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof AbstractVillager v)
				|| !enabled() || sp.isSpectator() || keepsItsOwnUse(sp.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			met(sp, v);
			if (!screenOpens() || v instanceof Villager baby && baby.isBaby()) {
				return InteractionResult.PASS;
			}
			if (v instanceof Villager villager) {
				QuestService.talk(sp, villager);
			}
			sendView(sp, v);
			return InteractionResult.SUCCESS;
		});
		ServerPlayNetworking.registerGlobalReceiver(VillagerPayloads.Act.TYPE, (payload, ctx) -> ctx.server().execute(() -> onAct(ctx.player(), payload)));
	}

	/** Items that do something to a villager themselves (naming, leading, spawn eggs) must still work. */
	private static boolean keepsItsOwnUse(ItemStack stack) {
		return stack.is(Items.NAME_TAG) || stack.is(Items.LEAD) || stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem;
	}

	/** The lore lists in use, or null before the data has loaded. */
	public static VillagerLoreDef lore() {
		return VestigesData.VILLAGER_LORE.all().values().stream().findFirst().orElse(null);
	}

	private static void assign(Villager villager, ServerLevel level) {
		VillagerLoreDef lore = lore();
		if (lore == null) {
			return;
		}
		long seed = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
		String continent = level.dimension() == Level.OVERWORLD ? WorldAtlas.continentAt(level.getServer(), villager.getX(), villager.getZ()) : null;
		villager.setAttached(PROFILE, VillagerProfile.generate(lore, seed, continent, farLand(continent, seed)));
	}

	/** A villager's profile; made on the spot for one that loaded while the system was off. */
	public static VillagerProfile profileOf(Villager villager) {
		VillagerProfile p = villager.getAttached(PROFILE);
		if (p == null && villager.level() instanceof ServerLevel level) {
			assign(villager, level);
			p = villager.getAttached(PROFILE);
		}
		return p;
	}

	/** Another continent than the given one, picked by the seed; null in a world without continents. */
	static String farLand(String continent, long seed) {
		if (continent == null) {
			return null;
		}
		List<String> others = new ArrayList<>();
		for (var land : WorldPlans.current().lands()) {
			if (!land.name().equals(continent)) {
				others.add(land.name());
			}
		}
		return others.isEmpty() ? null : others.get((int) Math.floorMod(seed >> 7, (long) others.size()));
	}

	// ---- the library --------------------------------------------------------------------------------------------------

	private static long today(ServerPlayer p) {
		return p.level().getServer().overworld().getGameTime() / 24000L;
	}

	private static String jobOf(AbstractVillager v) {
		if (v instanceof Villager villager) {
			return villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		}
		return "wandering_trader";
	}

	private static String jobTitle(String job) {
		return switch (job) {
			case "none" -> "Villager";
			case "wandering_trader" -> "Wandering Trader";
			default -> Character.toUpperCase(job.charAt(0)) + job.substring(1);
		};
	}

	/** Records a meeting: every villager the player talks to goes in the library. The Founder feat counts the first. */
	static void met(ServerPlayer p, AbstractVillager v) {
		dev.vestiges.feats.FeatService.metVillager(p);
		if (!(v instanceof Villager villager)) {
			return;
		}
		VillagerProfile profile = profileOf(villager);
		if (profile == null) {
			return;
		}
		VillagerLibrary lib = p.getAttachedOrCreate(LIBRARY, () -> VillagerLibrary.EMPTY);
		long order = p.level().getServer().overworld().getGameTime();
		p.setAttached(LIBRARY, lib.with(villager.getUUID().toString(), new VillagerLibrary.Met(profile, jobOf(villager), p.level().dimension().identifier().toString(),
			villager.getBlockX(), villager.getBlockY(), villager.getBlockZ(), today(p), order)));
	}

	/** The player's library for the hub's Villagers tab, newest first. */
	public static JsonArray libraryJson(ServerPlayer p) {
		JsonArray out = new JsonArray();
		VillagerLibrary lib = p.getAttached(LIBRARY);
		if (lib == null) {
			return out;
		}
		for (Map.Entry<String, VillagerLibrary.Met> e : lib.recent()) {
			VillagerLibrary.Met m = e.getValue();
			JsonObject o = base(e.getKey(), "villager", m.profile().name(), jobTitle(m.job()), m.profile());
			JsonObject where = new JsonObject();
			where.addProperty("dim", m.dim());
			where.addProperty("x", m.x());
			where.addProperty("y", m.y());
			where.addProperty("z", m.z());
			where.addProperty("day", m.day());
			o.add("where", where);
			o.addProperty("viewOnly", true);
			out.add(o);
		}
		return out;
	}

	private static JsonObject base(String id, String kind, String name, String job, VillagerProfile profile) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("kind", kind);
		o.addProperty("name", name);
		o.addProperty("job", job);
		JsonArray overview = new JsonArray();
		JsonObject about = new JsonObject();
		if (profile != null) {
			profile.overview().forEach(overview::add);
			about.addProperty("birthplace", profile.birthplace());
			about.addProperty("family", profile.family());
			about.addProperty("turning", profile.turning());
			about.addProperty("wants", profile.wants());
			JsonArray traits = new JsonArray();
			profile.traits().forEach(traits::add);
			about.add("traits", traits);
			about.addProperty("hook", profile.hook());
		} else {
			overview.add("A trader from far away.");
			overview.add("The stock changes every " + TradeRules.TRADER_DAYS + " days.");
		}
		o.add("overview", overview);
		o.add("about", about);
		return o;
	}

	// ---- the screen ---------------------------------------------------------------------------------------------------

	private static int levelOf(ServerPlayer p) {
		return LevelService.enabled() ? LevelService.get(p).level() : 100;
	}

	private static VillagerTradesDef tradesFor(String job) {
		for (VillagerTradesDef d : VestigesData.VILLAGER_TRADES.all().values()) {
			if (d.job().equals(job)) {
				return d;
			}
		}
		return null;
	}

	/** The offers this villager shows (the same list the buy request counts into). Babies and jobless villagers have none. */
	static List<VillagerTradesDef.Offer> offersOf(AbstractVillager v, long day) {
		if (v instanceof Villager villager) {
			if (villager.isBaby()) {
				return List.of();
			}
			VillagerTradesDef def = tradesFor(jobOf(villager));
			return def == null ? List.of() : def.offers();
		}
		VillagerTradesDef pool = tradesFor("wandering_trader");
		return pool == null ? List.of() : TradeRules.traderPick(pool, v.getUUID().getMostSignificantBits() ^ v.getUUID().getLeastSignificantBits(), day);
	}

	private static double discount(ServerPlayer p) {
		return dev.vestiges.tree.StatEngine.get(p).get("trade_discount");
	}

	private static int priceOf(ServerPlayer p, VillagerTradesDef.Offer o, boolean trader, int tier) {
		return TradeRules.price(trader ? TradeRules.traderBase(o.price(), tier) : o.price(), discount(p));
	}

	/** Opens or refreshes the screen for the player. */
	static void sendView(ServerPlayer p, AbstractVillager v) {
		long day = today(p);
		boolean trader = !(v instanceof Villager);
		int level = levelOf(p);
		int tier = TradeRules.tierFor(level);
		VillagerProfile profile = v instanceof Villager villager ? profileOf(villager) : null;
		String name = v.hasCustomName() ? v.getCustomName().getString() : profile != null ? profile.name() : "Wandering Trader";
		JsonObject o = base(v.getUUID().toString(), trader ? "trader" : "villager", name, jobTitle(jobOf(v)), profile);
		o.addProperty("emeralds", Emeralds.free(p) ? 9999 : Emeralds.count(p));
		o.addProperty("level", level);
		o.addProperty("tier", tier);
		Stock stock = v.getAttachedOrElse(STOCK, Stock.EMPTY);
		JsonArray trades = new JsonArray();
		List<VillagerTradesDef.Offer> offers = offersOf(v, day);
		for (int i = 0; i < offers.size(); i++) {
			VillagerTradesDef.Offer offer = offers.get(i);
			JsonObject t = new JsonObject();
			t.addProperty("index", i);
			t.addProperty("item", offer.item());
			t.addProperty("count", offer.count());
			t.addProperty("price", priceOf(p, offer, trader, tier));
			t.addProperty("kind", offer.kind());
			boolean locked = !trader && offer.tier() > tier;
			t.addProperty("locked", locked);
			t.addProperty("minLevel", TradeRules.levelFor(offer.tier()));
			t.addProperty("left", Math.max(0, TradeRules.stock(offer.kind()) - stock.bought(p.getUUID() + "/" + i, day)));
			trades.add(t);
		}
		o.add("trades", trades);
		if (v instanceof Villager villager) {
			String quest = QuestService.available(p, villager);
			if (!quest.isEmpty()) {
				JsonObject q = QuestService.describe(quest);
				q.addProperty("full", QuestService.sideFull(p));
				o.add("quest", q);
			}
		}
		ServerPlayNetworking.send(p, new VillagerPayloads.View(o.toString()));
	}

	// ---- buying and quests --------------------------------------------------------------------------------------------

	private static AbstractVillager find(ServerPlayer p, String id) {
		try {
			Entity e = ((ServerLevel) p.level()).getEntity(UUID.fromString(id));
			return e instanceof AbstractVillager v && v.distanceTo(p) < 12 ? v : null;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void onAct(ServerPlayer p, VillagerPayloads.Act a) {
		if (!screenOpens()) {
			return;
		}
		AbstractVillager v = find(p, a.id());
		if (v == null) {
			MapNet.message(p, "That villager is too far away.", true);
			return;
		}
		switch (a.op()) {
			case "buy" -> buy(p, v, a.arg());
			case "quest" -> {
				if (v instanceof Villager villager && QuestService.available(p, villager).equals(a.arg()) && !QuestService.sideFull(p)) {
					QuestService.offer(p, a.arg(), v.getUUID().toString());
				} else {
					MapNet.message(p, "That quest is not on offer now.", true);
				}
			}
			default -> {
			}
		}
	}

	/** Buys one unit of an offer, or as many as the stock and emeralds allow ({@code arg} is "index" or "index:all"). */
	private static void buy(ServerPlayer p, AbstractVillager v, String arg) {
		boolean all = arg.endsWith(":all");
		int index;
		try {
			index = Integer.parseInt(all ? arg.substring(0, arg.length() - 4) : arg);
		} catch (NumberFormatException e) {
			return;
		}
		long day = today(p);
		List<VillagerTradesDef.Offer> offers = offersOf(v, day);
		if (index < 0 || index >= offers.size()) {
			return;
		}
		VillagerTradesDef.Offer offer = offers.get(index);
		boolean trader = !(v instanceof Villager);
		int tier = TradeRules.tierFor(levelOf(p));
		if (!trader && offer.tier() > tier) {
			MapNet.message(p, "You need level " + TradeRules.levelFor(offer.tier()) + " for that.", true);
			return;
		}
		Stock stock = v.getAttachedOrElse(STOCK, Stock.EMPTY);
		String key = p.getUUID() + "/" + index;
		int left = TradeRules.stock(offer.kind()) - stock.bought(key, day);
		int price = priceOf(p, offer, trader, tier);
		if (left <= 0) {
			MapNet.message(p, "Sold out for today.", true);
			return;
		}
		int have = Emeralds.free(p) ? Integer.MAX_VALUE : Emeralds.count(p);
		int qty = all ? Math.min(left, have / price) : 1;
		if (qty < 1 || have < price) {
			MapNet.message(p, "Not enough emeralds (" + price + " needed).", true);
			return;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(offer.item())).orElse(null);
		if (item == null || !Emeralds.pay(p, price * qty)) {
			return;
		}
		int remaining = offer.count() * qty;
		while (remaining > 0) {
			ItemStack s = new ItemStack(item, Math.min(remaining, item.getDefaultMaxStackSize()));
			remaining -= s.getCount();
			if (!p.getInventory().add(s)) {
				p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
			}
		}
		v.setAttached(STOCK, stock.add(key, day, qty));
		LevelService.addXp(p, (long) price * qty, "trade");
		sendView(p, v);
	}
}
