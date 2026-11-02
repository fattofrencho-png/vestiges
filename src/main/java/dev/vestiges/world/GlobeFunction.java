package dev.vestiges.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.vestiges.Vestiges;
import java.util.Arrays;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Lets vanilla's terrain chain read the {@link GlobeMap}: {@code {"type": "vestiges:globe", "channel": "continents_base"}}. The map follows the world's seed. */
public record GlobeFunction(Channel channel) implements DensityFunction {
	/** Each with the value that leaves vanilla's noise untouched, used past the globe. */
	public enum Channel {
		CONTINENTS_BASE(0), CONTINENTS_WEIGHT(1), CONTINENTS_CAP(3),
		TEMPERATURE_BASE(0), TEMPERATURE_WEIGHT(1),
		HUMIDITY_BASE(0), HUMIDITY_WEIGHT(1),
		EROSION_BASE(0), EROSION_WEIGHT(1),
		/** Extra height along ranges: 1.0 is about 128 blocks. */
		OFFSET_EXTRA(0),
		/** A ceiling on the ground near rivers, so water can reach them. */
		OFFSET_LIMIT(3),
		/** Blocks from a spot to the nearest river bank. Not terrain: the survey reads it. */
		RIVER_EDGE(9999),
		/** 1 inside an emerald site. Not terrain: the ore placement reads it. */
		EMERALD_SITE(0),
		/** Which continent a spot belongs to. Not terrain: this is how the survey reads the world's own map. */
		OWNER(GlobeMap.OCEAN);

		final double vanilla;

		Channel(double vanilla) {
			this.vanilla = vanilla;
		}

		String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static final Codec<Channel> CHANNEL_CODEC = Codec.STRING.xmap(
		id -> Arrays.stream(Channel.values()).filter(c -> c.id().equals(id)).findFirst().orElseThrow(), Channel::id);
	public static final MapCodec<GlobeFunction> CODEC = CHANNEL_CODEC.fieldOf("channel").xmap(GlobeFunction::new, GlobeFunction::channel);

	public static void register() {
		GlobeRidges.register();
		PlanSite.register();
		if (!BuiltInRegistries.DENSITY_FUNCTION_TYPE.containsKey(Vestiges.id("globe"))) {
			Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Vestiges.id("globe"), CODEC);
		}
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		return new Sampler(channel, GlobeMap.forSeed(context.createRandom(Vestiges.id("globe")).nextLong()));
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.of(-4, 4);
	}

	@Override
	public int domainAxes() {
		return AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<GlobeFunction> codec() {
		return CODEC;
	}

	private record Sampler(Channel channel, GlobeMap map) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
			DensitySampler.sampleVolumeNaive(context, buffer, volume, this);
		}

		@Override
		public float sampleValue(SamplerContext context, int x, int y, int z) {
			return (float) map.value(channel, x, z);
		}
	}
}
