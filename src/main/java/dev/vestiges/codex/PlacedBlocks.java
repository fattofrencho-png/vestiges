package dev.vestiges.codex;

import com.mojang.serialization.Codec;
import dev.vestiges.Vestiges;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Blocks placed by players, remembered per chunk (M4, approved): breaking them never counts for the Codex, and settlements (M5) count only
 * player-placed blocks for renown. Moved with pistons and falling blocks so moving a block does not make it "natural".
 */
public final class PlacedBlocks {
	/** The packed positions of player-placed blocks in one chunk. */
	public static final class Placed {
		final LongOpenHashSet positions;

		Placed() {
			this(new LongOpenHashSet());
		}

		Placed(LongOpenHashSet positions) {
			this.positions = positions;
		}
	}

	private static final Codec<Placed> CODEC = Codec.LONG_STREAM.xmap(s -> new Placed(new LongOpenHashSet(s.toArray())),
		p -> Arrays.stream(p.positions.toLongArray()));

	public static final AttachmentType<Placed> PLACED = AttachmentRegistry.create(Vestiges.id("placed_blocks"), b -> b.persistent(CODEC));
	/** On a falling block that left a player-placed spot, so its landing spot is marked too. */
	public static final AttachmentType<Boolean> FALLING = AttachmentRegistry.create(Vestiges.id("falling_placed"),
		b -> b.persistent(Codec.BOOL));

	private PlacedBlocks() {
	}

	/**
	 * Called at startup: the attachment types must exist before the first chunk loads, or the game drops the saved positions as "unknown
	 * attachment type" (that happened before this was called, so blocks placed before a restart were forgotten).
	 */
	public static void init() {
	}

	public static void mark(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Placed placed = chunk.getAttached(PLACED);
		if (placed == null) {
			placed = new Placed();
			chunk.setAttached(PLACED, placed);
		}
		if (placed.positions.add(pos.asLong())) {
			chunk.markUnsaved();
		}
	}

	/** Marks a just-placed block, both halves for tall plants, doors and beds. */
	public static void markPlaced(ServerLevel level, BlockPos pos) {
		mark(level, pos);
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			mark(level, state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
		}
		if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock) {
			mark(level, pos.relative(net.minecraft.world.level.block.AbstractBedBlock.getConnectedDirection(state)));
		}
	}

	public static boolean isPlaced(ServerLevel level, BlockPos pos) {
		Placed placed = level.getChunkAt(pos).getAttached(PLACED);
		return placed != null && placed.positions.contains(pos.asLong());
	}

	/** Every player-placed position in a chunk (renown counts them). */
	public static void forEach(LevelChunk chunk, java.util.function.LongConsumer action) {
		Placed placed = chunk.getAttached(PLACED);
		if (placed != null) {
			placed.positions.forEach(action);
		}
	}

	/** Forgets a spot and says whether a player had placed the block there. */
	public static boolean take(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Placed placed = chunk.getAttached(PLACED);
		if (placed != null && placed.positions.remove(pos.asLong())) {
			chunk.markUnsaved();
			return true;
		}
		return false;
	}
}
