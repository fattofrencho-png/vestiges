package dev.vestiges.world;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Where the lore's continents are, for other systems. Only worlds made with the Vestiges world type have them. */
public final class WorldAtlas {
	private static final Identifier SETTINGS = Identifier.fromNamespaceAndPath("vestiges", "trenec");

	private WorldAtlas() {
	}

	public static boolean active(MinecraftServer server) {
		return isPlanned(server.overworld().getChunkSource().getGenerator());
	}

	public static boolean isPlanned(ChunkGenerator chunkGenerator) {
		return chunkGenerator instanceof NoiseBasedChunkGenerator generator
			&& generator.generatorSettings().unwrapKey().map(key -> key.identifier().equals(SETTINGS)).orElse(false);
	}

	/** Size of one square of the coarse map sent to players, in blocks. */
	public static final int GRID_CELL = 2048;
	/** Squares across and down, as {@link #grid} lays them out. */
	public static int gridWidth() {
		return (int) Math.ceil((GlobeMap.X_MAX - GlobeMap.X_MIN - GRID_CELL / 2.0) / GRID_CELL);
	}

	public static int gridHeight() {
		return (int) Math.ceil((GlobeMap.Z_MAX - GlobeMap.Z_MIN - GRID_CELL / 2.0) / GRID_CELL);
	}

	private static MinecraftServer gridFor;
	private static String grid = "";

	/**
	 * Who owns each square of the globe, row by row from the north-west corner: a digit for a continent (its place in the plan), 'i' for an island and
	 * '.' for open sea. Players use it to name the place they are in. Read from the world's own terrain, so every seed gets its own shapes.
	 */
	public static synchronized String grid(MinecraftServer server) {
		if (gridFor != server) {
			var generator = (NoiseBasedChunkGenerator) server.overworld().getChunkSource().getGenerator();
			WorldSurvey survey = new WorldSurvey(generator, server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), server.overworld().getSeed());
			StringBuilder sb = new StringBuilder();
			for (int z = GlobeMap.Z_MIN + GRID_CELL / 2; z < GlobeMap.Z_MAX; z += GRID_CELL) {
				for (int x = GlobeMap.X_MIN + GRID_CELL / 2; x < GlobeMap.X_MAX; x += GRID_CELL) {
					int owner = survey.ownerAt(x, z);
					sb.append(owner == GlobeMap.OCEAN ? '.' : owner == GlobeMap.ISLAND ? 'i' : (char) ('0' + owner));
				}
			}
			grid = sb.toString();
			gridFor = server;
		}
		return grid;
	}

	/** Reads a grid from {@link #grid}: a continent's name (by its place in {@code names}), "an island", "the open sea", or null if the grid does not fit. */
	public static String lookup(String grid, java.util.List<String> names, double x, double z) {
		int w = gridWidth();
		int h = gridHeight();
		if (grid.length() != w * h) {
			return null;
		}
		if (x < GlobeMap.X_MIN || z < GlobeMap.Z_MIN || x >= GlobeMap.X_MAX || z >= GlobeMap.Z_MAX) {
			return "the open sea";
		}
		int ix = Math.min((int) Math.floor((x - GlobeMap.X_MIN) / GRID_CELL), w - 1);
		int iz = Math.min((int) Math.floor((z - GlobeMap.Z_MIN) / GRID_CELL), h - 1);
		char c = grid.charAt(iz * w + ix);
		if (c == '.') {
			return "the open sea";
		}
		if (c == 'i') {
			return "an island";
		}
		int i = c - '0';
		return i >= 0 && i < names.size() ? names.get(i) : null;
	}

	/** The continent a block is on, or null when this world has no continents. */
	public static String continentAt(MinecraftServer server, double x, double z) {
		if (!active(server)) {
			return null;
		}
		var lands = WorldPlans.current().lands();
		String name = lookup(grid(server), lands.stream().map(WorldPlan.Land::name).toList(), x, z);
		return name != null && lands.stream().anyMatch(l -> l.name().equals(name)) ? name : null;
	}

	/** The continent's name, "an island", or "the open sea". */
	public static String placeName(int owner) {
		return owner == GlobeMap.OCEAN ? "the open sea" : owner == GlobeMap.ISLAND ? "an island" : WorldPlans.current().lands().get(owner).name();
	}
}
