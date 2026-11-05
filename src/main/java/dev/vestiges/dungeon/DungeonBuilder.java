package dev.vestiges.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the dungeon world's rooms with plain code. Slice 1 has one room, the start room: an empty 32 x 32 hall with an exit frame against its
 * west wall. Later slices add the other rooms and the theme palettes through the same helpers.
 */
public final class DungeonBuilder {
	public static final int START_SIZE = 32;
	public static final int START_HEIGHT = 8;
	private static final int FLAGS = Block.UPDATE_CLIENTS;

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

	/**
	 * A closed hall: a floor of {@code width} x {@code depth} blocks starting at (x0, FLOOR_Y, z0), air above it for {@code height} blocks, and
	 * walls and ceiling around it.
	 */
	public static void hall(ServerLevel level, int x0, int z0, int width, int depth, int height, BlockState floor, BlockState wall, BlockState ceiling) {
		int y = DungeonPlots.FLOOR_Y;
		fill(level, x0 - 1, y - 1, z0 - 1, x0 + width, y + height + 1, z0 + depth, wall);
		fill(level, x0, y, z0, x0 + width - 1, y, z0 + depth - 1, floor);
		fill(level, x0, y + 1, z0, x0 + width - 1, y + height, z0 + depth - 1, Blocks.AIR.defaultBlockState());
		fill(level, x0, y + height + 1, z0, x0 + width - 1, y + height + 1, z0 + depth - 1, ceiling);
	}

	// ---- the start room ----------------------------------------------------------------------------------------------

	public static int startX(int plot) {
		return DungeonPlots.originX(plot) + 1;
	}

	public static int startZ(int plot) {
		return DungeonPlots.originZ(plot) - START_SIZE / 2;
	}

	/** Where players stand when they arrive. */
	public static Vec3 startSpawn(int plot) {
		return new Vec3(startX(plot) + 8.5, DungeonPlots.FLOOR_Y + 1, DungeonPlots.originZ(plot) + 0.5);
	}

	/** The exit frame's inside: stepping into it leaves the dungeon. */
	public static AABB exitZone(int plot) {
		int x = startX(plot);
		int z = DungeonPlots.originZ(plot);
		return new AABB(x, DungeonPlots.FLOOR_Y + 1, z - 1, x + 1, DungeonPlots.FLOOR_Y + 4, z + 1);
	}

	/** Builds (or rebuilds) the start room of a plot. */
	public static void buildStart(ServerLevel level, int plot) {
		int x0 = startX(plot);
		int z0 = startZ(plot);
		BlockState wall = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		hall(level, x0, z0, START_SIZE, START_SIZE, START_HEIGHT, Blocks.MOSS_BLOCK.defaultBlockState(), wall, wall);
		int y = DungeonPlots.FLOOR_Y;
		int mid = z0 + START_SIZE / 2;
		for (int x = x0 + 4; x < x0 + START_SIZE; x += 8) {
			for (int z = z0 + 4; z < z0 + START_SIZE; z += 8) {
				level.setBlock(new BlockPos(x, y + START_HEIGHT + 1, z), Blocks.SHROOMLIGHT.defaultBlockState(), FLAGS);
			}
		}
		// The exit frame: it follows the same rules as the entrance, standing against the west wall.
		BlockState tuff = Blocks.CHISELED_TUFF.defaultBlockState();
		BlockState copper = Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
		fill(level, x0, y, mid - 1, x0, y, mid, copper);
		fill(level, x0, y + 4, mid - 1, x0, y + 4, mid, copper);
		fill(level, x0, y + 1, mid - 2, x0, y + 3, mid - 2, tuff);
		fill(level, x0, y + 1, mid + 1, x0, y + 3, mid + 1, tuff);
	}

	/** Empties the start room again and removes what was left lying around. */
	public static void clearStart(ServerLevel level, int plot) {
		int x0 = startX(plot);
		int z0 = startZ(plot);
		int y = DungeonPlots.FLOOR_Y;
		fill(level, x0 - 1, y - 1, z0 - 1, x0 + START_SIZE, y + START_HEIGHT + 1, z0 + START_SIZE, Blocks.AIR.defaultBlockState());
		AABB box = new AABB(x0 - 1, y - 1, z0 - 1, x0 + START_SIZE + 1, y + START_HEIGHT + 2, z0 + START_SIZE + 1);
		for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
			e.discard();
		}
	}
}
