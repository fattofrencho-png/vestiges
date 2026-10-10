package dev.vestiges.server;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** Operator checks in one place: admin tools and config changes need operator permission (cheats on, in single player). */
public final class Permissions {
	private Permissions() {
	}

	public static boolean isOperator(ServerPlayer player) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
	}
}
