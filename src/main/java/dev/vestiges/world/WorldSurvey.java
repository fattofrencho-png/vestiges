package dev.vestiges.world;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSpawnFinder;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Reads a world's terrain without generating chunks, for any seed: draws maps and checks the globe's rules. Runs the game's own samplers and biome
 * picker, so what it reports is what the world would generate. Used by {@code /vestiges world} and by the tests.
 */
public final class WorldSurvey {
	private static final int SEA_LEVEL = 63;
	/** Below the surface, for the cave biomes that never show on top. */
	private static final int CAVE_Y = -40;
	private static final RandomSpreadStructurePlacement VILLAGES = new RandomSpreadStructurePlacement(34, 8, RandomSpreadType.LINEAR, 10387312);
	private static final String[] BANDS = {"cold", "cool", "temperate", "warm", "hot"};

	private final MultiNoiseBiomeSource biomes;
	private final NoiseGeneratorSettings settings;
	private final RandomState random;
	private final Climate.Sampler climate;
	private final DensitySampler.Bound surface;
	private final long seed;
	private final DensitySampler.Bound owners;
	private final DensitySampler.Bound riverEdges;

	public WorldSurvey(NoiseBasedChunkGenerator generator, HolderGetter<NormalNoise> noises, long seed) {
		this.biomes = (MultiNoiseBiomeSource) generator.getBiomeSource();
		this.settings = generator.generatorSettings().value();
		this.random = RandomState.create(noises, seed, settings);
		this.climate = random.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		this.surface = random.getSampler(settings.noiseRouter().chunkSurfaceLevel()).bind(SamplerContext.EMPTY_UNCACHED);
		this.seed = seed;
		this.owners = random.getSampler(new GlobeFunction(GlobeFunction.Channel.OWNER)).bind(SamplerContext.EMPTY_UNCACHED);
		this.riverEdges = random.getSampler(new GlobeFunction(GlobeFunction.Channel.RIVER_EDGE)).bind(SamplerContext.EMPTY_UNCACHED);
	}

	/** Each emerald site once, as "x z", found by scanning the map every 128 blocks. */
	public List<String> emeraldSites() {
		DensitySampler.Bound sites = random.getSampler(new GlobeFunction(GlobeFunction.Channel.EMERALD_SITE)).bind(SamplerContext.EMPTY_UNCACHED);
		List<int[]> found = new ArrayList<>();
		for (int x = GlobeMap.X_MIN; x < GlobeMap.X_MAX; x += 128) {
			for (int z = GlobeMap.Z_MIN; z < GlobeMap.Z_MAX; z += 128) {
				int px = x;
				int pz = z;
				if (sites.sampleValue(x, 0, z) > .5f && found.stream().noneMatch(f -> Math.hypot(f[0] - px, f[1] - pz) < 1000)) {
					found.add(new int[] {x, z});
				}
			}
		}
		return found.stream().map(f -> f[0] + " " + f[1]).toList();
	}

	public int ownerAt(int x, int z) {
		return Math.round(owners.sampleValue(x, 0, z));
	}

	/** Dilecta's place on this world's map: north from Trenec's centre to its coast. */
	public int dilectaZ() {
		int z = 0;
		while (ownerAt(0, z - GlobeMap.CELL) == 0) {
			z -= GlobeMap.CELL;
		}
		return z;
	}

	public int surfaceY(int x, int z) {
		return Math.round(surface.sampleValue(x, 0, z));
	}

	public Holder<Biome> biomeAt(int x, int y, int z) {
		return biomes.getNoiseBiome(climate.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
	}

	/** The biome on top, which is sea level over water. */
	public Holder<Biome> surfaceBiome(int x, int z) {
		return biomeAt(x, Math.max(surfaceY(x, z), SEA_LEVEL), z);
	}

	/** Anything that isn't open sea, so rivers and low ground count as part of the land they cross. */
	public boolean land(int x, int z) {
		return !isWater(surfaceBiome(x, z));
	}

	private static boolean isWater(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
	}

	public BlockPos spawn() {
		return NoiseSpawnFinder.findSpawnPosition(settings.spawnTarget(), random.samplersWithContext(SamplerContext.EMPTY_UNCACHED));
	}

	private int band(int x, int z) {
		float t = Climate.unquantizeCoord(climate.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(SEA_LEVEL), QuartPos.fromBlock(z)).temperature());
		return t < -.45f ? 0 : t < -.15f ? 1 : t < .2f ? 2 : t < .55f ? 3 : 4;
	}

