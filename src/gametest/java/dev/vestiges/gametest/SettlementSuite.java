package dev.vestiges.gametest;

import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.settlement.MarkerItems;
import dev.vestiges.settlement.MarkerType;
import dev.vestiges.settlement.SettlementData;
import dev.vestiges.settlement.SettlementService;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * S8: the earned markers (Bell, Banner, Ward Lantern, Gold Block): placement rules, setting up with {@code settlement finish}, renown, protection
 * for a non-member (the owner is swapped for a stranger, so one player is enough), markers returning to their owner, the switch, and the fight rule.
 */
final class SettlementSuite {
	private static final int X = 3000;
	private static final int Z = 3000;

	private SettlementSuite() {
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static SettlementData.World world(TestServerContext server) {
		return server.computeOnServer(SettlementService::world);
	}

	private static long markers(TestServerContext server, MarkerType type) {
		return world(server).markers().stream().filter(m -> m.kind() == type).count();
	}

	private static BlockHitResult top(BlockPos floor) {
		return new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
	}

	/** Puts the marker item in the main hand and uses it on the floor under {@code at}, the way a player does. */
	private static InteractionResult place(TestServerContext server, ItemStack stack, BlockPos at) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			InteractionResult r = p.gameMode.useItemOn(p, p.level(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, top(at.below()));
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return r;
		});
	}

	private static void put(ClientGameTestContext ctx, TestServerContext server, MarkerType type, BlockPos at) {
		place(server, MarkerItems.create(type), at);
		ctx.waitTicks(5);
	}

	private static BlockState state(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> s.overworld().getBlockState(pos));
	}

	private static boolean hasMarkerItem(TestServerContext server, MarkerType type) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				MarkerItems.Info info = MarkerItems.read(p.getInventory().getItem(i));
				if (info != null && info.type() == type) {
					return true;
				}
			}
			return false;
		});
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges config system settlements true");
		server.runCommand("kill @e[type=!minecraft:player]");
		BlockPos a = T.pad(ctx, server, X, Z);
		// The player stands on the pad's centre, so markers go beside them (a block cannot be placed into the player).
		BlockPos bell = a.offset(2, 0, 2);

		// ---------------------------------------------------------------- bell
		c.section("Bell");
		c.check("no settlement to start with", world(server).settlements().isEmpty(), "found " + world(server).settlements().size());
		InteractionResult first = place(server, MarkerItems.create(MarkerType.BELL), bell);
		ctx.waitTicks(5);
		c.check("a Bell was placed", state(server, bell).is(Blocks.BELL), "result " + first + ", block " + state(server, bell).getBlock());
		if (!state(server, bell).is(Blocks.BELL)) {
			c.note(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = s.overworld();
				return "diagnosis: fight " + SettlementService.inCombat(p) + ", structure "
					+ level.structureManager().getStructureWithPieceAt(a, h -> !h.is(net.minecraft.tags.StructureTags.VILLAGE)).isValid() + ", end "
					+ (level.dimension() == net.minecraft.world.level.Level.END) + ", mode " + p.gameMode.getGameModeForPlayer() + ", pos " + p.blockPosition()
					+ ", floor " + level.getBlockState(a.below()).getBlock() + ", enabled " + SettlementService.enabled();
			}));
		}
		SettlementData.World w = world(server);
		c.check("placing it founds a settlement in setup", w.settlements().size() == 1 && SettlementData.SETUP.equals(w.settlements().get(0).state()),
			"settlements " + w.settlements().size());
		if (w.settlements().isEmpty()) {
			c.note("the Bell was not accepted at " + bell + " (a structure there, or a placement rule?): the rest of this suite needs it");
			return;
		}
		c.check("the Bell record points at the block", w.settlements().get(0).bell() == bell.asLong());

		BlockPos b = T.pad(ctx, server, X + 100, Z).offset(2, 0, 2);
		put(ctx, server, MarkerType.BELL, b);
		c.check("a second Bell 100 blocks away is refused (2,000 apart)", world(server).settlements().size() == 1 && !state(server, b).is(Blocks.BELL),
			"settlements " + world(server).settlements().size());
		c.check("...and the refused Bell block is not left in the world", !state(server, b).is(Blocks.BELL));

		// ---------------------------------------------------------------- banners and wards (back at A)
		c.section("Banners, Ward Lanterns, Gold Blocks");
		server.runCommand("execute in minecraft:overworld run tp @p " + a.getX() + " " + a.getY() + " " + a.getZ());
		ctx.waitTicks(10);
		BlockPos banner1 = a.offset(4, 0, 4);
		put(ctx, server, MarkerType.BANNER16, banner1);
		c.check("a Banner (radius 16) is placed and recorded", state(server, banner1).is(net.minecraft.tags.BlockTags.BANNERS) && markers(server, MarkerType.BANNER16) == 1,
			"records " + markers(server, MarkerType.BANNER16));
		BlockPos banner2 = a.offset(4, 0, -4);
		put(ctx, server, MarkerType.BANNER16, banner2);
		c.check("a Banner whose area overlaps the first is refused", markers(server, MarkerType.BANNER16) == 1, "records " + markers(server, MarkerType.BANNER16));

		BlockPos ward1 = a.offset(-4, 0, 3);
		put(ctx, server, MarkerType.WARD6, ward1);
		c.check("a Ward Lantern (radius 6) is placed and recorded", state(server, ward1).is(Blocks.LANTERN) && markers(server, MarkerType.WARD6) == 1,
			"records " + markers(server, MarkerType.WARD6));
		put(ctx, server, MarkerType.WARD6, a.offset(-4, 0, -3));
		c.check("a Ward Lantern overlapping another Ward Lantern is refused", markers(server, MarkerType.WARD6) == 1, "records " + markers(server, MarkerType.WARD6));
		c.check("Banners and Ward Lanterns do not overlap-block each other",
			markers(server, MarkerType.BANNER16) == 1 && markers(server, MarkerType.WARD6) == 1);

		// A Gold Block works only inside your claim: far from the Bell it is refused.
		BlockPos far = T.pad(ctx, server, X + 100, Z + 100).offset(2, 0, 2);
		put(ctx, server, MarkerType.GOLD_BLOCK, far);
		c.check("a Gold Block outside any claim is refused", markers(server, MarkerType.GOLD_BLOCK) == 0, "records " + markers(server, MarkerType.GOLD_BLOCK));

		// ---------------------------------------------------------------- setting up
		c.section("Setting up");
		server.runCommand("execute in minecraft:overworld run tp @p " + a.getX() + " " + a.getY() + " " + a.getZ());
		ctx.waitTicks(10);
		c.expectOk(server, "vestiges settlement info");
		c.expectOk(server, "vestiges settlement finish");
		ctx.waitTicks(80);
		w = world(server);
		c.check("the settlement becomes active", !w.settlements().isEmpty() && w.settlements().get(0).active(),
			w.settlements().isEmpty() ? "none" : w.settlements().get(0).state());
		c.check("the Ward Lantern becomes active", w.markers().stream().filter(m -> m.kind() == MarkerType.WARD6).allMatch(SettlementData.Marker::active));
		c.check("the Banner is active at once", w.markers().stream().filter(m -> m.kind() == MarkerType.BANNER16).allMatch(SettlementData.Marker::active));

		// ---------------------------------------------------------------- renown
		c.section("Renown");
		int before = world(server).settlements().get(0).stats().total();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Block[] kinds = {Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.BRICKS, Blocks.GLASS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS};
			int n = 0;
			// 20 of each of 6 kinds: variety counts a kind from 16 blocks (5 points each), size is one point per 100 blocks.
			for (Block kind : kinds) {
				for (int i = 0; i < 20; i++) {
					BlockPos at = a.offset(-5 + (n % 11), 1 + ((n / 11) % 6), 4 + (n / 66));
					level.setBlock(at, kind.defaultBlockState(), 3);
					PlacedBlocks.markPlaced(level, at);
					n++;
				}
			}
		});
		c.expectOk(server, "vestiges settlement renown");
		SettlementData.Stats st = world(server).settlements().get(0).stats();
		c.check("renown counts the player-placed blocks (variety 6 kinds x 5 = 30, size 1)", st.size() == 1 && st.variety() == 30, "size " + st.size() + ", variety " + st.variety()
			+ ", total " + before + " -> " + st.total());
		c.note("renown now: size " + st.size() + ", variety " + st.variety() + ", homes " + st.homes() + ", people " + st.people() + ", deeds " + st.deeds()
			+ ", level " + st.level());

		// ---------------------------------------------------------------- gold block inside the claim
		c.section("Gold Block");
		BlockPos gold = a.offset(0, 0, -3);
		put(ctx, server, MarkerType.GOLD_BLOCK, gold);
		c.check("a Gold Block inside the settlement is placed", state(server, gold).is(Blocks.GOLD_BLOCK) && markers(server, MarkerType.GOLD_BLOCK) == 1,
			"records " + markers(server, MarkerType.GOLD_BLOCK));
		boolean loaded = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int cx = gold.getX() >> 4;
			int cz = gold.getZ() >> 4;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!level.getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz))) {
						return false;
					}
				}
			}
			return true;
		});
		c.check("it force-loads its chunk and the 8 around it", loaded);
		put(ctx, server, MarkerType.GOLD_BLOCK, a.offset(2, 0, -2));
		c.check("a second Gold Block overlapping its chunks is refused", markers(server, MarkerType.GOLD_BLOCK) == 1, "records " + markers(server, MarkerType.GOLD_BLOCK));

		// ---------------------------------------------------------------- the owner breaks a marker: it comes back
		c.section("Markers return to the owner");
		boolean broke = server.computeOnServer(s -> player(s).gameMode.destroyBlock(banner1));
		ctx.waitTicks(5);
		c.check("breaking your own Banner removes its record", markers(server, MarkerType.BANNER16) == 0, "records " + markers(server, MarkerType.BANNER16) + ", destroyBlock " + broke);
		c.check("...the block is gone", !state(server, banner1).is(net.minecraft.tags.BlockTags.BANNERS));
		c.check("...and the Banner item is back in the owner's inventory", hasMarkerItem(server, MarkerType.BANNER16));
		server.computeOnServer(s -> player(s).gameMode.destroyBlock(gold));
		ctx.waitTicks(5);
		c.check("breaking the Gold Block removes its record", markers(server, MarkerType.GOLD_BLOCK) == 0);
		boolean stillForced = server.computeOnServer(s -> s.overworld().getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.pack(gold.getX() >> 4, gold.getZ() >> 4)));
		c.check("...and its chunks are released", !stillForced);

		// ---------------------------------------------------------------- protection against a stranger
		c.section("Protection (the owner is swapped for a stranger)");
		SettlementData.World original = world(server);
		SettlementData.Settlement mine = original.settlements().get(0);
		SettlementData.Settlement theirs = new SettlementData.Settlement(mine.id(), mine.name(), UUID.randomUUID(), List.of(), mine.dimension(), mine.bell(),
			mine.state(), mine.setupLeft(), mine.stats(), mine.shared());
		server.runOnServer(s -> SettlementService.save(s, original.put(theirs)));
		server.runCommand("gamemode survival @p");
		ctx.waitTicks(5);
		BlockPos spot = a.offset(3, 0, 0);
		BlockPos gate = a.offset(-3, 0, -2);
		BlockPos chest = a.offset(-3, 0, 2);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
			PlacedBlocks.markPlaced(level, chest);
		});
		InteractionResult placed = place(server, new ItemStack(Items.STONE, 4), spot);
		c.check("a stranger cannot place a block in the settlement", !state(server, spot).is(Blocks.STONE) && placed == InteractionResult.FAIL,
			"result " + placed + ", block " + state(server, spot).getBlock());
		BlockPos floor = a.offset(1, -1, 1);
		boolean broke2 = server.computeOnServer(s -> player(s).gameMode.destroyBlock(floor));
		c.check("a stranger cannot break a block in the settlement", state(server, floor).is(Blocks.STONE) && !broke2, "destroyBlock " + broke2);
		boolean wasOpen = state(server, gate).getValue(FenceGateBlock.OPEN);
		server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.gameMode.useItemOn(p, p.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(gate), Direction.UP, gate, false));
		});
		c.check("a stranger can still use a gate (doors and levers stay usable)", state(server, gate).getValue(FenceGateBlock.OPEN) != wasOpen);
		InteractionResult opened = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.gameMode.useItemOn(p, p.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
		});
		c.check("a stranger cannot open a chest a member placed", opened == InteractionResult.FAIL, "result " + opened);
		boolean bellBroken = server.computeOnServer(s -> player(s).gameMode.destroyBlock(bell));
		ctx.waitTicks(3);
		c.check("a stranger cannot take the Bell", state(server, bell).is(Blocks.BELL) && !bellBroken);
		server.runOnServer(s -> SettlementService.save(s, original));
		server.runCommand("gamemode creative @p");
		ctx.waitTicks(5);
		c.check("the real owner is restored", world(server).settlements().get(0).owner().equals(mine.owner()));
		boolean mineBreaks = server.computeOnServer(s -> player(s).gameMode.destroyBlock(floor));
		c.check("the owner can break blocks in their own settlement", mineBreaks || !state(server, floor).is(Blocks.STONE));

		// ---------------------------------------------------------------- the switch
		c.section("Switch off");
		server.runCommand("vestiges config system settlements false");
		ctx.waitTicks(5);
		long recordsBefore = world(server).markers().size();
		BlockPos off = a.offset(2, 0, 3);
		put(ctx, server, MarkerType.WARD12, off);
		c.check("with Settlements off a marker is just a block (no record)", world(server).markers().size() == recordsBefore,
			"records " + recordsBefore + " -> " + world(server).markers().size());
		server.runCommand("vestiges config system settlements true");
		ctx.waitTicks(5);

		// ---------------------------------------------------------------- a fight stops placing (last: a fight lasts a while)
		c.section("Fights");
		server.runCommand("gamemode survival @p");
		boolean fight = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			Mob zombie = (Mob) MobSuite.type("zombie").spawn(level, p.blockPosition().offset(3, 0, 0), EntitySpawnReason.COMMAND);
			if (zombie == null) {
				return false;
			}
			p.hurtServer(level, level.damageSources().mobAttack(zombie), 1f);
			p.setLastHurtByMob(zombie);
			return SettlementService.inCombat(p);
		});
		if (fight) {
			long before2 = world(server).markers().size();
			put(ctx, server, MarkerType.BANNER16, a.offset(-1, 0, 4));
			c.check("a marker cannot be placed during a fight", world(server).markers().size() == before2, "records " + before2 + " -> " + world(server).markers().size());
		} else {
			c.note("could not put the test player in a fight, so the fight rule was not checked");
		}
		server.runCommand("gamemode creative @p");
		server.runCommand("kill @e[type=!minecraft:player]");
		c.note("not covered: homes (beds), real 10-minute timers (sped up with finish), a second player's membership, naming prompts, entry titles, explosions pruning records");
	}
}
