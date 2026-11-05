package dev.vestiges.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.vestiges.Vestiges;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Places a structure at one site from the {@link WorldPlan}. Structure lists apply to every world in a game, so this answers no unless the running
 * world uses a Vestiges world type: plain vanilla worlds never get these structures.
 */
public record PlanSite(String village) implements StructurePlacement {
	public static final MapCodec<PlanSite> CODEC = Codec.STRING.fieldOf("village").xmap(PlanSite::new, PlanSite::village);
	/** Inland from the coast, so the village's centre stands on land. */
	private static final int INLAND = 700;
	private static final int INNER_DISTRICTS = 5;
	private static final int OUTER_DISTRICTS = 3;
	private static final int INNER_RADIUS = 340;
	private static final int OUTER_RADIUS = 640;
	private static final Map<RandomState, List<ChunkPos>> SITES = Collections.synchronizedMap(new WeakHashMap<>());

	public static void register() {
		if (!BuiltInRegistries.STRUCTURE_PLACEMENT.containsKey(Vestiges.id("plan_site"))) {
			Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Vestiges.id("plan_site"), CODEC);
		}
	}

	/**
	 * The capital's chunks: north from Trenec's centre to its coast, back inland for the main village, then districts around it. One village can't pass
	 * about 257 blocks, so the city is several. A district that would stand in the sea or a river is left out.
	 */
	public static List<ChunkPos> capital(RandomState random) {
		return SITES.computeIfAbsent(random, r -> {
			DensitySampler.Bound owners = r.getSampler(new GlobeFunction(GlobeFunction.Channel.OWNER)).bind(SamplerContext.EMPTY_UNCACHED);
			DensitySampler.Bound rivers = r.getSampler(new GlobeFunction(GlobeFunction.Channel.RIVER_EDGE)).bind(SamplerContext.EMPTY_UNCACHED);
			int coast = 0;
			while (Math.round(owners.sampleValue(0, 0, coast - GlobeMap.CELL)) == 0) {
				coast -= GlobeMap.CELL;
			}
			int z = coast + INLAND;
			List<ChunkPos> sites = new ArrayList<>();
			sites.add(new ChunkPos(0, z >> 4));
			// The turn comes from the coast's place, so the layout changes with the seed.
			double turn = Math.floorMod(coast, 360) * Math.PI / 180;
			for (int i = 0; i < INNER_DISTRICTS + OUTER_DISTRICTS; i++) {
				boolean inner = i < INNER_DISTRICTS;
				// The outer ones spread to the south, away from the sea.
				double angle = inner ? turn + i * Math.PI * 2 / INNER_DISTRICTS : Math.PI / 2 + (i - INNER_DISTRICTS - 1) * .75;
				int distance = inner ? INNER_RADIUS : OUTER_RADIUS;
				int x = (int) (Math.cos(angle) * distance);
				int dz = z + (int) (Math.sin(angle) * distance);
				if (Math.round(owners.sampleValue(x, 0, dz)) == 0 && rivers.sampleValue(x, 0, dz) > 130) {
					sites.add(new ChunkPos(x >> 4, dz >> 4));
				}
			}
			return List.copyOf(sites);
		});
	}

	@Override
	public boolean isStructureChunk(ChunkGeneratorStructureState state, int x, int z) {
		if (!WorldPlans.active()) {
			return false;
		}
		return capital(state.randomState()).contains(new ChunkPos(x, z));
	}

	@Override
	public MapCodec<PlanSite> codec() {
		return CODEC;
	}
}
