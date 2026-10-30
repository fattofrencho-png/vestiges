package dev.vestiges.map;

/**
 * Map coordinates and layer names, free of Minecraft types so they are unit-tested. A region is 512×512 blocks (one pixel per block); an
 * underground "slice" is 16 blocks tall. Layer keys are "surface" or "u&lt;slice&gt;" (slice can be negative, e.g. "u-2" = y -32..-17).
 */
public final class MapMath {
	public static final int REGION_SIZE = 512;
	public static final int SLICE_HEIGHT = 16;
	public static final String SURFACE = "surface";
	/** A column whose chunk is not loaded: it gives no relief and casts no shadow. */
	public static final int NO_HEIGHT = Integer.MIN_VALUE;
	/** How far (in diagonal blocks) a shadow can fall. */
	public static final int SHADOW_REACH = 12;
	private static final float SHADOW_DARK = 0.35f;
	/** How high the sun stands: a shadow loses this many blocks of height per diagonal step (2 = shadows half as long as the object is tall). */
	private static final int SUN_HEIGHT = 2;

	private MapMath() {
	}

	public static int region(int blockCoord) {
		return Math.floorDiv(blockCoord, REGION_SIZE);
	}

	public static int local(int blockCoord) {
		return Math.floorMod(blockCoord, REGION_SIZE);
	}

	public static int slice(int y) {
		return Math.floorDiv(y, SLICE_HEIGHT);
	}

	public static String sliceKey(int slice) {
		return "u" + slice;
	}

	public static boolean isSurface(String layer) {
		return SURFACE.equals(layer);
	}

	public static int sliceOf(String layer) {
		return Integer.parseInt(layer.substring(1));
	}

	/** What the player sees as the layer name: Surface, Caves (y a..b), Deepslate (below y 0) or Nether. */
	public static String label(String layer, boolean nether) {
		if (isSurface(layer)) {
			return "Surface";
		}
		int s = sliceOf(layer);
		int lo = s * SLICE_HEIGHT;
		int hi = lo + SLICE_HEIGHT - 1;
		String range = " (y " + lo + " to " + hi + ")";
		if (nether) {
			return "Nether" + range;
		}
		return (lo < 0 ? "Deepslate" : "Caves") + range;
	}

	/** Brightens (positive) or darkens (negative) an opaque ARGB colour by a factor, keeping alpha. */
	public static int shade(int argb, float factor) {
		int a = argb >>> 24;
		int r = clamp(Math.round(((argb >> 16) & 0xFF) * factor));
		int g = clamp(Math.round(((argb >> 8) & 0xFF) * factor));
		int b = clamp(Math.round((argb & 0xFF) * factor));
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** Vanilla-map style relief: a column higher than its northern neighbour is brighter, a lower one darker. */
	public static float relief(int height, int northHeight) {
		if (northHeight == NO_HEIGHT) {
			return 1.0f;
		}
		int d = height - northHeight;
		if (d > 0) {
			return 1.0f + Math.min(d, 4) * 0.06f;
		}
		if (d < 0) {
			return 1.0f - Math.min(-d, 4) * 0.09f;
		}
		return 1.0f;
	}

	/**
	 * Shadow cast by the sun in the north-west: darkens a column when something towards the sun rises above the light's line, which drops
	 * {@link #SUN_HEIGHT} blocks per diagonal step. Soft at its edges and fading out at {@link #SHADOW_REACH}. {@code heights} is a square of columns, {@code size}
	 * wide, read row by row (north to south).
	 */
	public static float shadow(int[] heights, int size, int x, int z) {
		int h = heights[z * size + x];
		int depth = 0;
		for (int i = 1; i <= SHADOW_REACH && x - i >= 0 && z - i >= 0; i++) {
			int blocker = heights[(z - i) * size + x - i];
			if (blocker != NO_HEIGHT) {
				depth = Math.max(depth, Math.min(blocker - h - i * SUN_HEIGHT, SHADOW_REACH + 1 - i));
			}
		}
		return 1.0f - SHADOW_DARK * Math.min(1.0f, depth / 3.0f);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
