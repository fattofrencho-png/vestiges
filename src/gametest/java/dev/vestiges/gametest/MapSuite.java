package dev.vestiges.gametest;

import dev.vestiges.client.map.MapStore;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.map.MapMath;
import dev.vestiges.server.Emeralds;
import dev.vestiges.waystone.Waystone;
import dev.vestiges.waystone.WaystoneItems;
import dev.vestiges.waystone.WaystoneList;
import dev.vestiges.waystone.WaystoneService;
import dev.vestiges.waystone.WaystoneStore;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/** S2: the map fills in everywhere the player goes, and waystones are placed, used, paid for, renamed and broken by the rules. */
final class MapSuite {
	private static final int[][] SPOTS = {{0, 0}, {400, 300}, {-700, 500}, {1200, -900}, {-1500, -1200}, {2500, 2500}, {-3000, 800},
		{900, 3200}, {-2200, -2800}, {4000, -500}};

	private MapSuite() {
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		map(ctx, server, c);
		waystones(ctx, server, c);
	}

	// ------------------------------------------------------------------ map

	private static void map(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Map: every place the player goes is drawn");
		Identifier overworld = ctx.computeOnClient(mc -> mc.level.dimension().identifier());
		TreeSet<String> biomes = new TreeSet<>();
		int[] offsets = {-8, 0, 8};
		for (int[] spot : SPOTS) {
			int x = spot[0];
			int z = spot[1];
			int y = T.goTo(ctx, server, x, z, 100);
			int filled = 0;
			int total = 0;
			List<Integer> colours = new ArrayList<>();
			for (int dx : offsets) {
				for (int dz : offsets) {
					int argb = MapStore.pixel(overworld, MapMath.SURFACE, x + dx, z + dz);
					total++;
					if ((argb >>> 24) != 0) {
						filled++;
						colours.add(argb);
					}
				}
			}
			String biome = server.computeOnServer(s -> s.overworld().getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
			biomes.add(biome);
			c.check("map is drawn around " + x + ", " + z + " (" + biome + ")", filled >= 8, filled + " of " + total + " columns drawn");
			c.check("map colours near " + x + ", " + z + " are not all black", colours.stream().anyMatch(col -> (col & 0xFFFFFF) != 0));
		}
		c.note("biomes visited: " + biomes);

		server.runCommand("execute in minecraft:the_end run tp @p 0 100 0");
		ctx.waitTicks(120);
		Identifier end = ctx.computeOnClient(mc -> mc.level.dimension().identifier());
		c.check("the End is entered", end.getPath().equals("the_end"), end.toString());
		int drawn = 0;
		for (int dx : offsets) {
			for (int dz : offsets) {
				if ((MapStore.pixel(end, MapMath.SURFACE, dx, dz) >>> 24) != 0) {
					drawn++;
				}
			}
		}
		c.check("the End island is drawn on the map", drawn >= 6, drawn + " of 9");

		server.runCommand("execute in minecraft:the_nether run tp @p 0 100 0");
		ctx.waitTicks(120);
		Identifier nether = ctx.computeOnClient(mc -> mc.level.dimension().identifier());
		c.check("the Nether is entered without trouble", nether.getPath().equals("the_nether"), nether.toString());
		c.note("Nether map uses slices; only the arrival was checked");
		T.goTo(ctx, server, 0, 0, 40);
	}

	// ------------------------------------------------------------------ waystones

	private static void waystones(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Waystones");
		server.runCommand("clear @p");
		server.runCommand("gamemode survival @p");
		server.runCommand("vestiges config system settlements true");
		server.runCommand("vestiges config system waystones true");
		int ax = 100;
		int az = 100;
		int ay = T.goTo(ctx, server, ax, az, 30);
		guard(c, server, "after arriving at the first waystone spot");

		guard(c, server, "before the first placement");
		// Placing outside a settlement is refused while settlements are on.
		int before = count(server);
		place(server, ax, ay, az);
		ctx.waitTicks(10);
		guard(c, server, "after the refused placement");
		c.check("a waystone is refused outside a settlement", count(server) == before, "waystones: " + count(server));

		// With settlements off the rule steps aside and the waystone is placed.
		server.runCommand("vestiges config system settlements false");
		place(server, ax, ay, az);
		ctx.waitTicks(10);
		c.check("a waystone is placed (settlements off)", count(server) == before + 1, "waystones: " + count(server));
		Waystone a = find(server, ax, az);
		c.check("the waystone is stored where it was placed", a != null && a.pos().equals(new BlockPos(ax, ay, az)),
			a == null ? "not found" : a.pos().toString());
		c.check("the waystone belongs to the player", a != null && a.owner().equals(T.player(server).getUUID()));
		c.check("the block is a lodestone", server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(ax, ay, az)).is(net.minecraft.world.level.block.Blocks.LODESTONE)));

