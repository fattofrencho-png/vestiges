package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a run's rooms and corridors with plain code, from the theme's palette. A room is a closed square hall; its shape is a map of floor heights
 * (pits, raised bands) and pillars. It also works out the markers the run needs: where monsters may spawn, where the cache goes, the boss spot.
 * Doors are not cut here: the corridor builder opens them, and a gate (bars) closes a door while a room is being fought.
 */
public final class RoomBuilder {
	/** A cell of the floor map that a pillar fills. */
	public static final int BLOCKED = Integer.MIN_VALUE;
	/** A cell of the floor map with no floor at all: a drop into the void (the End's gaps). */
	public static final int VOID = Integer.MIN_VALUE + 1;
	private static final int DEPTH = 5;

	private RoomBuilder() {
	}

	/** The theme's blocks, resolved from their ids (an unknown id becomes stone bricks, so a typo in a pack can't break a run). */
	public record Palette(List<BlockState> floor, List<BlockState> wall, List<BlockState> ceiling, List<BlockState> pillar, List<BlockState> light,
						  List<BlockState> gate) {
		public static Palette of(DungeonTheme.Palette p) {
			return new Palette(resolve(p.floor()), resolve(p.wall()), resolve(p.ceiling()), resolve(p.pillar()), resolve(p.light()), resolve(p.gate()));
		}

		private static List<BlockState> resolve(List<String> ids) {
			List<BlockState> out = new ArrayList<>();
			for (String id : ids) {
				Identifier key = Identifier.tryParse(id);
				var block = key == null ? java.util.Optional.<net.minecraft.world.level.block.Block>empty() : BuiltInRegistries.BLOCK.getOptional(key);
				out.add(block.orElse(Blocks.STONE_BRICKS).defaultBlockState());
			}
			if (out.isEmpty()) {
				out.add(Blocks.STONE_BRICKS.defaultBlockState());
			}
			return List.copyOf(out);
		}

		BlockState pick(List<BlockState> list, Random random) {
			return list.get(random.nextInt(list.size()));
		}
	}

	/** The floor map of a shape: heights relative to the floor level, {@link #BLOCKED} for a pillar. */
	public static int[][] floorMap(String shape, int size) {
		int[][] g = new int[size][size];
		int c = size / 2;
		switch (shape) {
			case "arena" -> {
				int q = size / 4;
				pillar(g, q, q);
				pillar(g, size - q - 2, q);
				pillar(g, q, size - q - 2);
				pillar(g, size - q - 2, size - q - 2);
			}
			case "pillars" -> {
				for (int i = 1; i <= 3; i++) {
					for (int j = 1; j <= 3; j++) {
						pillar(g, size * i / 4 - 1, size * j / 4 - 1);
					}
				}
			}
			case "pit" -> {
				for (int dx = 0; dx < size; dx++) {
					for (int dz = 0; dz < size; dz++) {
						int d = Math.max(Math.abs(dx - c), Math.abs(dz - c));
						g[dx][dz] = d <= 3 ? -3 : d <= 5 ? -2 : d <= 7 ? -1 : 0;
					}
				}
			}
			case "split" -> {
				for (int dx = 0; dx < size; dx++) {
					for (int dz = 0; dz < size; dz++) {
						int edge = Math.min(dz, size - 1 - dz);
						g[dx][dz] = edge <= 7 ? 2 : edge <= 9 ? 1 : 0;
					}
				}
			}
			case "gaps" -> {
				// Two void channels across the room, each crossed by a bridge 3 blocks wide on the line between the doors. At most a quarter void.
				int w = Math.max(3, size / 8);
				for (int centre : new int[] {size / 3, size * 2 / 3}) {
					for (int dx = centre - w / 2; dx < centre - w / 2 + w; dx++) {
						for (int dz = 0; dz < size; dz++) {
							if (Math.abs(dz - c) > 1) {
								g[dx][dz] = VOID;
							}
						}
					}
				}
			}
			case "islands" -> {
				// A void square in the middle with a small island in its centre, joined to both sides by a bridge 3 blocks wide.
				int half = size / 5;
				for (int dx = c - half; dx <= c + half; dx++) {
					for (int dz = c - half; dz <= c + half; dz++) {
						boolean bridge = Math.abs(dz - c) <= 1;
						boolean island = Math.abs(dx - c) <= 2 && Math.abs(dz - c) <= 2;
						if (!bridge && !island) {
							g[dx][dz] = VOID;
						}
					}
				}
			}
			default -> {
			}
		}
		return g;
	}

