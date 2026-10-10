package dev.vestiges.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The ascendancy utility powers, all switched on and off together with V: vein mining, tree felling, 3x3 pickaxe and shovel, area harvest,
 * area tilling, wide paths, whole-trunk stripping, cluster harvest and flock shearing. Extra blocks are broken through the normal survival
 * path (tool wear, drops, XP, protection), and a power stops before it would break your tool.
 */
public final class UtilityPowers {
	private static boolean working;
	private static final List<Runnable> QUEUE = new ArrayList<>();
	private static final Set<Block> FLATTENABLE = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MYCELIUM,
		Blocks.ROOTED_DIRT);
	private static final Set<Block> TILLABLE = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH);

	private UtilityPowers() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!working && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				afterBreak(sp, sl, pos, state);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!working && player instanceof ServerPlayer sp && level instanceof ServerLevel sl && hit.getDirection() != Direction.DOWN) {
				onUseBlock(sp, sl, hand, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl && entity instanceof Sheep sheep) {
				onShear(sp, sl, hand, sheep);
			}
			return InteractionResult.PASS;
		});
	}

	/** Runs follow-ups queued during a use (after vanilla has tilled, flattened, stripped or sheared). */
	static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) {
			return;
		}
		List<Runnable> now = new ArrayList<>(QUEUE);
		QUEUE.clear();
		for (Runnable r : now) {
			working = true;
			try {
				r.run();
			} finally {
				working = false;
			}
		}
	}

	private static boolean active(ServerPlayer p, PlayerStats s, String mechanic) {
		return s.has(mechanic) && !p.isSpectator();
	}

	private static PlayerStats powers(ServerPlayer p) {
		if (!TreeService.enabled() || !TreeService.get(p).utilityOn()) {
			return PlayerStats.NONE;
		}
		return StatEngine.get(p);
	}

	// ---- breaking --------------------------------------------------------------------------------------------------

	private static void afterBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
		PlayerStats s = powers(p);
		if (s == PlayerStats.NONE) {
			return;
		}
		ItemStack tool = p.getMainHandItem();
		boolean pickaxe = tool.is(ItemTags.PICKAXES);
		if (pickaxe && state.is(ConventionalBlockTags.ORES) && active(p, s, "vein_miner")) {
			Block ore = state.getBlock();
			breakAll(p, level, tool, connected(level, pos, b -> b.is(ore), (int) max(s, "vein_miner", "max", 32) - 1, false));
		} else if (pickaxe && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && active(p, s, "excavator")) {
			float hardness = state.getDestroySpeed(level, pos);
			breakAll(p, level, tool, plane(p, pos, (int) max(s, "excavator", "radius", 1), b -> b.is(BlockTags.MINEABLE_WITH_PICKAXE)
				&& p.hasCorrectToolForDrops(b) && hardness(b, level, pos) <= hardness + 2));
		}
		if (tool.is(ItemTags.SHOVELS) && earth(state) && active(p, s, "earthmover")) {
			breakAll(p, level, tool, plane(p, pos, (int) max(s, "earthmover", "radius", 1), UtilityPowers::earth));
		}
		if (tool.is(ItemTags.AXES) && state.is(BlockTags.LOGS) && active(p, s, "lumberjack")) {
			fellTree(p, level, pos, state, tool, (int) max(s, "lumberjack", "max", 64));
		}
		if (TreeDrops.isRipeCrop(state) && active(p, s, "reaper")) {
			reap(p, level, pos, state, (int) max(s, "reaper", "radius", 2));
		}
		int group = clusterGroup(state);
		if (group >= 0 && active(p, s, "cluster_harvest")) {
			breakAll(p, level, tool, connected(level, pos, b -> clusterGroup(b) == group, (int) max(s, "cluster_harvest", "max", 32) - 1, false));
		}
	}

	/** The biggest value of one parameter among the nodes giving this power. */
	private static double max(PlayerStats s, String mechanic, String param, double fallback) {
		double best = 0;
		for (TreeDefs.Effect e : s.all(mechanic)) {
			best = Math.max(best, e.param(param, fallback));
		}
		return best > 0 ? best : fallback;
	}

	private static float hardness(BlockState b, ServerLevel level, BlockPos pos) {
		float h = b.getDestroySpeed(level, pos);
		return h < 0 ? Float.MAX_VALUE : h;
	}

	/** Blocks touching each other (including diagonals) that match, nearest first, not counting the start. */
	private static List<BlockPos> connected(ServerLevel level, BlockPos start, Predicate<BlockState> match, int limit, boolean upOnly) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && out.size() < limit) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1 && out.size() < limit; dx++) {
				for (int dy = -1; dy <= 1 && out.size() < limit; dy++) {
					for (int dz = -1; dz <= 1 && out.size() < limit; dz++) {
						BlockPos n = cur.offset(dx, dy, dz);
						if ((upOnly && n.getY() < start.getY()) || !seen.add(n) || start.distManhattan(n) > 48) {
							continue;
						}
						if (match.test(level.getBlockState(n))) {
							out.add(n);
							queue.add(n);
						}
					}
				}
			}
		}
		return out;
	}

	/** The 3x3 (or bigger) square around the block, facing the way you are looking. */
	private static List<BlockPos> plane(ServerPlayer p, BlockPos center, int r, Predicate<BlockState> match) {
		List<BlockPos> out = new ArrayList<>();
		float pitch = p.getXRot();
		Direction.Axis axis = Math.abs(pitch) > 50 ? Direction.Axis.Y : p.getDirection().getAxis();
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos n = switch (axis) {
					case Y -> center.offset(a, 0, b);
					case X -> center.offset(0, a, b);
					case Z -> center.offset(a, b, 0);
				};
				if (match.test(p.level().getBlockState(n))) {
					out.add(n);
				}
			}
		}
		return out;
	}

	private static void breakAll(ServerPlayer p, ServerLevel level, ItemStack tool, List<BlockPos> positions) {
		working = true;
		try {
			for (BlockPos n : positions) {
				if (p.getMainHandItem() != tool || (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1)) {
					return;
				}
				if (level.getBlockEntity(n) != null || !level.mayInteract(p, n)) {
					continue;
				}
				p.gameMode.destroyBlock(n);
			}
		} finally {
			working = false;
		}
	}

	public static boolean earth(BlockState b) {
		return b.is(BlockTags.DIRT) || b.is(BlockTags.SAND) || b.is(Blocks.GRAVEL) || b.is(Blocks.CLAY) || b.is(Blocks.SNOW) || b.is(Blocks.SNOW_BLOCK)
			|| b.is(Blocks.POWDER_SNOW) || b.is(Blocks.MUD) || b.is(Blocks.FARMLAND) || b.is(Blocks.DIRT_PATH);
	}

	/** Cluster groups: coral, moss, dripstone, amethyst buds, glow lichen, vines, flower patches. -1 = not a cluster block. */
	public static int clusterGroup(BlockState b) {
		if (b.is(BlockTags.CORAL_BLOCKS) || b.is(BlockTags.CORALS) || b.is(BlockTags.WALL_CORALS)) {
			return 0;
		}
		if (b.is(Blocks.MOSS_BLOCK) || b.is(Blocks.MOSS_CARPET) || b.is(Blocks.PALE_MOSS_BLOCK) || b.is(Blocks.PALE_MOSS_CARPET)) {
			return 1;
		}
		if (b.is(Blocks.POINTED_DRIPSTONE) || b.is(Blocks.DRIPSTONE_BLOCK)) {
			return 2;
		}
		if (b.is(Blocks.AMETHYST_CLUSTER) || b.is(Blocks.LARGE_AMETHYST_BUD) || b.is(Blocks.MEDIUM_AMETHYST_BUD) || b.is(Blocks.SMALL_AMETHYST_BUD)) {
			return 3;
		}
		if (b.is(Blocks.GLOW_LICHEN)) {
			return 4;
		}
		if (b.is(Blocks.VINE) || b.is(BlockTags.CAVE_VINES) || b.is(Blocks.WEEPING_VINES) || b.is(Blocks.WEEPING_VINES_PLANT)
			|| b.is(Blocks.TWISTING_VINES) || b.is(Blocks.TWISTING_VINES_PLANT)) {
			return 5;
		}
		if (b.is(BlockTags.SMALL_FLOWERS) || b.is(Blocks.PINK_PETALS) || b.is(Blocks.WILDFLOWERS)) {
			return 6;
		}
		return -1;
	}

	// ---- tree felling ------------------------------------------------------------------------------------------------

	private static void fellTree(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, int limit) {
		Block log = state.getBlock();
		List<BlockPos> logs = connected(level, pos, b -> b.is(log), limit - 1, true);
		// A tree has natural leaves around its logs; a log wall or a house does not.
		boolean natural = false;
		for (BlockPos l : logs) {
			for (Direction d : Direction.values()) {
				BlockState n = level.getBlockState(l.relative(d));
				if (n.getBlock() instanceof LeavesBlock && !n.getValue(LeavesBlock.PERSISTENT)) {
					natural = true;
					break;
				}
			}
			if (natural) {
				break;
			}
		}
		if (!natural) {
			return;
		}
		breakAll(p, level, tool, logs);
		Block sapling = saplingFor(log);
		BlockState below = level.getBlockState(pos.below());
		if (sapling != null && level.getBlockState(pos).isAir() && below.is(BlockTags.DIRT) && sapling.defaultBlockState().canSurvive(level, pos)) {
			level.setBlockAndUpdate(pos, sapling.defaultBlockState());
		}
	}

	private static Block saplingFor(Block log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log);
		String base = id.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
		String name = base.equals("mangrove") ? "mangrove_propagule" : base + "_sapling";
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), name)).orElse(null);
	}

	// ---- harvest -----------------------------------------------------------------------------------------------------

	private static void reap(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, int r) {
		int harvested = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos n = pos.offset(dx, 0, dz);
				BlockState ns = level.getBlockState(n);
				if (n.equals(pos) || !TreeDrops.isRipeCrop(ns) || !level.mayInteract(p, n)) {
					continue;
				}
				Item seed = ns.getBlock().asItem();
				boolean seedTaken = false;
				for (ItemStack drop : Block.getDrops(ns, level, n, null, p, p.getMainHandItem())) {
					if (!seedTaken && drop.is(seed)) {
						drop.shrink(1);
						seedTaken = true;
					}
					if (!drop.isEmpty()) {
						Block.popResource(level, n, drop);
					}
				}
				level.setBlockAndUpdate(n, youngCrop(ns));
				harvested++;
			}
		}
		// The crop you broke yourself is replanted with a seed from its own drops.
		Item seed = state.getBlock().asItem();
		if (level.getBlockState(pos).isAir() && youngCrop(state).canSurvive(level, pos)) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0), e -> e.getItem().is(seed))) {
				item.getItem().shrink(1);
				if (item.getItem().isEmpty()) {
					item.discard();
				}
				level.setBlockAndUpdate(pos, youngCrop(state));
				break;
			}
		}
		if (harvested > 0) {
			level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
			p.setAttached(TreeService.TREE, TreeService.get(p).addCounter("crops", harvested));
		}
	}

	private static BlockState youngCrop(BlockState ripe) {
		if (ripe.getBlock() instanceof CropBlock crop) {
			return crop.getStateForAge(0);
		}
		return ripe.getBlock().defaultBlockState();
	}

	// ---- using tools on blocks ---------------------------------------------------------------------------------------

	private static void onUseBlock(ServerPlayer p, ServerLevel level, InteractionHand hand, BlockPos pos) {
		PlayerStats s = powers(p);
		if (s == PlayerStats.NONE) {
			return;
		}
		ItemStack tool = p.getItemInHand(hand);
		BlockState before = level.getBlockState(pos);
		boolean airAbove = level.getBlockState(pos.above()).isAir();
		if (tool.is(ItemTags.HOES) && TILLABLE.contains(before.getBlock()) && airAbove && active(p, s, "tiller")) {
			int r = (int) max(s, "tiller", "radius", 1);
			QUEUE.add(() -> {
				if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
					return;
				}
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						BlockPos n = pos.offset(dx, 0, dz);
						if (tool.isEmpty() || !TILLABLE.contains(level.getBlockState(n).getBlock()) || !level.getBlockState(n.above()).isAir()
							|| !level.mayInteract(p, n)) {
							continue;
						}
						level.setBlockAndUpdate(n, Blocks.FARMLAND.defaultBlockState());
						tool.hurtAndBreak(1, p, hand);
					}
				}
			});
		} else if (tool.is(ItemTags.SHOVELS) && FLATTENABLE.contains(before.getBlock()) && airAbove && active(p, s, "trailmaker")) {
			int side = Math.max(0, ((int) max(s, "trailmaker", "width", 3) - 1) / 2);
			Direction facing = p.getDirection();
			QUEUE.add(() -> {
				if (!level.getBlockState(pos).is(Blocks.DIRT_PATH)) {
					return;
				}
				for (int i = 1; i <= side; i++) {
					for (Direction d : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
						BlockPos n = pos.relative(d, i);
						if (tool.isEmpty() || !FLATTENABLE.contains(level.getBlockState(n).getBlock()) || !level.getBlockState(n.above()).isAir()
							|| !level.mayInteract(p, n)) {
							continue;
						}
						level.setBlockAndUpdate(n, Blocks.DIRT_PATH.defaultBlockState());
						tool.hurtAndBreak(1, p, hand);
					}
				}
			});
		} else if (tool.is(ItemTags.AXES) && before.is(BlockTags.LOGS) && active(p, s, "strip_trunk")) {
			Block log = before.getBlock();
			Block stripped = strippedFor(log);
			if (stripped == null) {
				return;
			}
			int limit = (int) max(s, "strip_trunk", "max", 32);
			QUEUE.add(() -> {
				if (!level.getBlockState(pos).is(stripped)) {
					return;
				}
				for (BlockPos n : connected(level, pos, b -> b.is(log), limit - 1, false)) {
					if (tool.isEmpty() || !level.mayInteract(p, n)) {
						break;
					}
					BlockState old = level.getBlockState(n);
					BlockState next = stripped.defaultBlockState();
					if (old.hasProperty(RotatedPillarBlock.AXIS) && next.hasProperty(RotatedPillarBlock.AXIS)) {
						next = next.setValue(RotatedPillarBlock.AXIS, old.getValue(RotatedPillarBlock.AXIS));
					}
					level.setBlockAndUpdate(n, next);
					tool.hurtAndBreak(1, p, hand);
				}
			});
		}
	}

	private static Block strippedFor(Block log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log);
		if (id.getPath().startsWith("stripped_")) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), "stripped_" + id.getPath())).orElse(null);
	}

	private static void onShear(ServerPlayer p, ServerLevel level, InteractionHand hand, Sheep first) {
		PlayerStats s = powers(p);
		ItemStack shears = p.getItemInHand(hand);
		if (s == PlayerStats.NONE || !shears.is(Items.SHEARS) || !first.readyForShearing() || !active(p, s, "shearmaster")) {
			return;
		}
		double r = Math.max(1, s.all("shearmaster").get(0).param("radius", 5));
		QUEUE.add(() -> {
			for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, first.getBoundingBox().inflate(r), x -> x != first && x.readyForShearing())) {
				if (shears.isEmpty()) {
					break;
				}
				sheep.shear(level, SoundSource.PLAYERS, shears);
				shears.hurtAndBreak(1, p, hand);
			}
		});
	}
}
