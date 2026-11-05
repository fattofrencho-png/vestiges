package dev.vestiges.dungeon;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The dungeon portal frame. It follows the Nether portal's rules: upright, any size from 2 x 3 up to 21 x 21 inside, corners not needed. Our
 * pattern: the two side columns are chiseled tuff, the top and bottom rows are copper blocks (any rust stage, waxed or not). Pure logic over a
 * {@link Blocks} view, so it is unit-tested without a world.
 */
public final class PortalFrame {
	public enum Kind { AIR, TUFF, COPPER, OTHER }

	/** What is at a position (the world, or a test grid). */
	@FunctionalInterface
	public interface Blocks {
		Kind at(int x, int y, int z);
	}

	public static final int MIN_WIDTH = 2;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_SIZE = 21;
	/** The smallest frame (2 x 3 inside) needs this many of each block; the First Key feat hands them out. */
	public static final int KIT_TUFF = 6;
	public static final int KIT_COPPER = 4;

	private PortalFrame() {
	}

	/**
	 * A frame that was found. {@code axis} 0 means the frame is as wide along X, 1 along Z. {@code (x, y, z)} is the lowest interior corner with the
	 * smallest coordinate along the width.
	 */
	public record Shape(int axis, int x, int y, int z, int width, int height) {
		public int cellX(int i) {
			return axis == 0 ? x + i : x;
		}

		public int cellZ(int i) {
			return axis == 0 ? z : z + i;
		}

		public double centerX() {
			return axis == 0 ? x + width / 2.0 : x + 0.5;
		}

		public double centerZ() {
			return axis == 0 ? z + 0.5 : z + width / 2.0;
		}
	}

	/** The frame that the position belongs to (a frame block or an air block inside it), or null. */
	public static Shape find(Blocks w, int px, int py, int pz) {
		for (int axis = 0; axis < 2; axis++) {
			Shape s = onAxis(w, axis, px, py, pz);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static Kind at(Blocks w, int axis, int plane, int u, int y) {
		return axis == 0 ? w.at(u, y, plane) : w.at(plane, y, u);
	}

	private static Shape onAxis(Blocks w, int axis, int px, int py, int pz) {
		int plane = axis == 0 ? pz : px;
		int pu = axis == 0 ? px : pz;
		if (at(w, axis, plane, pu, py) == Kind.AIR) {
			return fill(w, axis, plane, pu, py);
		}
		for (int[] d : DIRS) {
			if (at(w, axis, plane, pu + d[0], py + d[1]) == Kind.AIR) {
				Shape s = fill(w, axis, plane, pu + d[0], py + d[1]);
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	/** Floods the air around a seed (in the frame's plane); it must be a closed rectangle inside the size limits with the right blocks around it. */
	private static Shape fill(Blocks w, int axis, int plane, int su, int sy) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {su, sy});
		seen.add(key(su, sy));
		int minU = su;
		int maxU = su;
		int minY = sy;
		int maxY = sy;
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			minU = Math.min(minU, c[0]);
			maxU = Math.max(maxU, c[0]);
			minY = Math.min(minY, c[1]);
			maxY = Math.max(maxY, c[1]);
			if (maxU - minU + 1 > MAX_SIZE || maxY - minY + 1 > MAX_SIZE) {
				return null;
			}
			for (int[] d : DIRS) {
				int nu = c[0] + d[0];
				int ny = c[1] + d[1];
				if (at(w, axis, plane, nu, ny) == Kind.AIR && seen.add(key(nu, ny))) {
					queue.add(new int[] {nu, ny});
				}
			}
		}
		int width = maxU - minU + 1;
		int height = maxY - minY + 1;
		if (width < MIN_WIDTH || height < MIN_HEIGHT || seen.size() != width * height) {
			return null;
		}
		for (int i = 0; i < width; i++) {
			if (at(w, axis, plane, minU + i, minY - 1) != Kind.COPPER || at(w, axis, plane, minU + i, maxY + 1) != Kind.COPPER) {
				return null;
			}
		}
		for (int j = 0; j < height; j++) {
			if (at(w, axis, plane, minU - 1, minY + j) != Kind.TUFF || at(w, axis, plane, maxU + 1, minY + j) != Kind.TUFF) {
				return null;
			}
		}
		return axis == 0 ? new Shape(0, minU, minY, plane, width, height) : new Shape(1, plane, minY, minU, width, height);
	}

	private static long key(int u, int y) {
		return ((long) u << 32) ^ (y & 0xffffffffL);
	}
}