	private static String name(Holder<Biome> biome) {
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
	}

	/** One entry per failed rule, and how much room each biome gets. */
	public Report check() {
		List<String> failures = new ArrayList<>();
		BlockPos spawn = spawn();
		if (Math.hypot(spawn.getX(), spawn.getZ()) > 1500) {
			failures.add("spawn is " + (int) Math.hypot(spawn.getX(), spawn.getZ()) + " blocks from the centre");
		}
		if (ownerAt(spawn.getX(), spawn.getZ()) != 0) {
			failures.add("spawn is not on Trenec");
		}
		if (surfaceY(spawn.getX(), spawn.getZ()) > 130) {
			failures.add("spawn is high up, at " + surfaceY(spawn.getX(), spawn.getZ()));
		}
		if (ownerAt(0, dilectaZ()) != 0) {
			failures.add("Dilecta is not on Trenec");
		}

		WorldPlan plan = WorldPlans.current();
		int lands = plan.lands().size();
		int[] area = new int[lands];
		int[] tallest = new int[lands];
		int[][] bands = new int[lands][BANDS.length];
		Map<String, Integer> room = new HashMap<>();
		Set<Holder<Biome>> found = new HashSet<>();
		int step = 512;
		for (int x = GlobeMap.X_MIN; x <= GlobeMap.X_MAX; x += step) {
			for (int z = GlobeMap.Z_MIN; z <= GlobeMap.Z_MAX; z += step) {
				int owner = ownerAt(x, z);
				if (owner >= 0 && owner < lands) {
					area[owner]++;
					bands[owner][band(x, z)]++;
				}
				int y = surfaceY(x, z);
				if (owner >= 0 && owner < lands) {
					tallest[owner] = Math.max(tallest[owner], y);
				}
				Holder<Biome> top = biomeAt(x, Math.max(y, SEA_LEVEL), z);
				found.add(top);
				room.merge(name(top), 1, Integer::sum);
				for (Holder<Biome> under : List.of(biomeAt(x, CAVE_Y, z), biomeAt(x, y - 8, z), biomeAt(x, y + 32, z))) {
					if (found.add(under)) {
						room.putIfAbsent(name(under), 0);
					}
				}
			}
		}

		for (int i = 1; i < lands; i++) {
			if (area[i] >= area[0]) {
				failures.add(plan.lands().get(i).name() + " is as big as Trenec");
			}
		}
		for (int i = 0; i < lands; i++) {
			int ranges = 0;
			for (int count : bands[i]) {
				ranges += count >= area[i] * .02 ? 1 : 0;
			}
			if (ranges < 2) {
				failures.add(plan.lands().get(i).name() + " has only " + ranges + " temperature range");
			}
		}
		int thornner = plan.landIndex("Thornner");
		int restena = plan.landIndex("Restena");
		int swetren = plan.landIndex("Swetren");
		if (bands[thornner][2] < area[thornner] * .02) {
			failures.add("Thornner has no temperate part");
		}
		if (bands[restena][0] < area[restena] * .02 && bands[swetren][0] < area[swetren] * .02) {
			failures.add("neither Restena nor Swetren touches the cold");
		}

		// Whatever the first pass missed gets finer passes, down to a spot a player could still reach by exploring.
		for (int fine = 256; fine >= 128 && found.size() < biomes.possibleBiomes().size(); fine /= 2) {
			for (int x = GlobeMap.X_MIN; x <= GlobeMap.X_MAX; x += fine) {
				for (int z = GlobeMap.Z_MIN; z <= GlobeMap.Z_MAX; z += fine) {
					int y = surfaceY(x, z);
					found.add(biomeAt(x, Math.max(y, SEA_LEVEL), z));
					found.add(biomeAt(x, CAVE_Y, z));
					found.add(biomeAt(x, y - 8, z));
					found.add(biomeAt(x, y + 32, z));
				}
			}
		}
		Set<String> missing = new TreeSet<>();
		for (Holder<Biome> biome : biomes.possibleBiomes()) {
			if (!found.contains(biome) && !biome.is(Biomes.THE_VOID)) {
				missing.add(name(biome));
			}
		}
		int[] rivers = rivers();
		int coastal = coastalVillages();
		if (rivers[0] == 0 || rivers[1] < rivers[0] * .7) {
			failures.add("only " + rivers[1] + " of " + rivers[0] + " river spots are river");
		}
		if (rivers[2] > rivers[1] * .1) {
			failures.add(rivers[2] + " stray river spots");
		}
		if (coastal < 5) {
			failures.add("only " + coastal + " coastal village spots on Trenec");
		}
		return new Report(seed, failures, missing, room, area, tallest, rivers, coastal, spawn, dilectaZ());
	}

