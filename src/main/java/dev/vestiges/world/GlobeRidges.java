package dev.vestiges.world;

import com.mojang.serialization.MapCodec;
import dev.vestiges.Vestiges;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Vanilla's ridges noise, steered by the {@link GlobeMap}: vanilla makes a river where this is near 0, so it goes to 0 on our rivers and stays away from 0 elsewhere. */
public record GlobeRidges(DensityFunction input) implements DensityFunction {
	public static final MapCodec<GlobeRidges> CODEC = DensityFunction.CODEC.fieldOf("input").xmap(GlobeRidges::new, GlobeRidges::input);

	public static void register() {
		if (!BuiltInRegistries.DENSITY_FUNCTION_TYPE.containsKey(Vestiges.id("globe_ridges"))) {
			Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Vestiges.id("globe_ridges"), CODEC);
		}
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		return new Sampler(input.compileSampler(context), GlobeMap.forSeed(context.createRandom(Vestiges.id("globe")).nextLong()));
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return new GlobeRidges(rule.rewrite(input));
	}

	@Override
	public Interval range() {
		return input.range();
	}

	@Override
	public int domainAxes() {
		return input.domainAxes() | AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<GlobeRidges> codec() {
		return CODEC;
	}

	private record Sampler(DensitySampler input, GlobeMap map) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
			DensitySampler.sampleVolumeNaive(context, buffer, volume, this);
		}

		@Override
		public float sampleValue(SamplerContext context, int x, int y, int z) {
			return (float) map.ridges(input.sampleValue(context, x, y, z), x, z);
		}
	}
}
