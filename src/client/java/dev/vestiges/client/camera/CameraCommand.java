package dev.vestiges.client.camera;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

/** {@code /vcam}: sets up the test zone for the dungeon camera. */
public final class CameraCommand {
	private CameraCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("vcam")
			.then(ClientCommands.literal("here")
				.executes(c -> here(c.getSource(), 20))
				.then(ClientCommands.argument("size", IntegerArgumentType.integer(3, 100))
					.executes(c -> here(c.getSource(), IntegerArgumentType.getInteger(c, "size")))))
			.then(ClientCommands.literal("zone")
				.then(ClientCommands.argument("x1", IntegerArgumentType.integer())
					.then(ClientCommands.argument("y1", IntegerArgumentType.integer())
						.then(ClientCommands.argument("z1", IntegerArgumentType.integer())
							.then(ClientCommands.argument("x2", IntegerArgumentType.integer())
								.then(ClientCommands.argument("y2", IntegerArgumentType.integer())
									.then(ClientCommands.argument("z2", IntegerArgumentType.integer())
										.executes(c -> {
											IsoCamera.setZone(IntegerArgumentType.getInteger(c, "x1"), IntegerArgumentType.getInteger(c, "y1"),
												IntegerArgumentType.getInteger(c, "z1"), IntegerArgumentType.getInteger(c, "x2"),
												IntegerArgumentType.getInteger(c, "y2"), IntegerArgumentType.getInteger(c, "z2"));
											return say(c.getSource(), describe());
										}))))))))
			.then(ClientCommands.literal("clear").executes(c -> {
				IsoCamera.clearZone();
				return say(c.getSource(), "Camera zone removed.");
			}))
			.then(ClientCommands.literal("toggle").executes(c -> {
				IsoCamera.setForced(!IsoCamera.forced());
				return say(c.getSource(), IsoCamera.forced() ? "Dungeon view forced on everywhere." : "Dungeon view back to zone only.");
			}))
			.then(ClientCommands.literal("info").executes(c -> say(c.getSource(), describe())))));
	}

	/** A square zone of {@code size} blocks around the player, from 3 blocks below to 40 above. */
	private static int here(FabricClientCommandSource source, int size) {
		BlockPos at = source.getPlayer().blockPosition();
		int half = size / 2;
		IsoCamera.setZone(at.getX() - half, at.getY() - 3, at.getZ() - half, at.getX() + half, at.getY() + 40, at.getZ() + half);
		return say(source, describe());
	}

	private static String describe() {
		AABB z = IsoCamera.zone();
		if (z == null) {
			return "No camera zone set.";
		}
		return String.format("Camera zone: %d %d %d to %d %d %d. Walk in to switch the view.", (int) z.minX, (int) z.minY, (int) z.minZ,
			(int) z.maxX - 1, (int) z.maxY - 1, (int) z.maxZ - 1);
	}

	private static int say(FabricClientCommandSource source, String text) {
		source.sendFeedback(Component.literal(text));
		return 1;
	}
}
