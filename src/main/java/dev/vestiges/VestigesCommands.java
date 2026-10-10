package dev.vestiges;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
import net.minecraft.network.chat.Component;

/**
 * /vestiges info | data | config list | config system &lt;id&gt; &lt;true|false&gt; | config reload. Operator only.
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
