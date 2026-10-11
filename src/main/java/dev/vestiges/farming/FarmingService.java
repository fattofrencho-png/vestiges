package dev.vestiges.farming;

import com.google.gson.JsonObject;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import dev.vestiges.tree.StatEngine;
import dev.vestiges.tree.TreeService;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Farming for everyone (M4, approved): right-click a ripe crop to harvest and replant it (wheat, carrots, potatoes, beetroot, nether wart, cocoa
 * pods; a ripe pitcher crop replants only if you carry a pitcher pod), no trampling, and the tree's farming nodes (crop growth, bone meal, baby
 * growth, breeding cooldown). Also answers the look-at card's questions about animals (growth, breeding, owner).
 */
public final class FarmingService {
	/** Who is using bone meal right now (set around BoneMealItem.useOn), so crops can read their bone meal node. */
	public static final ThreadLocal<ServerPlayer> BONEMEAL_USER = new ThreadLocal<>();

	private FarmingService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.FARMING);
	}

	public static void register() {
		UseBlockCallback.EVENT.register(FarmingService::use);
		ServerPlayNetworking.registerGlobalReceiver(dev.vestiges.net.LookAtPayloads.EntityInfo.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> entityInfo(ctx.player(), payload.id())));
	}

	/** Ripe and harvestable by right-click (torchflowers turn into a flower instead, so they never are). */
	public static boolean ripe(BlockState state) {
		Block b = state.getBlock();
		if (b instanceof CropBlock crop) {
			return !(b instanceof TorchflowerCropBlock) && crop.isMaxAge(state);
		}
		if (b instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (b instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		}
		if (b instanceof PitcherCropBlock) {
			return state.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE;
		}
		return false;
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown() || !enabled()
			|| player.getMainHandItem().is(Items.BONE_MEAL)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PitcherCropBlock && state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
			state = level.getBlockState(pos);
		}
		if (!ripe(state)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && sl.mayInteract(sp, pos)) {
			harvest(sl, sp, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	private static void harvest(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		ItemStack tool = player.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);
		Item seed = state.getBlock().asItem();
		boolean replant = false;
		for (ItemStack d : drops) {
			if (!replant && d.is(seed)) {
				d.shrink(1);
				replant = true;
			}
		}
		boolean pitcher = state.getBlock() instanceof PitcherCropBlock;
		if (!replant && pitcher) {
			// Vanilla gives no pod back: one is taken from the inventory, if there is one.
			int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.PITCHER_POD));
			if (slot >= 0) {
				player.getInventory().removeItem(slot, 1);
				replant = true;
			}
		}
		level.levelEvent(2001, pos, Block.getId(state));
		if (pitcher && level.getBlockState(pos.above()).is(Blocks.PITCHER_CROP)) {
			level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
		}
		level.setBlock(pos, replant ? young(state) : Blocks.AIR.defaultBlockState(), 3);
		for (ItemStack d : drops) {
			if (!d.isEmpty()) {
				Block.popResource(level, pos, d);
			}
		}
		dev.vestiges.codex.CodexService.harvested(player, level, pos, state.getBlock());
		player.setAttached(TreeService.TREE, TreeService.get(player).addCounter("crops", 1));
		dev.vestiges.tree.UtilityPowers.reapAround(player, level, pos, state);
	}

	private static BlockState young(BlockState ripe) {
		Block b = ripe.getBlock();
		if (b instanceof CropBlock crop) {
			return crop.getStateForAge(0);
		}
		if (b instanceof NetherWartBlock) {
			return ripe.setValue(NetherWartBlock.AGE, 0);
		}
		if (b instanceof CocoaBlock) {
			return ripe.setValue(CocoaBlock.AGE, 0);
		}
		return Blocks.PITCHER_CROP.defaultBlockState();
	}

	// ---- the tree's farming nodes ----------------------------------------------------------------------------------------

	/** Crop growth: the nearest player's "crops grow faster" stat, in %. */
	public static double cropGrowth(ServerLevel level, BlockPos pos) {
		if (!enabled()) {
			return 0;
		}
		Player p = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32, false);
		return p instanceof ServerPlayer sp ? StatEngine.get(sp).get("crop_growth") : 0;
	}

	/** Bone meal on crops: the stat in % turns into extra growth stages (the fraction is rolled). */
	public static int bonemeal(ServerPlayer player, int stages, RandomSource random) {
		double pct = enabled() && player != null ? StatEngine.get(player).get("bonemeal") : 0;
		if (pct <= 0) {
			return stages;
		}
		double v = stages * (1 + pct / 100.0);
		int whole = (int) v;
		return whole + (random.nextDouble() < v - whole ? 1 : 0);
	}

	/** After a breeding: Bestiary count, shorter breeding cooldown for both parents, a baby that grows up sooner. */
	public static void bred(ServerPlayer player, Animal parent, Animal partner, AgeableMob baby) {
		dev.vestiges.codex.CodexService.onMob(player, parent.getType());
		if (!enabled()) {
			return;
		}
		double cooldown = Math.min(90, StatEngine.get(player).get("breeding_cooldown"));
		if (cooldown > 0) {
			parent.setAge((int) (parent.getAge() * (1 - cooldown / 100.0)));
			partner.setAge((int) (partner.getAge() * (1 - cooldown / 100.0)));
		}
		double growth = Math.min(90, StatEngine.get(player).get("baby_growth"));
		if (growth > 0 && baby != null && baby.getAge() < 0) {
			baby.setAge((int) (baby.getAge() * (1 - growth / 100.0)));
		}
	}

	// ---- look-at card ---------------------------------------------------------------------------------------------------

	private static void entityInfo(ServerPlayer player, int id) {
		Entity e = player.level().getEntity(id);
		if (e == null || e.distanceToSqr(player) > 24 * 24) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		if (e instanceof AgeableMob a) {
			o.addProperty("age", a.getAge());
		}
		if (e instanceof Animal an) {
			o.addProperty("love", an.isInLove());
		}
		if (e instanceof OwnableEntity own && own.getOwnerReference() != null) {
			LivingEntity owner = own.getOwner();
			o.addProperty("owner", owner != null ? owner.getName().getString() : "someone away");
		}
		LevelService.notice(player, "entity_info", o);
	}
}
