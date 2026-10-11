package dev.vestiges.tree;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Counts what the ascension checklists ask for: hostile kills (and by weapon), ores, ancient debris, logs, shovel blocks, ripe crops,
 * cluster blocks and coral blocks. Reaching deepslate is noted by {@link TreeTicker}.
 */
public final class TreeCounters {
	private TreeCounters() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(TreeCounters::onKill);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
				onBreak(sp, state);
			}
		});
	}

	private static void onKill(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(dead instanceof Enemy) || !TreeService.enabled()) {
			return;
		}
		Map<String, Long> add = new HashMap<>();
		add.put("kills", 1L);
		dev.vestiges.mobs.MobData mob = dev.vestiges.mobs.MobService.data(dead);
		if (mob != null && mob.elite()) {
			add.put("elites", 1L);
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof AbstractArrow arrow) {
			ItemStack weapon = arrow.getWeaponItem();
			if (weapon != null && weapon.getItem() instanceof BowItem) {
				add.put("bow_kills", 1L);
			} else if (weapon != null && weapon.getItem() instanceof CrossbowItem) {
				add.put("crossbow_kills", 1L);
			}
		} else if (source.getDirectEntity() == killer) {
			ItemStack hand = killer.getMainHandItem();
			if (hand.is(ItemTags.SWORDS)) {
				add.put("sword_kills", 1L);
			} else if (hand.getItem() instanceof MaceItem) {
				add.put("mace_kills", 1L);
			} else if (hand.is(ItemTags.SPEARS)) {
				add.put("spear_kills", 1L);
			}
		}
		addAll(killer, add);
	}

	private static void onBreak(ServerPlayer player, BlockState state) {
		if (!TreeService.enabled()) {
			return;
		}
		Map<String, Long> add = new HashMap<>();
		if (state.is(ConventionalBlockTags.ORES)) {
			add.put("ores", 1L);
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			add.put("ancient_debris", 1L);
		}
		if (state.is(BlockTags.LOGS)) {
			add.put("logs", 1L);
		}
		if (player.getMainHandItem().is(ItemTags.SHOVELS) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			add.put("shovel_blocks", 1L);
		}
		if (TreeDrops.isRipeCrop(state)) {
			add.put("crops", 1L);
		}
		if (UtilityPowers.clusterGroup(state) >= 0) {
			add.put("cluster_blocks", 1L);
		}
		if (state.is(BlockTags.CORAL_BLOCKS)) {
			add.put("coral_blocks", 1L);
		}
		addAll(player, add);
	}

	private static void addAll(ServerPlayer player, Map<String, Long> add) {
		if (add.isEmpty()) {
			return;
		}
		PlayerTree tree = TreeService.get(player);
		for (Map.Entry<String, Long> e : add.entrySet()) {
			tree = tree.addCounter(e.getKey(), e.getValue());
		}
		player.setAttached(TreeService.TREE, tree);
	}
}