	/** Free floor: not a pillar and not a drop into the void. */
	public static boolean solid(int cell) {
		return cell != BLOCKED && cell != VOID;
	}

	private static void pillar(int[][] g, int x, int z) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				g[x + dx][z + dz] = BLOCKED;
			}
		}
	}

	/** Builds one room's hall and returns it with its markers filled in. */
	public static DungeonRuns.Room build(ServerLevel level, DungeonRuns.Room room, DungeonTheme theme, Random random) {
		DungeonRoom def = VestigesData.DUNGEON_ROOMS.get(Vestiges.id(room.template()));
		if (def != null && !def.structure().isEmpty()) {
			// A room the author built: pasted from its structure file. A missing file falls back to the built layout.
			DungeonRuns.Room placed = RoomAssets.place(level, room, RoomAssets.load(level.getServer(), def.structure()), random);
			if (placed != null) {
				exitFrame(level, room);
				return placed;
			}
			Vestiges.LOGGER.warn("Vestiges: dungeon room {} names the structure {}, which is missing: using the built layout", room.template(), def.structure());
		}
		Palette pal = Palette.of(theme.palette());
		int x0 = room.x();
		int z0 = room.z();
		int size = room.size();
		int h = room.height();
		int y = DungeonPlots.FLOOR_Y;
		// The shell: a solid block with the room cut out of it.
		DungeonBuilder.fillRandom(level, x0 - 1, y - DEPTH, z0 - 1, x0 + size, y + h + 1, z0 + size, pal.wall(), random);
		DungeonBuilder.air(level, x0, y + 1, z0, x0 + size - 1, y + h, z0 + size - 1);
		DungeonBuilder.fillRandom(level, x0, y + h + 1, z0, x0 + size - 1, y + h + 1, z0 + size - 1, pal.ceiling(), random);
		int[][] g = floorMap(room.shape(), size);
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int top = g[dx][dz];
				int x = x0 + dx;
				int z = z0 + dz;
				if (top == VOID) {
					DungeonBuilder.air(level, x, y - DEPTH, z, x, y, z);
				} else if (top == BLOCKED) {
					DungeonBuilder.fillRandom(level, x, y, z, x, y + h, z, pal.pillar(), random);
				} else if (top >= 0) {
					DungeonBuilder.fillRandom(level, x, y, z, x, y + top, z, pal.floor(), random);
				} else {
					DungeonBuilder.air(level, x, y + top + 1, z, x, y, z);
					DungeonBuilder.set(level, x, y + top, z, pal.pick(pal.floor(), random));
				}
			}
		}
		// The ceiling: one lamp every 8 blocks.
		for (int dx = 4; dx < size; dx += 8) {
			for (int dz = 4; dz < size; dz += 8) {
				DungeonBuilder.set(level, x0 + dx, y + h + 1, z0 + dz, pal.pick(pal.light(), random));
			}
		}
		exitFrame(level, room);
		return room.withMarkers(spawnPoints(room, g, random), cachePoints(room, g), bossSpot(room, g));
	}

	/** Rooms with an exit frame: against the west wall for the start room, against the east wall for the reward room. */
	private static void exitFrame(ServerLevel level, DungeonRuns.Room room) {
		if (room.role().equals("start")) {
			DungeonBuilder.exitFrame(level, room.x(), room.centerZ());
		} else if (room.role().equals("reward")) {
			DungeonBuilder.exitFrame(level, room.x() + room.size() - 1, room.centerZ());
		}
	}

	/** Cells where monsters may stand: free floor, away from the walls and from the entrance. */
	static List<BlockPos> spawnPoints(DungeonRuns.Room room, int[][] g, Random random) {
		if (!room.fighting()) {
			return List.of();
		}
		List<BlockPos> all = new ArrayList<>();
		int size = room.size();
		for (int dx = 8; dx < size - 3; dx++) {
			for (int dz = 3; dz < size - 3; dz++) {
				if (solid(g[dx][dz]) && solid(g[dx - 1][dz]) && solid(g[dx + 1][dz]) && solid(g[dx][dz - 1]) && solid(g[dx][dz + 1])) {
					all.add(new BlockPos(room.x() + dx, DungeonPlots.FLOOR_Y + g[dx][dz] + 1, room.z() + dz));
				}
			}
		}
		Collections.shuffle(all, random);
		return List.copyOf(all.subList(0, Math.min(30, all.size())));
	}

	/** Where the cache goes: a back corner (the reward room gets two, beside its exit frame). */
	private static List<BlockPos> cachePoints(DungeonRuns.Room room, int[][] g) {
		int size = room.size();
		if (room.role().equals("reward")) {
			// The reward chest and a block cache, either side of the exit frame.
			return List.of(new BlockPos(room.x() + size - 3, DungeonPlots.FLOOR_Y + 1, room.centerZ() - 3),
				new BlockPos(room.x() + size - 3, DungeonPlots.FLOOR_Y + 1, room.centerZ() + 3));
		}
		if (!room.fighting()) {
			return List.of();
		}
		int dx = size - 3;
		int dz = !solid(g[dx][3]) ? size - 4 : 3;
		return List.of(new BlockPos(room.x() + dx, DungeonPlots.FLOOR_Y + Math.max(0, g[dx][dz]) + 1, room.z() + dz));
	}

	private static List<BlockPos> bossSpot(DungeonRuns.Room room, int[][] g) {
		if (!room.role().equals("lieutenant") && !room.role().equals("boss")) {
			return List.of();
		}
		int c = room.size() / 2;
		int top = !solid(g[c][c]) ? 0 : g[c][c];
		return List.of(new BlockPos(room.x() + c, DungeonPlots.FLOOR_Y + top + 1, room.z() + c));
	}

	/** Builds the corridor from room A's east door to room B's west door and cuts both doors. */
	public static void corridor(ServerLevel level, DungeonRuns.Room a, DungeonRuns.Room b, DungeonTheme theme, Random random) {
		Palette pal = Palette.of(theme.palette());
		int y = DungeonPlots.FLOOR_Y;
		int cz = a.centerZ();
		int xs = a.eastWallX();
		int xe = b.westWallX();
		DungeonBuilder.fillRandom(level, xs, y - 1, cz - 3, xe, y + 5, cz + 3, pal.wall(), random);
		DungeonBuilder.air(level, xs, y + 1, cz - 2, xe, y + 4, cz + 2);
		DungeonBuilder.fillRandom(level, xs, y, cz - 2, xe, y, cz + 2, pal.floor(), random);
		for (int x = xs + 3; x < xe - 1; x += 6) {
			DungeonBuilder.set(level, x, y + 5, cz, pal.pick(pal.light(), random));
		}
	}

	/** Closes (bars) or opens (air) a door: the 5 x 4 opening in a room's wall column. */
	public static void gate(ServerLevel level, int wallX, int centerZ, boolean closed, DungeonTheme theme, Random random) {
		int y = DungeonPlots.FLOOR_Y;
		if (closed) {
			Palette pal = Palette.of(theme.palette());
			DungeonBuilder.fillRandom(level, wallX, y + 1, centerZ - 2, wallX, y + 4, centerZ + 2, pal.gate(), random);
		} else {
			DungeonBuilder.air(level, wallX, y + 1, centerZ - 2, wallX, y + 4, centerZ + 2);
		}
	}
}
