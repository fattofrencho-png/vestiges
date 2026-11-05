package dev.vestiges.dungeon;

import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The block-editing primitives the room builder and the plot cleaner share. */
public final class DungeonBuilder {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private DungeonBuilder() {
	}

	/** Fills a box of blocks (both corners included, any order). */
	public static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					level.setBlock(pos.set(x, y, z), state, FLAGS);
				}
			}
		}
	}

	/** Like {@link #fill}, but every block is picked at random from the list. */
	public static void fillRandom(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, List<BlockState> states, Random random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					level.setBlock(pos.set(x, y, z), states.get(random.nextInt(states.size())), FLAGS);
				}
			}
		}
	}

	public static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, FLAGS);
	}

	public static void air(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(level, x0, y0, z0, x1, y1, z1, AIR);
	}

	/** Empties one chunk column of the plot's height range and removes what lies around in it (players are never touched). */
	public static void clearChunk(ServerLevel level, int chunkX, int chunkZ) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		air(level, x0, DungeonPlots.FLOOR_Y - 8, z0, x0 + 15, DungeonPlots.FLOOR_Y + 24, z0 + 15);
		AABB box = new AABB(x0, DungeonPlots.FLOOR_Y - 8, z0, x0 + 16, DungeonPlots.FLOOR_Y + 25, z0 + 16);
		for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
			e.discard();
		}
	}

	/**
	 * The exit frame: a portal frame of the same blocks as the entrance, standing against a room's west or east wall. Stepping into it leaves the
	 * dungeon. The frame's plane is the column {@code planeX}; its inside is two blocks wide, centred on {@code centerZ}.
	 */
	public static void exitFrame(ServerLevel level, int planeX, int centerZ) {
		int y = DungeonPlots.FLOOR_Y;
		BlockState tuff = Blocks.CHISELED_TUFF.defaultBlockState();
		BlockState copper = Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
		fill(level, planeX, y, centerZ - 1, planeX, y, centerZ, copper);
		fill(level, planeX, y + 4, centerZ - 1, planeX, y + 4, centerZ, copper);
		fill(level, planeX, y + 1, centerZ - 2, planeX, y + 3, centerZ - 2, tuff);
		fill(level, planeX, y + 1, centerZ + 1, planeX, y + 3, centerZ + 1, tuff);
	}

	/** The inside of an exit frame at that column. */
	public static AABB exitZone(int planeX, int centerZ) {
		return new AABB(planeX, DungeonPlots.FLOOR_Y + 1, centerZ - 1, planeX + 1, DungeonPlots.FLOOR_Y + 4, centerZ + 1);
	}

	/** Where a run's exit frames are: against the start room's west wall and the reward room's east wall. */
	public static List<AABB> exitZones(DungeonRuns.Run run) {
		if (run.rooms().isEmpty()) {
			return List.of();
		}
		DungeonRuns.Room start = run.rooms().get(0);
		DungeonRuns.Room reward = run.rooms().get(run.rooms().size() - 1);
		return List.of(exitZone(start.x(), start.centerZ()), exitZone(reward.x() + reward.size() - 1, reward.centerZ()));
	}
}
