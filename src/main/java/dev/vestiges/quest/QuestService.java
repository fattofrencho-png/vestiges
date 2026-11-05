package dev.vestiges.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.codex.CodexEntry;
import dev.vestiges.codex.CodexProgress;
import dev.vestiges.codex.CodexService;
import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.QuestDef;
import dev.vestiges.data.VestigesData;
import dev.vestiges.feats.FeatService;
import dev.vestiges.gear.Rarity;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.level.LevelService;
import dev.vestiges.loot.LootGear;
import dev.vestiges.loot.LootLevels;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import dev.vestiges.net.QuestPayloads;
import dev.vestiges.server.Emeralds;
import dev.vestiges.server.HubNet;
import dev.vestiges.server.MapNet;
import dev.vestiges.settlement.SettlementData;
import dev.vestiges.settlement.SettlementService;
import dev.vestiges.tree.PlayerTree;
import dev.vestiges.tree.TreeService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/**
 * Quests: optional, accepted by the player, checked on the server. State steps (a level, a feat) are read every two seconds, stat steps count
 * from vanilla's statistics since the step began, event steps count what the player does (shared with players nearby who have the same quest),
 * bring steps are handed in from the Quests tab. Rewards pay once per finish. Numbers and texts are drafts until the quest list is reviewed.
 */
public final class QuestService {
	public static final AttachmentType<QuestState> STATE = AttachmentRegistry.create(Vestiges.id("quests"),
		b -> b.persistent(QuestState.CODEC).copyOnDeath());
	private static final String NOTE_KEY = "vestiges_quest";
	private static final Set<String> STAT_TYPES = Set.of("enchant", "breed", "fish", "craft", "use", "trade", "stat");
	/** The kinds a generic {@code stat} step names before the id ("mined:minecraft:stone"); no kind means a custom statistic ("minecraft:walk_one_cm"). */
	private static final Set<String> STAT_KINDS = Set.of("custom", "mined", "crafted", "used", "broken", "picked_up", "dropped", "killed", "killed_by");
	private static final Set<String> EVENT_TYPES = Set.of("kill", "mine", "harvest", "structure", "biome", "waypoint", "go_there", "waystone_trip", "talk", "new_biome",
		"hub_open", "track_quest", "grid_craft", "loot_chest", "kill_high_level", "kill_rank", "kill_power");
	private static final Set<String> STATE_TYPES = Set.of("level", "codex", "fight_points", "craft_points", "rarity", "wear", "rack_tool", "rack_weapon", "hold_tool", "hold_weapon",
		"belt", "bank", "ascended", "familiar", "bestiary", "feat", "renown", "named_settlement", "ward", "table_slot", "advancement");
	/** Offers a player has seen and may still accept: quest id to the villager who offered it, "" for a note. Not saved. */
	private static final Map<UUID, Map<String, String>> OFFERS = new HashMap<>();
	/** The last tracker text sent per player, so an unchanged one isn't sent again. Not saved. */
	private static final Map<UUID, String> LAST_TRACKER = new HashMap<>();
	/** Whether the block being broken was placed by a player: read before the Codex takes it from the placed-block memory. Not saved. */
	private static final Map<UUID, Boolean> BREAKING_PLACED = new HashMap<>();

