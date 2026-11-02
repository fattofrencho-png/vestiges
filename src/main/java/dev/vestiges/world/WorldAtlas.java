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

	/** The continent's name, "an island", or "the open sea". */
	public static String placeName(int owner) {
		return owner == GlobeMap.OCEAN ? "the open sea" : owner == GlobeMap.ISLAND ? "an island" : WorldPlans.current().lands().get(owner).name();
	}
}
