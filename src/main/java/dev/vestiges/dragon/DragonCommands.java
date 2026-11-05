package dev.vestiges.dragon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Operator tools for dragons: {@code dragon info} (kill count, the next dragon and its gate) and {@code dragon count <n>} (sets the world's kill count;
 * the only way to change it besides killing a dragon, so players in survival cannot).
 */
public final class DragonCommands {
	private DragonCommands() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> dragon() {
		return Commands.literal("dragon")
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("count")
				.then(Commands.argument("kills", IntegerArgumentType.integer(0, 24))
					.executes(ctx -> count(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "kills")))));
	}

	private static int info(CommandSourceStack src) {
		MinecraftServer server = src.getServer();
		DragonWorld w = DragonService.world(server);
		int next = DragonRules.nextNumber(w.kills());
		DragonDef def = DragonService.def(next);
		String line = "Dragons: " + (DragonService.enabled() ? "on" : "off") + ", kills " + w.kills() + ", next is dragon " + next
			+ (def == null ? " (a plain respawn)" : " " + def.display() + ", needs level " + def.gate())
			+ (w.active() > 0 ? ", fighting dragon " + w.active() + " (" + w.hurt().size() + " credited)" : "");
		src.sendSuccess(() -> Component.literal(line), false);
		return w.kills();
	}

	private static int count(CommandSourceStack src, int kills) {
		DragonService.save(src.getServer(), DragonService.world(src.getServer()).withKills(kills));
		src.sendSuccess(() -> Component.literal("Dragon kill count set to " + kills + ". The next dragon is " + DragonRules.nextNumber(kills) + "."), true);
		return kills;
	}
}
