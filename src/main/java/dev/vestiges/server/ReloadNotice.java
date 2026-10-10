package dev.vestiges.server;

import dev.vestiges.data.DataType;
import dev.vestiges.data.VestigesData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** After every /reload, tells operators in chat what Vestiges loaded (or that it failed), so a reload is never silent. */
public final class ReloadNotice {
	private ReloadNotice() {
	}

	public static void register() {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			Component msg = success ? Component.literal(summary()).withStyle(ChatFormatting.GREEN)
				: Component.literal("Vestiges: data reload failed, the previous data is kept. Check the log.").withStyle(ChatFormatting.RED);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (Permissions.isOperator(player)) {
					player.sendSystemMessage(msg);
				}
			}
		});
	}

	public static String summary() {
		StringBuilder sb = new StringBuilder("Vestiges data reloaded:");
		for (DataType<?> type : VestigesData.ALL) {
			sb.append(' ').append(type.all().size()).append(' ').append(type.name());
		}
		return sb.toString();
	}
}
