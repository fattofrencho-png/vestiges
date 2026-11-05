package dev.vestiges.gametest;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small helpers shared by the automatic suites. */
final class T {
	private T() {
	}

	/** Which switches are on, so a suite that flips them (turning one off also turns off what needs it) can put them back. */
	static Map<VestigesSystem, Boolean> switches() {
		Map<VestigesSystem, Boolean> m = new LinkedHashMap<>();
		for (VestigesSystem s : VestigesSystem.values()) {
			m.put(s, ConfigStore.server().enabled(s));
		}
		return m;
	}

	static void restore(TestServerContext server, Map<VestigesSystem, Boolean> wanted) {
		for (boolean on : new boolean[] {true, false}) {
			for (Map.Entry<VestigesSystem, Boolean> e : wanted.entrySet()) {
				VestigesSystem s = e.getKey();
				if (e.getValue() == on && !s.core() && !s.creationOnly() && ConfigStore.server().enabled(s) != on) {
					server.runCommand("vestiges config system " + s.id + " " + on);
				}
			}
		}
	}

	static ServerPlayer player(TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0));
	}

	/** The y a player stands at on the overworld surface of this column (loads the chunk). */
	static int ground(TestServerContext server, int x, int z) {
		return server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
	}

	/**
	 * Like {@link #goTo}, then lays an 11 x 11 stone floor one block under the player and clears 4 blocks of air above it, so a survival player
	 * stands safely on a flat platform wherever the terrain is (water, trees, slopes). Returns the position stood on (the player's feet).
	 */
	static net.minecraft.core.BlockPos pad(ClientGameTestContext ctx, TestServerContext server, int x, int z) {
		int y = goTo(ctx, server, x, z, 20);
		server.runCommand("execute in minecraft:overworld run fill " + (x - 5) + " " + (y + 1) + " " + (z - 5) + " " + (x + 5) + " " + (y + 4) + " " + (z + 5)
			+ " minecraft:air");
		server.runCommand("execute in minecraft:overworld run fill " + (x - 5) + " " + (y - 1) + " " + (z - 5) + " " + (x + 5) + " " + (y - 1) + " " + (z + 5)
			+ " minecraft:stone");
		server.runCommand("execute in minecraft:overworld run fill " + (x - 5) + " " + y + " " + (z - 5) + " " + (x + 5) + " " + y + " " + (z + 5)
			+ " minecraft:air");
		server.runCommand("execute in minecraft:overworld run tp @p " + x + " " + y + " " + z);
		ctx.waitTicks(10);
		return new net.minecraft.core.BlockPos(x, y, z);
	}

	/** Loads the column (no falling through the world), teleports onto the ground, and waits for the chunks around to arrive. Returns the y stood on. */
	static int goTo(ClientGameTestContext ctx, TestServerContext server, int x, int z, int waitTicks) {
		server.runCommand("execute in minecraft:overworld run forceload add " + x + " " + z);
		server.waitFor(s -> s.overworld().getChunkSource().getChunkNow(x >> 4, z >> 4) != null, 600);
		int y = ground(server, x, z);
		server.runCommand("execute in minecraft:overworld run tp @p " + x + " " + y + " " + z);
		ctx.waitTicks(Math.max(waitTicks, 20));
		server.runCommand("execute in minecraft:overworld run forceload remove " + x + " " + z);
		return y;
	}
}
