package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vestiges.world.GlobeFunction;
import dev.vestiges.world.GlobeMap;
import dev.vestiges.world.WorldPlan;
import dev.vestiges.world.WorldPlans;
import dev.vestiges.world.WorldSurvey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loads the world type with the game's own data loader, from vanilla's data and ours, then surveys real terrain values.
 * Set VESTIGES_WORLD_SEEDS to check that many seeds into build/world/report.txt, with a map of the first.
 */
class WorldgenTest {
	private static RegistryAccess.Frozen registries;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		GlobeFunction.register();
		PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
		repository.reload();
		repository.setSelected(List.of("vanilla"));
		List<PackResources> packs = new ArrayList<>(repository.openAllSelected());
		packs.add(new PathPackResources(new PackLocationInfo("vestiges", Component.literal("Vestiges"), PackSource.BUILT_IN, Optional.empty()), Path.of("src/main/resources")));
		try (MultiPackResourceManager data = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
			// Fabric delays freezing the built-in registries until the game starts, which never happens here, so do it the way BuiltInRegistries.freeze does.
			BuiltInRegistries.REGISTRY.freeze();
			for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
				((MappedRegistry<?>) registry).bindAllTagsToEmpty();
				registry.freeze();
			}
			RegistryAccess.Frozen builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
			List<Registry.PendingTags<?>> tags = TagLoader.loadTagsForExistingRegistries(data, builtIn);
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(builtIn, tags);
			registries = RegistryDataLoader.load(data, lookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
		}
	}

	private static NoiseBasedChunkGenerator generator() {
		WorldPreset preset = registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("vestiges", "trenec"))).value();
		return (NoiseBasedChunkGenerator) preset.overworld().orElseThrow().generator();
	}

	private static WorldSurvey survey(long seed) {
		return new WorldSurvey(generator(), registries.lookupOrThrow(Registries.NOISE), seed);
	}

	@Test
	void theLoreLandsAreOnTheMap() {
		assertNotNull(generator());
		assertEquals(5, WorldPlans.current().lands().size());
		assertEquals("Trenec", WorldPlans.current().lands().getFirst().name());
		GlobeMap map = GlobeMap.forSeed(1L);
		assertEquals(0, map.ownerAt(0, 0), "spawn is on Trenec");
		assertEquals(0, map.ownerAt(0, map.dilectaZ()), "Dilecta is on Trenec");
		assertTrue(map.temperature(0, -80000) < -.45 && map.temperature(0, 80000) > .55, "cold north, hot south");
	}

	@Test
	void emeraldSitesFollowThePlan() {
		WorldPlan plan = WorldPlans.current();
		for (long seed = 1; seed <= 5; seed++) {
			var sites = GlobeMap.forSeed(seed).emeraldSites();
			assertTrue(sites.size() >= plan.emeraldMinTotal(), "seed " + seed + " has " + sites.size());
			for (int land = 0; land < plan.lands().size(); land++) {
				int here = land;
				assertTrue(sites.stream().filter(site -> site[2] == here).count() <= plan.emeraldMaxPerLand(), "seed " + seed);
			}
			for (int[] site : sites) {
				assertEquals(site[2], GlobeMap.forSeed(seed).ownerAt(site[0], site[1]), "a site stands on its own land");
			}
		}
	}

	@Test
	void aSavedPlanComesBackTheSame() throws IOException {
		WorldPlan plan = WorldPlans.current();
		assertEquals(plan, WorldPlans.read(new java.io.StringReader(WorldPlans.write(plan))));
		assertEquals(plan.lands().size(), plan.landOrder().size(), "every land has a place in the order");
		plan.landOrder().forEach(plan::landIndex);
	}

	@Test
	void vanillaWorldsAreUntouched() {
		assertTrue(!Files.exists(Path.of("src/main/resources/data/minecraft/worldgen")), "we never replace vanilla's terrain or biome files");
		WorldPreset normal = registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, Identifier.withDefaultNamespace("normal"))).value();
		NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) normal.overworld().orElseThrow().generator();
		assertEquals(Identifier.withDefaultNamespace("overworld"), generator.generatorSettings().unwrapKey().orElseThrow().identifier());
	}

	@Test
	void surveyWhenAsked() throws IOException {
		String seeds = System.getenv("VESTIGES_WORLD_SEEDS");
		if (seeds == null || seeds.isBlank()) {
			return;
		}
		Path out = Path.of("build/world");
		Files.createDirectories(out);
		List<String> lines = new ArrayList<>();
		int passed = 0;
		int count = Integer.parseInt(seeds);
		for (long seed = 1; seed <= count; seed++) {
			long start = System.nanoTime();
			GlobeMap.forSeed(seed * 31);
			long drawn = (System.nanoTime() - start) / 1_000_000;
			WorldSurvey survey = survey(seed);
			if (seed == 1) {
				ImageIO.write(survey.draw(300), "png", out.resolve("seed1.png").toFile());
			}
			WorldSurvey.Report report = survey.check();
			passed += report.passed() ? 1 : 0;
			lines.add(report.line() + ", land " + java.util.Arrays.toString(report.area()) + ", map drawn in " + drawn + " ms");
			lines.add("  rarest: " + report.rarest());
			Files.write(out.resolve("report.txt"), lines);
		}
		lines.add(passed + " of " + count + " seeds passed");
		Files.write(out.resolve("report.txt"), lines);
	}

	/** Over many seeds: does each continent exist, does the plan centre (where the map label goes) stand on it, and how much do size and shape vary? */
	@Test
	void continentsKeepTheirNamesAcrossSeeds() throws IOException {
		var lands = WorldPlans.current().lands();
		List<String> lines = new ArrayList<>();
		int centreOff = 0;
		int total = 0;
		for (long seed = 1; seed <= 20; seed++) {
			GlobeMap map = GlobeMap.forSeed(seed * 31);
			long[] cells = new long[lands.size()];
			double[] sx = new double[lands.size()];
			double[] sz = new double[lands.size()];
			int[] minX = new int[lands.size()];
			int[] maxX = new int[lands.size()];
			int[] minZ = new int[lands.size()];
			int[] maxZ = new int[lands.size()];
			java.util.Arrays.fill(minX, Integer.MAX_VALUE);
			java.util.Arrays.fill(minZ, Integer.MAX_VALUE);
			java.util.Arrays.fill(maxX, Integer.MIN_VALUE);
			java.util.Arrays.fill(maxZ, Integer.MIN_VALUE);
			for (int x = GlobeMap.X_MIN; x < GlobeMap.X_MAX; x += GlobeMap.CELL * 4) {
				for (int z = GlobeMap.Z_MIN; z < GlobeMap.Z_MAX; z += GlobeMap.CELL * 4) {
					int o = map.ownerAt(x, z);
					if (o >= 0 && o < lands.size()) {
						cells[o]++;
						sx[o] += x;
						sz[o] += z;
						minX[o] = Math.min(minX[o], x);
						maxX[o] = Math.max(maxX[o], x);
						minZ[o] = Math.min(minZ[o], z);
						maxZ[o] = Math.max(maxZ[o], z);
					}
				}
			}
			StringBuilder row = new StringBuilder("seed " + seed * 31 + ":");
			for (int i = 0; i < lands.size(); i++) {
				var land = lands.get(i);
				assertTrue(cells[i] > 0, "seed " + seed * 31 + " has no " + land.name());
				boolean centreOn = map.ownerAt(land.x(), land.z()) == i;
				int cx = (int) (sx[i] / cells[i]);
				int cz = (int) (sz[i] / cells[i]);
				boolean meanOn = map.ownerAt(cx, cz) == i;
				total++;
				if (!centreOn) {
					centreOff++;
				}
				row.append(String.format(" | %s area %d km2, %dx%d km, plan centre %s, mean centre %s", land.name(), cells[i] * 16L * GlobeMap.CELL * GlobeMap.CELL / 1_000_000L,
					(maxX[i] - minX[i]) / 1000, (maxZ[i] - minZ[i]) / 1000, centreOn ? "on land" : "IN SEA/OTHER", meanOn ? "on land" : "IN SEA/OTHER"));
			}
			lines.add(row.toString());
		}
		lines.add("plan centre off its continent: " + centreOff + " of " + total);
		Path out = Path.of("build", "world");
		Files.createDirectories(out);
		Files.write(out.resolve("labels.txt"), lines);
	}
}
