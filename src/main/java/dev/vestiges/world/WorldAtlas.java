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

	/** The continent's name, "an island", or "the open sea". */
	public static String placeName(int owner) {
		return owner == GlobeMap.OCEAN ? "the open sea" : owner == GlobeMap.ISLAND ? "an island" : WorldPlans.current().lands().get(owner).name();
	}
}