	/** On Trenec: spots on our river lines, how many of those really are river or sea, and river biome far from our lines. */
	private int[] rivers() {
		int on = 0;
		int wet = 0;
		int stray = 0;
		for (int x = -36000; x <= 36000; x += 160) {
			for (int z = -32000; z <= 32000; z += 160) {
				if (ownerAt(x, z) != 0) {
					continue;
				}
				double edge = riverEdges.sampleValue(x, 0, z);
				if (edge < 0) {
					on++;
					Holder<Biome> biome = biomeAt(x, SEA_LEVEL, z);
					wet += biome.is(BiomeTags.IS_RIVER) || isWater(biome) ? 1 : 0;
				} else if (edge > 400 && biomeAt(x, SEA_LEVEL, z).is(BiomeTags.IS_RIVER)) {
					stray++;
				}
			}
		}
		return new int[] {on, wet, stray};
	}

	/** Vanilla's village grid over Trenec: a spot counts if its biome takes villages and open water is within 256 blocks. A site can still fail to fit. */
	private int coastalVillages() {
		int count = 0;
		int grid = VILLAGES.spacing();
		int range = 45000 / 16 / grid;
		for (int gx = -range; gx <= range; gx++) {
			for (int gz = -range; gz <= range; gz++) {
				var chunk = VILLAGES.getPotentialStructureChunk(seed, gx * grid, gz * grid);
				int x = chunk.getMiddleBlockX();
				int z = chunk.getMiddleBlockZ();
				if (ownerAt(x, z) != 0 || !takesVillages(surfaceBiome(x, z))) {
					continue;
				}
				for (int bearing = 0; bearing < 360; bearing += 30) {
					if (!land(x + (int) (256 * Math.sin(Math.toRadians(bearing))), z + (int) (256 * Math.cos(Math.toRadians(bearing))))) {
						count++;
						break;
					}
				}
			}
		}
		return count;
	}

