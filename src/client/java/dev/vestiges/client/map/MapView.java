package dev.vestiges.client.map;

import dev.vestiges.map.MapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;

/** Drawing shared by the minimap and the world map: map tiles, markers and the player arrow. */
public final class MapView {
	private MapView() {
	}

	/** The layer that fits where the player is: the current slice underground or in the Nether, otherwise the surface. */
	public static String currentLayer(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return MapMath.SURFACE;
		}
		int y = mc.player.getBlockY();
		if (level.dimensionType().hasCeiling()) {
			return MapMath.sliceKey(MapMath.slice(y));
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mc.player.getBlockX(), mc.player.getBlockZ());
		return y < surface - 4 ? MapMath.sliceKey(MapMath.slice(y)) : MapMath.SURFACE;
	}

	public static boolean isNether(Minecraft mc) {
		return mc.level != null && mc.level.dimensionType().hasCeiling();
	}

	/**
	 * Draws the map of {@code layer} into the screen rectangle (sx, sy, w, h), centred on block (cx, cz), at {@code zoom} screen pixels per
	 * block. The caller sets the scissor.
	 */
	public static void drawTiles(GuiGraphicsExtractor g, Identifier dim, String layer, int sx, int sy, int w, int h, double cx, double cz, float zoom) {
		cx = snap(cx, zoom);
		cz = snap(cz, zoom);
		double minBX = cx - w / (2.0 * zoom);
		double minBZ = cz - h / (2.0 * zoom);
		double maxBX = cx + w / (2.0 * zoom);
		double maxBZ = cz + h / (2.0 * zoom);
		int rx0 = MapMath.region((int) Math.floor(minBX));
		int rx1 = MapMath.region((int) Math.floor(maxBX));
		int rz0 = MapMath.region((int) Math.floor(minBZ));
		int rz1 = MapMath.region((int) Math.floor(maxBZ));
		for (int rx = rx0; rx <= rx1; rx++) {
			for (int rz = rz0; rz <= rz1; rz++) {
				MapRegion r = MapStore.forRead(dim, layer, rx, rz);
				if (r == null) {
					continue;
				}
				Identifier tex = r.texture();
				double bx0 = (double) rx * MapMath.REGION_SIZE;
				double bz0 = (double) rz * MapMath.REGION_SIZE;
				double vx0 = Math.max(bx0, minBX);
				double vx1 = Math.min(bx0 + MapMath.REGION_SIZE, maxBX);
				double vz0 = Math.max(bz0, minBZ);
				double vz1 = Math.min(bz0 + MapMath.REGION_SIZE, maxBZ);
				if (vx1 <= vx0 || vz1 <= vz0) {
					continue;
				}
				int x0 = (int) Math.floor(sx + (vx0 - minBX) * zoom);
				int x1 = (int) Math.ceil(sx + (vx1 - minBX) * zoom);
				int y0 = (int) Math.floor(sy + (vz0 - minBZ) * zoom);
				int y1 = (int) Math.ceil(sy + (vz1 - minBZ) * zoom);
				float u0 = (float) ((vx0 - bx0) / MapMath.REGION_SIZE);
				float u1 = (float) ((vx1 - bx0) / MapMath.REGION_SIZE);
				float v0 = (float) ((vz0 - bz0) / MapMath.REGION_SIZE);
				float v1 = (float) ((vz1 - bz0) / MapMath.REGION_SIZE);
				g.blit(tex, x0, y0, x1, y1, u0, u1, v0, v1);
			}
		}
	}

	/** Rounds a view centre to whole screen pixels, so the map moves in clean 1-pixel steps instead of shaking. */
	public static double snap(double c, float zoom) {
		return Math.floor(c * zoom) / zoom;
	}

	/** Screen position of a block inside a view; may fall outside the rectangle. */
	public static int[] toScreen(int sx, int sy, int w, int h, double cx, double cz, float zoom, double bx, double bz) {
		cx = snap(cx, zoom);
		cz = snap(cz, zoom);
		return new int[] {(int) Math.round(sx + w / 2.0 + (bx - cx) * zoom), (int) Math.round(sy + h / 2.0 + (bz - cz) * zoom)};
	}
}
