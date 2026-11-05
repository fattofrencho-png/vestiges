package dev.vestiges.gametest;

import dev.vestiges.world.GlobeMap;
import dev.vestiges.world.WorldAtlas;
import dev.vestiges.world.WorldSurvey;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * VESTIGES_TOUR=world: picks Vanilla Biome RPG World on the real Create World screen, then photographs many places from above, once the land has
 * finished loading. Writes screenshots/world_map.png with a numbered mark for each shot and a line for the way it faces, and world_tour.txt with the facts.
 */
final class WorldTour {
	private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TYPE = ResourceKey.create(Registries.WORLD_PRESET, dev.vestiges.Vestiges.id("trenec"));
	private static final int RENDER_DISTANCE = 32;
	private static final int BLOCKS_PER_PIXEL = 200;

	/** yaw: 0 faces south, 90 west, 180 north, -90 east. */
	private record Spot(String name, int x, int z, int yaw) {
	}

	private WorldTour() {
	}

	/** VESTIGES_TOUR=capital: no pictures, only whether Dilecta exists in a real world and how big it is. */
	static void capital(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> {
			WorldCreationUiState state = ((CreateWorldScreen) mc.gui.screen()).getUiState();
			state.setSeed("vestiges");
			state.setWorldType(state.getNormalPresetList().stream().filter(t -> t.preset() != null && t.preset().is(TYPE)).findFirst().orElseThrow());
		});
		ctx.waitTicks(5);
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 6000);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		System.out.println("[world] " + server.computeOnServer(s -> {
			var level = s.overworld();
			var sites = dev.vestiges.world.PlanSite.capital(level.getChunkSource().randomState());
			var structure = s.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(dev.vestiges.Vestiges.id("dilecta"));
			int built = 0;
			int pieces = 0;
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (var site : sites) {
				var start = level.getChunk(site.x(), site.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
				if (start != null && start.isValid()) {
					var box = start.getBoundingBox();
					built++;
					pieces += start.getPieces().size();
					minX = Math.min(minX, box.minX());
					minZ = Math.min(minZ, box.minZ());
					maxX = Math.max(maxX, box.maxX());
					maxZ = Math.max(maxZ, box.maxZ());
				}
			}
			var emerald = level.getChunkSource().randomState().getSampler(new dev.vestiges.world.GlobeFunction(dev.vestiges.world.GlobeFunction.Channel.EMERALD_SITE)).bind(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
			String ore = "no emerald site found";
			search:
			for (int x = GlobeMap.X_MIN; x < GlobeMap.X_MAX; x += 128) {
				for (int z = GlobeMap.Z_MIN; z < GlobeMap.Z_MAX; z += 128) {
					if (emerald.sampleValue(x, 0, z) > .5f) {
						ore = "emerald blocks at site " + x + " " + z + ": " + emeralds(level, x, z) + ", 1 chunk outside it: " + emeralds(level, x + 400, z);
						break search;
					}
				}
			}
			return ore + ", plan active " + dev.vestiges.world.WorldPlans.active() + ", " + built + " of " + sites.size() + " villages, " + pieces + " pieces, " + (maxX - minX) + " x " + (maxZ - minZ) + " blocks";
		}));
		System.out.println("[world] finished");
	}

	private static int emeralds(net.minecraft.server.level.ServerLevel level, int x, int z) {
		var chunk = level.getChunk(x >> 4, z >> 4);
		int count = 0;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int y = -64; y < 320; y++) {
					var state = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + dx, y, chunk.getPos().getMinBlockZ() + dz));
					count += state.is(net.minecraft.world.level.block.Blocks.EMERALD_ORE) || state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE) ? 1 : 0;
				}
			}
		}
		return count;
	}

	static void run(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1600, 900);
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
		});
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> {
			WorldCreationUiState state = ((CreateWorldScreen) mc.gui.screen()).getUiState();
			state.setSeed("vestiges");
			state.setWorldType(state.getNormalPresetList().stream().filter(t -> t.preset() != null && t.preset().is(TYPE)).findFirst().orElseThrow());
		});
		ctx.waitTicks(5);
		long start = System.currentTimeMillis();
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 6000);
		System.out.println("[world] world ready in " + (System.currentTimeMillis() - start) + " ms");
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		WorldSurvey survey = ctx.computeOnClient(mc -> {
			var s = mc.getSingleplayerServer();
			return new WorldSurvey((NoiseBasedChunkGenerator) s.overworld().getChunkSource().getGenerator(), s.registryAccess().lookupOrThrow(Registries.NOISE), s.overworld().getSeed());
		});
		BlockPos spawn = ctx.computeOnClient(mc -> mc.player.blockPosition());
		System.out.println("[world] spawn " + spawn + " on " + WorldAtlas.placeName(survey.ownerAt(spawn.getX(), spawn.getZ())));
		server.runCommand("weather clear");
		server.runCommand("gamemode spectator @p");

		int dilecta = survey.dilectaZ();
		List<Spot> spots = List.of(
			new Spot("spawn", spawn.getX(), spawn.getZ(), 180),
			new Spot("dilecta", 0, dilecta + 1500, 180),
			onLand(survey, new Spot("trenec_west", -18000, 2000, 90), 0),
			onLand(survey, new Spot("trenec_east", 18000, 2000, -90), 0),
			onLand(survey, new Spot("trenec_south", 0, 22000, 0), 0),
			onLand(survey, new Spot("thornner_south", -4000, -44000, 180), 1),
			onLand(survey, new Spot("thornner_north", -4000, -66000, 180), 1),
			onLand(survey, new Spot("restena_north", 62000, -36000, 180), 2),
			onLand(survey, new Spot("restena_south", 62000, -10000, 0), 2),
			onLand(survey, new Spot("swetren_north", -75000, -14000, 180), 4),
			onLand(survey, new Spot("swetren_south", -75000, 24000, 0), 4),
			onLand(survey, new Spot("ruthenos_west", -14000, 62000, 90), 3),
			onLand(survey, new Spot("ruthenos_east", 24000, 62000, -90), 3),
			new Spot("globe_edge", 118000, 0, -90));
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			lines.add(visit(ctx, server, survey, i + 1, spots.get(i)));
		}
		ctx.runOnClient(mc -> writeReport(survey, spots, lines));
		System.out.println("[world] finished");
	}

	/** Coasts move with the seed, so a spot walks outward in rings until it is on the continent it is meant to show. */
	private static Spot onLand(WorldSurvey survey, Spot s, int owner) {
		for (int r = 0; r <= 24000; r += 500) {
			for (int bearing = 0; bearing < 360; bearing += 15) {
				int x = s.x + (int) (r * Math.sin(Math.toRadians(bearing)));
				int z = s.z + (int) (r * Math.cos(Math.toRadians(bearing)));
				if (survey.ownerAt(x, z) == owner) {
					return new Spot(s.name, x, z, s.yaw);
				}
			}
		}
		return s;
	}

	private static String visit(ClientGameTestContext ctx, TestServerContext server, WorldSurvey survey, int number, Spot s) {
		server.runCommand("time set noon");
		server.runCommand("tp @p " + s.x + " 300 " + s.z + " " + s.yaw + " 18");
		ctx.waitFor(mc -> mc.player.blockPosition().getX() == s.x && mc.level.getChunkSource().hasChunk(s.x >> 4, s.z >> 4), 2400);
		// Land is ready once the loaded chunk count stops growing, with a floor for drawing and a ceiling so a slow spot can't stall the tour.
		int last = -1;
		for (int waited = 0; waited < 1400; waited += 100) {
			ctx.waitTicks(100);
			int now = ctx.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
			if (waited >= 400 && now == last) {
				break;
			}
			last = now;
		}
		// Vanilla draws about 500 blocks at most, so a higher camera only sees less. This height shows the most land.
		int ground = server.computeOnServer(l -> l.overworld().getChunk(s.x >> 4, s.z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x & 15, s.z & 15));
		server.runCommand("tp @p " + s.x + " " + (Math.max(ground, 63) + 110) + " " + s.z + " " + s.yaw + " 18");
		server.runCommand("time set noon");
		ctx.waitTicks(200);
		int y = survey.surfaceY(s.x, s.z);
		String biome = survey.biomeAt(s.x, Math.max(63, y), s.z).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
		String line = number + " " + s.name + ": " + WorldAtlas.placeName(survey.ownerAt(s.x, s.z)) + ", x " + s.x + " z " + s.z + ", ground about " + y + ", " + biome + ", " + last + " chunks";
		System.out.println("[world] " + line);
		ctx.takeScreenshot(String.format("world_%02d_%s", number, s.name));
		return line;
	}

	private static void writeReport(WorldSurvey survey, List<Spot> spots, List<String> lines) {
		BufferedImage map = survey.draw(BLOCKS_PER_PIXEL);
		Graphics2D g = map.createGraphics();
		int x0 = GlobeMap.X_MIN - 15000;
		int z0 = GlobeMap.Z_MIN - 15000;
		g.setStroke(new java.awt.BasicStroke(2));
		for (int i = 0; i < spots.size(); i++) {
			Spot s = spots.get(i);
			int px = (s.x - x0) / BLOCKS_PER_PIXEL;
			int pz = (s.z - z0) / BLOCKS_PER_PIXEL;
			double yaw = Math.toRadians(s.yaw);
			g.setColor(Color.YELLOW);
			g.drawLine(px, pz, px - (int) (Math.sin(yaw) * 30), pz + (int) (Math.cos(yaw) * 30));
			g.setColor(Color.BLACK);
			g.fillOval(px - 10, pz - 10, 20, 20);
			g.setColor(Color.YELLOW);
			g.drawOval(px - 10, pz - 10, 20, 20);
			String n = String.valueOf(i + 1);
			g.drawString(n, px - g.getFontMetrics().stringWidth(n) / 2, pz + 5);
		}
		g.dispose();
		try {
			Path dir = Path.of("screenshots");
			Files.createDirectories(dir);
			ImageIO.write(map, "png", dir.resolve("world_map.png").toFile());
			Files.write(dir.resolve("world_tour.txt"), lines);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
