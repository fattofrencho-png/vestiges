package dev.vestiges.tree;

import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.settlement.SettlementService;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tree's building nodes (M5): placing speed, climb speed, faster breaking of blocks you placed, and Rebuilder (inside your claimed areas,
 * blocks you placed break instantly and go straight back to your inventory). Breaking and climbing run on both sides; the client only knows the
 * blocks it placed this session and the claimed areas on its map, so it is never faster than the server.
 */
public final class BuildingNodes {
	/** Client side: blocks the local player placed this session, and whether a spot is inside one of its claimed areas. */
	public static Predicate<BlockPos> clientPlaced = p -> false;
	public static Consumer<BlockPos> clientPlacedAdd = p -> {
	};
	public static Predicate<BlockPos> clientClaimed = p -> false;
	private static double placeCarry;

	private BuildingNodes() {
	}

	private static boolean placed(Player p, BlockGetter level, BlockPos pos) {
		if (level instanceof ServerLevel sl) {
			return PlacedBlocks.isPlaced(sl, pos);
		}
		return p.level().isClientSide() && clientPlaced.test(pos);
	}

	private static boolean claimed(Player p, BlockGetter level, BlockPos pos) {
		if (level instanceof ServerLevel sl) {
			return SettlementService.claimedBy(p, sl, pos);
		}
		return p.level().isClientSide() && clientClaimed.test(pos);
	}

	/** Breaking progress per tick on blocks you placed: faster with the node, instant with Rebuilder in your claimed areas. */
	public static float destroyProgress(Player p, BlockGetter level, BlockPos pos, float progress) {
		if (progress <= 0 || p.getAbilities().instabuild) {
			return progress;
		}
		PlayerStats s = TreeHooks.stats(p);
		if (s == PlayerStats.NONE || !s.has("rebuilder") && s.get("own_block_break") == 0 || !placed(p, level, pos)) {
			return progress;
		}
		if (s.has("rebuilder") && claimed(p, level, pos)) {
			return Math.max(progress, 1.0f);
		}
		return (float) (progress * (1 + s.get("own_block_break") / 100.0));
	}

	/** Rebuilder: the broken block goes into the inventory instead of dropping. Returns whether vanilla should still break it. */
	public static boolean beforeBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockEntity be) {
		if (p.getAbilities().instabuild || !StatEngine.get(p).has("rebuilder") || !PlacedBlocks.isPlaced(level, pos)
			|| !SettlementService.claimedBy(p, level, pos)) {
			return true;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, be, p, p.getMainHandItem());
		PlacedBlocks.take(level, pos);
		level.destroyBlock(pos, false, p);
		for (ItemStack d : drops) {
			if (!p.getInventory().add(d) && !d.isEmpty()) {
				Block.popResource(level, pos, d);
			}
		}
		return false;
	}

	/** Climb speed on ladders, vines and scaffolding. */
	public static double climb(LivingEntity e, double speed) {
		if (!(e instanceof Player p)) {
			return speed;
		}
		double pct = TreeHooks.stats(p).get("climb_speed");
		return pct == 0 ? speed : speed * (1 + pct / 100.0);
	}

	/** Placing speed (tree node and copper trims): the delay after placing a block, with the fraction carried to the next one. */
	public static int placeDelay(Player p, int delay) {
		double pct = TreeHooks.stats(p).get("placing_speed");
		if (pct <= 0) {
			return delay;
		}
		double d = delay / (1 + pct / 100.0) + placeCarry;
		int whole = (int) d;
		placeCarry = d - whole;
		return Math.max(1, whole);
	}
}
