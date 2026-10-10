package dev.vestiges.map;

/**
 * Map coordinates and layer names, free of Minecraft types so they are unit-tested. A region is 512×512 blocks (one pixel per block); an
 * underground "slice" is 16 blocks tall. Layer keys are "surface" or "u&lt;slice&gt;" (slice can be negative, e.g. "u-2" = y -32..-17).
 */
public final class MapMath {
	public static final int REGION_SIZE = 512;
	public static final int SLICE_HEIGHT = 16;
	public static final String SURFACE = "surface";

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
		int d = height - northHeight;
		if (d > 0) {
			return 1.0f + Math.min(d, 4) * 0.04f;
		}
		if (d < 0) {
			return 1.0f - Math.min(-d, 4) * 0.06f;
		}
		return 1.0f;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
