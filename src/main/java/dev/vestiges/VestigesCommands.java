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
							.executes(ctx -> giveWaystone(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "count")))))))
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
			.then(Commands.literal("config")
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
				.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
				.then(Commands.literal("system")
					.then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(VestigesSystem.values()).map(s -> s.id), builder))
						.then(Commands.argument("on", BoolArgumentType.bool())
							.executes(ctx -> setSystem(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "on"))))))));
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
		ConfigStore.loadServer();
		src.sendSuccess(() -> Component.literal("Vestiges config reloaded"), true);
		return 1;
	}

	private static int setSystem(CommandSourceStack src, String id, boolean on) {
		VestigesSystem s = VestigesSystem.byId(id);
		if (s == null) {
			src.sendFailure(Component.literal("Unknown system: " + id));
			return 0;
		}
		ConfigStore.server().set(s, on);
		ConfigStore.saveServer();
		src.sendSuccess(() -> Component.literal("Vestiges: " + s.label + " is now " + (on ? "on" : "off")), true);
		return 1;
	}
}
