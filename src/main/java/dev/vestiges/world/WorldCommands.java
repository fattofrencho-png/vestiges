package dev.vestiges.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.vestiges.Vestiges;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;

/** /vestiges world where | sites | preview [seed] | check [seeds]. The last two read terrain values for any seed, generate no chunks and run off the server thread. */
public final class WorldCommands {
	private WorldCommands() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("world")
			.then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
			.then(Commands.literal("sites").executes(ctx -> sites(ctx.getSource())))
			.then(Commands.literal("preview")
				.executes(ctx -> preview(ctx.getSource(), ctx.getSource().getServer().overworld().getSeed()))
				.then(Commands.argument("seed", LongArgumentType.longArg())
					.executes(ctx -> preview(ctx.getSource(), LongArgumentType.getLong(ctx, "seed")))))
			.then(Commands.literal("check")
				.executes(ctx -> check(ctx.getSource(), 1))
				.then(Commands.argument("seeds", IntegerArgumentType.integer(1, 200))
					.executes(ctx -> check(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seeds")))));
	}

	private static boolean active(CommandSourceStack src) {
		boolean active = WorldAtlas.active(src.getServer());
		if (!active) {
			src.sendFailure(Component.literal("Vestiges: this world doesn't use the Vestiges world type."));
		}
		return active;
	}

	private static WorldSurvey survey(MinecraftServer server, long seed) {
		NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) server.overworld().getChunkSource().getGenerator();
		return new WorldSurvey(generator, server.registryAccess().lookupOrThrow(Registries.NOISE), seed);
	}

	private static int where(CommandSourceStack src) {
		if (!active(src)) {
			return 0;
		}
		BlockPos pos = BlockPos.containing(src.getPosition());
		String place = WorldAtlas.placeName(survey(src.getServer(), src.getServer().overworld().getSeed()).ownerAt(pos.getX(), pos.getZ()));
		src.sendSuccess(() -> Component.literal("Vestiges: you are in " + place), false);
		return 1;
	}

	private static int sites(CommandSourceStack src) {
		if (!active(src)) {
			return 0;
		}
		MinecraftServer server = src.getServer();
		inBackground(src, say -> say.accept("Vestiges: emerald sites near " + survey(server, server.overworld().getSeed()).emeraldSites()));
		return 1;
	}

	private static int preview(CommandSourceStack src, long seed) {
		if (!active(src)) {
			return 0;
		}
		MinecraftServer server = src.getServer();
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("vestiges").resolve("map-" + seed + ".png");
		src.sendSuccess(() -> Component.literal("Vestiges: drawing the map of seed " + seed + "..."), false);
		inBackground(src, say -> {
			try {
				Files.createDirectories(file.getParent());
				ImageIO.write(survey(server, seed).draw(300), "png", file.toFile());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			say.accept("Vestiges: map saved to " + file);
		});
		return 1;
	}

	/** Starts from the world's own seed, then the seeds after it. */
	private static int check(CommandSourceStack src, int seeds) {
		if (!active(src)) {
			return 0;
		}
		MinecraftServer server = src.getServer();
		long first = server.overworld().getSeed();
		src.sendSuccess(() -> Component.literal("Vestiges: checking " + seeds + " seeds..."), false);
		inBackground(src, say -> {
			int passed = 0;
			for (long seed = first; seed < first + seeds; seed++) {
				WorldSurvey.Report report = survey(server, seed).check();
				passed += report.passed() ? 1 : 0;
				say.accept("Vestiges: " + report.line());
			}
			say.accept("Vestiges: " + passed + " of " + seeds + " seeds passed");
		});
		return 1;
	}

	private static void inBackground(CommandSourceStack src, Consumer<Consumer<String>> work) {
		MinecraftServer server = src.getServer();
		Consumer<String> say = text -> server.execute(() -> src.sendSuccess(() -> Component.literal(text), false));
		CompletableFuture.runAsync(() -> work.accept(say)).exceptionally(e -> {
			Vestiges.LOGGER.error("Vestiges: world survey failed", e);
			server.execute(() -> src.sendFailure(Component.literal("Vestiges: world survey failed, see the log.")));
			return null;
		});
	}
}
