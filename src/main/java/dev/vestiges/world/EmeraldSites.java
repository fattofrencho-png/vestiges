package dev.vestiges.world;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/** In Vestiges worlds emerald ore only exists at the sites on the {@link GlobeMap}, as rich veins. The two mixins do nothing in any other world. */
public final class EmeraldSites {
	private static final int VEINS_PER_CHUNK = 16;
	private static final int VEIN_SIZE = 6;
	private static final int LOWEST = -32;
	private static final int HIGHEST = 120;
	private static final Map<RandomState, DensitySampler.Bound> SITES = Collections.synchronizedMap(new WeakHashMap<>());
	private static OreFeature veins;

	private EmeraldSites() {
	}

	private static OreFeature veins() {
		if (veins == null) {
			veins = new OreFeature(List.of(
				new BlockReplacement(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), Blocks.EMERALD_ORE.defaultBlockState()),
				new BlockReplacement(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState())), VEIN_SIZE);
		}
		return veins;
	}

	/** Vanilla's emerald ore stays out of Vestiges worlds. Ours is allowed through. */
	public static boolean blocks(OreFeature feature) {
		return WorldPlans.active() && feature != veins
			&& feature.targetStates().stream().anyMatch(target -> target.state().is(Blocks.EMERALD_ORE));
	}

	/** Called once per chunk after vanilla's decoration. */
	public static void decorate(WorldGenLevel level, ChunkGenerator generator, ChunkAccess chunk) {
		if (!WorldPlans.active() || level.getLevel().dimension() != Level.OVERWORLD) {
			return;
		}
		RandomState state = level.getLevel().getChunkSource().randomState();
		DensitySampler.Bound sites = SITES.computeIfAbsent(state, r -> r.getSampler(new GlobeFunction(GlobeFunction.Channel.EMERALD_SITE)).bind(SamplerContext.EMPTY_UNCACHED));
		int x = chunk.getPos().getMiddleBlockX();
		int z = chunk.getPos().getMiddleBlockZ();
		if (sites.sampleValue(x, 0, z) < .5f) {
			return;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ chunk.getPos().x() * 341873128712L ^ chunk.getPos().z() * 132897987541L);
		for (int i = 0; i < VEINS_PER_CHUNK; i++) {
			veins().place(level, generator, random, new BlockPos(x - 8 + random.nextInt(16), LOWEST + random.nextInt(HIGHEST - LOWEST), z - 8 + random.nextInt(16)));
		}
	}
}
