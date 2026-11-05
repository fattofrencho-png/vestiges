package dev.vestiges.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Operator tools for dungeons: {@code give key <theme> <tier> [targets]}, {@code give frame [targets]}, {@code dungeon info | end}. */
public final class DungeonCommands {
	private DungeonCommands() {
	}

	/** Joins the existing {@code give} node. */
	public static LiteralArgumentBuilder<CommandSourceStack> give() {
		return Commands.literal("give")
			.then(Commands.literal("key")
				.then(Commands.argument("theme", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(VestigesData.DUNGEON_THEMES.all().keySet().stream().map(k -> k.getPath()), b))
					.then(Commands.argument("tier", IntegerArgumentType.integer(1, 999))
						.executes(ctx -> giveKey(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "theme"),
							IntegerArgumentType.getInteger(ctx, "tier")))
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(ctx -> giveKey(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "theme"),
								IntegerArgumentType.getInteger(ctx, "tier")))))))
			.then(Commands.literal("frame")
				.executes(ctx -> giveFrame(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(ctx -> giveFrame(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> dungeon() {
		return Commands.literal("dungeon")
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("end").executes(ctx -> end(ctx.getSource())));
	}

	private static int giveKey(CommandSourceStack src, Collection<ServerPlayer> targets, String theme, int tier) {
		if (VestigesData.DUNGEON_THEMES.get(Vestiges.id(theme)) == null) {
			src.sendFailure(Component.literal("Vestiges: no dungeon theme called " + theme));
			return 0;
		}
		for (ServerPlayer p : targets) {
			DungeonService.give(p, DungeonKeys.create(theme, tier));
		}
		src.sendSuccess(() -> Component.literal("Gave a " + DungeonKeys.themeName(theme) + " key, tier " + tier + " to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int giveFrame(CommandSourceStack src, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			for (var s : DungeonKeys.frameKit()) {
				DungeonService.give(p, s);
			}
		}
		src.sendSuccess(() -> Component.literal("Gave the blocks of the smallest portal frame to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int info(CommandSourceStack src) {
		DungeonRuns all = DungeonService.runs(src.getServer());
		StringBuilder sb = new StringBuilder("Dungeon runs: ").append(all.runs().size()).append(" running, free plots ").append(all.freePlots())
			.append(", next new plot ").append(all.nextPlot());
		for (DungeonRuns.Run r : all.runs()) {
			sb.append("\n  run ").append(r.id()).append(": ").append(r.theme()).append(" tier ").append(r.tier()).append(", plot ").append(r.plot()).append(", players ");
			r.party().forEach(m -> sb.append(m.name()).append(m.out() ? " (out)" : " (" + m.lives() + " lives)").append(' '));
		}
		src.sendSuccess(() -> Component.literal(sb.toString()), false);
		return all.runs().size();
	}

	private static int end(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		DungeonRuns.Run run = DungeonService.runs(src.getServer()).byPlayer(p.getUUID());
		if (run == null) {
			src.sendFailure(Component.literal("Vestiges: you are not in a dungeon run"));
			return 0;
		}
		DungeonService.endRun(src.getServer(), run.id());
		src.sendSuccess(() -> Component.literal("Ended run " + run.id()), true);
		return 1;
	}
}
