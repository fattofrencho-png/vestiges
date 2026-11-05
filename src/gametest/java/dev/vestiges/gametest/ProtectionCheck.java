package dev.vestiges.gametest;

import dev.vestiges.settlement.SettlementData;
import dev.vestiges.settlement.SettlementData.Settlement;
import dev.vestiges.settlement.SettlementService;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * VESTIGES_TOUR=protection: land protection in a real world, against a settlement owned by someone else. Prints "[protection] ok" lines and fails
 * the run if a check doesn't hold.
 */
final class ProtectionCheck {
	private static final int Y = 220;

	private ProtectionCheck() {
	}

	static void run(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 6000);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		server.runCommand("gamemode survival @p");
		ctx.waitTicks(20);
		server.runOnServer(ProtectionCheck::check);
		System.out.println("[protection] finished");
	}

	private static void check(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos bell = new BlockPos(player.blockPosition().getX(), Y, player.blockPosition().getZ());
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		Settlement town = new Settlement(UUID.randomUUID().toString(), "Test", UUID.randomUUID(), List.of(), SettlementService.dim(level), bell.asLong(),
			SettlementData.ACTIVE, 0, SettlementData.Stats.START, SettlementData.Shared.EMPTY);
		SettlementService.save(server, SettlementService.world(server).put(town));
		int r = town.radius();

		// Placing from just outside the border.
		BlockPos clicked = bell.offset(r + 1, 0, 0);
		BlockPos landing = bell.offset(r, 0, 0);
		level.setBlockAndUpdate(clicked, Blocks.STONE.defaultBlockState());
		use(level, player, new ItemStack(Items.STONE, 8), clicked, Direction.WEST);
		expect(level.getBlockState(landing).isAir(), "a non-member can't place from the edge");

		// Harvesting by right-click.
		BlockPos crop = bell.offset(3, 0, 3);
		BlockState ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
		level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 2);
		level.setBlock(crop, ripe, 2);
		use(level, player, ItemStack.EMPTY, crop, Direction.UP);
		expect(level.getBlockState(crop).equals(ripe), "a non-member can't harvest");

		// Tilling with a hoe.
		BlockPos dirt = bell.offset(-3, 0, 3);
		level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
		use(level, player, new ItemStack(Items.WOODEN_HOE), dirt, Direction.UP);
		expect(level.getBlockState(dirt).is(Blocks.DIRT), "a non-member can't till");

		// An explosion with no mob behind it stops at the border. The rays go through the air around the middle.
		BlockPos in = bell.offset(r - 1, -20, 0);
		BlockPos out = bell.offset(r + 1, -20, 0);
		level.setBlock(in, Blocks.DIRT.defaultBlockState(), 2);
		level.setBlock(out, Blocks.DIRT.defaultBlockState(), 2);
		Vec3 middle = Vec3.atCenterOf(bell.offset(r, -20, 0));
		level.explode(null, middle.x, middle.y, middle.z, 4f, Level.ExplosionInteraction.TNT);
		expect(level.getBlockState(in).is(Blocks.DIRT), "TNT can't damage blocks inside");
		expect(level.getBlockState(out).isAir(), "TNT still damages blocks outside");

		// A mob's explosion is left to the griefing switch.
		BlockPos mobInside = bell.offset(0, -30, 5);
		level.setBlock(mobInside, Blocks.DIRT.defaultBlockState(), 2);
		Vec3 mobMiddle = Vec3.atCenterOf(mobInside.above());
		level.explode(new Creeper(net.minecraft.world.entity.EntityTypes.CREEPER, level), mobMiddle.x, mobMiddle.y, mobMiddle.z, 4f, Level.ExplosionInteraction.MOB);
		expect(level.getBlockState(mobInside).isAir(), "a creeper still works by the griefing rule");

		// The same things work for a member.
		SettlementService.save(server, SettlementService.world(server).put(town.withMembers(List.of(player.getUUID()))));
		use(level, player, new ItemStack(Items.STONE, 8), clicked, Direction.WEST);
		expect(level.getBlockState(landing).is(Blocks.STONE), "a member can place from the edge");
		use(level, player, ItemStack.EMPTY, crop, Direction.UP);
		expect(!level.getBlockState(crop).equals(ripe), "a member can harvest");
		use(level, player, new ItemStack(Items.WOODEN_HOE), dirt, Direction.UP);
		expect(level.getBlockState(dirt).is(Blocks.FARMLAND), "a member can till");
		level.setBlock(in, Blocks.DIRT.defaultBlockState(), 2);
		level.explode(player, middle.x, middle.y, middle.z, 4f, Level.ExplosionInteraction.TNT);
		expect(level.getBlockState(in).isAir(), "a member's own TNT works");
	}

	private static void use(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos, Direction side) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false));
	}

	private static void expect(boolean holds, String what) {
		if (!holds) {
			throw new IllegalStateException("[protection] FAILED: " + what);
		}
		System.out.println("[protection] ok: " + what);
	}
}
