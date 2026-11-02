package dev.vestiges;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.DataType;
import dev.vestiges.data.VestigesData;
import dev.vestiges.server.HubNet;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import dev.vestiges.waystone.WaystoneItems;
import java.util.Collection;
import net.minecraft.network.chat.Component;

/**
 * /vestiges info | data | give waystone [targets] [count] | level set &lt;targets&gt; &lt;level&gt; [paragon] | xp add &lt;targets&gt; &lt;amount&gt; | config list | config system &lt;id&gt; &lt;true|false&gt; | config reload. Operator only.
 * Player, world and debug tools (level, feats, villagers, dungeon tiers, spawn elite, roll loot...) join this tree with their systems.
 */
public final class VestigesCommands {
	private VestigesCommands() {
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> questId() {
		return Commands.argument("quest", StringArgumentType.word())
			.suggests((ctx, b) -> SharedSuggestionProvider.suggest(dev.vestiges.data.VestigesData.QUESTS.all().keySet().stream().map(k -> k.getPath()), b));
	}

	private static int questList(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		dev.vestiges.quest.QuestState st = dev.vestiges.quest.QuestService.get(src.getPlayerOrException());
		src.sendSuccess(() -> Component.literal(dev.vestiges.data.VestigesData.QUESTS.all().size() + " quests, running: " + st.active().keySet() + ", finished: " + st.finished().keySet()), false);
		return 1;
	}

	private static int questOp(CommandSourceStack src, String op, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.level.ServerPlayer p = src.getPlayerOrException();
		boolean ok = switch (op) {
			case "start" -> dev.vestiges.quest.QuestService.adminStart(p, id);
			case "complete" -> dev.vestiges.quest.QuestService.adminComplete(p, id);
			case "reset" -> dev.vestiges.quest.QuestService.adminReset(p, id);
			default -> p.getInventory().add(dev.vestiges.quest.QuestService.note(id));
		};
		if (!ok) {
			src.sendFailure(Component.literal("Vestiges: could not " + op + " quest " + id));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("Vestiges: quest " + id + ": " + op + " done"), false);
		return 1;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("vestiges")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("data").executes(ctx -> data(ctx.getSource())))
			.then(Commands.literal("give")
				.then(Commands.literal("waystone")
					.executes(ctx -> giveWaystone(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), 1))
					.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> giveWaystone(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
							.executes(ctx -> giveWaystone(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("marker")
					.then(Commands.argument("type", StringArgumentType.word())
						.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
							java.util.Arrays.stream(dev.vestiges.settlement.MarkerType.values()).map(t -> t.id), b))
						.executes(ctx -> giveMarker(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "type")))
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(ctx -> giveMarker(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "type")))))))
			.then(Commands.literal("feat")
				.then(Commands.literal("grant")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("feat", StringArgumentType.word())
							.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								dev.vestiges.data.VestigesData.FEATS.all().keySet().stream().map(k -> k.getPath()), b))
							.executes(ctx -> featGrant(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "feat"), true)))))
				.then(Commands.literal("revoke")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("feat", StringArgumentType.word())
							.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								dev.vestiges.data.VestigesData.FEATS.all().keySet().stream().map(k -> k.getPath()), b))
							.executes(ctx -> featGrant(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "feat"), false))))))
			.then(Commands.literal("quest")
				.then(Commands.literal("list").executes(ctx -> questList(ctx.getSource())))
				.then(Commands.literal("start").then(questId().executes(ctx -> questOp(ctx.getSource(), "start", StringArgumentType.getString(ctx, "quest")))))
				.then(Commands.literal("complete").then(questId().executes(ctx -> questOp(ctx.getSource(), "complete", StringArgumentType.getString(ctx, "quest")))))
				.then(Commands.literal("reset").then(questId().executes(ctx -> questOp(ctx.getSource(), "reset", StringArgumentType.getString(ctx, "quest")))))
				.then(Commands.literal("note").then(questId().executes(ctx -> questOp(ctx.getSource(), "note", StringArgumentType.getString(ctx, "quest"))))))
			.then(Commands.literal("reputation")
				.then(Commands.argument("group", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(dev.vestiges.quest.QuestRules.GROUP_NAMES.keySet(), b))
					.then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
						.executes(ctx -> {
							dev.vestiges.quest.QuestService.adminReputation(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "group"), IntegerArgumentType.getInteger(ctx, "value"));
							ctx.getSource().sendSuccess(() -> Component.literal("Vestiges: standing set"), false);
							return 1;
						}))))
			.then(Commands.literal("settlement")
				.then(Commands.literal("info").executes(ctx -> settlementInfo(ctx.getSource())))
				.then(Commands.literal("renown").executes(ctx -> settlementRenown(ctx.getSource())))
				.then(Commands.literal("finish").executes(ctx -> settlementFinish(ctx.getSource()))))
			.then(Commands.literal("level")
				.then(Commands.literal("set")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
							.executes(ctx -> setLevel(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "level"), 0))
							.then(Commands.argument("paragon", IntegerArgumentType.integer(0, 100000))
								.executes(ctx -> setLevel(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "level"),
									IntegerArgumentType.getInteger(ctx, "paragon"))))))))
			.then(Commands.literal("xp")
				.then(Commands.literal("add")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("amount", IntegerArgumentType.integer(1))
							.executes(ctx -> addXp(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "amount")))))))
			.then(Commands.literal("tree")
				.then(Commands.literal("reset")
					.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> resetTree(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
				.then(Commands.literal("counter")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("key", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(COUNTERS, builder))
							.then(Commands.argument("value", IntegerArgumentType.integer(0))
								.executes(ctx -> setCounter(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
									StringArgumentType.getString(ctx, "key"), IntegerArgumentType.getInteger(ctx, "value"))))))))
			.then(Commands.literal("mob")
				.then(Commands.literal("info").executes(ctx -> mobInfo(ctx.getSource())))
				.then(Commands.literal("rank")
					.then(Commands.argument("rank", StringArgumentType.word())
						.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(MOB_RANKS, b))
						.executes(ctx -> mobRank(ctx.getSource(), StringArgumentType.getString(ctx, "rank"), 0))
						.then(Commands.argument("level", IntegerArgumentType.integer(1, 200))
							.executes(ctx -> mobRank(ctx.getSource(), StringArgumentType.getString(ctx, "rank"), IntegerArgumentType.getInteger(ctx, "level")))))))
			.then(Commands.literal("codex")
				.then(Commands.literal("info")
					.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> codexInfo(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
				.then(Commands.literal("set")
					.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("entry", StringArgumentType.string())
							.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								dev.vestiges.codex.CodexIndex.get().entries.keySet().stream().map(k -> "\"" + k + "\""), b))
							.then(Commands.argument("stage", IntegerArgumentType.integer(0, 4))
								.executes(ctx -> codexSet(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
									StringArgumentType.getString(ctx, "entry"), IntegerArgumentType.getInteger(ctx, "stage")))))))
				.then(Commands.literal("reset")
					.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> codexReset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))))
			.then(Commands.literal("loot")
				.then(Commands.literal("roll")
					.then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> lootRoll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level"), null))
						.then(Commands.argument("rarity", StringArgumentType.word())
							.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(RARITIES, b))
							.executes(ctx -> lootRoll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level"), StringArgumentType.getString(ctx, "rarity"))))))
				.then(Commands.literal("chest")
					.then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> lootChest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
				.then(Commands.literal("test")
					.then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> lootTest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level"))))))
			.then(dev.vestiges.world.WorldCommands.tree())
			.then(Commands.literal("config")
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
				.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
				.then(Commands.literal("system")
					.then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(VestigesSystem.values()).map(s -> s.id), builder))
						.then(Commands.argument("on", BoolArgumentType.bool())
							.executes(ctx -> setSystem(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "on"))))))));
	}

	private static int giveMarker(CommandSourceStack src, Collection<ServerPlayer> targets, String type) {
		dev.vestiges.settlement.MarkerType t = dev.vestiges.settlement.MarkerType.byId(type);
		if (t == null) {
			src.sendFailure(Component.literal("Marker types: bell, banner16, banner48, ward6, ward12, ward30, gold_block"));
			return 0;
		}
		for (ServerPlayer p : targets) {
			net.minecraft.world.item.ItemStack stack = dev.vestiges.settlement.MarkerItems.create(t);
			if (!p.getInventory().add(stack)) {
				p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
			}
		}
		src.sendSuccess(() -> Component.literal("Gave a " + t.label + " to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int featGrant(CommandSourceStack src, Collection<ServerPlayer> targets, String feat, boolean on) {
		for (ServerPlayer p : targets) {
			if (!dev.vestiges.feats.FeatService.grant(p, feat, on)) {
				src.sendFailure(Component.literal("Unknown feat: " + feat));
				return 0;
			}
		}
		src.sendSuccess(() -> Component.literal((on ? "Granted " : "Revoked ") + feat + " for " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	/** The settlement you stand in (or the nearest of yours): its renown breakdown. */
	private static dev.vestiges.settlement.SettlementData.Settlement settlementHere(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		java.util.List<dev.vestiges.settlement.SettlementData.Settlement> all = dev.vestiges.settlement.SettlementService.world(src.getServer()).settlements();
		dev.vestiges.settlement.SettlementData.Settlement best = null;
		double bestD = Double.MAX_VALUE;
		for (dev.vestiges.settlement.SettlementData.Settlement s : all) {
			if (!s.hasBell() || p == null || !s.dimension().equals(dev.vestiges.settlement.SettlementService.dim(p.level()))) {
				continue;
			}
			double d = s.bellPos().distSqr(p.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}

	private static int settlementInfo(CommandSourceStack src) {
		dev.vestiges.settlement.SettlementData.Settlement s = settlementHere(src);
		if (s == null) {
			src.sendFailure(Component.literal("No settlement in this dimension"));
			return 0;
		}
		dev.vestiges.settlement.SettlementData.Stats st = s.stats();
		String msg = s.displayName() + " (" + s.state() + "): " + dev.vestiges.settlement.Renown.name(st.level()) + ", " + st.total() + " renown = size "
			+ st.size() + " + variety " + st.variety() + " + homes " + st.homes() + " + people " + st.people() + " + deeds " + st.deeds() + "; radius " + s.radius()
			+ "; members " + s.members().size() + (st.belowTicks() > 0 ? "; below its level for " + st.belowTicks() / 1200 + " min" : "");
		src.sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	/** Testing: finishes setting up your settlements and Ward Lanterns (they complete on the next second). */
	private static int settlementFinish(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		dev.vestiges.settlement.SettlementData.World w = dev.vestiges.settlement.SettlementService.world(src.getServer());
		for (dev.vestiges.settlement.SettlementData.Settlement s : w.settlements()) {
			if (s.owner().equals(p.getUUID()) && dev.vestiges.settlement.SettlementData.SETUP.equals(s.state())) {
				w = w.put(s.withState(dev.vestiges.settlement.SettlementData.SETUP, 20));
			}
		}
		for (dev.vestiges.settlement.SettlementData.Marker m : w.markers()) {
			if (m.owner().equals(p.getUUID()) && !m.active()) {
				w = w.putMarker(m.withSetupLeft(20));
			}
		}
		dev.vestiges.settlement.SettlementService.save(src.getServer(), w);
		src.sendSuccess(() -> Component.literal("Setting up finishes now (stay within 128 blocks)"), false);
		return 1;
	}

	/** Counts renown now instead of waiting for the 10-minute count (testing). */
	private static int settlementRenown(CommandSourceStack src) {
		dev.vestiges.settlement.SettlementData.Settlement s = settlementHere(src);
		if (s == null) {
			src.sendFailure(Component.literal("No settlement in this dimension"));
			return 0;
		}
		dev.vestiges.settlement.SettlementService.renown(src.getServer(), s.id());
		return settlementInfo(src);
	}

	private static int codexInfo(CommandSourceStack src, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			dev.vestiges.codex.CodexProgress prog = dev.vestiges.codex.CodexService.get(p);
			java.util.Map<String, int[]> bySection = new java.util.TreeMap<>();
			dev.vestiges.codex.CodexIndex.get().entries.forEach((id, e) -> {
				int[] c = bySection.computeIfAbsent(e.section(), k -> new int[3]);
				int stage = prog.stage(id);
				c[0]++;
				if (stage > 0) {
					c[1]++;
				}
				if (stage > 0 && stage >= dev.vestiges.codex.CodexRules.maxStage(e)) {
					c[2]++;
				}
			});
			StringBuilder sb = new StringBuilder(p.getName().getString() + ": " + prog.discovered() + " found, "
				+ dev.vestiges.codex.CodexService.bonusPoints(p) + " bonus Craft points");
			bySection.forEach((k, c) -> sb.append(" \u00B7 ").append(k).append(' ').append(c[1]).append('/').append(c[0]).append(" (").append(c[2]).append(" mastered)"));
			src.sendSuccess(() -> Component.literal(sb.toString()), false);
		}
		return targets.size();
	}

	private static int codexSet(CommandSourceStack src, Collection<ServerPlayer> targets, String entry, int stage) {
		for (ServerPlayer p : targets) {
			if ("all".equals(entry)) {
				// Testing helper: every entry at once (e.g. to see the bonus Craft points).
				for (String id : dev.vestiges.codex.CodexIndex.get().entries.keySet()) {
					dev.vestiges.codex.CodexService.setStageQuiet(p, id, stage);
				}
				dev.vestiges.codex.CodexService.refresh(p);
				continue;
			}
			if (!dev.vestiges.codex.CodexService.setStage(p, entry, stage)) {
				src.sendFailure(Component.literal("Unknown Codex entry: " + entry + " (for example blocks/stone, mobs/zombie)"));
				return 0;
			}
		}
		src.sendSuccess(() -> Component.literal("Set " + entry + " to stage " + stage + " for " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int codexReset(CommandSourceStack src, Collection<ServerPlayer> targets) {
		targets.forEach(dev.vestiges.codex.CodexService::reset);
		src.sendSuccess(() -> Component.literal("Reset the Codex of " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static final java.util.List<String> RARITIES = java.util.List.of("common", "uncommon", "superior", "rare", "epic", "legendary", "relic");
	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> SCALED =
		net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Vestiges.id("loot/scaled"));

	/** Gives a rolled item (gear 3 times in 4, else a book) for a loot level, optionally of a chosen rarity. */
	private static int lootRoll(CommandSourceStack src, int level, String rarity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		dev.vestiges.gear.Rarity.Tier tier = null;
		if (rarity != null) {
			if (!RARITIES.contains(rarity.toLowerCase(java.util.Locale.ROOT))) {
				src.sendFailure(Component.literal("Rarity must be one of " + String.join(", ", RARITIES)));
				return 0;
			}
			tier = dev.vestiges.gear.Rarity.Tier.valueOf(rarity.toUpperCase(java.util.Locale.ROOT));
		}
		net.minecraft.world.item.ItemStack stack = dev.vestiges.loot.LootGear.build(player.level(), player.getRandom(), level,
			player.getRandom().nextFloat() < 0.25f, null, tier);
		dev.vestiges.gear.Rarity.Result r = dev.vestiges.gear.Rarity.of(stack);
		Component name = stack.getHoverName();
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
		}
		src.sendSuccess(() -> Component.literal("Rolled ").append(name).append(" (" + r.tier().label + ", " + r.score() + " points)"), false);
		return 1;
	}

	private static net.minecraft.world.level.storage.loot.LootParams chestParams(ServerPlayer player, net.minecraft.world.phys.Vec3 at) {
		return new net.minecraft.world.level.storage.loot.LootParams.Builder(player.level())
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, at)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, player)
			.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
	}

	/** Fills the container you look at with one band roll for a loot level (the structure's own items are not included). */
	private static int lootChest(CommandSourceStack src, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		net.minecraft.world.phys.HitResult hit = player.pick(6.0, 1.0f, false);
		if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bh)
			|| !(player.level().getBlockEntity(bh.getBlockPos()) instanceof net.minecraft.world.Container container)) {
			src.sendFailure(Component.literal("Look at a chest or barrel first"));
			return 0;
		}
		dev.vestiges.loot.LootLevels.force(level);
		try {
			container.clearContent();
			player.level().getServer().reloadableRegistries().getLootTable(SCALED)
				.fill(container, chestParams(player, net.minecraft.world.phys.Vec3.atCenterOf(bh.getBlockPos())), player.getRandom().nextLong());
		} finally {
			dev.vestiges.loot.LootLevels.force(null);
		}
		src.sendSuccess(() -> Component.literal("Filled with a loot level " + level + " roll"), false);
		return 1;
	}

	/** Rolls 1,000 band rolls at a loot level and prints the averages, to check the balance in game. */
	private static int lootTest(CommandSourceStack src, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		int n = 1000;
		int stacks = 0;
		int gear = 0;
		int books = 0;
		int emeralds = 0;
		int diamonds = 0;
		int[] tiers = new int[dev.vestiges.gear.Rarity.Tier.values().length];
		net.minecraft.world.level.storage.loot.LootTable table = player.level().getServer().reloadableRegistries().getLootTable(SCALED);
		net.minecraft.world.level.storage.loot.LootParams params = chestParams(player, player.position());
		dev.vestiges.loot.LootLevels.force(level);
		try {
			for (int i = 0; i < n; i++) {
				for (net.minecraft.world.item.ItemStack s : table.getRandomItems(params, player.getRandom())) {
					stacks++;
					if (s.is(net.minecraft.world.item.Items.EMERALD)) {
						emeralds += s.getCount();
					} else if (s.is(net.minecraft.world.item.Items.EMERALD_BLOCK)) {
						emeralds += 9 * s.getCount();
					} else if (s.is(net.minecraft.world.item.Items.DIAMOND)) {
						diamonds += s.getCount();
					} else if (s.is(net.minecraft.world.item.Items.DIAMOND_BLOCK)) {
						diamonds += 9 * s.getCount();
					}
					boolean book = s.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
					if (book || dev.vestiges.gear.GearLadder.isGear(s)) {
						if (book) {
							books++;
						} else {
							gear++;
						}
						tiers[dev.vestiges.gear.Rarity.of(s).tier().ordinal()]++;
					}
				}
			}
		} finally {
			dev.vestiges.loot.LootLevels.force(null);
		}
		int found = Math.max(1, gear + books);
		StringBuilder odds = new StringBuilder();
		for (dev.vestiges.gear.Rarity.Tier t : dev.vestiges.gear.Rarity.Tier.values()) {
			odds.append(odds.length() > 0 ? " · " : "").append(t.label).append(' ')
				.append(String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * tiers[t.ordinal()] / found));
		}
		String line1 = String.format(java.util.Locale.ROOT, "Loot level %d (band %d), %d rolls: %.1f stacks · %.2f gear · %.2f books · %.2f emeralds · %.2f diamonds per chest",
			level, dev.vestiges.loot.LootRules.get().band(level), n, stacks / (double) n, gear / (double) n, books / (double) n, emeralds / (double) n, diamonds / (double) n);
		src.sendSuccess(() -> Component.literal(line1), false);
		src.sendSuccess(() -> Component.literal("Gear and books by rarity: " + odds), false);
		return 1;
	}

	private static final java.util.List<String> MOB_RANKS = java.util.List.of("normal", "champion", "elite", "warlord");

	/** The nearest scaling monster within 16 blocks of whoever runs the command. */
	private static net.minecraft.world.entity.Mob nearestMob(CommandSourceStack src) {
		net.minecraft.world.phys.Vec3 pos = src.getPosition();
		net.minecraft.world.entity.Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (net.minecraft.world.entity.Mob m : src.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
			new net.minecraft.world.phys.AABB(pos, pos).inflate(16), dev.vestiges.mobs.MobService::scales)) {
			double d = m.position().distanceToSqr(pos);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static int mobInfo(CommandSourceStack src) {
		net.minecraft.world.entity.Mob mob = nearestMob(src);
		dev.vestiges.mobs.MobData d = mob == null ? null : dev.vestiges.mobs.MobService.data(mob);
		if (d == null) {
			src.sendFailure(Component.literal("No levelled monster within 16 blocks"));
			return 0;
		}
		String msg = mob.getName().getString() + ": level " + d.level() + " · " + MOB_RANKS.get(d.rank()) + (d.powers().isEmpty() ? "" : " · " + String.join(", ", d.powers()))
			+ " · size " + Math.round(d.size() * 100) + "% · health " + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth())
			+ " · damage x" + String.format(java.util.Locale.ROOT, "%.2f", dev.vestiges.mobs.MobService.damageMultiplier(mob));
		src.sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	private static int mobRank(CommandSourceStack src, String rank, int level) {
		int r = MOB_RANKS.indexOf(rank.toLowerCase(java.util.Locale.ROOT));
		if (r < 0) {
			src.sendFailure(Component.literal("Rank must be normal, champion, elite or warlord"));
			return 0;
		}
		net.minecraft.world.entity.Mob mob = nearestMob(src);
		if (mob == null) {
			src.sendFailure(Component.literal("No monster within 16 blocks"));
			return 0;
		}
		dev.vestiges.mobs.MobService.setRank(mob, r, level);
		return mobInfo(src);
	}

	private static int info(CommandSourceStack src) {
		ServerConfig c = ConfigStore.server();
		long on = c.systems.values().stream().filter(b -> b).count();
		src.sendSuccess(() -> Component.literal("Vestiges " + HubNet.version() + " · " + on + " of " + c.systems.size() + " systems on · config in config/vestiges/"), false);
		return 1;
	}

	private static int data(CommandSourceStack src) {
		StringBuilder sb = new StringBuilder("Vestiges data:");
		for (DataType<?> type : VestigesData.ALL) {
			sb.append(' ').append(type.name()).append('=').append(type.all().size());
		}
		String msg = sb.toString();
		src.sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}

	/** Temporary source of waystones until the First Waystone feat (M5) hands them out. */
	private static int giveWaystone(CommandSourceStack src, Collection<ServerPlayer> targets, int count) {
		for (ServerPlayer p : targets) {
			if (!p.getInventory().add(WaystoneItems.create(count))) {
				net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), WaystoneItems.create(count));
				drop.setTarget(p.getUUID());
				p.level().addFreshEntity(drop);
			}
		}
		src.sendSuccess(() -> Component.literal("Gave " + count + " waystone" + (count == 1 ? "" : "s") + " to " + targets.size() + " player" + (targets.size() == 1 ? "" : "s")), true);
		return targets.size();
	}

	private static int setLevel(CommandSourceStack src, Collection<ServerPlayer> targets, int level, int paragon) {
		for (ServerPlayer p : targets) {
			dev.vestiges.level.PlayerLevel old = dev.vestiges.level.LevelService.get(p);
			dev.vestiges.level.LevelService.set(p, new dev.vestiges.level.PlayerLevel(level, 0, level >= 100 ? paragon : 0, old.firsts()));
			dev.vestiges.tree.TreeService.sendState(p, "");
		}
		src.sendSuccess(() -> Component.literal("Set level " + level + (paragon > 0 && level >= 100 ? " · Paragon " + paragon : "") + " for " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int addXp(CommandSourceStack src, Collection<ServerPlayer> targets, int amount) {
		for (ServerPlayer p : targets) {
			dev.vestiges.level.LevelService.addXp(p, amount, "command");
		}
		src.sendSuccess(() -> Component.literal("Gave " + amount + " XP to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static final java.util.List<String> COUNTERS = java.util.List.of("kills", "ores", "deepslate", "crops", "sword_kills", "logs",
		"crossbow_kills", "mace_kills", "ancient_debris", "shovel_blocks", "bow_kills", "spear_kills", "cluster_blocks", "coral_blocks");

	/** Admin: empty passive trees (no refund cost), for testing and until the Respec Tome exists. */
	private static int resetTree(CommandSourceStack src, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			dev.vestiges.tree.TreeService.reset(p);
		}
		src.sendSuccess(() -> Component.literal("Reset the passive trees of " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	/** Admin: sets an ascension checklist counter (kills, ores, deepslate...), for testing. */
	private static int setCounter(CommandSourceStack src, Collection<ServerPlayer> targets, String key, int value) {
		for (ServerPlayer p : targets) {
			dev.vestiges.tree.TreeService.setCounter(p, key, value);
		}
		src.sendSuccess(() -> Component.literal("Set " + key + " = " + value + " for " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int list(CommandSourceStack src) {
		ServerConfig c = ConfigStore.server();
		for (VestigesSystem s : VestigesSystem.values()) {
			boolean on = c.enabled(s);
			src.sendSuccess(() -> Component.literal((on ? "[on]  " : "[off] ") + s.id + " - " + s.label), false);
		}
		return 1;
	}

	private static int reload(CommandSourceStack src) {
		dev.vestiges.server.FeatureNet.change(src.getServer(), c -> ConfigStore.loadServer());
		src.sendSuccess(() -> Component.literal("Vestiges config reloaded"), true);
		return 1;
	}

	private static int setSystem(CommandSourceStack src, String id, boolean on) {
		VestigesSystem s = VestigesSystem.byId(id);
		if (s == null) {
			src.sendFailure(Component.literal("Unknown system: " + id));
			return 0;
		}
		if (s.core()) {
			src.sendFailure(Component.literal(s.label + " is the core of Vestiges and stays on"));
			return 0;
		}
		if (s.creationOnly()) {
			src.sendFailure(Component.literal(s.label + " can only be chosen when the world is created"));
			return 0;
		}
		dev.vestiges.server.FeatureNet.change(src.getServer(), c -> c.setWithNeeds(s, on));
		src.sendSuccess(() -> Component.literal("Vestiges: " + s.label + " is now " + (on ? "on" : "off")), true);
		return 1;
	}
}
