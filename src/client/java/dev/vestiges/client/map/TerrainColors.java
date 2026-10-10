package dev.vestiges.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vestiges.Vestiges;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Map colours "seen from above", like JourneyMap: the average colour of each block's top-face texture, tinted by biome when the block is
 * tinted in game (grass, leaves, water). Texture averages and per-state top faces are cached; everything runs on the client thread.
 */
public final class TerrainColors {
	private record Top(int color, int tintIndex) {
	}

	private static final Map<Identifier, Integer> TEXTURE_AVERAGE = new HashMap<>();
	private static final Map<BlockState, Top> TOP = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create(42L);
	private static final int LAVA = 0xFFD2621C;

	private TerrainColors() {
	}

	/** Clears the caches, e.g. after a resource pack change. */
	public static void reset() {
		TEXTURE_AVERAGE.clear();
		TOP.clear();
	}

	/** Opaque ARGB colour of a block as seen from above at {@code pos}. */
	public static int color(ClientLevel level, BlockState state, BlockPos pos) {
		if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource() && state.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
			return 0xFF000000 | multiply(0xFF8C8C8C, level.getBlockTint(pos, BiomeColors.WATER_COLOR_RESOLVER));
		}
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return LAVA;
		}
		Top top = TOP.computeIfAbsent(state, TerrainColors::computeTop);
		int c = top.color();
		if (top.tintIndex() >= 0) {
			List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(state);
			if (top.tintIndex() < sources.size()) {
				try {
					c = multiply(c, sources.get(top.tintIndex()).colorInWorld(state, level, pos));
				} catch (RuntimeException ignored) {
					// A tint source that needs more context than a map scan gives: keep the untinted colour.
				}
			}
		}
		return 0xFF000000 | c;
	}

	private static Top computeTop(BlockState state) {
		try {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RANDOM, parts);
			BakedQuad best = null;
			for (BlockStateModelPart part : parts) {
				for (BakedQuad q : part.getQuads(Direction.UP)) {
					best = q;
					break;
				}
				if (best == null) {
					for (BakedQuad q : part.getQuads(null)) {
						if (q.direction() == Direction.UP) {
							best = q;
							break;
						}
					}
				}
				if (best != null) {
					break;
				}
			}
			if (best != null) {
				int avg = average(best.materialInfo().sprite());
				return new Top(avg, best.materialInfo().isTinted() ? best.materialInfo().tintIndex() : -1);
			}
			// Cross models (flowers, grass), torches...: use the particle texture.
			TextureAtlasSprite particle = model.particleMaterial().sprite();
			boolean tinted = !Minecraft.getInstance().getBlockColors().getTintSources(state).isEmpty();
			return new Top(average(particle), tinted ? 0 : -1);
		} catch (RuntimeException e) {
			return new Top(0x7F7F7F, -1);
		}
	}

	private static int average(TextureAtlasSprite sprite) {
		Identifier name = sprite.contents().name();
		return TEXTURE_AVERAGE.computeIfAbsent(name, TerrainColors::readAverage);
	}

	private static int readAverage(Identifier name) {
		Identifier file = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
		if (res.isEmpty()) {
			return 0x7F7F7F;
		}
		try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
			long r = 0, g = 0, b = 0, n = 0;
			int w = img.getWidth();
			int h = img.getHeight();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = img.getPixel(x, y);
					if ((argb >>> 24) < 32) {
						continue;
					}
					r += (argb >> 16) & 0xFF;
					g += (argb >> 8) & 0xFF;
					b += argb & 0xFF;
					n++;
				}
			}
			if (n == 0) {
				return 0x7F7F7F;
			}
			return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
		} catch (Exception e) {
			Vestiges.LOGGER.debug("Vestiges map: could not average {}", file, e);
			return 0x7F7F7F;
		}
	}

	static int multiply(int a, int b) {
		int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
		int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
		int bl = (a & 0xFF) * (b & 0xFF) / 255;
		return r << 16 | g << 8 | bl;
	}
}
