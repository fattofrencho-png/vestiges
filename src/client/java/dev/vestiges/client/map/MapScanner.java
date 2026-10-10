package dev.vestiges.client.map;

import dev.vestiges.map.MapMath;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Turns chunks the client has seen into map pixels. Every chunk is mapped from above when it loads; chunks around the player are re-mapped
 * every two seconds (so new builds show up), and while underground or in the Nether the player's current 16-block slice is mapped too.
 */
public final class MapScanner {
	private static final int CHUNKS_PER_TICK = 6;
	private static final int NEARBY_RADIUS = 3;
	private static final int ROCK = 0xFF1E1E1E;
	private static final int VOID = 0xFF0C0C0C;
	private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();
	private static int ticks;

	private MapScanner() {
	}

	public static void register() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> enqueue(chunk.getPos()));
	}

	public static void reset() {
		QUEUE.clear();
		QUEUED.clear();
	}

	private static void enqueue(ChunkPos pos) {
		long key = (long) pos.x() << 32 | (pos.z() & 0xFFFFFFFFL);
		if (QUEUED.add(key)) {
			QUEUE.add(key);
		}
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || MapStore.worldDir() == null) {
			return;
		}
		Identifier dim = level.dimension().identifier();
		boolean ceiling = level.dimensionType().hasCeiling();
		ticks++;
		if (ticks % 40 == 0) {
			ChunkPos center = mc.player.chunkPosition();
			String layer = MapView.currentLayer(mc);
			for (int dx = -NEARBY_RADIUS; dx <= NEARBY_RADIUS; dx++) {
				for (int dz = -NEARBY_RADIUS; dz <= NEARBY_RADIUS; dz++) {
					LevelChunk c = loaded(level, center.x() + dx, center.z() + dz);
					if (c == null) {
						continue;
					}
					if (!ceiling) {
						scanSurface(level, c, dim);
					}
					if (!MapMath.isSurface(layer)) {
						scanSlice(level, c, dim, MapMath.sliceOf(layer));
					}
				}
			}
		}
		for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
			long key = QUEUE.poll();
			QUEUED.remove(key);
			LevelChunk c = loaded(level, (int) (key >> 32), (int) key);
			if (c == null) {
				continue;
			}
			if (ceiling) {
				scanSlice(level, c, dim, MapMath.slice(mc.player.getBlockY()));
			} else {
				scanSurface(level, c, dim);
			}
		}
	}

	private static LevelChunk loaded(ClientLevel level, int cx, int cz) {
		LevelChunk c = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		return c == null || c instanceof EmptyLevelChunk ? null : c;
	}

	private static void scanSurface(ClientLevel level, LevelChunk chunk, Identifier dim) {
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int minY = level.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] heights = new int[16];
		for (int lx = 0; lx < 16; lx++) {
			heights[lx] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, minX + lx, minZ - 1);
		}
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int wx = minX + lx;
				int wz = minZ + lz;
				int h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz) + 1;
				int y = h - 1;
				if (y < minY) {
					heights[lx] = h;
					continue;
				}
				pos.set(wx, y + 1, wz);
				BlockState above = chunk.getBlockState(pos);
				pos.set(wx, y, wz);
				BlockState state = chunk.getBlockState(pos);
				if (above.is(net.minecraft.world.level.block.Blocks.SNOW)) {
					state = above;
				}
				int color;
				if (state.getFluidState().is(FluidTags.WATER)) {
					color = water(level, chunk, pos, wx, y, wz, minY);
				} else {
					color = TerrainColors.color(level, state, pos);
				}
				color = MapMath.shade(color, MapMath.relief(h, heights[lx]));
				heights[lx] = h;
				MapStore.setPixel(dim, MapMath.SURFACE, wx, wz, color);
			}
		}
	}

	/** Water: tinted by biome, darker the deeper it is, with a hint of the floor in shallow water. */
	private static int water(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int wx, int y, int wz, int minY) {
		int waterColor = TerrainColors.color(level, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), pos.immutable());
		int depth = 0;
		BlockState floor = null;
		for (int yy = y; yy >= Math.max(minY, y - 16); yy--) {
			pos.set(wx, yy, wz);
			BlockState s = chunk.getBlockState(pos);
			if (!s.getFluidState().is(FluidTags.WATER)) {
				floor = s;
				break;
			}
			depth++;
		}
		int color = waterColor;
		if (floor != null && depth <= 3) {
			color = blend(waterColor, TerrainColors.color(level, floor, pos.immutable()), depth <= 1 ? 0.45f : 0.25f);
		}
		return MapMath.shade(color, 1.0f - Math.min(depth, 12) * 0.035f);
	}

	private static void scanSlice(ClientLevel level, LevelChunk chunk, Identifier dim, int slice) {
		int top = Math.min(level.getMaxY(), slice * MapMath.SLICE_HEIGHT + MapMath.SLICE_HEIGHT - 1);
		int bottom = Math.max(level.getMinY(), slice * MapMath.SLICE_HEIGHT - 8);
		String layer = MapMath.sliceKey(slice);
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int wx = minX + lx;
				int wz = minZ + lz;
				int y = top;
				pos.set(wx, y, wz);
				// Inside rock at the top of the slice: go down to the first open space.
				while (y >= bottom && !chunk.getBlockState(pos).isAir()) {
					pos.set(wx, --y, wz);
				}
				if (y < bottom) {
					MapStore.setPixel(dim, layer, wx, wz, ROCK);
					continue;
				}
				// Then down through the open space to the floor.
				while (y >= bottom && chunk.getBlockState(pos).isAir()) {
					pos.set(wx, --y, wz);
				}
				if (y < bottom) {
					MapStore.setPixel(dim, layer, wx, wz, VOID);
					continue;
				}
				BlockState floor = chunk.getBlockState(pos);
				int color = floor.getFluidState().is(FluidTags.WATER)
					? TerrainColors.color(level, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), pos.immutable())
					: TerrainColors.color(level, floor, pos.immutable());
				float depthShade = 1.0f - Math.min(top - y, 24) * 0.02f;
				MapStore.setPixel(dim, layer, wx, wz, MapMath.shade(color, depthShade));
			}
		}
	}

	private static int blend(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
