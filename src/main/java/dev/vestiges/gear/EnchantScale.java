package dev.vestiges.gear;

import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.TreeHooks;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enchant levels I-VI (approved in Batch 3 and M1c). The effects live in the built-in "enchant_levels" datapack (max level IV, effects
 * written for all six levels); this class holds the rules code needs: emerald costs, the anvil limit, the table's level V chance, Silk Touch
 * tiers, Flame's burn time, and how loot tables read Fortune and Looting so IV equals vanilla's max.
 */
public final class EnchantScale {
	/** The table, loot and trades stop here; V and VI come from Vestiges' own rules. */
	public static final int TABLE_MAX = 4;
	/** Vanilla's max level per enchantment (curses excluded: unchanged). */
	private static final Map<String, Integer> VANILLA_MAX = Map.ofEntries(
		Map.entry("aqua_affinity", 1), Map.entry("bane_of_arthropods", 5), Map.entry("blast_protection", 4), Map.entry("breach", 4),
		Map.entry("channeling", 1), Map.entry("density", 5), Map.entry("depth_strider", 3), Map.entry("efficiency", 5), Map.entry("feather_falling", 4),
		Map.entry("fire_aspect", 2), Map.entry("fire_protection", 4), Map.entry("flame", 1), Map.entry("fortune", 3), Map.entry("frost_walker", 2),
		Map.entry("impaling", 5), Map.entry("infinity", 1), Map.entry("knockback", 2), Map.entry("looting", 3), Map.entry("loyalty", 3),
		Map.entry("luck_of_the_sea", 3), Map.entry("lunge", 3), Map.entry("lure", 3), Map.entry("mending", 1), Map.entry("multishot", 1),
		Map.entry("piercing", 4), Map.entry("power", 5), Map.entry("projectile_protection", 4), Map.entry("protection", 4), Map.entry("punch", 2),
		Map.entry("quick_charge", 3), Map.entry("respiration", 3), Map.entry("riptide", 3), Map.entry("sharpness", 5), Map.entry("silk_touch", 1),
		Map.entry("smite", 5), Map.entry("soul_speed", 3), Map.entry("sweeping_edge", 3), Map.entry("swift_sneak", 3), Map.entry("thorns", 3),
		Map.entry("unbreaking", 3), Map.entry("wind_burst", 3));
	/** Arcane blocks in the bookshelf ring give the table's 3rd slot a chance at level V (Nether materials, as agreed). */
	public static final Set<Block> ARCANE = Set.of(Blocks.CRYING_OBSIDIAN, Blocks.GILDED_BLACKSTONE, Blocks.SHROOMLIGHT);
	private static final float[] FLAME_SECONDS = {1f, 2.33f, 3.67f, 5f, 7f, 9f};

	private EnchantScale() {
	}

	private static String path(Holder<Enchantment> e) {
		return e.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	/** True when the I-VI pack is active for this enchantment (its max level is IV). */
	public static boolean scaled(Holder<Enchantment> e) {
		return e.value().getMaxLevel() == TABLE_MAX && VANILLA_MAX.containsKey(path(e));
	}

	/**
	 * The level loot tables see (Fortune's ore bonus, Looting's extra drops...): our I-IV are spread over vanilla's I-max, so IV gives exactly
	 * vanilla's max; V and VI stay small (a 25% / 50% chance of one level more).
	 */
	public static int lootLevel(Holder<Enchantment> e, int level, RandomSource random) {
		if (level <= 0 || !scaled(e)) {
			return level;
		}
		return lootLevel(VANILLA_MAX.get(path(e)), level, random.nextDouble());
	}

	/** Pure form for tests: {@code roll} is a random number in [0, 1). */
	public static int lootLevel(int vanillaMax, int level, double roll) {
		if (level <= TABLE_MAX) {
			return Math.max(1, (int) Math.round(1 + (level - 1) * (vanillaMax - 1) / 3.0));
		}
		return vanillaMax + (roll < (level == 5 ? 0.25 : 0.5) ? 1 : 0);
	}

	/** Silk Touch tiers: I glass and leaves · II + grass and mycelium · III + ores · IV everything. */
	public static int silkRequired(BlockState state) {
		if (state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES) || state.is(BlockTags.LEAVES)) {
			return 1;
		}
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)) {
			return 2;
		}
		if (state.is(ConventionalBlockTags.ORES)) {
			return 3;
		}
		return 4;
	}

	/** Flame: the target burns 1 s (I) ... 5 s (IV), 7 s (V), 9 s (VI). */
	public static float flameSeconds(int level) {
		return FLAME_SECONDS[Math.max(1, Math.min(6, level)) - 1];
	}

	/** Arcane blocks around a table, counted like bookshelves (same spots, nothing in between), at most 8. */
	public static int arcaneBlocks(Level level, BlockPos table) {
		int n = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			BlockState s = level.getBlockState(table.offset(offset));
			BlockPos between = table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
			if (ARCANE.contains(s.getBlock()) && level.getBlockState(between).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) {
				n++;
			}
		}
		return Math.min(8, n);
	}

	/** Chance the table's 3rd slot comes out one level higher (IV to V): 2.5% per arcane block, +15% with Arcane Insight. */
	public static double levelFiveChance(Player player, int arcane) {
		return 0.025 * Math.min(8, arcane) + (TreeHooks.stats(player).has("arcane_insight") ? 0.15 : 0);
	}

	/** Emeralds for a table enchant: the number shown / 5, rounded up, after "-X% enchanting cost". */
	public static int tableEmeralds(Player player, int shownCost) {
		double base = Math.ceil(shownCost / 5.0);
		return Math.max(1, (int) Math.round(base * Math.max(0, TreeHooks.stats(player).mult("enchant_cost"))));
	}

	/** Emeralds for an anvil use: vanilla's level cost in emeralds, after "-X% anvil cost". */
	public static int anvilEmeralds(Player player, int levelCost) {
		return Math.max(1, (int) Math.round(levelCost * Math.max(0, TreeHooks.stats(player).mult("anvil_cost"))));
	}

	/** "Too Expensive!" starts here: 40, raised by tree nodes. */
	public static int anvilLimit(Player player) {
		return 40 + (int) Math.round(TreeHooks.stats(player).sum("anvil_limit", "levels"));
	}

	/** Lapis given back after enchanting ("-X% lapis needed"), rolled so small percentages still count. */
	public static int lapisRefund(Player player, int used, RandomSource random) {
		PlayerStats s = TreeHooks.stats(player);
		double back = used * Math.max(0, -s.get("lapis_cost")) / 100.0;
		int whole = (int) Math.floor(back);
		return whole + (random.nextDouble() < back - whole ? 1 : 0);
	}
}
