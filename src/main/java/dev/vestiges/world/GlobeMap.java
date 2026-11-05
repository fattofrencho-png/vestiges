package dev.vestiges.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/**
 * The planned world, drawn once per seed on a coarse grid: where the land is, where the mountains are and how wet it is. The same rough map in every
 * world, with outlines and ranges from the seed. Past the globe's edge it fades back to vanilla. North is -Z, east is +X, and spawn is at the centre.
 */
public final class GlobeMap {
	public static final int CELL = 128;
	public static final int X_MIN = -120000;
	public static final int X_MAX = 120000;
	public static final int Z_MIN = -90000;
	public static final int Z_MAX = 90000;
	/** Beyond the globe: open ocean for this far, then a fade to vanilla over the same distance again. */
	private static final int FADE = 7500;
	private static final int GRID_X0 = X_MIN - FADE * 2;
	private static final int GRID_Z0 = Z_MIN - FADE * 2;
	private static final int W = (X_MAX - X_MIN + FADE * 4) / CELL;
	private static final int H = (Z_MAX - Z_MIN + FADE * 4) / CELL;

	public static final byte OCEAN = -1;
	/** Above any land index. */
	public static final byte ISLAND = 100;

	private static final double COAST_BAND = .06;
	private static final int CAPITAL_CLEAR = 3000;
	private static final int CAPITAL_INLAND = 700;
	/** Wet-weighted land, in square blocks, that has to drain through a spot before it counts as a river. */
	private static final double RIVER_SOURCE = 30_000_000;
	private static final int RIVER_REACH = 900;
	private static final int BUCKET = 1024;
	private static final double BED_OFFSET = -.57;
	private static final double BANK_RISE = .0026;
	private static final double BANK = 120;
	/** Vanilla's rivers need ridges within about 0.05 of 0, so staying past this removes them. */
	private static final double NO_RIVER = .07;

	private record Key(long seed, WorldPlan plan) {
	}