		// A second one 600 blocks east.
		int bx = ax + 600;
		int bz = az;
		guard(c, server, "before going to the second spot");
		int by = T.goTo(ctx, server, bx, bz, 30);
		guard(c, server, "after arriving at the second spot");
		place(server, bx, by, bz);
		ctx.waitTicks(10);
		Waystone b = find(server, bx, bz);
		String why = server.computeOnServer(s -> "below=" + s.overworld().getBlockState(new BlockPos(bx, by - 1, bz)).getBlock() + ", at=" + s.overworld().getBlockState(new BlockPos(bx, by, bz)).getBlock()
			+ ", biome=" + s.overworld().getBiome(new BlockPos(bx, by, bz)).unwrapKey().map(k -> k.identifier().toString()).orElse("?") + ", list=" + WaystoneStore.get(s).waystones().stream().map(w -> w.pos().toShortString()).toList());
		c.check("a second waystone is placed 600 blocks away", b != null && count(server) == before + 2, why);
		if (a == null || b == null) {
			c.check("waystone tests can go on", false, "a waystone is missing");
			return;
		}

		// Travel rules.
		int fromId = a.id();
		int toId = b.id();
		server.runCommand("give @p minecraft:emerald 10");
		T.goTo(ctx, server, ax + 60, az + 60, 10);
		Vec3 farPos = pos(server);
		travel(server, fromId, toId);
		ctx.waitTicks(5);
		c.check("travel is refused when far from the starting waystone", flat(pos(server), farPos) < 2 && emeralds(server) == 10,
			pos(server) + ", emeralds " + emeralds(server));

		T.goTo(ctx, server, ax + 2, az, 5);
		server.runCommand("clear @p minecraft:emerald");
		Vec3 start = pos(server);
		travel(server, fromId, toId);
		ctx.waitTicks(5);
		c.check("travel is refused without emeralds", pos(server).distanceTo(start) < 2, pos(server).toString());

