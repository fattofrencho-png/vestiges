package dev.vestiges.dungeon;

/**
 * Where a run's rooms stand in the dungeon world: one plot per run, on a grid far apart so runs never touch. A plot is a strip along +X (13
 * rooms of up to 48 blocks with corridors of up to 16 fit in about 820 blocks); plots are reused after a run ends. Pure maths.
 */
public final class DungeonPlots {
	public static final int SPACING = 1024;
	public static final int COLUMNS = 16;
	/** The floor height of every room. */
	public static final int FLOOR_Y = 64;

	private DungeonPlots() {
	}

	/** The west end of the plot's strip (the start room's west wall). */
	public static int originX(int plot) {
		return (plot % COLUMNS) * SPACING;
	}

	/** The middle line of the strip. */
	public static int originZ(int plot) {
		return (plot / COLUMNS) * SPACING;
	}

	/** The chunk columns of a plot's strip between two x coordinates (inclusive), west to east, as {chunkX, chunkZ}: 4 rows cover every room and corridor. */
	public static java.util.List<int[]> stripChunks(int plot, int fromX, int toX) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int z = originZ(plot);
		for (int cx = Math.floorDiv(fromX, 16); cx <= Math.floorDiv(toX, 16); cx++) {
			for (int cz = Math.floorDiv(z - 26, 16); cz <= Math.floorDiv(z + 26, 16); cz++) {
				out.add(new int[] {cx, cz});
			}
		}
		return out;
	}

	/** The plot a position belongs to, or -1 when it lies outside the plot grid. */
	public static int plotAt(int x, int z) {
		int col = Math.floorDiv(x, SPACING);
		int row = Math.floorDiv(z + SPACING / 2, SPACING);
		return x < 0 || row < 0 || col >= COLUMNS ? -1 : row * COLUMNS + col;
	}
}
