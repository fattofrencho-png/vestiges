package dev.vestiges.gametest;

import dev.vestiges.codex.CodexEntry;
import dev.vestiges.codex.CodexIndex;
import dev.vestiges.codex.CodexRules;
import dev.vestiges.codex.CodexService;
import dev.vestiges.codex.PlacedBlocks;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.tree.TreeRules;
import dev.vestiges.tree.TreeService;
import dev.vestiges.tree.UtilityPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** S7: the Codex fills from natural blocks, kills, breeding, places and items; placed blocks do not count; bonus points; admin commands; cluster-harvest gate. */
final class CodexSuite {
	private static final Pattern WHERE = Pattern.compile("\\[(-?\\d+), (~|-?\\d+), (-?\\d+)\\]");

	private CodexSuite() {
	}

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void setLevel(TestServerContext server, int level) {
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START.withXp(level, 0, 0)));
	}

	private static long totalXp(ServerPlayer p) {
		PlayerLevel l = LevelService.get(p);
		return LevelCurve.totalTo(l.level()) + l.xp();
	}

	private static long xp(TestServerContext server) {
		return server.computeOnServer(s -> totalXp(player(s)));
	}

	private static int count(TestServerContext server, String id) {
		return server.computeOnServer(s -> CodexService.get(player(s)).count(id));
	}

	private static int stage(TestServerContext server, String id) {
		return server.computeOnServer(s -> CodexService.get(player(s)).stage(id));
	}

	private static String at(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static boolean breakBlock(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> player(s).gameMode.destroyBlock(pos));
	}

	private static void clearMobs(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (Mob m : p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(30))) {
				m.discard();
			}
		});
	}

	private static Mob spawn(ServerPlayer p, String type, BlockPos pos) {
		return (Mob) MobSuite.type(type).spawn(p.level(), pos, EntitySpawnReason.COMMAND);
	}

	private static void killByPlayer(ServerPlayer p, Mob mob) {
		ServerLevel level = p.level();
		mob.hurtServer(level, level.damageSources().playerAttack(p), 100000f);
	}

	// ------------------------------------------------------------------ the suite

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode survival @p");
		server.runCommand("clear @p");
		server.runCommand("vestiges tree reset @p");
		server.runCommand("vestiges codex reset @p");
		setLevel(server, 10);
		var snapshot = T.switches();
		BlockPos pad = T.pad(ctx, server, 500, 500);

		blocks(ctx, server, c, pad, snapshot);
		mobs(ctx, server, c, pad);
		places(ctx, server, c);
		bonus(ctx, server, c, pad);
		commands(server, c);
		cluster(ctx, server, c, snapshot);
		farming(ctx, server, c, pad);
		visits(ctx, server, c);

		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges codex reset @p");
		setLevel(server, 20);
	}

	// ------------------------------------------------------------------ blocks

	private static void blocks(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad, Map<VestigesSystem, Boolean> snapshot) {
		c.section("Blocks: natural ones count, placed ones do not");
		CodexEntry stone = CodexIndex.get().entry("blocks/stone");
		c.check("the Codex has a Stone entry (common)", stone != null && "common".equals(stone.cls()));
		c.check("Stone starts not found", stage(server, "blocks/stone") == 0 && count(server, "blocks/stone") == 0);

		BlockPos cube = pad.offset(2, 0, 2);
		server.runCommand("execute in minecraft:overworld run fill " + at(cube) + " " + at(cube.offset(3, 3, 3)) + " minecraft:stone");
		ctx.waitTicks(3);
		long xp0 = xp(server);
		c.check("breaking the first natural stone works", breakBlock(server, cube));
		c.check("one stone broken: count 1, stage 1 (Discovered)", count(server, "blocks/stone") == 1 && stage(server, "blocks/stone") == 1,
			"count " + count(server, "blocks/stone") + ", stage " + stage(server, "blocks/stone"));
		long paid = xp(server) - xp0;
		c.check("Discovered pays " + CodexRules.xpFor(stone, 1) + " XP (common block)", paid == CodexRules.xpFor(stone, 1), "paid " + paid);

		// A placed stone, put down the way a player does (through the block item), does not count when broken.
		BlockPos placedAt = pad.offset(-3, 0, 0);
		boolean placed = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos floor = placedAt.below();
			ItemStack stack = new ItemStack(Items.STONE, 4);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
			stack.useOn(new UseOnContext(level, p, InteractionHand.MAIN_HAND, stack, hit));
			return level.getBlockState(placedAt).is(Blocks.STONE) && PlacedBlocks.isPlaced(level, placedAt);
		});
		c.check("a stone placed by the player is remembered as placed", placed);
		long xp1 = xp(server);
		breakBlock(server, placedAt);
		c.check("breaking the placed stone does not count", count(server, "blocks/stone") == 1, "count " + count(server, "blocks/stone"));
		c.check("breaking it pays no Codex XP", xp(server) == xp1, "paid " + (xp(server) - xp1));
		c.check("the placed mark is forgotten once the block is gone", !server.computeOnServer(s -> PlacedBlocks.isPlaced(player(s).level(), placedAt)));

		// The same spot, filled by a command (not a player), is natural.
		server.runCommand("execute in minecraft:overworld run setblock " + at(placedAt) + " minecraft:stone");
		breakBlock(server, placedAt);
		c.check("a stone put there by a command (not a player) counts", count(server, "blocks/stone") == 2, "count " + count(server, "blocks/stone"));

		int broken = 2;
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 4; y++) {
				for (int z = 0; z < 4; z++) {
					BlockPos p = cube.offset(x, y, z);
					if (p.equals(cube)) {
						continue;
					}
					if (broken == 63) {
						c.check("63 stones broken: still stage 1", stage(server, "blocks/stone") == 1, "stage " + stage(server, "blocks/stone"));
					}
					if (broken < 64 && breakBlock(server, p)) {
						broken++;
					}
				}
			}
		}
		c.check("64 natural stones broken: count 64", count(server, "blocks/stone") == 64, "count " + count(server, "blocks/stone"));
		c.check("64 stones: stage 2 (Familiar, common block needs 64)", stage(server, "blocks/stone") == 2, "stage " + stage(server, "blocks/stone"));
		long total = xp(server) - xp0;
		long expect = CodexRules.xpFor(stone, 1) + CodexRules.xpFor(stone, 2);
		c.check("Discovered + Familiar paid " + expect + " XP in all", total == expect, "paid " + total);

		// A real piece of terrain next to the pad.
		String terrain = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			for (int dx = 7; dx <= 14; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					int x = pad.getX() + dx;
					int z = pad.getZ() + dz;
					BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
					BlockState st = level.getBlockState(top);
					String id = CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(st.getBlock()));
					if (id != null && !PlacedBlocks.isPlaced(level, top) && !st.is(Blocks.STONE)) {
						int before = CodexService.get(p).count(id);
						boolean ok = p.gameMode.destroyBlock(top);
						return id + "|" + ok + "|" + (CodexService.get(p).count(id) - before);
					}
				}
			}
			return "";
		});
		if (terrain.isEmpty()) {
			c.note("no natural block next to the pad maps to a Codex entry (water or trees); the terrain check was skipped");
		} else {
			String[] t = terrain.split("\\|");
			c.check("breaking real terrain (" + t[0] + ") counts once", t[1].equals("true") && t[2].equals("1"), terrain);
		}

		// Counting stops when the Codex is off, and in creative.
		BlockPos single = pad.offset(0, 0, 3);
		server.runCommand("execute in minecraft:overworld run setblock " + at(single) + " minecraft:stone");
		int before = count(server, "blocks/stone");
		server.runCommand("vestiges config system codex false");
		ctx.waitTicks(3);
		breakBlock(server, single);
		c.check("with the Codex off, breaking stone counts nothing", count(server, "blocks/stone") == before);
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		server.runCommand("execute in minecraft:overworld run setblock " + at(single) + " minecraft:stone");
		server.runCommand("gamemode creative @p");
		breakBlock(server, single);
		c.check("a creative-mode player's breaks count nothing", count(server, "blocks/stone") == before);
		server.runCommand("gamemode survival @p");
		ctx.waitTicks(3);
	}

	// ------------------------------------------------------------------ mobs

	private static void mobs(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad) {
		c.section("Bestiary: kills, sight and breeding");
		clearMobs(server);
		BlockPos spot = pad.offset(3, 0, -3);
		c.check("Zombie starts not found", stage(server, "mobs/zombie") == 0);
		for (int i = 1; i <= 10; i++) {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				killByPlayer(p, spawn(p, "zombie", spot));
			});
			int n = i;
			if (n == 1) {
				c.check("1 zombie kill: stage 1 (Discovered)", stage(server, "mobs/zombie") == 1, "stage " + stage(server, "mobs/zombie"));
			}
			if (n == 9) {
				c.check("9 zombie kills: still stage 1", stage(server, "mobs/zombie") == 1 && count(server, "mobs/zombie") == 9,
					"stage " + stage(server, "mobs/zombie") + ", count " + count(server, "mobs/zombie"));
			}
		}
		c.check("10 zombie kills: stage 2", stage(server, "mobs/zombie") == 2 && count(server, "mobs/zombie") == 10,
			"stage " + stage(server, "mobs/zombie") + ", count " + count(server, "mobs/zombie"));
		double[] bestiary = new double[6];
		int[] stages = {2, 3, 4};
		for (int i = 0; i < 3; i++) {
			int st = stages[i];
			server.runCommand("vestiges codex set @p \"mobs/zombie\" " + st);
			double[] r = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Mob z = spawn(p, "zombie", spot);
				double[] out = {CodexService.damageMultiplier(p, z), CodexService.dropBonus(p, z)};
				z.discard();
				return out;
			});
			bestiary[i * 2] = r[0];
			bestiary[i * 2 + 1] = r[1];
		}
		c.check("Bestiary damage: +2% / +4% / +6% at stages 2 / 3 / 4", Math.abs(bestiary[0] - 1.02) < 1e-9 && Math.abs(bestiary[2] - 1.04) < 1e-9 && Math.abs(bestiary[4] - 1.06) < 1e-9,
			bestiary[0] + ", " + bestiary[2] + ", " + bestiary[4]);
		c.check("Bestiary drop bonus: 0 / +1% / +2% at stages 2 / 3 / 4", bestiary[1] == 0 && Math.abs(bestiary[3] - 0.01) < 1e-9 && Math.abs(bestiary[5] - 0.02) < 1e-9,
			bestiary[1] + ", " + bestiary[3] + ", " + bestiary[5]);

		// Seeing a peaceful mob discovers it.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mob cow = spawn(p, "cow", pad.offset(0, 0, -4));
			cow.setNoAi(true);
		});
		server.runCommand("execute as @p at @p run tp @s ~ ~ ~ facing " + pad.getX() + " " + pad.getY() + " " + (pad.getZ() - 4));
		ctx.waitTicks(50);
		c.check("a cow in plain sight is discovered (stage 1, no kills)", stage(server, "mobs/cow") == 1 && count(server, "mobs/cow") == 0,
			"stage " + stage(server, "mobs/cow") + ", count " + count(server, "mobs/cow"));
		clearMobs(server);

		// Breeding.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Animal a = (Animal) spawn(p, "cow", pad.offset(3, 0, 3));
			Animal b = (Animal) spawn(p, "cow", pad.offset(4, 0, 3));
			a.setInLove(p);
			b.setInLove(p);
		});
		int bred = 0;
		for (int i = 0; i < 30 && bred == 0; i++) {
			ctx.waitTicks(20);
			bred = count(server, "mobs/cow");
		}
		c.check("breeding two cows counts for the Bestiary", bred == 1, "cow count " + bred);
		clearMobs(server);
	}

	// ------------------------------------------------------------------ places

	private static void places(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Places: biome chunks and structures");
		server.runCommand("vestiges codex reset @p");
		String biome = CodexIndex.get().find(CodexEntry.BIOMES, Identifier.withDefaultNamespace("plains"));
		CodexEntry be = CodexIndex.get().entry(biome);
		c.check("the Codex has a Plains biome entry", be != null);
		long xp0 = xp(server);
		for (int i = 0; i < 32; i++) {
			long chunk = 1000L + (i % 31);
			// The 32nd call repeats an earlier chunk until a new one arrives, which must not count twice.
			server.runOnServer(s -> CodexService.onBiome(player(s), Identifier.withDefaultNamespace("plains"), chunk));
			if (i == 0) {
				c.check("first chunk of Plains: stage 1 (Discovered)", stage(server, biome) == 1 && count(server, biome) == 0);
			}
		}
		c.check("31 different chunks (one repeated): not yet Mastered", stage(server, biome) == 1, "stage " + stage(server, biome));
		server.runOnServer(s -> CodexService.onBiome(player(s), Identifier.withDefaultNamespace("plains"), 5000L));
		c.check("the 32nd different chunk makes Plains Mastered (stage 3)", stage(server, biome) == 3, "stage " + stage(server, biome));
		c.check("Mastered pays 100 XP", xp(server) - xp0 == 100, "paid " + (xp(server) - xp0));

		String village = CodexIndex.get().find(CodexEntry.STRUCTURES, Identifier.withDefaultNamespace("village_plains"));
		c.check("the Codex has a Plains Village entry", village != null);
		long xp1 = xp(server);
		for (long chunk : new long[] {11, 11, 22, 33}) {
			server.runOnServer(s -> CodexService.onStructure(player(s), Identifier.withDefaultNamespace("village_plains"), chunk));
		}
		c.check("three different villages make the entry Mastered (a repeat does not count)", stage(server, village) == 3, "stage " + stage(server, village));
		c.check("Mastered pays 100 XP", xp(server) - xp1 == 100, "paid " + (xp(server) - xp1));
	}

	// ------------------------------------------------------------------ bonus points

	private static void bonus(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad) {
		c.section("Bonus Craft points at 100 / 250 / 500 discovered entries");
		server.runCommand("vestiges codex reset @p");
		setLevel(server, 20);
		List<String> ids = new ArrayList<>(CodexIndex.get().entries.keySet());
		ids.remove("blocks/stone");
		c.note("the Codex has " + (ids.size() + 1) + " entries");
		int base = server.computeOnServer(s -> TreeService.points(player(s), "craft"));
		c.check("with an empty Codex the Craft wheel has " + TreeRules.craftPoints(20, 0) + " points at level 20", base == TreeRules.craftPoints(20, 0), "points " + base);
		int[] steps = {99, 100, 249, 250, 499, 500};
		int done = 0;
		for (int target : steps) {
			if (target > ids.size()) {
				c.note("only " + (ids.size() + 1) + " entries, so the " + target + "-entry step could not be tested");
				continue;
			}
			int from = done;
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				for (int i = from; i < target; i++) {
					CodexService.setStageQuiet(p, ids.get(i), 1);
				}
			});
			done = target;
			int discovered = server.computeOnServer(s -> CodexService.discovered(player(s)));
			int points = server.computeOnServer(s -> TreeService.points(player(s), "craft"));
			int bonus = server.computeOnServer(s -> CodexService.bonusPoints(player(s)));
			c.check(target + " entries discovered: " + CodexRules.bonusPoints(target, false) + " bonus point(s) and " + TreeRules.craftPoints(20, CodexRules.bonusPoints(target, false))
				+ " Craft points", discovered == target && bonus == CodexRules.bonusPoints(target, false) && points == TreeRules.craftPoints(20, bonus),
				"discovered " + discovered + ", bonus " + bonus + ", points " + points);
		}

		// The natural path: one more entry found by breaking a stone pays the next bonus point as it is found.
		server.runCommand("vestiges codex reset @p");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (int i = 0; i < 99; i++) {
				CodexService.setStageQuiet(p, ids.get(i), 1);
			}
		});
		int before = server.computeOnServer(s -> TreeService.points(player(s), "craft"));
		BlockPos stone = pad.offset(0, 0, 4);
		server.runCommand("execute in minecraft:overworld run setblock " + at(stone) + " minecraft:stone");
		breakBlock(server, stone);
		int after = server.computeOnServer(s -> TreeService.points(player(s), "craft"));
		c.check("finding the 100th entry by breaking a stone adds one Craft point", after == before + 1 && stage(server, "blocks/stone") == 1,
			"points " + before + " -> " + after);
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges codex set @p all 1");
		int all = server.computeOnServer(s -> CodexService.discovered(player(s)));
		c.check("`codex set @p all 1` discovers every entry", all == CodexIndex.get().entries.size(), all + " of " + CodexIndex.get().entries.size());
		server.runCommand("vestiges codex reset @p");
	}

	// ------------------------------------------------------------------ commands

	private static void commands(TestServerContext server, Checks c) {
		c.section("`vestiges codex set` and `reset`");
		server.runCommand("vestiges codex reset @p");
		c.expectOk(server, "vestiges codex set @p \"blocks/stone\" 3");
		c.check("set stone to stage 3: stage 3 and count 256", stage(server, "blocks/stone") == 3 && count(server, "blocks/stone") == 256,
			"stage " + stage(server, "blocks/stone") + ", count " + count(server, "blocks/stone"));
		c.expectOk(server, "vestiges codex set @p \"mobs/zombie\" 4");
		c.check("set zombie to stage 4", stage(server, "mobs/zombie") == 4);
		c.expectOk(server, "vestiges codex set @p \"blocks/stone\" 0");
		c.check("set stone back to stage 0: stage 0 and count 0", stage(server, "blocks/stone") == 0 && count(server, "blocks/stone") == 0);
		c.expectRejected(server, "vestiges codex set @p \"blocks/stone\" 5");
		c.expectRejected(server, "vestiges codex set @p \"nonsense/entry\" 1");
		c.expectOk(server, "vestiges codex info @p");
		c.expectOk(server, "vestiges codex reset @p");
		c.check("reset empties the Codex", server.computeOnServer(s -> CodexService.discovered(player(s))) == 0 && stage(server, "mobs/zombie") == 0);
	}

	// ------------------------------------------------------------------ cluster harvest gate

	private static void cluster(ClientGameTestContext ctx, TestServerContext server, Checks c, Map<VestigesSystem, Boolean> snapshot) {
		c.section("Cluster harvest only takes Discovered blocks");
		server.runCommand("vestiges codex reset @p");
		Block unmapped = null;
		for (Block b : new Block[] {Blocks.BEDROCK, Blocks.BARRIER, Blocks.COMMAND_BLOCK, Blocks.STRUCTURE_VOID}) {
			if (CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(b)) == null) {
				unmapped = b;
				break;
			}
		}
		Block un = unmapped;
		c.check("moss is a cluster block and stone is not", UtilityPowers.clusterGroup(Blocks.MOSS_BLOCK.defaultBlockState()) >= 0
			&& UtilityPowers.clusterGroup(Blocks.STONE.defaultBlockState()) < 0);
		c.check("moss is in the Codex", CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(Blocks.MOSS_BLOCK)) != null);
		boolean undiscovered = server.computeOnServer(s -> CodexService.discoveredBlock(player(s), Blocks.MOSS_BLOCK));
		c.check("undiscovered moss is refused", !undiscovered);
		server.runCommand("vestiges codex set @p \"blocks/moss_block\" 1");
		c.check("Discovered moss is allowed", server.computeOnServer(s -> CodexService.discoveredBlock(player(s), Blocks.MOSS_BLOCK)));
		boolean coral = server.computeOnServer(s -> CodexService.discoveredBlock(player(s), Blocks.BRAIN_CORAL_BLOCK));
		c.check("other undiscovered cluster blocks (brain coral) stay refused", !coral);
		if (un != null) {
			c.check("a block with no Codex entry is always allowed", server.computeOnServer(s -> CodexService.discoveredBlock(player(s), un)));
		}
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges config system codex false");
		ctx.waitTicks(3);
		c.check("with the Codex off, cluster harvest takes anything", server.computeOnServer(s -> CodexService.discoveredBlock(player(s), Blocks.MOSS_BLOCK)));
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		c.note("the cluster-harvest power itself belongs to the Lancer ascendancy (dropped from the plan); only the Codex gate that it asks is tested");
	}

	// ------------------------------------------------------------------ farming

	private static void farming(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad) {
		c.section("Right-click harvest counts only crops that nobody planted");
		String entry = CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(Blocks.NETHER_WART));
		c.check("the Codex has a Nether Wart entry", entry != null);
		BlockPos natural = pad.offset(-2, 0, -3);
		BlockPos planted = pad.offset(-4, 0, -3);
		server.runCommand("execute in minecraft:overworld run setblock " + at(natural.below()) + " minecraft:soul_sand");
		server.runCommand("execute in minecraft:overworld run setblock " + at(natural) + " minecraft:nether_wart[age=3]");
		server.runCommand("execute in minecraft:overworld run setblock " + at(planted.below()) + " minecraft:soul_sand");
		boolean plantedOk = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			ItemStack seed = new ItemStack(Items.NETHER_WART, 2);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(planted.below()).add(0, 0.5, 0), Direction.UP, planted.below(), false);
			seed.useOn(new UseOnContext(level, p, InteractionHand.MAIN_HAND, seed, hit));
			if (!level.getBlockState(planted).is(Blocks.NETHER_WART)) {
				return false;
			}
			level.setBlock(planted, level.getBlockState(planted).setValue(NetherWartBlock.AGE, 3), 3);
			return PlacedBlocks.isPlaced(level, planted);
		});
		c.check("a nether wart planted by the player is marked as placed and grown to ripe", plantedOk);
		ctx.waitTicks(3);
		int before = count(server, entry);
		int result = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var r = UseBlockCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(natural), Direction.UP, natural, false));
			return r.consumesAction() ? 1 : 0;
		});
		c.check("right-clicking a ripe wild nether wart harvests it", result == 1);
		c.check("a wild crop harvested by right-click counts once", count(server, entry) == before + 1, "count " + before + " -> " + count(server, entry));
		int mid = count(server, entry);
		int result2 = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var r = UseBlockCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(planted), Direction.UP, planted, false));
			return r.consumesAction() ? 1 : 0;
		});
		c.check("right-clicking the ripe wart the player planted harvests it", result2 == 1);
		c.check("a crop the player planted counts nothing", count(server, entry) == mid, "count " + mid + " -> " + count(server, entry));
	}

	// ------------------------------------------------------------------ real world visits

	private static void visits(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Visiting biomes and a village by teleport");
		server.runCommand("vestiges codex reset @p");
		String here = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
		});
		String hereEntry = CodexIndex.get().find(CodexEntry.BIOMES, Identifier.parse(here));
		ctx.waitTicks(50);
		if (hereEntry != null) {
			c.check("standing in " + here + " discovers its entry", stage(server, hereEntry) >= 1, "stage " + stage(server, hereEntry));
		} else {
			c.note("the biome at the pad (" + here + ") has no Codex entry");
		}
		for (String biome : new String[] {"minecraft:desert", "minecraft:snowy_plains", "minecraft:jungle"}) {
			Checks.Result r = c.run(server, "execute in minecraft:overworld run locate biome " + biome);
			Matcher m = WHERE.matcher(r.text());
			if (r.crashed() || r.refused() || !m.find()) {
				c.note(biome + " could not be located: " + r.text());
				continue;
			}
			int x = Integer.parseInt(m.group(1));
			int z = Integer.parseInt(m.group(3));
			String id = CodexIndex.get().find(CodexEntry.BIOMES, Identifier.parse(biome));
			T.goTo(ctx, server, x, z, 20);
			ctx.waitTicks(60);
			String standing = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
			});
			if (!standing.equals(biome)) {
				c.note("teleported to " + x + ", " + z + " for " + biome + " but stood in " + standing);
			}
			String standingEntry = CodexIndex.get().find(CodexEntry.BIOMES, Identifier.parse(standing));
			c.check("teleporting into " + standing + " discovers it in the Codex", standingEntry != null && stage(server, standingEntry) >= 1,
				standingEntry == null ? "no entry for " + standing : "stage " + stage(server, standingEntry));
		}
		Checks.Result v = c.run(server, "execute in minecraft:overworld run locate structure #minecraft:village");
		Matcher m = WHERE.matcher(v.text());
		if (v.crashed() || v.refused() || !m.find()) {
			c.note("no village could be located: " + v.text());
			return;
		}
		T.goTo(ctx, server, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(3)), 20);
		ctx.waitTicks(90);
		boolean any = false;
		StringBuilder seen = new StringBuilder();
		for (String id : CodexIndex.get().entries.keySet()) {
			if (id.startsWith("structures/village") && stage(server, id) >= 1) {
				any = true;
				seen.append(id).append(' ');
			}
		}
		c.check("standing in a village discovers a village entry", any, "located at " + m.group(1) + ", " + m.group(3));
		if (any) {
			c.note("village entries found: " + seen);
		}
	}
}