	private static boolean takesVillages(Holder<Biome> biome) {
		return biome.is(BiomeTags.HAS_VILLAGE_PLAINS) || biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)
			|| biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.is(BiomeTags.HAS_VILLAGE_TAIGA);
	}

	public record Report(long seed, List<String> failures, Set<String> missingBiomes, Map<String, Integer> room, int[] area, int[] tallest, int[] rivers, int coastalVillages, BlockPos spawn, int dilectaZ) {
		public boolean passed() {
			return failures.isEmpty() && missingBiomes.isEmpty();
		}

		/** The surface biomes with the least room, from a sample every 512 blocks. */
		public String rarest() {
			return room.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue)).limit(12).map(e -> e.getKey() + " " + e.getValue()).toList().toString();
		}

		public String line() {
			return "seed " + seed + ": " + (passed() ? "ok" : "FAILED") + ", spawn " + spawn.getX() + " " + spawn.getZ() + ", Dilecta z " + dilectaZ + ", coastal villages " + coastalVillages + ", tallest " + java.util.Arrays.toString(tallest) + ", rivers " + rivers[1] + " of " + rivers[0] + ", stray " + rivers[2]
				+ (failures.isEmpty() ? "" : ", " + failures) + (missingBiomes.isEmpty() ? "" : ", missing " + missingBiomes);
		}
	}

	/** The whole globe and a little past it: one pixel is {@code blocksPerPixel} blocks, north is up. */
	public BufferedImage draw(int blocksPerPixel) {
		int x0 = GlobeMap.X_MIN - 15000;
		int z0 = GlobeMap.Z_MIN - 15000;
		int w = (GlobeMap.X_MAX - GlobeMap.X_MIN + 30000) / blocksPerPixel;
		int h = (GlobeMap.Z_MAX - GlobeMap.Z_MIN + 30000) / blocksPerPixel;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Map<Holder<Biome>, Integer> colours = new HashMap<>();
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int x = x0 + px * blocksPerPixel;
				int z = z0 + py * blocksPerPixel;
				int y = surfaceY(x, z);
				Holder<Biome> biome = biomeAt(x, Math.max(y, SEA_LEVEL), z);
				image.setRGB(px, py, shade(colours.computeIfAbsent(biome, WorldSurvey::colour), y, isWater(biome)));
			}
		}
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(255, 255, 255, 230));
		for (WorldPlan.Land land : WorldPlans.current().lands()) {
			g.drawString(land.name(), (land.x() - x0) / blocksPerPixel - g.getFontMetrics().stringWidth(land.name()) / 2, (land.z() - z0) / blocksPerPixel);
		}
		g.fillOval(-x0 / blocksPerPixel - 2, (dilectaZ() - z0) / blocksPerPixel - 2, 5, 5);
		g.drawString("Dilecta", -x0 / blocksPerPixel + 5, (dilectaZ() - z0) / blocksPerPixel + 4);
		g.dispose();
		return image;
	}

	private static int shade(int colour, int y, boolean water) {
		float light = water ? 0.55f + 0.45f * Math.clamp((y + 10) / 73f, 0f, 1f) : 0.8f + 0.5f * Math.clamp((y - SEA_LEVEL) / 110f, 0f, 1f);
		int r = Math.min(255, (int) ((colour >> 16 & 255) * light));
		int g = Math.min(255, (int) ((colour >> 8 & 255) * light));
		int b = Math.min(255, (int) ((colour & 255) * light));
		return r << 16 | g << 8 | b;
	}

	/** Map colours by what a biome is, in the style of the usual seed maps. */
	private static int colour(Holder<Biome> biome) {
		String n = name(biome);
		if (n.contains("frozen_ocean") || n.equals("frozen_river")) return 0xA8C8E8;
		if (n.contains("deep") && n.contains("ocean")) return 0x1A3F7A;
		if (n.contains("warm_ocean")) return 0x2A8FB0;
		if (n.contains("ocean")) return 0x2A5CA8;
		if (n.equals("river")) return 0x3F76E4;
		if (n.contains("snowy_beach")) return 0xE4E8D8;
		if (n.equals("beach")) return 0xE8DCA0;
		if (n.equals("stony_shore")) return 0x8A8A8A;
		if (n.equals("mushroom_fields")) return 0xB070B0;
		if (n.contains("badlands")) return 0xC8693A;
		if (n.equals("desert")) return 0xE0CF88;
		if (n.equals("windswept_savanna")) return 0x9A9050;
		if (n.contains("savanna")) return 0xB5A650;
		if (n.contains("jungle")) return 0x2F8F2F;
		if (n.equals("mangrove_swamp")) return 0x3F7F50;
		if (n.equals("swamp")) return 0x4F7050;
		if (n.equals("ice_spikes")) return 0xC8E8F4;
		if (n.equals("snowy_taiga") || n.equals("grove")) return 0xB8D0C8;
		if (n.equals("snowy_plains")) return 0xE8F0F0;
		if (n.equals("snowy_slopes")) return 0xD8E4EC;
		if (n.equals("frozen_peaks") || n.equals("jagged_peaks")) return 0xF0F4FA;
		if (n.equals("stony_peaks")) return 0x9A9A98;
		if (n.startsWith("windswept")) return 0x7A8A78;
		if (n.contains("spruce") || n.contains("pine") || n.equals("taiga")) return 0x3F6F58;
		if (n.equals("dark_forest") || n.equals("pale_garden")) return 0x2F5A2A;
		if (n.equals("cherry_grove")) return 0xE8B0C8;
		if (n.equals("meadow")) return 0x8FBF60;
		if (n.contains("birch")) return 0x6FA868;
		if (n.equals("flower_forest")) return 0x5FA848;
		if (n.equals("forest")) return 0x4F8F40;
		if (n.equals("sunflower_plains")) return 0xA0C860;
		return 0x8DB360;
	}
}
