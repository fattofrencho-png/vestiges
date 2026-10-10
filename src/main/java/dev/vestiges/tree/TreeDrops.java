package dev.vestiges.tree;

import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearService;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Tree effects on block drops, applied to the loot of blocks a player breaks: crop yield, seeds, saplings and apples, extra ore drops and
 * flint from gravel. It changes the counts vanilla already rolled, so Fortune and Silk Touch keep working as usual.
 */
public final class TreeDrops {
	private static final Set<Item> SEEDS = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS,
		Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD);

	/** True while Silk Touch tiers re-roll a block's drops (the nested roll must not be checked again). */
	private static boolean rerolling;

	private TreeDrops() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register(TreeDrops::modify);
	}

	private static void modify(Holder<?> table, LootContext ctx, List<ItemStack> drops) {
		if (drops.isEmpty()) {
			return;
		}
		BlockState state = ctx.getOptional(LootContextParams.BLOCK_STATE);
		Entity entity = ctx.getOptional(LootContextParams.THIS_ENTITY);
		if (state == null || !(entity instanceof ServerPlayer player)) {
			return;
		}
		if (silkTier(ctx, state, player, drops) || !TreeService.enabled()) {
			return;
		}
		PlayerStats s = StatEngine.get(player);
		if (s == PlayerStats.NONE) {
			return;
		}
		RandomSource random = ctx.getRandom();
		if (isRipeCrop(state)) {
			Item seed = state.getBlock().asItem();
			boolean seedIsCrop = drops.stream().allMatch(d -> d.is(seed));
			for (ItemStack d : drops) {
				boolean product = seedIsCrop || !d.is(seed);
				grow(d, product ? s.get("crop_yield") : s.get("seed_drops"), random);
			}
			return;
		}
		if (state.is(BlockTags.LEAVES)) {
			for (ItemStack d : drops) {
				if (d.is(ItemTags.SAPLINGS)) {
					grow(d, s.get("sapling_drops"), random);
				} else if (d.is(Items.APPLE)) {
					grow(d, s.get("apple_drops"), random);
				}
			}
			return;
		}
		if (state.is(Blocks.GRAVEL)) {
			gravel(drops, s.get("flint_chance"), random, silkTouch(player));
			return;
		}
		if (state.is(ConventionalBlockTags.ORES)) {
			Item self = state.getBlock().asItem();
			for (ItemStack d : drops) {
				if (!d.is(self)) {
					grow(d, s.get("extra_ore"), random);
				}
			}
			return;
		}
		for (ItemStack d : drops) {
			if (SEEDS.contains(d.getItem())) {
				grow(d, s.get("seed_drops"), random);
			}
		}
	}

	/**
	 * Silk Touch tiers (I glass and leaves, II + grass and mycelium, III + ores, IV everything): when the tool's Silk Touch is too low for
	 * this block, the drops are rolled again as if the tool had no Silk Touch. Returns true when it replaced the drops (the re-roll already
	 * had the tree bonuses applied).
	 */
	private static boolean silkTier(LootContext ctx, BlockState state, ServerPlayer player, List<ItemStack> drops) {
		if (!GearService.enabled() || rerolling) {
			return false;
		}
		ItemStack tool = player.getMainHandItem();
		Holder<Enchantment> silk = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		int level = EnchantmentHelper.getItemEnchantmentLevel(silk, tool);
		if (level <= 0 || !EnchantScale.scaled(silk) || level >= EnchantScale.silkRequired(state)) {
			return false;
		}
		Vec3 origin = ctx.getOptional(LootContextParams.ORIGIN);
		if (origin == null) {
			return false;
		}
		ItemStack plain = tool.copy();
		EnchantmentHelper.updateEnchantments(plain, m -> m.removeIf(h -> h.is(Enchantments.SILK_TOUCH)));
		rerolling = true;
		try {
			List<ItemStack> again = Block.getDrops(state, ctx.getLevel(), BlockPos.containing(origin), ctx.getOptional(LootContextParams.BLOCK_ENTITY), player, plain);
			drops.clear();
			drops.addAll(again);
		} finally {
			rerolling = false;
		}
		return true;
	}

	public static boolean isRipeCrop(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		return state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	/** Adds pct% more items to a stack, with the fraction rolled (+3% on 1 item = a 3% chance of one more). */
	private static void grow(ItemStack stack, double pct, RandomSource random) {
		if (pct <= 0 || stack.isEmpty()) {
			return;
		}
		int extra = TreeHooks.randomRound(stack.getCount() * pct / 100.0, random);
		if (extra > 0) {
			stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + extra));
		}
	}

	/** Flint "X% more often": gravel turns into flint (or back) so the overall flint chance moves by that percentage. */
	private static void gravel(List<ItemStack> drops, double pct, RandomSource random, boolean silk) {
		if (pct == 0 || silk) {
			return;
		}
		for (int i = 0; i < drops.size(); i++) {
			ItemStack d = drops.get(i);
			if (pct > 0 && d.is(Items.GRAVEL) && random.nextDouble() < 0.1 * pct / 100.0 / 0.9) {
				drops.set(i, new ItemStack(Items.FLINT, d.getCount()));
			} else if (pct < 0 && d.is(Items.FLINT) && random.nextDouble() < -pct / 100.0) {
				drops.set(i, new ItemStack(Items.GRAVEL, d.getCount()));
			}
		}
	}

	private static boolean silkTouch(ServerPlayer player) {
		Holder<Enchantment> silk = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		return EnchantmentHelper.getItemEnchantmentLevel(silk, player.getMainHandItem()) > 0;
	}
}