		server.runCommand("give @p minecraft:emerald 10");
		server.runCommand("execute at @p run summon minecraft:zombie ~2 ~ ~ {NoAI:1b,PersistenceRequired:1b}");
		ctx.waitTicks(5);
		boolean hurt = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var zombies = p.level().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, p.getBoundingBox().inflate(6));
			if (zombies.isEmpty()) {
				return false;
			}
			p.setLastHurtByMob(zombies.get(0));
			return true;
		});
		c.check("a zombie is marked as the one that hurt the player (setting up the fight)", hurt, state(server));
		ctx.waitTicks(3);
		start = pos(server);
		travel(server, fromId, toId);
		ctx.waitTicks(5);
		c.check("travel is refused during a fight", pos(server).distanceTo(start) < 2 && emeralds(server) == 10,
			pos(server) + ", emeralds " + emeralds(server));
		c.note("after the fight setup: " + state(server));
		server.runCommand("kill @e[type=minecraft:zombie]");
		server.runCommand("effect give @p minecraft:instant_health 1 5");
		ctx.waitTicks(230);

		c.note("before the real trip: " + state(server));
		travel(server, fromId, toId);
		ctx.waitTicks(10);
		Vec3 arrived = pos(server);
		double off = arrived.distanceTo(Vec3.atCenterOf(b.pos()));
		c.check("travel arrives at the other waystone", off < 3, "distance from it " + off);
		int spent = 10 - emeralds(server);
		c.check("600 blocks cost 2 emeralds", spent == 2, "spent " + spent);

		// Creative travels free; the Bank pays when the pockets are empty.
		server.runCommand("gamemode creative @p");
		server.runCommand("clear @p minecraft:emerald");
		travel(server, toId, fromId);
		ctx.waitTicks(10);
		c.check("creative travels free, back to the first waystone", pos(server).distanceTo(Vec3.atCenterOf(a.pos())) < 3, pos(server).toString());
		server.runCommand("gamemode survival @p");
		T.goTo(ctx, server, ax + 2, az, 5);
		server.runOnServer(s -> ((ExtraHolder) s.getPlayerList().getPlayers().get(0)).vestiges$setBank(5L));
		travel(server, fromId, toId);
		ctx.waitTicks(10);
		long bank = server.computeOnServer(s -> Emeralds.bank(s.getPlayerList().getPlayers().get(0)));
		c.check("the Bank pays when the pockets are empty", pos(server).distanceTo(Vec3.atCenterOf(b.pos())) < 3 && bank == 3,
			"bank " + bank + ", at " + pos(server));

		// Names.
		server.runOnServer(s -> WaystoneService.rename(s.getPlayerList().getPlayers().get(0), fromId, "  Home   Base  "));
		c.check("a name is cleaned up", "Home Base".equals(byId(server, fromId).name()), byId(server, fromId).name());
		server.runOnServer(s -> WaystoneService.rename(s.getPlayerList().getPlayers().get(0), fromId, "x".repeat(60)));
		c.check("a long name is cut to 32", byId(server, fromId).name().length() <= 32, byId(server, fromId).name());

		// A lodestone that is no longer there is dropped from the list.
		server.runCommand("execute in minecraft:overworld run forceload add " + bx + " " + bz);
		server.waitFor(s -> s.overworld().getChunkSource().getChunkNow(bx >> 4, bz >> 4) != null, 600);
		server.runCommand("setblock " + bx + " " + by + " " + bz + " minecraft:air");
		T.goTo(ctx, server, ax + 2, az, 5);
		travel(server, fromId, toId);
		ctx.waitTicks(5);
		c.check("a waystone whose block is gone is not travelled to", flat(pos(server), Vec3.atCenterOf(a.pos())) < 5, pos(server).toString());
		c.check("a waystone whose block is gone leaves the list", byId(server, toId) == null);
		server.runCommand("execute in minecraft:overworld run forceload remove " + bx + " " + bz);
		server.runOnServer(s -> WaystoneService.sendMarkers(s.getPlayerList().getPlayers().get(0)));

		// Breaking: the owner gets the item back, the entry goes.
		T.goTo(ctx, server, ax + 2, az, 5);
		int listed = count(server);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.gameMode.destroyBlock(new BlockPos(ax, ay, az));
		});
		ctx.waitTicks(10);
		c.check("breaking the waystone removes it from the list", find(server, ax, az) == null, "waystones " + listed + " -> " + count(server));
		boolean dropped = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(ax, ay, az)).inflate(6),
			e -> WaystoneItems.isWaystone(e.getItem())).isEmpty());
		c.check("the owner gets the Waystone item back", dropped);
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("gamemode creative @p");
		server.runCommand("vestiges config system settlements true");
		c.note("not covered: a second player breaking, sharing or using someone else's waystone; the price cap (undecided)");
	}

	private static void place(TestServerContext server, int x, int y, int z) {
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.teleportTo(x + 3.5, s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 3, z), z + 0.5);
			p.setItemInHand(InteractionHand.MAIN_HAND, WaystoneItems.create(1));
			BlockPos below = new BlockPos(x, y - 1, z);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
			p.gameMode.useItemOn(p, p.level(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
			p.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		});
	}

	private static void travel(TestServerContext server, int from, int to) {
		server.runOnServer(s -> WaystoneService.travel(s.getPlayerList().getPlayers().get(0), from, to));
	}

	private static void guard(Checks c, TestServerContext server, String when) {
		String s = state(server);
		if (s.contains("dead true")) {
			c.note("PLAYER IS DEAD " + when + ": " + s);
		}
	}

	private static String state(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return "pos " + p.position() + ", health " + p.getHealth() + ", dead " + p.isDeadOrDying() + ", mode " + p.gameMode.getGameModeForPlayer()
				+ ", lastHurtBy " + p.getLastHurtByMob() + " at " + p.getLastHurtByMobTimestamp() + ", tick " + p.tickCount
				+ (p.isDeadOrDying() ? ", death: " + p.getCombatTracker().getDeathMessage().getString() : "");
		});
	}

	private static double flat(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static Vec3 pos(TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).position());
	}

	private static int emeralds(TestServerContext server) {
		return server.computeOnServer(s -> Emeralds.count(s.getPlayerList().getPlayers().get(0)));
	}

	private static int count(TestServerContext server) {
		return server.computeOnServer(s -> WaystoneStore.get(s).waystones().size());
	}

	private static Waystone find(TestServerContext server, int x, int z) {
		return server.computeOnServer(s -> {
			WaystoneList list = WaystoneStore.get(s);
			for (Waystone w : list.waystones()) {
				if (w.pos().getX() == x && w.pos().getZ() == z) {
					return w;
				}
			}
			return null;
		});
	}

	private static Waystone byId(TestServerContext server, int id) {
		return server.computeOnServer(s -> WaystoneStore.get(s).byId(id));
	}
}