	private static final Map<Key, GlobeMap> CACHE = new LinkedHashMap<>(4, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, GlobeMap> eldest) {
			return size() > 2;
		}
	};

	public static GlobeMap forSeed(long seed) {
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(new Key(seed, WorldPlans.current()), key -> new GlobeMap(key.seed(), key.plan()));
		}
	}

	private final float[] shape = new float[W * H];
	private final float[] mountain = new float[W * H];
	private final float[] wet = new float[W * H];
	private final byte[] owner = new byte[W * H];
	private final int s;
	private final WorldPlan plan;
	private final Placed[] placed;

	private static final class Placed {
		WorldPlan.Land land;
		double cx, cz, rx, rz, cos, sin;
		final List<double[]> lobes = new ArrayList<>();
		final List<double[]> peninsulas = new ArrayList<>();
		final List<double[]> bays = new ArrayList<>();
		boolean fixed;

		/** Positive inside the outline, in units of the continent's radius. */
		double score(double u, double v) {
			double n = Math.hypot(u, v);
			if (n > 2.2) {
				return -9;
			}
			double t = land.core() - n;
			for (double[] l : lobes) {
				t = Math.max(t, l[2] - Math.hypot(u - l[0], v - l[1]));
			}
			for (double[] p : peninsulas) {
				t = Math.max(t, p[4] - segment(u, v, p));
			}
			for (double[] b : bays) {
				t = Math.min(t, Math.hypot(u - b[0], v - b[1]) - b[2]);
			}
			return t;
		}

		private static double segment(double u, double v, double[] p) {
			double dx = p[2] - p[0];
			double dz = p[3] - p[1];
			double t = Math.max(0, Math.min(1, ((u - p[0]) * dx + (v - p[1]) * dz) / (dx * dx + dz * dz)));
			return Math.hypot(u - p[0] - dx * t, v - p[1] - dz * t);
		}
	}

	private GlobeMap(long seed, WorldPlan plan) {
		this.plan = plan;
		SplittableRandom rng = new SplittableRandom(seed);
		s = Long.hashCode(seed);
		placed = new Placed[plan.lands().size()];
		for (int i = 0; i < placed.length; i++) {
			placed[i] = place(plan.lands().get(i), i == 0, rng);
		}
		drawLand(rng);
		for (Placed c : placed) {
			drawRanges(c, rng);
		}
		clearSpawn();
		drawRain();
		drawRivers();
		chooseEmeraldSites(rng);
	}

	private static Placed place(WorldPlan.Land land, boolean fixed, SplittableRandom rng) {
		Placed c = new Placed();
		c.land = land;
		c.fixed = fixed;
		double jitter = fixed ? 0 : land.r() * .18;
		c.cx = land.x() + (rng.nextDouble() - .5) * jitter;
		c.cz = land.z() + (rng.nextDouble() - .5) * jitter;
		c.rx = land.r() * land.sx() * (fixed ? 1.02 + rng.nextDouble() * .06 : .9 + rng.nextDouble() * .1);
		c.rz = land.r() * land.sz() * (fixed ? 1.02 + rng.nextDouble() * .06 : .9 + rng.nextDouble() * .1);
		double angle = (rng.nextDouble() - .5) * (fixed ? .2 : 1.4);
		c.cos = Math.cos(angle);
		c.sin = Math.sin(angle);
		int lobes = 3 + rng.nextInt(3);
		double start = rng.nextDouble() * 7;
		for (int i = 0; i < lobes; i++) {
			double a = start + i * Math.PI * 2 / lobes + (rng.nextDouble() - .5) * .9;
			double d = .45 + rng.nextDouble() * .35;
			c.lobes.add(new double[] {Math.cos(a) * d, Math.sin(a) * d, .28 + rng.nextDouble() * .24});
		}
		int peninsulas = 1 + rng.nextInt(3);
		for (int i = 0; i < peninsulas; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double far = .95 + rng.nextDouble() * .4;
			double turn = (rng.nextDouble() - .5) * .7;
			c.peninsulas.add(new double[] {Math.cos(a) * .55, Math.sin(a) * .55, Math.cos(a + turn) * far, Math.sin(a + turn) * far, .07 + rng.nextDouble() * .09});
		}
		int bays = 2 + rng.nextInt(3);
		for (int i = 0; i < bays; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double d = .75 + rng.nextDouble() * .25;
			c.bays.add(new double[] {Math.cos(a) * d, Math.sin(a) * d, .14 + rng.nextDouble() * .2});
		}
		return c;
	}

	private void drawLand(SplittableRandom rng) {
		float[][] scores = new float[placed.length][W * H];
		float[] warpedX = new float[W * H];
		float[] warpedZ = new float[W * H];
		float[] coast = new float[W * H];
		for (int y = 0, i = 0; y < H; y++) {
			double z = GRID_Z0 + y * CELL;
			for (int x = 0; x < W; x++, i++) {
				double wx = GRID_X0 + x * CELL;
				owner[i] = OCEAN;
				shape[i] = -1;
				if (edgeDistance(wx, z) > -plan.edgeGap()) {
					for (float[] score : scores) {
						score[i] = -9;
					}
					continue;
				}
				double qx = wx + (fbm(wx / 19000, z / 19000, s + 1, 3) - .5) * 16000 + (fbm(wx / 6000, z / 6000, s + 10, 3) - .5) * 7000;
				double qz = z + (fbm(wx / 19000, z / 19000, s + 2, 3) - .5) * 16000 + (fbm(wx / 6000, z / 6000, s + 13, 3) - .5) * 7000;
				double noise = (fbm(wx / 8000, z / 8000, s + 3, 4) - .5) * .42;
				warpedX[i] = (float) qx;
				warpedZ[i] = (float) qz;
				coast[i] = (float) noise;
				for (int n = 0; n < placed.length; n++) {
					Placed c = placed[n];
					double dx = qx - c.cx;
					double dz = qz - c.cz;
					scores[n][i] = (float) (c.score((dx * c.cos + dz * c.sin) / c.rx, (-dx * c.sin + dz * c.cos) / c.rz) + noise);
				}
				// Trenec's middle is always solid land, so the walk to Dilecta can only end on its real north coast.
				if (Math.hypot(wx / placed[0].rx, z / placed[0].rz) < .7) {
					scores[0][i] = Math.max(scores[0][i], .04f);
				}
			}
		}

		// The safe distance is a hard rule: land too close to a continent already placed is removed, so no seed can make a land bridge.
		boolean[] done = new boolean[W * H];
		for (int order = 0; order < plan.landOrder().size(); order++) {
			int n = plan.landIndex(plan.landOrder().get(order));
			float[] dist = order == 0 ? null : distance(done);
			for (int i = 0; i < W * H; i++) {
				float v = scores[n][i];
				if (v <= -9) {
					continue;
				}
				if (dist != null) {
					if (dist[i] < plan.safeGap()) {
						v = Math.min(v, -.1f);
					} else if (dist[i] < plan.safeGap() * 1.25f) {
						v -= (plan.safeGap() * 1.25f - dist[i]) / (plan.safeGap() * .25f) * .3f;
					}
				}
				shape[i] = Math.max(shape[i], v);
				if (v > 0) {
					owner[i] = (byte) n;
				}
			}
			for (int i = 0; i < W * H; i++) {
				done[i] |= owner[i] == n;
			}
		}

		float[] clear = distance(done);
		int count = 16 + rng.nextInt(22);
		for (int n = 0; n < count; n++) {
			double ix = X_MIN + rng.nextDouble() * (X_MAX - X_MIN);
			double iz = Z_MIN + rng.nextDouble() * (Z_MAX - Z_MIN);
			double r = rng.nextDouble() < .2 ? 3000 + rng.nextDouble() * 2500 : 700 + rng.nextDouble() * 1800;
			int reach = (int) Math.ceil(r * 2.5 / CELL);
			int cx = (int) ((ix - GRID_X0) / CELL);
			int cy = (int) ((iz - GRID_Z0) / CELL);
			for (int y = Math.max(0, cy - reach); y <= Math.min(H - 1, cy + reach); y++) {
				for (int x = Math.max(0, cx - reach); x <= Math.min(W - 1, cx + reach); x++) {
					int i = y * W + x;
					if (clear[i] < plan.islandGap() || edgeDistance(GRID_X0 + x * CELL, GRID_Z0 + y * CELL) > -plan.edgeGap()) {
						continue;
					}
					float v = (float) (1 - Math.hypot(warpedX[i] - ix, warpedZ[i] - iz) / r + coast[i] * 2);
					shape[i] = Math.max(shape[i], v * .4f);
					if (v > 0) {
						owner[i] = ISLAND;
					}
				}
			}
		}
	}

	private static double edgeDistance(double x, double z) {
		return Math.max(Math.max(X_MIN - x, x - X_MAX), Math.max(Z_MIN - z, z - Z_MAX));
	}

	/** Blocks from every cell to the nearest marked cell. */
	private static float[] distance(boolean[] from) {
		float[] d = new float[W * H];
		float diagonal = (float) Math.sqrt(2);
		for (int i = 0; i < W * H; i++) {
			d[i] = from[i] ? 0 : 1e9f;
		}
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int i = y * W + x;
				if (x > 0) {
					d[i] = Math.min(d[i], d[i - 1] + 1);
				}
				if (y > 0) {
					d[i] = Math.min(d[i], d[i - W] + 1);
					if (x > 0) {
						d[i] = Math.min(d[i], d[i - W - 1] + diagonal);
					}
					if (x < W - 1) {
						d[i] = Math.min(d[i], d[i - W + 1] + diagonal);
					}
				}
			}
		}
		for (int y = H - 1; y >= 0; y--) {
			for (int x = W - 1; x >= 0; x--) {
				int i = y * W + x;
				if (x < W - 1) {
					d[i] = Math.min(d[i], d[i + 1] + 1);
				}
				if (y < H - 1) {
					d[i] = Math.min(d[i], d[i + W] + 1);
					if (x < W - 1) {
						d[i] = Math.min(d[i], d[i + W + 1] + diagonal);
					}
					if (x > 0) {
						d[i] = Math.min(d[i], d[i + W - 1] + diagonal);
					}
				}
			}
		}
		for (int i = 0; i < W * H; i++) {
			d[i] *= CELL;
		}
		return d;
	}

	private int stampId;

	/** A range is a line whose width and height change along it, so it narrows, broadens and dips into passes. */
	private void stamp(Placed c, double[][] line, double width, double bendShare, double strength) {
		int id = ++stampId;
		for (int g = 0; g < line.length - 1; g++) {
			double ax = c.cx + line[g][0] * c.rx * c.cos - line[g][1] * c.rz * c.sin;
			double az = c.cz + line[g][0] * c.rx * c.sin + line[g][1] * c.rz * c.cos;
			double bx = c.cx + line[g + 1][0] * c.rx * c.cos - line[g + 1][1] * c.rz * c.sin;
			double bz = c.cz + line[g + 1][0] * c.rx * c.sin + line[g + 1][1] * c.rz * c.cos;
			double len = Math.max(1, Math.hypot(bx - ax, bz - az));
			int steps = (int) Math.ceil(len / Math.max(CELL, width * .2));
			for (int t = 0; t <= steps; t++) {
				double f = t / (double) steps;
				double along = g + f;
				double bend = (fbm(along * 1.3, id * 1.7, s + 5, 3) - .5) * c.land.r() * bendShare * 2;
				double here = width * (.55 + .9 * fbm(along * 2.1, id * 7.3, s + 11, 2));
				double top = strength * (.55 + .75 * fbm(along * 2.6, id * 3.1, s + 12, 2));
				double px = ax + (bx - ax) * f - (bz - az) / len * bend;
				double pz = az + (bz - az) * f + (bx - ax) / len * bend;
				int r = (int) Math.ceil(here * 2 / CELL);
				int cx = (int) ((px - GRID_X0) / CELL);
				int cy = (int) ((pz - GRID_Z0) / CELL);
				for (int y = Math.max(0, cy - r); y <= Math.min(H - 1, cy + r); y++) {
					for (int x = Math.max(0, cx - r); x <= Math.min(W - 1, cx + r); x++) {
						double d = Math.hypot(x - cx, y - cy) * CELL / here;
						int i = y * W + x;
						mountain[i] = Math.max(mountain[i], (float) (top * Math.exp(-d * d)));
					}
				}
			}
		}
	}

	/** Keeps a range on its continent. On Trenec it also stays south of the north coast, so Dilecta's gap stays open. */
	private static double[] keep(Placed c, double x, double z) {
		double n = Math.hypot(x, z);
		double f = n > .9 ? .9 / n : 1;
		return new double[] {x * f, c.fixed ? Math.max(-.35, z * f) : z * f};
	}

	/** A random path in any direction: straight, a steady arc or a zigzag. */
	private static double[][] path(Placed c, double length, SplittableRandom rng) {
		int kind = rng.nextInt(3);
		double curve = (rng.nextBoolean() ? -1 : 1) * (.5 + rng.nextDouble() * 1.1);
		double dir = rng.nextDouble() * Math.PI * 2;
		double seg = length / 4;
		double o = rng.nextDouble() * Math.PI * 2;
		double off = rng.nextDouble() * .35;
		double[][] raw = new double[5][];
		raw[0] = new double[] {Math.cos(o) * off - Math.cos(dir) * length / 2, Math.sin(o) * off - Math.sin(dir) * length / 2};
		for (int i = 0; i < 4; i++) {
			dir += switch (kind) {
				case 1 -> curve / 2;
				case 2 -> (i % 2 == 1 ? -1 : 1) * (.5 + rng.nextDouble() * .5);
				default -> (rng.nextDouble() - .5) * .35;
			};
			raw[i + 1] = new double[] {raw[i][0] + Math.cos(dir) * seg, raw[i][1] + Math.sin(dir) * seg};
		}
		double[][] out = new double[5][];
		for (int i = 0; i < 5; i++) {
			out[i] = keep(c, raw[i][0], raw[i][1]);
		}
		return out;
	}

	private void drawRanges(Placed c, SplittableRandom rng) {
		if (c.fixed) {
			// Dilecta sits between mountains, so Trenec always has these two. Only their reach and angle change.
			for (int side = -1; side <= 1; side += 2) {
				stamp(c, new double[][] {{side * (.2 + rng.nextDouble() * .05), -.84}, {side * (.5 + rng.nextDouble() * .3), -.78 + rng.nextDouble() * .4}}, 1000 + rng.nextDouble() * 700, .08, 1);
			}
		}
		WorldPlan.Land land = c.land;
		for (int n = 0; n < land.mains(); n++) {
			double width = land.widthMin() + (land.widthMax() - land.widthMin()) * rng.nextDouble();
			double[][] line = path(c, land.lenMin() + (land.lenMax() - land.lenMin()) * rng.nextDouble(), rng);
			stamp(c, line, width, .2, 1);
			int spurs = 2 + rng.nextInt(4);
			for (int i = 0; i < spurs; i++) {
				int at = rng.nextInt(line.length - 1);
				double[] a = line[at];
				double[] b = line[at + 1];
				double f = rng.nextDouble();
				double dir = Math.atan2(b[1] - a[1], b[0] - a[0]) + (rng.nextBoolean() ? -1 : 1) * (.7 + rng.nextDouble() * .9);
				double len = .12 + rng.nextDouble() * .26;
				double fromX = a[0] + (b[0] - a[0]) * f;
				double fromZ = a[1] + (b[1] - a[1]) * f;
				stamp(c, new double[][] {{fromX, fromZ}, keep(c, fromX + Math.cos(dir) * len, fromZ + Math.sin(dir) * len)}, width * (.35 + rng.nextDouble() * .35), .12, .8);
			}
		}
		int extra = 1 + rng.nextInt(3);
		for (int n = 0; n < extra; n++) {
			stamp(c, path(c, .25 + rng.nextDouble() * .4, rng), 800 + rng.nextDouble() * 2400, .15, .85);
		}
	}

	/** New players start on gentle ground: ranges fade out around the centre of the world. */
	private void clearSpawn() {
		int capital = dilectaZ();
		for (int y = 0, i = 0; y < H; y++) {
			for (int x = 0; x < W; x++, i++) {
				double wx = GRID_X0 + x * CELL;
				double wz = GRID_Z0 + y * CELL;
				// An uneven edge, or the cleared ground shows on the map as a perfect circle.
				double d = Math.hypot(wx, wz) + (fbm(wx / 5000, wz / 5000, s + 20, 3) - .5) * 7000;
				if (d < plan.spawnClear()) {
					double f = Math.max(0, (d - plan.spawnClear() / 2.0) / (plan.spawnClear() / 2.0));
					mountain[i] *= (float) (f * f * (3 - 2 * f));
				}
				// The capital needs gentle ground too, between its two coastal ranges.
				double site = Math.hypot(wx, wz - capital - CAPITAL_INLAND);
				if (site < CAPITAL_CLEAR) {
					double f = Math.max(0, (site - CAPITAL_CLEAR / 2.0) / (CAPITAL_CLEAR / 2.0));
					mountain[i] *= (float) (f * f * (3 - 2 * f));
				}
			}
		}
	}

	/** Wet air comes in from the eastern ocean and rains out on the way, most of all on ranges, so land behind them stays dry. */
	private void drawRain() {
		double k = CELL / 500.0;
		double loss = Math.pow(.992, k);
		for (int y = 0; y < H; y++) {
			double w = 1;
			for (int x = W - 1; x >= 0; x--) {
				int i = y * W + x;
				if (shape[i] > 0) {
					mountain[i] *= 1;
					w *= loss;
					w *= 1 - Math.min(.5, mountain[i] * .07 * k);
				} else {
					mountain[i] = 0;
					w = Math.min(1, w + .022 * k);
				}
				wet[i] = (float) w;
			}
		}
		for (int y = 0, i = 0; y < H; y++) {
			for (int x = 0; x < W; x++, i++) {
				wet[i] = (float) Math.max(0, Math.min(1, wet[i] + (fbm((GRID_X0 + x * CELL) / 15000.0, (GRID_Z0 + y * CELL) / 15000.0, s + 9, 3) - .5) * .5));
			}
		}
	}

	/**
	 * Floods inland from the coast, so every land cell gets a way down to the sea, then adds up the water along those ways. Cells carrying enough water
	 * become river segments: they start high, join up, widen and always end at the sea.
	 */
	private void drawRivers() {
		int size = W * H;
		float[] height = new float[size];
		for (int y = 0, i = 0; y < H; y++) {
			for (int x = 0; x < W; x++, i++) {
				if (shape[i] > 0) {
					height[i] = (float) (Math.min(shape[i], 1) * .5 + mountain[i] * .85 + fbm((GRID_X0 + x * CELL) / 2600.0, (GRID_Z0 + y * CELL) / 2600.0, s + 31, 3) * .12);
				}
			}
		}
		int[] parent = new int[size];
		float[] level = new float[size];
		boolean[] seen = new boolean[size];
		java.util.Arrays.fill(parent, -1);
		PriorityQueue<long[]> queue = new PriorityQueue<>(java.util.Comparator.comparingLong(a -> a[0]));
		int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
		int[] dz = {0, 0, 1, -1, 1, -1, 1, -1};
		for (int y = 1; y < H - 1; y++) {
			for (int x = 1; x < W - 1; x++) {
				int i = y * W + x;
				if (shape[i] > 0) {
					continue;
				}
				for (int n = 0; n < 8; n++) {
					if (shape[(y + dz[n]) * W + x + dx[n]] > 0) {
						seen[i] = true;
						queue.add(new long[] {0, i});
						break;
					}
				}
			}
		}
		int[] order = new int[size];
		int count = 0;
		while (!queue.isEmpty()) {
			int cur = (int) queue.poll()[1];
			int x = cur % W;
			int y = cur / W;
			for (int n = 0; n < 8; n++) {
				int xx = x + dx[n];
				int yy = y + dz[n];
				if (xx < 0 || yy < 0 || xx >= W || yy >= H) {
					continue;
				}
				int j = yy * W + xx;
				if (seen[j] || shape[j] <= 0) {
					continue;
				}
				seen[j] = true;
				parent[j] = cur;
				level[j] = Math.max(height[j], level[cur] + .0005f);
				order[count++] = j;
				queue.add(new long[] {(long) (level[j] * 1_000_000), j});
			}
		}
		float[] water = new float[size];
		for (int n = count - 1; n >= 0; n--) {
			int i = order[n];
			water[i] += (.25f + wet[i]) * CELL * CELL;
			if (shape[parent[i]] > 0) {
				water[parent[i]] += water[i];
			}
		}
		int bucketsX = W * CELL / BUCKET + 2;
		int bucketsZ = H * CELL / BUCKET + 2;
		riverBuckets = new ArrayList[bucketsX * bucketsZ];
		riverBucketsX = bucketsX;
		for (int n = 0; n < count; n++) {
			int i = order[n];
			if (water[i] < RIVER_SOURCE) {
				continue;
			}
			float ax = GRID_X0 + i % W * CELL;
			float az = GRID_Z0 + i / W * CELL;
			float bx = GRID_X0 + parent[i] % W * CELL;
			float bz = GRID_Z0 + parent[i] / W * CELL;
			float half = (float) Math.min(70, 10 + 9 * Math.sqrt(water[i] / RIVER_SOURCE));
			int bucket = (int) (((az + bz) / 2 - GRID_Z0) / BUCKET) * bucketsX + (int) (((ax + bx) / 2 - GRID_X0) / BUCKET);
			if (riverBuckets[bucket] == null) {
				riverBuckets[bucket] = new ArrayList<>();
			}
			riverBuckets[bucket].add(new float[] {ax, az, bx, bz, half});
		}
	}

	private final List<int[]> emeraldSites = new ArrayList<>();

	/** Each continent rolls its own number of sites, then a world with too few gets more. Sites sit on the highest ground found on that continent. */
	private void chooseEmeraldSites(SplittableRandom rng) {
		int lands = plan.lands().size();
		for (int land = 0; land < lands; land++) {
			int rolled = rng.nextInt(plan.emeraldMaxPerLand() + 1);
			for (int i = 0; i < rolled; i++) {
				addEmeraldSite(land, rng);
			}
		}
		for (int tries = 0; tries < 50 && emeraldSites.size() < plan.emeraldMinTotal(); tries++) {
			int land = rng.nextInt(lands);
			if (emeraldSites.stream().filter(site -> site[2] == land).count() < plan.emeraldMaxPerLand()) {
				addEmeraldSite(land, rng);
			}
		}
	}

	private void addEmeraldSite(int land, SplittableRandom rng) {
		Placed c = placed[land];
		int capital = dilectaZ();
		int[] best = null;
		float bestHeight = -1;
		for (int tries = 0; tries < 600; tries++) {
			int x = (int) (c.cx + (rng.nextDouble() * 2 - 1) * c.rx);
			int z = (int) (c.cz + (rng.nextDouble() * 2 - 1) * c.rz);
			if (ownerAt(x, z) != land || Math.hypot(x, z) < 6000 || Math.hypot(x, z - capital) < 6000) {
				continue;
			}
			// Two mines on one continent stay far enough apart to be separate places.
			if (emeraldSites.stream().anyMatch(site -> Math.hypot(site[0] - x, site[1] - z) < 8000)) {
				continue;
			}
			float height = mountain[(z - GRID_Z0) / CELL * W + (x - GRID_X0) / CELL];
			if (height > bestHeight) {
				bestHeight = height;
				best = new int[] {x, z, land};
			}
		}
		if (best != null) {
			emeraldSites.add(best);
		}
	}

	/** {x, z, land} of each site. */
	public List<int[]> emeraldSites() {
		return emeraldSites;
	}

	public boolean emeraldSite(double x, double z) {
		for (int[] site : emeraldSites) {
			if (Math.hypot(site[0] - x, site[1] - z) <= plan.emeraldRadius()) {
				return true;
			}
		}
		return false;
	}

	private List<float[]>[] riverBuckets;
	private int riverBucketsX;

	/** Blocks from a spot to the nearest river bank, negative in the water, and at least {@link #RIVER_REACH} when no river is close. A small wobble keeps the banks from running straight. */
	public double riverEdge(double x, double z) {
		double qx = x + (fbm(x / 180, z / 180, s + 40, 2) - .5) * 140;
		double qz = z + (fbm(x / 180, z / 180, s + 41, 2) - .5) * 140;
		int bx = (int) Math.floor((qx - GRID_X0) / BUCKET);
		int bz = (int) Math.floor((qz - GRID_Z0) / BUCKET);
		double best = RIVER_REACH;
		for (int zz = bz - 1; zz <= bz + 1; zz++) {
			for (int xx = bx - 1; xx <= bx + 1; xx++) {
				int bucket = zz * riverBucketsX + xx;
				if (xx < 0 || zz < 0 || xx >= riverBucketsX || bucket >= riverBuckets.length || riverBuckets[bucket] == null) {
					continue;
				}
				for (float[] r : riverBuckets[bucket]) {
					double dx = r[2] - r[0];
					double dz = r[3] - r[1];
					double t = Math.max(0, Math.min(1, ((qx - r[0]) * dx + (qz - r[1]) * dz) / (dx * dx + dz * dz)));
					best = Math.min(best, Math.hypot(qx - r[0] - dx * t, qz - r[1] - dz * t) - r[4]);
				}
			}
		}
		return best;
	}

	/** Vanilla's ridges value with our rivers in it. See {@link GlobeRidges}. */
	public double ridges(double noise, double x, double z) {
		double share = vanillaShare(x, z);
		if (share >= 1) {
			return noise;
		}
		double away = noise < 0 ? Math.min(noise, -NO_RIVER) : Math.max(noise, NO_RIVER);
		double edge = riverEdge(x, z);
		if (edge < BANK) {
			double f = Math.max(0, edge / BANK);
			away *= f * f * (3 - 2 * f);
		}
		return fade(away, noise, share);
	}

	private static double sample(float[] grid, double x, double z, float outside) {
		double gx = (x - GRID_X0) / CELL;
		double gz = (z - GRID_Z0) / CELL;
		int ix = (int) Math.floor(gx);
		int iz = (int) Math.floor(gz);
		if (ix < 0 || iz < 0 || ix >= W - 1 || iz >= H - 1) {
			return outside;
		}
		double fx = gx - ix;
		double fz = gz - iz;
		int i = iz * W + ix;
		return (grid[i] * (1 - fx) + grid[i + 1] * fx) * (1 - fz) + (grid[i + W] * (1 - fx) + grid[i + W + 1] * fx) * fz;
	}

	/** 0 inside the globe and through its ocean ring, then rising to 1 where the world is plain vanilla. */
	private static double vanillaShare(double x, double z) {
		double e = edgeDistance(x, z);
		if (e <= FADE) {
			return 0;
		}
		double f = Math.min(1, (e - FADE) / FADE);
		return f * f * (3 - 2 * f);
	}

	private static double fade(double planned, double vanilla, double share) {
		return planned + (vanilla - planned) * share;
	}

	public byte ownerAt(int x, int z) {
		int ix = (x - GRID_X0) / CELL;
		int iz = (z - GRID_Z0) / CELL;
		return x < GRID_X0 || z < GRID_Z0 || ix >= W || iz >= H ? OCEAN : owner[iz * W + ix];
	}

	/** Dilecta's place: walk north from Trenec's centre to its coast, so it can only ever be on Trenec. */
	public int dilectaZ() {
		int z = 0;
		while (ownerAt(0, z - CELL) == 0) {
			z -= CELL;
		}
		return z;
	}

	/** One value the terrain chain reads, in vanilla's climate units. */
	public double value(GlobeFunction.Channel channel, double x, double z) {
		if (channel == GlobeFunction.Channel.OWNER) {
			return ownerAt((int) x, (int) z);
		}
		if (channel == GlobeFunction.Channel.EMERALD_SITE) {
			return emeraldSite(x, z) ? 1 : 0;
		}
		if (channel == GlobeFunction.Channel.RIVER_EDGE) {
			return riverEdge(x, z);
		}
		double share = vanillaShare(x, z);
		if (share >= 1) {
			return channel.vanilla;
		}
		double v = sample(shape, x, z, -1);
		// The grid is coarse, so without fine noise its straight edges show along the coast. Fades out away from the shore, so the safe distance holds.
		if (Math.abs(v) < COAST_BAND) {
			v += (fbm(x / 220, z / 220, s + 30, 3) - .5) * .05 * (1 - Math.abs(v) / COAST_BAND);
		}
		double m = sample(mountain, x, z, 0);
		double planned = switch (channel) {
			// Vanilla's coast sits near -0.14, and the steep rise across it keeps beaches narrow. Mountains stand on far inland ground, or vanilla won't raise them.
			case CONTINENTS_BASE -> v >= 0 ? -.14 + Math.min(v, .015) * 6 + Math.max(0, v - .015) * 1.1 + m * .55 : Math.max(-.62, -.14 + v * 8);
			case CONTINENTS_WEIGHT -> v >= 0 ? Math.min(.3, .06 + v) : Math.min(.45, .06 - v * 2);
			// The hard ceiling on the sea, so the noise can't raise land inside the safe distance.
			case CONTINENTS_CAP -> v > -COAST_BAND / 2 ? 3 : -.2;
			case TEMPERATURE_BASE -> temperature(x, z);
			case TEMPERATURE_WEIGHT -> .25;
			// Cold air holds little water, and vanilla's snowy plains, ice spikes and gravelly hills all need dry cold.
			case HUMIDITY_BASE -> (sample(wet, x, z, .5f) * 2 - 1) * .8 - .7 * Math.max(0, Math.min(1, (-.15 - temperature(x, z)) / .3));
			case HUMIDITY_WEIGHT -> .35;
			case EROSION_BASE -> -m;
			case EROSION_WEIGHT -> 1 - m * .6;
			case OFFSET_EXTRA -> m * .45;
			// The river bed sits just under sea level, and the ground may rise about 1 block per 3 from the bank. Steep land becomes a gorge.
			case OFFSET_LIMIT -> {
				double edge = riverEdge(x, z);
				yield edge >= RIVER_REACH ? 3 : BED_OFFSET + Math.max(0, edge) * BANK_RISE;
			}
			case RIVER_EDGE, OWNER, EMERALD_SITE -> channel.vanilla;
		};
		return fade(planned, channel.vanilla, share);
	}

	/** The cold border tilts, so it reaches Restena's north but only the top of Thornner. In vanilla's units: cold ends at -0.45 and hot starts at 0.55. */
	public double temperature(double x, double z) {
		List<WorldPlan.Point> points = plan.temperature();
		double q = z - x * plan.coldTilt();
		if (q <= points.getFirst().at()) {
			return points.getFirst().value();
		}
		for (int i = 1; i < points.size(); i++) {
			WorldPlan.Point from = points.get(i - 1);
			WorldPlan.Point to = points.get(i);
			if (q <= to.at()) {
				return from.value() + (to.value() - from.value()) * (q - from.at()) / (to.at() - from.at());
			}
		}
		return points.getLast().value();
	}

	private static double hash(int ix, int iy, int seed) {
		int h = ix * 374761393 + iy * 668265263 + seed * 2147483629;
		h = (h ^ h >>> 13) * 1274126177;
		return ((h ^ h >>> 16) & 0xFFFFFFFFL) / 4294967296.0;
	}

	private static double noise(double x, double y, int seed) {
		int ix = (int) Math.floor(x);
		int iy = (int) Math.floor(y);
		double fx = x - ix;
		double fy = y - iy;
		double u = fx * fx * (3 - 2 * fx);
		double v = fy * fy * (3 - 2 * fy);
		double a = hash(ix, iy, seed);
		double b = hash(ix + 1, iy, seed);
		double c = hash(ix, iy + 1, seed);
		double d = hash(ix + 1, iy + 1, seed);
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}

	private static double fbm(double x, double y, int seed, int octaves) {
		double sum = 0;
		double amp = .5;
		double total = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise(x, y, seed + i * 17) * amp;
			total += amp;
			amp *= .5;
			x *= 2.03;
			y *= 2.03;
		}
		return sum / total;
	}
}
