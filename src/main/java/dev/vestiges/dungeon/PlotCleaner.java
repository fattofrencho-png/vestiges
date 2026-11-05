package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Empties the plots of finished runs, one chunk every other tick so the server never stalls. A plot goes back to the free list only when it is
 * empty. What is left to clear is saved with the runs, so a restart picks the work up again.
 */
final class PlotCleaner {
	private static final ArrayDeque<int[]> QUEUE = new ArrayDeque<>();
	private static final Map<Integer, Integer> PENDING = new HashMap<>();
	private static boolean resumed;

	private PlotCleaner() {
	}

	static void reset() {
		QUEUE.clear();
		PENDING.clear();
		resumed = false;
	}

	static void enqueue(DungeonRuns.Dirty dirty) {
		if (PENDING.containsKey(dirty.plot())) {
			return;
		}
		var chunks = DungeonPlots.stripChunks(dirty.plot(), dirty.fromX(), dirty.toX());
		PENDING.put(dirty.plot(), chunks.size());
		for (int[] c : chunks) {
			QUEUE.add(new int[] {dirty.plot(), c[0], c[1]});
		}
	}

	static void tick(MinecraftServer server) {
		if (!resumed) {
			resumed = true;
			DungeonService.runs(server).clearing().forEach(PlotCleaner::enqueue);
		}
		if (QUEUE.isEmpty() || server.getTickCount() % 2 != 0) {
			return;
		}
		ServerLevel level = DungeonService.dungeonLevel(server);
		if (level == null) {
			return;
		}
		int[] c = QUEUE.poll();
		DungeonBuilder.clearChunk(level, c[1], c[2]);
		int left = PENDING.merge(c[0], -1, Integer::sum);
		if (left <= 0) {
			PENDING.remove(c[0]);
			DungeonService.save(server, DungeonService.runs(server).cleared(c[0]));
			Vestiges.LOGGER.info("Vestiges: dungeon plot {} is cleared", c[0]);
		}
	}
}