	private QuestService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.QUESTS);
	}

	public static QuestState get(ServerPlayer p) {
		return p.getAttachedOrElse(STATE, QuestState.EMPTY);
	}

	private static QuestDef def(String id) {
		return VestigesData.QUESTS.get(Vestiges.id(id));
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(QuestService::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(QuestService::onKill);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl) {
				BREAKING_PLACED.put(player.getUUID(), PlacedBlocks.isPlaced(sl, pos));
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			boolean placed = BREAKING_PLACED.getOrDefault(player.getUUID(), true);
			if (player instanceof ServerPlayer sp && !placed && !sp.getAbilities().instabuild) {
				event(sp, "mine", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof Villager v && sp.isShiftKeyDown() && enabled()) {
				return onVillager(sp, v) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && enabled()) {
				String id = noteQuest(sp.getItemInHand(hand));
				if (!id.isEmpty()) {
					offer(sp, id, "");
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		ServerPlayNetworking.registerGlobalReceiver(QuestPayloads.Action.TYPE, (payload, ctx) -> onAction(ctx.player(), payload));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0 || !enabled()) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			check(p);
			sendTracker(p);
		}
	}

	/** Moves every running quest on as far as what the player already has allows. */
	public static void check(ServerPlayer p) {
		for (String id : List.copyOf(get(p).active().keySet())) {
			QuestDef d = def(id);
			if (d == null) {
				continue;
			}
			while (true) {
				QuestState.Active a = get(p).active().get(id);
				if (a == null || a.step() >= d.steps().size()) {
					break;
				}
				QuestDef.Step s = d.steps().get(a.step());
				if ("biome".equals(s.type())) {
					checkBiome(p, id, a, s);
					a = get(p).active().get(id);
				}
				if (!finishedStep(p, s, a)) {
					break;
				}
				advance(p, id, d, a);
			}
		}
	}

	private static void checkBiome(ServerPlayer p, String id, QuestState.Active a, QuestDef.Step s) {
		if (a.count() < goal(s, a) && p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("").equals(pick(s, a))) {
			setActive(p, id, a.counted(a.count() + 1));
		}
	}

	private static boolean finishedStep(ServerPlayer p, QuestDef.Step s, QuestState.Active a) {
		return !"bring".equals(s.type()) && !"choice".equals(s.type()) && have(p, s, a) >= goal(s, a);
	}

	private static void setActive(ServerPlayer p, String id, QuestState.Active a) {
		QuestState st = get(p);
		String tracked = st.tracked();
		p.setAttached(STATE, st.withActive(id, a).tracking(tracked.isEmpty() ? id : tracked));
	}

	private static void advance(ServerPlayer p, String id, QuestDef d, QuestState.Active a) {
		if (a.step() + 1 >= d.steps().size()) {
			complete(p, id, d, a);
			return;
		}
		QuestDef.Step next = d.steps().get(a.step() + 1);
		QuestState.Active moved = a.next(0);
		setActive(p, id, new QuestState.Active(moved.step(), 0, stat(p, next, moved), moved.giver(), moved.picks(), moved.comeback()));
	}

	private static void complete(ServerPlayer p, String id, QuestDef d, QuestState.Active a) {
		QuestDef.Reward r = d.reward();
		long day = p.level().getServer().overworld().getGameTime() / 24000L;
		QuestState st = get(p).finish(id, day);
		boolean titleNew = !r.title().isEmpty() && a.comeback() == 1;
		if (titleNew) {
			st = st.earned(r.title());
		}
		p.setAttached(STATE, st);
		long xp = QuestRules.xp(r.xp(), r.share(), levelNeed(p), d.side(), a.comeback());
		long gained = xp > 0 ? payXp(p, xp) : 0;
		int emeralds = emeralds(p, d, a.comeback());
		if (emeralds > 0) {
			give(p, new ItemStack(Items.EMERALD, emeralds));
		}
		if (a.comeback() == 1) {
			for (String item : r.items()) {
				give(p, stack(item));
			}
			if (!r.gear().isEmpty()) {
				give(p, gearPiece(p, r.gear()));
			}
			if (r.refunds() > 0 && ConfigStore.server().enabled(VestigesSystem.PASSIVE_TREE)) {
				TreeService.addFreeRefunds(p, r.refunds());
			}
		}
		checkAllGuides(p);
		JsonObject o = new JsonObject();
		o.addProperty("name", d.name());
		o.addProperty("reward", titleNew ? "Title: " + r.title() : "Quest done");
		o.addProperty("xp", gained);
		o.addProperty("icon", "minecraft:writable_book");
		o.addProperty("quest", true);
		LevelService.notice(p, "feat", o);
		MapNet.message(p, "Quest done: " + d.name(), false);
	}

	/** One piece of gear of the asked rarity, in the band of the player's loot level (the same builder as found loot). */
	private static ItemStack gearPiece(ServerPlayer p, String rarity) {
		Rarity.Tier tier = Rarity.Tier.valueOf(rarity.toUpperCase(java.util.Locale.ROOT));
		return LootGear.build(p.level(), p.getRandom(), LootLevels.forPlayer(p, p.position()), false, tier, tier);
	}

	/** What a share of XP is taken from: the Levels need, or with Levels off the player's next vanilla level. */
	private static long levelNeed(ServerPlayer p) {
		return LevelService.enabled() ? LevelService.get(p).need() : p.getXpNeededForNextLevel();
	}

	/** Pays quest XP as Levels XP, or as vanilla XP when Levels are off (vanilla quests must work without the mod's core). Returns what was paid. */
	private static long payXp(ServerPlayer p, long xp) {
		if (LevelService.enabled()) {
			return LevelService.addXp(p, xp, "quest");
		}
		p.giveExperiencePoints((int) Math.min(xp, Integer.MAX_VALUE));
		return xp;
	}

	/** The emeralds a quest pays this player: written for levels 1-9, times their price tier. With Levels off there is no tier. */
	private static int emeralds(ServerPlayer p, QuestDef d, int comeback) {
		int level = LevelService.enabled() ? LevelService.get(p).level() : 1;
		return QuestRules.emeralds(d.reward().emeralds(), d.side(), comeback, level);
	}

	/** Finishing every guide quest gives its own title. */
	private static void checkAllGuides(ServerPlayer p) {
		QuestState st = get(p);
		boolean any = false;
		for (Map.Entry<Identifier, QuestDef> e : VestigesData.QUESTS.all().entrySet()) {
			if ("guide".equals(e.getValue().kind())) {
				any = true;
				if (!st.done(e.getKey().getPath())) {
					return;
				}
			}
		}
		if (any) {
			p.setAttached(STATE, st.earned("the Ready"));
		}
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!stack.isEmpty() && !p.getInventory().add(stack)) {
			net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack);
			drop.setTarget(p.getUUID());
			p.level().addFreshEntity(drop);
		}
	}

	private static ItemStack stack(String item) {
		int at = item.lastIndexOf(':');
		int count = 1;
		String name = item;
		if (at > 0 && item.substring(at + 1).chars().allMatch(Character::isDigit)) {
			count = Integer.parseInt(item.substring(at + 1));
			name = item.substring(0, at);
		}
		Identifier key = Identifier.tryParse(name);
		Item it = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
		return new ItemStack(it, count);
	}

	// ---- steps -------------------------------------------------------------------------------------------------------

	private static String pick(QuestDef.Step s, QuestState.Active a) {
		return a.step() < a.picks().size() ? a.picks().get(a.step()) : "";
	}

	/** A kill step asks for elites from its comeback on, and always when the target says so. */
	private static boolean wantsElite(QuestDef.Step s, QuestState.Active a) {
		return "elite".equals(pick(s, a)) || s.eliteFrom() > 0 && a.comeback() >= s.eliteFrom() && ConfigStore.server().enabled(VestigesSystem.ELITES);
	}

	static int goal(QuestDef.Step s, QuestState.Active a) {
		if (wantsElite(s, a) && "kill".equals(s.type())) {
			return Math.max(1, QuestRules.goal(s.amount(), a.comeback()) / 3);
		}
		return STATE_TYPES.contains(s.type()) ? s.amount() : QuestRules.goal(s.amount(), a.comeback());
	}

	/** How far a step has come. */
	private static long have(ServerPlayer p, QuestDef.Step s, QuestState.Active a) {
		if (STAT_TYPES.contains(s.type())) {
			return stat(p, s, a) - a.base();
		}
		if (STATE_TYPES.contains(s.type())) {
			return value(p, s, a);
		}
		if ("bring".equals(s.type())) {
			return count(p, stack(pick(s, a)).getItem());
		}
		return a.count();
	}

	/** Vanilla's statistic behind a stat step. */
	private static long stat(ServerPlayer p, QuestDef.Step s, QuestState.Active a) {
		var stats = p.getStats();
		Identifier key = Identifier.tryParse(pick(s, a));
		Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
		return switch (s.type()) {
			case "enchant" -> stats.getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM));
			case "breed" -> stats.getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED));
			case "fish" -> stats.getValue(Stats.CUSTOM.get(Stats.FISH_CAUGHT));
			case "craft" -> stats.getValue(Stats.ITEM_CRAFTED.get(item));
			case "use" -> stats.getValue(Stats.ITEM_USED.get(item));
			case "trade" -> stats.getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
			case "stat" -> statValue(p, pick(s, a));
			default -> 0;
		};
	}

	/**
	 * Any vanilla statistic by id, for the generic {@code stat} step: "kind:id" with a kind from {@link #STAT_KINDS}, or just an id for a custom
	 * statistic. Distances ("..._one_cm") count in blocks. An id that doesn't exist counts 0.
	 */
	static long statValue(ServerPlayer p, String target) {
		String kind = "custom";
		String id = target;
		for (String k : STAT_KINDS) {
			if (target.startsWith(k + ":")) {
				kind = k;
				id = target.substring(k.length() + 1);
				break;
			}
		}
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return 0;
		}
		var stats = p.getStats();
		return switch (kind) {
			case "mined" -> BuiltInRegistries.BLOCK.containsKey(key) ? stats.getValue(Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.getValue(key))) : 0;
			case "crafted" -> BuiltInRegistries.ITEM.containsKey(key) ? stats.getValue(Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.getValue(key))) : 0;
			case "used" -> BuiltInRegistries.ITEM.containsKey(key) ? stats.getValue(Stats.ITEM_USED.get(BuiltInRegistries.ITEM.getValue(key))) : 0;
			case "broken" -> BuiltInRegistries.ITEM.containsKey(key) ? stats.getValue(Stats.ITEM_BROKEN.get(BuiltInRegistries.ITEM.getValue(key))) : 0;
			case "picked_up" -> BuiltInRegistries.ITEM.containsKey(key) ? stats.getValue(Stats.ITEM_PICKED_UP.get(BuiltInRegistries.ITEM.getValue(key))) : 0;
			case "dropped" -> BuiltInRegistries.ITEM.containsKey(key) ? stats.getValue(Stats.ITEM_DROPPED.get(BuiltInRegistries.ITEM.getValue(key))) : 0;
			case "killed" -> BuiltInRegistries.ENTITY_TYPE.containsKey(key) ? stats.getValue(Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.getValue(key))) : 0;
			case "killed_by" -> BuiltInRegistries.ENTITY_TYPE.containsKey(key) ? stats.getValue(Stats.ENTITY_KILLED_BY.get(BuiltInRegistries.ENTITY_TYPE.getValue(key))) : 0;
			default -> {
				long raw = BuiltInRegistries.CUSTOM_STAT.containsKey(key) ? stats.getValue(Stats.CUSTOM.get(key)) : 0;
				yield key.getPath().endsWith("_one_cm") ? raw / 100 : raw;
			}
		};
	}

	/** Whether the player has a vanilla advancement, for the generic {@code advancement} step (0 or 1). */
	private static int advancement(ServerPlayer p, String id) {
		Identifier key = Identifier.tryParse(id);
		var holder = key == null ? null : p.level().getServer().getAdvancements().get(key);
		return holder != null && p.getAdvancements().getOrStartProgress(holder).isDone() ? 1 : 0;
	}

	private static long value(ServerPlayer p, QuestDef.Step s, QuestState.Active a) {
		PlayerTree tree = TreeService.get(p);
		String target = pick(s, a);
		return switch (s.type()) {
			case "level" -> LevelService.get(p).level();
			case "codex" -> CodexService.discovered(p);
			case "fight_points" -> TreeService.spent("fight", tree.fight());
			case "craft_points" -> TreeService.spent("craft", tree.craft());
			case "rarity" -> bestRarity(p);
			case "wear" -> armorPieces(p);
			case "rack_tool" -> rack(p, ExtraSlots.TOOLS, ExtraSlots.TOOL_COUNT);
			case "rack_weapon" -> rack(p, ExtraSlots.WEAPONS, ExtraSlots.WEAPON_COUNT);
			case "hold_tool" -> holding(p, ExtraSlots.TOOLS, ExtraSlots.TOOL_COUNT);
			case "hold_weapon" -> holding(p, ExtraSlots.WEAPONS, ExtraSlots.WEAPON_COUNT);
			case "belt" -> onBelt(p, target);
			case "bank" -> Emeralds.bank(p);
			case "ascended" -> tree.ascendancy().isEmpty() ? 0 : 1;
			case "familiar" -> bestBlockStage(p);
			case "bestiary" -> bestKillCount(p);
			case "feat" -> FeatService.get(p).done().contains(target) ? 1 : 0;
			case "renown" -> SettlementService.bestOwnedLevel(p);
			case "named_settlement" -> namedSettlements(p);
			case "ward" -> wards(p);
			case "table_slot" -> ((ExtraHolder) p).vestiges$slots().hasTable() ? 1 : 0;
			case "advancement" -> advancement(p, target);
			default -> 0;
		};
	}

	private static int bestRarity(ServerPlayer p) {
		int best = 0;
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && Rarity.applies(stack)) {
				best = Math.max(best, Rarity.of(stack).tier().ordinal());
			}
		}
		return best;
	}

	private static int armorPieces(ServerPlayer p) {
		int n = 0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			if (!p.getItemBySlot(slot).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	private static int rack(ServerPlayer p, int from, int size) {
		ExtraSlots slots = ((ExtraHolder) p).vestiges$slots();
		for (int i = from; i < from + size; i++) {
			if (!slots.getItem(i).isEmpty()) {
				return 1;
			}
		}
		return 0;
	}

	private static int holding(ServerPlayer p, int from, int size) {
		ExtraSlots slots = ((ExtraHolder) p).vestiges$slots();
		ItemStack hand = p.getMainHandItem();
		for (int i = from; i < from + size; i++) {
			if (!hand.isEmpty() && slots.getItem(i) == hand) {
				return 1;
			}
		}
		return 0;
	}

	private static int onBelt(ServerPlayer p, String target) {
		ExtraSlots slots = ((ExtraHolder) p).vestiges$slots();
		Item item = stack(target).getItem();
		for (int i = ExtraSlots.BELT; i < ExtraSlots.BELT + ExtraSlots.BELT_COUNT; i++) {
			if (slots.getItem(i).is(item)) {
				return 1;
			}
		}
		return 0;
	}

	private static int bestBlockStage(ServerPlayer p) {
		CodexProgress progress = CodexService.get(p);
		int best = 0;
		for (Map.Entry<Identifier, CodexEntry> e : VestigesData.CODEX.all().entrySet()) {
			if (CodexEntry.BLOCKS.equals(e.getValue().section())) {
				best = Math.max(best, progress.stage(e.getKey().getPath()));
			}
		}
		return best;
	}

	private static int bestKillCount(ServerPlayer p) {
		CodexProgress progress = CodexService.get(p);
		int best = 0;
		for (Map.Entry<Identifier, CodexEntry> e : VestigesData.CODEX.all().entrySet()) {
			if (CodexEntry.MOBS.equals(e.getValue().section())) {
				best = Math.max(best, progress.count(e.getKey().getPath()));
			}
		}
		return best;
	}

	private static int namedSettlements(ServerPlayer p) {
		int n = 0;
		for (SettlementData.Settlement s : SettlementService.of(p)) {
			if (s.owner().equals(p.getUUID()) && !"Unnamed settlement".equals(s.name())) {
				n++;
			}
		}
		return n;
	}

	private static int wards(ServerPlayer p) {
		int n = 0;
		for (SettlementData.Marker m : SettlementService.world(p.level().getServer()).markers()) {
			if (m.owner().equals(p.getUUID()) && m.type().startsWith("ward")) {
				n++;
			}
		}
		return n;
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	// ---- events ------------------------------------------------------------------------------------------------------

	private static void onKill(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		MobData mob = MobService.data(dead);
		event(killer, "kill", EntityType.getKey(dead.getType()).toString(), mob != null && mob.elite() ? 2 : 1);
		if (mob != null) {
			if (mob.rank() > MobData.NORMAL) {
				event(killer, "kill_rank", rankName(mob.rank()), 1);
			}
			for (String power : mob.powers()) {
				event(killer, "kill_power", power, 1);
			}
			int gap = mob.level() - LevelService.get(killer).level();
			if (gap > 0) {
				event(killer, "kill_high_level", String.valueOf(gap), 1);
			}
		}
	}

	private static String rankName(int rank) {
		return switch (rank) {
			case MobData.CHAMPION -> "champion";
			case MobData.ELITE -> "elite";
			default -> "warlord";
		};
	}

	/**
	 * Something the player did. For kills, {@code amount} 2 means an elite. Players nearby who have the same quest on a step like it also move on,
	 * and each gets their own reward when they finish.
	 */
	public static void event(ServerPlayer p, String type, String target, int amount) {
		if (!enabled()) {
			return;
		}
		apply(p, type, target, amount);
		if ("kill".equals(type) || "mine".equals(type) || "harvest".equals(type) || "kill_rank".equals(type) || "kill_power".equals(type)) {
			for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
				if (other != p && other.level() == p.level() && other.distanceTo(p) <= QuestRules.SHARE_RADIUS) {
					apply(other, type, target, amount);
				}
			}
		}
	}

	private static void apply(ServerPlayer p, String type, String target, int amount) {
		for (Map.Entry<String, QuestState.Active> e : get(p).active().entrySet()) {
			QuestDef d = def(e.getKey());
			QuestState.Active a = e.getValue();
			if (d == null || a.step() >= d.steps().size()) {
				continue;
			}
			QuestDef.Step s = d.steps().get(a.step());
			if (!s.type().equals(type) || a.count() >= goal(s, a)) {
				continue;
			}
			String wanted = pick(s, a);
			boolean elite = "kill".equals(type) && amount == 2;
			boolean match = "kill".equals(type) && wantsElite(s, a) ? elite : wanted.isEmpty() || "any".equals(wanted) || wanted.equals(target)
					|| "kill_high_level".equals(type) && QuestRules.atLeast(target, wanted);
			if (match) {
				setActive(p, e.getKey(), a.counted(a.count() + 1));
			}
		}
	}

	private static boolean onVillager(ServerPlayer p, Villager v) {
		String giver = v.getUUID().toString();
		// A talk step at the villager who gave the quest comes first.
		for (Map.Entry<String, QuestState.Active> e : get(p).active().entrySet()) {
			QuestDef d = def(e.getKey());
			QuestState.Active a = e.getValue();
			if (d != null && a.step() < d.steps().size() && "talk".equals(d.steps().get(a.step()).type()) && giver.equals(a.giver())) {
				setActive(p, e.getKey(), a.counted(a.count() + 1));
				check(p);
				sendTracker(p);
				return true;
			}
		}
		String job = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		QuestState st = get(p);
		long today = p.level().getServer().overworld().getGameTime() / 24000L;
		long activeSide = st.active().keySet().stream().map(QuestService::def).filter(d -> d != null && d.side()).count();
		for (Map.Entry<Identifier, QuestDef> e : VestigesData.QUESTS.all().entrySet()) {
			QuestDef d = e.getValue();
			String id = e.getKey().getPath();
			if ("villager".equals(d.start()) && job.equals(d.job()) && !st.active().containsKey(id) && !blocked(d)
				&& (!st.done(id) || QuestRules.canReturn(st.finishedDay().getOrDefault(id, 0L), today))) {
				if (activeSide >= QuestRules.MAX_SIDE_ACTIVE) {
					MapNet.message(p, "You already have " + QuestRules.MAX_SIDE_ACTIVE + " side quests.", true);
					return true;
				}
				offer(p, id, giver);
				return true;
			}
		}
		return false;
	}

	// ---- offers and actions ------------------------------------------------------------------------------------------

	/** Opens the offer page. Only quests offered this way (and tab quests) can be accepted. */
	public static void offer(ServerPlayer p, String id, String giver) {
		QuestDef d = def(id);
		QuestState st = get(p);
		if (d == null || st.active().containsKey(id) || !d.side() && st.done(id)) {
			MapNet.message(p, "You have already taken this up.", false);
			return;
		}
		OFFERS.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(id, giver);
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("name", d.name());
		o.addProperty("giver", d.giver());
		o.addProperty("intro", d.intro());
		JsonArray steps = new JsonArray();
		for (QuestDef.Step s : d.steps()) {
			steps.add(fill(s.text(), firstTarget(s), QuestRules.goal(s.amount(), st.finished().getOrDefault(id, 0) + 1)));
		}
		o.add("steps", steps);
		o.addProperty("reward", rewardText(p, d, st.finished().getOrDefault(id, 0) + 1));
		LevelService.notice(p, "quest_offer", o);
	}

	private static String firstTarget(QuestDef.Step s) {
		return s.targets().isEmpty() ? "" : s.targets().get(0);
	}

	private static void onAction(ServerPlayer p, QuestPayloads.Action a) {
		if (!enabled()) {
			return;
		}
		switch (a.op()) {
			case "accept" -> accept(p, a.id());
			case "drop" -> p.setAttached(STATE, get(p).without(a.id()));
			case "track" -> {
				if (get(p).active().containsKey(a.id())) {
					p.setAttached(STATE, get(p).tracking(a.id()));
					event(p, "track_quest", a.id(), 1);
				}
			}
			case "hand_in" -> handIn(p, a.id());
			case "choose" -> choose(p, a.id(), a.arg());
			case "title" -> {
				if (a.id().isEmpty() || get(p).titles().contains(a.id())) {
					p.setAttached(STATE, get(p).wearing(a.id()));
				}
			}
			case "event" -> {
				if ("waypoint".equals(a.id()) || "go_there".equals(a.id()) || "hub_open".equals(a.id())) {
					event(p, a.id(), "", 1);
				}
			}
			default -> {
			}
		}
		check(p);
		sendTracker(p);
		if (!"event".equals(a.op())) {
			HubNet.send(p);
		}
	}

	/** Starts a quest the player may start: a tab quest whose turn has come, or one offered to them. */
	public static boolean accept(ServerPlayer p, String id) {
		QuestDef d = def(id);
		QuestState st = get(p);
		if (d == null || st.active().containsKey(id)) {
			return false;
		}
		Map<String, String> offers = OFFERS.getOrDefault(p.getUUID(), Map.of());
		boolean allowed = offers.containsKey(id) || "tab".equals(d.start()) && tabAvailable(st, id, d);
		if (!allowed || !d.side() && st.done(id)) {
			return false;
		}
		String giver = offers.getOrDefault(id, "");
		if (offers.containsKey(id)) {
			OFFERS.get(p.getUUID()).remove(id);
		}
		start(p, id, d, giver);
		return true;
	}

	private static void start(ServerPlayer p, String id, QuestDef d, String giver) {
		QuestState st = get(p);
		List<String> picks = new ArrayList<>();
		for (QuestDef.Step s : d.steps()) {
			picks.add(s.targets().isEmpty() ? "" : s.targets().get(p.getRandom().nextInt(s.targets().size())));
		}
		QuestState.Active a = new QuestState.Active(0, 0, 0, giver, List.copyOf(picks), st.finished().getOrDefault(id, 0) + 1);
		a = new QuestState.Active(0, 0, stat(p, d.steps().get(0), a), giver, a.picks(), a.comeback());
		p.setAttached(STATE, st.withActive(id, a));
	}

	/** Admin: starts any quest without the usual gates. */
	public static boolean adminStart(ServerPlayer p, String id) {
		QuestDef d = def(id);
		if (d == null || get(p).active().containsKey(id)) {
			return false;
		}
		start(p, id, d, "");
		check(p);
		sendTracker(p);
		return true;
	}

	/** Admin: finishes a running quest with its reward. */
	public static boolean adminComplete(ServerPlayer p, String id) {
		QuestDef d = def(id);
		QuestState.Active a = get(p).active().get(id);
		if (d == null || a == null) {
			return false;
		}
		complete(p, id, d, a);
		sendTracker(p);
		return true;
	}

	public static boolean adminReset(ServerPlayer p, String id) {
		if (def(id) == null) {
			return false;
		}
		p.setAttached(STATE, get(p).reset(id));
		sendTracker(p);
		return true;
	}

	public static void adminReputation(ServerPlayer p, String group, int value) {
		Map<String, Integer> m = new HashMap<>(get(p).standing());
		m.put(group, Math.max(-QuestRules.MAX_REP, Math.min(QuestRules.MAX_REP, value)));
		p.setAttached(STATE, get(p).withStanding(m));
		earnRanks(p);
	}

	/** True while a feature one of its steps needs is off in this world. */
	private static boolean blocked(QuestDef d) {
		for (QuestDef.Step s : d.steps()) {
			VestigesSystem needed = QuestRules.needs(s.type());
			if (needed != null && !ConfigStore.server().enabled(needed) || s.targets().contains("elite") && !ConfigStore.server().enabled(VestigesSystem.ELITES)) {
				return true;
			}
		}
		return false;
	}

	private static boolean tabAvailable(QuestState st, String id, QuestDef d) {
		if (!"tab".equals(d.start()) || st.done(id) || st.active().containsKey(id) || blocked(d)) {
			return false;
		}
		for (Map.Entry<Identifier, QuestDef> e : VestigesData.QUESTS.all().entrySet()) {
			QuestDef other = e.getValue();
			if (other.chain().equals(d.chain()) && other.order() < d.order() && !st.done(e.getKey().getPath())) {
				return false;
			}
		}
		return true;
	}

	/** Takes what a bring step asks for from the inventory, on the server. */
	private static void handIn(ServerPlayer p, String id) {
		QuestDef d = def(id);
		QuestState.Active a = get(p).active().get(id);
		if (d == null || a == null || a.step() >= d.steps().size() || !"bring".equals(d.steps().get(a.step()).type())) {
			return;
		}
		QuestDef.Step s = d.steps().get(a.step());
		Item item = stack(pick(s, a)).getItem();
		int need = goal(s, a);
		if (item == Items.AIR || count(p, item) < need) {
			MapNet.message(p, "You don't have enough yet.", true);
			return;
		}
		int left = need;
		var items = p.getInventory().getNonEquipmentItems();
		for (ItemStack stack : items) {
			if (left > 0 && stack.is(item)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		p.getInventory().setChanged();
		advance(p, id, d, a);
	}

	private static void choose(ServerPlayer p, String id, String option) {
		QuestDef d = def(id);
		QuestState.Active a = get(p).active().get(id);
		if (d == null || a == null || a.step() >= d.steps().size() || !"choice".equals(d.steps().get(a.step()).type())) {
			return;
		}
		List<QuestDef.Choice> choices = d.steps().get(a.step()).choices();
		int i;
		try {
			i = Integer.parseInt(option);
		} catch (NumberFormatException e) {
			return;
		}
		if (i < 0 || i >= choices.size()) {
			return;
		}
		QuestDef.Choice c = choices.get(i);
		p.setAttached(STATE, get(p).withStanding(QuestRules.applyChoice(get(p).standing(), c.reputation())));
		earnRanks(p);
		if (!c.after().isEmpty()) {
			MapNet.message(p, c.after(), false);
		}
		advance(p, id, d, a);
	}

	/** Rank titles follow standing: earned at the rank and lost below it, so a worn one comes off when the standing falls. */
	private static void earnRanks(ServerPlayer p) {
		QuestState st = get(p);
		for (Map.Entry<String, Integer> e : st.standing().entrySet()) {
			String rank = QuestRules.rank(e.getKey(), e.getValue());
			if (!rank.isEmpty()) {
				st = st.earned(rank);
			}
		}
		if (!st.title().isEmpty() && st.title().contains(" of ") && !isCurrentRank(st, st.title())) {
			st = st.wearing("");
		}
		p.setAttached(STATE, st);
	}

	private static boolean isCurrentRank(QuestState st, String title) {
		for (String group : QuestRules.RANKS.keySet()) {
			for (int i = 0; i < QuestRules.RANK_AT.length; i++) {
				String candidate = QuestRules.RANKS.get(group).get(i) + " of " + QuestRules.GROUP_NAMES.get(group);
				if (candidate.equals(title)) {
					return st.standing().getOrDefault(group, 0) >= QuestRules.RANK_AT[i];
				}
			}
		}
		return true;
	}

	// ---- notes --------------------------------------------------------------------------------------------------------

	/** A note that offers a quest: a vanilla written book with a marker. No new item. */
	public static ItemStack note(String id) {
		QuestDef d = def(id);
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		if (d != null) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(d.name()));
			CompoundTag tag = new CompoundTag();
			tag.putString(NOTE_KEY, id);
			CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		}
		return stack;
	}

	private static String noteQuest(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(NOTE_KEY, "");
	}

	// ---- what the client sees -----------------------------------------------------------------------------------------

	private static String fill(String text, String target, int amount) {
		return text.replace("{target}", label(target)).replace("{amount}", String.valueOf(amount));
	}

	/** A readable name for a step's subject. */
	private static String label(String target) {
		Identifier key = target.contains(":") ? Identifier.tryParse(target) : null;
		if (key == null) {
			return target;
		}
		if (BuiltInRegistries.ITEM.containsKey(key)) {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();
		}
		if (BuiltInRegistries.BLOCK.containsKey(key)) {
			Block block = BuiltInRegistries.BLOCK.getValue(key);
			return block.getName().getString();
		}
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
			return BuiltInRegistries.ENTITY_TYPE.getValue(key).getDescription().getString();
		}
		return key.getPath().replace('_', ' ');
	}

	private static String rewardText(ServerPlayer p, QuestDef d, int comeback) {
		QuestDef.Reward r = d.reward();
		List<String> parts = new ArrayList<>();
		long xp = QuestRules.xp(r.xp(), r.share(), levelNeed(p), d.side(), comeback);
		if (xp > 0) {
			parts.add(xp + " XP");
		}
		int emeralds = emeralds(p, d, comeback);
		if (emeralds > 0) {
			parts.add(emeralds + " emeralds");
		}
		if (comeback == 1) {
			for (String item : r.items()) {
				ItemStack stack = stack(item);
				parts.add(stack.getCount() + " " + stack.getHoverName().getString());
			}
			if (!r.gear().isEmpty()) {
				parts.add("1 " + r.gear() + " piece of gear");
			}
			if (r.refunds() > 0 && ConfigStore.server().enabled(VestigesSystem.PASSIVE_TREE)) {
				parts.add(r.refunds() + " free tree refund" + (r.refunds() == 1 ? "" : "s"));
			}
			if (!r.title().isEmpty()) {
				parts.add("Title: " + r.title());
			}
		}
		return String.join(", ", parts);
	}

	private static JsonObject questJson(ServerPlayer p, String id, QuestDef d, QuestState.Active a) {
		QuestState st = get(p);
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("name", d.name());
		o.addProperty("kind", d.kind());
		o.addProperty("chain", d.chain());
		o.addProperty("giver", d.giver());
		o.addProperty("intro", d.intro());
		int comeback = a != null ? a.comeback() : st.finished().getOrDefault(id, 0) + 1;
		o.addProperty("reward", rewardText(p, d, comeback));
		o.addProperty("comeback", comeback);
		JsonArray steps = new JsonArray();
		for (int i = 0; i < d.steps().size(); i++) {
			QuestDef.Step s = d.steps().get(i);
			JsonObject j = new JsonObject();
			if (a == null) {
				QuestState.Active preview = new QuestState.Active(i, 0, 0, "", List.of(), comeback);
				j.addProperty("text", fill(s.text(), firstTarget(s), goal(s, preview)));
				j.addProperty("need", goal(s, preview));
				j.addProperty("have", 0);
				j.addProperty("state", "ahead");
			} else {
				QuestState.Active at = new QuestState.Active(i, a.count(), a.base(), a.giver(), a.picks(), a.comeback());
				int need = "choice".equals(s.type()) ? 1 : goal(s, at);
				j.addProperty("text", fill(s.text(), i < a.picks().size() ? a.picks().get(i) : "", need));
				j.addProperty("need", need);
				boolean now = i == a.step();
				j.addProperty("have", i < a.step() ? need : now ? Math.min(need, have(p, s, at)) : 0);
				j.addProperty("state", i < a.step() ? "done" : now ? "now" : "ahead");
				j.addProperty("type", s.type());
				if (now && "choice".equals(s.type())) {
					JsonArray choices = new JsonArray();
					for (QuestDef.Choice c : s.choices()) {
						JsonObject cj = new JsonObject();
						cj.addProperty("label", c.label());
						Map<String, Integer> after = QuestRules.applyChoice(st.standing(), c.reputation());
						List<String> effects = new ArrayList<>();
						for (Map.Entry<String, Integer> e : after.entrySet()) {
							int change = e.getValue() - st.standing().getOrDefault(e.getKey(), 0);
							if (change != 0) {
								effects.add(QuestRules.GROUP_NAMES.getOrDefault(e.getKey(), e.getKey()) + " " + (change > 0 ? "+" : "") + change);
							}
						}
						effects.sort(Comparator.naturalOrder());
						cj.addProperty("effects", String.join(", ", effects));
						choices.add(cj);
					}
					j.add("choices", choices);
				}
			}
			steps.add(j);
		}
		o.add("steps", steps);
		if (a != null) {
			o.addProperty("tracked", id.equals(st.tracked()));
		}
		return o;
	}

	/** The Quests tab's section of the hub snapshot. */
	public static JsonObject snapshot(ServerPlayer p) {
		QuestState st = get(p);
		JsonObject root = new JsonObject();
		JsonArray active = new JsonArray();
		JsonArray available = new JsonArray();
		JsonArray done = new JsonArray();
		List<Map.Entry<Identifier, QuestDef>> all = new ArrayList<>(VestigesData.QUESTS.all().entrySet());
		all.sort(Comparator.<Map.Entry<Identifier, QuestDef>, String>comparing(e -> e.getValue().chain()).thenComparingInt(e -> e.getValue().order()));
		Map<String, String> offers = OFFERS.getOrDefault(p.getUUID(), Map.of());
		for (Map.Entry<Identifier, QuestDef> e : all) {
			String id = e.getKey().getPath();
			QuestDef d = e.getValue();
			QuestState.Active a = st.active().get(id);
			if (a != null) {
				active.add(questJson(p, id, d, a));
			} else if (tabAvailable(st, id, d) || offers.containsKey(id)) {
				available.add(questJson(p, id, d, null));
			}
			if (st.done(id)) {
				done.add(questJson(p, id, d, null));
			}
		}
		root.add("active", active);
		root.add("available", available);
		root.add("done", done);
		JsonArray titles = new JsonArray();
		st.titles().stream().sorted().forEach(titles::add);
		root.add("titles", titles);
		root.addProperty("title", st.title());
		JsonObject standing = new JsonObject();
		for (String group : QuestRules.GROUP_NAMES.keySet().stream().sorted().toList()) {
			int v = st.standing().getOrDefault(group, 0);
			JsonObject g = new JsonObject();
			g.addProperty("name", QuestRules.GROUP_NAMES.get(group));
			g.addProperty("value", v);
			g.addProperty("band", QuestRules.band(v));
			standing.add(group, g);
		}
		root.add("standing", standing);
		return root;
	}

	/** Tells the client what the tracker shows, when it changed. */
	public static void sendTracker(ServerPlayer p) {
		QuestState st = get(p);
		QuestDef d = st.tracked().isEmpty() ? null : def(st.tracked());
		QuestState.Active a = d == null ? null : st.active().get(st.tracked());
		JsonObject o = new JsonObject();
		if (a != null && a.step() < d.steps().size()) {
			QuestDef.Step s = d.steps().get(a.step());
			int need = "choice".equals(s.type()) ? 1 : goal(s, a);
			o.addProperty("name", d.name());
			o.addProperty("text", fill(s.text(), pick(s, a), need));
			o.addProperty("have", Math.min(need, "choice".equals(s.type()) ? 0 : have(p, s, a)));
			o.addProperty("need", need);
		}
		String json = o.toString();
		if (!json.equals(LAST_TRACKER.get(p.getUUID()))) {
			LAST_TRACKER.put(p.getUUID(), json);
			LevelService.notice(p, "quest_track", o);
		}
	}

	/** The title worn, shown beside the player's name on the server. */
	public static String title(ServerPlayer p) {
		return get(p).title();
	}

	/** What isn't saved goes when the player leaves. */
	public static void forget(Player p) {
		OFFERS.remove(p.getUUID());
		LAST_TRACKER.remove(p.getUUID());
		BREAKING_PLACED.remove(p.getUUID());
	}
}
