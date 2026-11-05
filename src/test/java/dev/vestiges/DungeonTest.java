package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.dungeon.DungeonPlots;
import dev.vestiges.mobs.MobData;
import dev.vestiges.dungeon.DungeonRoom;
import dev.vestiges.dungeon.RoomBuilder;
import dev.vestiges.dungeon.RoomLayout;
import dev.vestiges.dungeon.DungeonRules;
import dev.vestiges.dungeon.DungeonRuns;
import dev.vestiges.dungeon.DungeonTheme;
import dev.vestiges.dungeon.PortalFrame;
import dev.vestiges.dungeon.PortalFrame.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The dungeon rules that need no world: the portal frame, the plot grid, the saved runs and the theme data. */
class DungeonTest {
	/** A little world of blocks for the frame finder: anything not set is air. */
	private static final class Grid implements PortalFrame.Blocks {
		final Map<String, Kind> blocks = new HashMap<>();

		void set(int x, int y, int z, Kind k) {
			blocks.put(x + "," + y + "," + z, k);
		}

		@Override
		public Kind at(int x, int y, int z) {
			return blocks.getOrDefault(x + "," + y + "," + z, Kind.AIR);
		}

		/** A frame with its lowest interior corner at (x, y, z); {@code corners} also fills the four corner blocks with stone. */
		Grid frame(int axis, int x, int y, int z, int w, int h, boolean corners) {
			for (int i = 0; i < w; i++) {
				put(axis, x, y - 1, z, i, Kind.COPPER);
				put(axis, x, y + h, z, i, Kind.COPPER);
			}
			for (int j = 0; j < h; j++) {
				put(axis, x, y + j, z, -1, Kind.TUFF);
				put(axis, x, y + j, z, w, Kind.TUFF);
			}
			if (corners) {
				put(axis, x, y - 1, z, -1, Kind.OTHER);
				put(axis, x, y - 1, z, w, Kind.OTHER);
				put(axis, x, y + h, z, -1, Kind.OTHER);
				put(axis, x, y + h, z, w, Kind.OTHER);
			}
			return this;
		}

		private void put(int axis, int x, int y, int z, int i, Kind k) {
			set(axis == 0 ? x + i : x, y, axis == 0 ? z : z + i, k);
		}
	}

	@Test
	void theSmallestFrameIsFoundFromInsideAndFromItsBlocks() {
		Grid g = new Grid().frame(0, 10, 70, 5, 2, 3, false);
		PortalFrame.Shape s = PortalFrame.find(g, 10, 70, 5);
		assertNotNull(s);
		assertEquals(new PortalFrame.Shape(0, 10, 70, 5, 2, 3), s);
		assertEquals(s, PortalFrame.find(g, 9, 71, 5), "a side block");
		assertEquals(s, PortalFrame.find(g, 11, 69, 5), "a bottom block");
		assertEquals(s, PortalFrame.find(g, 10, 73, 5), "a top block");
		assertEquals(11.0, s.centerX());
		assertEquals(5.5, s.centerZ());
	}

	@Test
	void anySizeFromTwoByThreeToTwentyOneSquareWorks() {
		assertNotNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 4, 4, false), 1, 71, 0));
		assertNotNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 3, 7, false), 1, 71, 0));
		assertNotNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 21, 21, false), 5, 75, 0));
		assertNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 22, 5, false), 5, 71, 0), "too wide");
		assertNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 5, 22, false), 2, 71, 0), "too tall");
		assertNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 1, 3, false), 0, 71, 0), "too narrow");
		assertNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 2, 2, false), 0, 70, 0), "too short");
	}

	@Test
	void theFrameCanStandAlongEitherAxis() {
		Grid g = new Grid().frame(1, 7, 70, 20, 3, 4, false);
		PortalFrame.Shape s = PortalFrame.find(g, 7, 71, 21);
		assertEquals(new PortalFrame.Shape(1, 7, 70, 20, 3, 4), s);
		assertEquals(7.5, s.centerX());
		assertEquals(21.5, s.centerZ());
	}

	@Test
	void cornersAreNotNeededButMayBeThere() {
		assertNotNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 2, 3, false), 0, 70, 0));
		assertNotNull(PortalFrame.find(new Grid().frame(0, 0, 70, 0, 2, 3, true), 0, 70, 0));
	}

	@Test
	void aWrongOrMissingFrameBlockBreaksIt() {
		Grid wrongSide = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		wrongSide.set(-1, 71, 0, Kind.COPPER);
		assertNull(PortalFrame.find(wrongSide, 0, 70, 0), "copper where tuff belongs");
		Grid swapped = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		swapped.set(0, 73, 0, Kind.TUFF);
		assertNull(PortalFrame.find(swapped, 0, 70, 0), "tuff where copper belongs");
		Grid gap = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		gap.set(1, 69, 0, Kind.AIR);
		assertNull(PortalFrame.find(gap, 0, 70, 0), "a hole in the bottom");
		Grid stone = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		stone.set(1, 71, 0, Kind.OTHER);
		assertNull(PortalFrame.find(stone, 0, 70, 0), "something inside");
	}

	@Test
	void anOpenFrameAndOutsideAirFindNothing() {
		Grid g = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		assertNull(PortalFrame.find(g, 20, 70, 20), "far away");
		assertNull(PortalFrame.find(new Grid(), 0, 70, 0), "no frame at all");
		Grid open = new Grid().frame(0, 0, 70, 0, 2, 3, false);
		open.set(-1, 72, 0, Kind.AIR);
		assertNull(PortalFrame.find(open, 0, 70, 0), "the side is open, the air runs out");
	}

	@Test
	void frameKitBuildsTheSmallestFrame() {
		assertEquals(PortalFrame.KIT_TUFF, 2 * PortalFrame.MIN_HEIGHT);
		assertEquals(PortalFrame.KIT_COPPER, 2 * PortalFrame.MIN_WIDTH);
	}

	@Test
	void plotsAreFarApartAndAddressable() {
		Set<String> seen = new HashSet<>();
		for (int plot = 0; plot < 64; plot++) {
			assertTrue(seen.add(DungeonPlots.originX(plot) + "," + DungeonPlots.originZ(plot)), "plot " + plot + " is unique");
			assertEquals(plot, DungeonPlots.plotAt(DungeonPlots.originX(plot) + 300, DungeonPlots.originZ(plot) + 20), "plot " + plot);
			assertEquals(plot, DungeonPlots.plotAt(DungeonPlots.originX(plot), DungeonPlots.originZ(plot) - 24), "plot " + plot + " north edge");
		}
		assertEquals(0, DungeonPlots.originX(0));
		assertEquals(1024, DungeonPlots.originX(1));
		assertEquals(1024, DungeonPlots.originZ(DungeonPlots.COLUMNS));
		assertEquals(-1, DungeonPlots.plotAt(-5, 0));
	}

	@Test
	void livesFollowTheTier() {
		assertEquals(3, DungeonRules.startingLives(1));
		assertEquals(3, DungeonRules.startingLives(9));
		assertEquals(2, DungeonRules.startingLives(10));
		assertEquals(2, DungeonRules.startingLives(24));
		assertEquals(1, DungeonRules.startingLives(25));
		assertEquals(1, DungeonRules.startingLives(300));
	}

	@Test
	void monstersScaleWithTheTierAndTheParty() {
		assertEquals(15, DungeonRules.mobLevel(1), "tier 1 is level 15");
		assertEquals(18, DungeonRules.mobLevel(2));
		assertEquals(21, DungeonRules.mobLevel(3));
		assertEquals(42, DungeonRules.mobLevel(10));
		assertEquals(15, DungeonRules.mobLevel(0), "never below tier 1");
		assertEquals(1.0, DungeonRules.partyHealth(1));
		assertEquals(1.5, DungeonRules.partyHealth(2), "+50% for each extra player");
		assertEquals(2.5, DungeonRules.partyHealth(4));
		assertEquals(1.0, DungeonRules.partyHealth(0));
		assertEquals(6, DungeonRules.MIN_MONSTERS);
		assertEquals(10, DungeonRules.MAX_MONSTERS);
	}

	@Test
	void bossRanksAreSixAndTenTimesAsTough() {
		assertEquals(6, new MobData(15, MobData.LIEUTENANT, List.of(), 1f, false).strength());
		assertEquals(10, new MobData(15, MobData.BOSS, List.of(), 1f, false).strength());
		assertEquals(5, new MobData(15, MobData.LIEUTENANT, List.of(), 1f, false).damageStrength(), "damage stops at a Warlord's x5");
		assertEquals(5, new MobData(15, MobData.BOSS, List.of(), 1f, false).damageStrength());
		assertEquals(3, new MobData(15, MobData.ELITE, List.of(), 1f, false).damageStrength(), "the other ranks are unchanged");
		assertEquals(1, new MobData(15, MobData.NORMAL, List.of(), 1f, false).damageStrength());
		assertTrue(new MobData(15, MobData.BOSS, List.of(), 1f, false).elite());
	}

	@Test
	void theNextKeyFollowsTheAgreedOdds() {
		// After clearing tier 5: lower 15%, same 55%, one up 25%, two up 5%.
		assertEquals(4, DungeonRules.nextTier(5, 0.0));
		assertEquals(4, DungeonRules.nextTier(5, 0.149));
		assertEquals(5, DungeonRules.nextTier(5, 0.15));
		assertEquals(5, DungeonRules.nextTier(5, 0.699));
		assertEquals(6, DungeonRules.nextTier(5, 0.70));
		assertEquals(6, DungeonRules.nextTier(5, 0.949));
		assertEquals(7, DungeonRules.nextTier(5, 0.95));
		assertEquals(7, DungeonRules.nextTier(5, 0.9999));
		assertEquals(1, DungeonRules.nextTier(1, 0.0), "never below tier 1");
		Random random = new Random(7);
		int[] seen = new int[4];
		int n = 200_000;
		for (int i = 0; i < n; i++) {
			seen[DungeonRules.nextTier(10, random.nextDouble()) - 9]++;
		}
		assertEquals(0.15, seen[0] / (double) n, 0.01);
		assertEquals(0.55, seen[1] / (double) n, 0.01);
		assertEquals(0.25, seen[2] / (double) n, 0.01);
		assertEquals(0.05, seen[3] / (double) n, 0.01);
	}

	@Test
	void theNextKeyKeepsTheThemeSixtyPercentOfTheTime() {
		List<String> open = List.of("end", "overgrown", "trial");
		assertEquals("overgrown", DungeonRules.nextTheme("overgrown", open, 0.0, 0.9));
		assertEquals("overgrown", DungeonRules.nextTheme("overgrown", open, 0.599, 0.9));
		assertEquals("end", DungeonRules.nextTheme("overgrown", open, 0.60, 0.0), "changed: one of the others");
		assertEquals("trial", DungeonRules.nextTheme("overgrown", open, 0.99, 0.99));
		assertEquals("overgrown", DungeonRules.nextTheme("overgrown", List.of("overgrown"), 0.99, 0.5), "no other theme is open");
		assertEquals("overgrown", DungeonRules.nextTheme("overgrown", List.of(), 0.99, 0.5));
	}

	@Test
	void everyThemeHasItsDungeonLootTables() throws IOException {
		Path themes = Path.of("src/main/resources/data/vestiges/vestiges/dungeon_themes");
		Path tables = Path.of("src/main/resources/data/vestiges/loot_table/dungeon");
		try (var files = Files.list(themes)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				String theme = f.getFileName().toString().replace(".json", "");
				for (String kind : List.of("cache", "lieutenant", "reward")) {
					Path t = tables.resolve(theme + "_" + kind + ".json");
					assertTrue(Files.exists(t), "loot table " + t.getFileName() + " (run tools/build_loot.py)");
					assertEquals("minecraft:chest", JsonParser.parseString(Files.readString(t, StandardCharsets.UTF_8)).getAsJsonObject().get("type").getAsString());
				}
			}
		}
	}

	@Test
	void aRunIsFoundByItsPlot() {
		UUID a = UUID.randomUUID();
		DungeonRuns r = DungeonRuns.EMPTY.with(run(1, 0, a)).with(run(2, 1, UUID.randomUUID()));
		assertEquals(2, r.byPlot(1).id());
		assertNull(r.byPlot(5));
	}

	private static DungeonRuns.Run run(int id, int plot, UUID... players) {
		List<DungeonRuns.Member> party = new ArrayList<>();
		for (UUID u : players) {
			party.add(new DungeonRuns.Member(u, "p", 3, false, "minecraft:overworld", new BlockPos(1, 2, 3)));
		}
		return new DungeonRuns.Run(id, plot, "overgrown", 1, 100L, party, List.of());
	}

	@Test
	void plotsAreHandedOutLowestFirstAndOnlyWhenCleared() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		DungeonRuns r = DungeonRuns.EMPTY;
		assertEquals(0, r.peekPlot());
		r = r.with(run(r.nextRun(), r.peekPlot(), a));
		assertEquals(1, r.peekPlot());
		r = r.with(run(r.nextRun(), r.peekPlot(), b));
		assertEquals(2, r.peekPlot());
		assertEquals(2, r.runs().size());
		assertEquals(1, r.byPlayer(b).plot());
		r = r.end(1);
		assertEquals(1, r.clearing().size());
		assertEquals(0, r.clearing().get(0).plot());
		assertTrue(r.isClearing(0));
		assertEquals(List.of(), r.freePlots(), "a plot that is still being cleared is not free");
		assertEquals(2, r.peekPlot(), "so the next run takes a new one");
		assertNull(r.byPlayer(a));
		r = r.cleared(0);
		assertEquals(List.of(0), r.freePlots());
		assertFalse(r.isClearing(0));
		assertEquals(0, r.peekPlot(), "the cleared plot comes back before a new one");
		r = r.with(run(r.nextRun(), r.peekPlot(), a));
		assertEquals(List.of(), r.freePlots());
		assertEquals(2, r.nextPlot());
		assertEquals(3, r.runs().get(1).id(), "run numbers keep counting");
	}

	@Test
	void theClearedStretchFollowsTheBuiltRooms() {
		List<DungeonRuns.Room> rooms = RoomLayout.plan(3, pool(), new Random(1));
		DungeonRuns.Run run = new DungeonRuns.Run(1, 3, "overgrown", 1, 0L, List.of(),
			List.of(rooms.get(0).withMarkers(List.of(), List.of(), List.of()), rooms.get(1)));
		DungeonRuns.Dirty d = DungeonRuns.EMPTY.with(run).end(1).clearing().get(0);
		assertEquals(3, d.plot());
		assertEquals(rooms.get(0).x() - 2, d.fromX());
		assertEquals(rooms.get(0).x() + rooms.get(0).size() + 2, d.toX(), "only the built room");
		assertEquals(4 * (Math.floorDiv(d.toX(), 16) - Math.floorDiv(d.fromX(), 16) + 1), DungeonPlots.stripChunks(3, d.fromX(), d.toX()).size());
	}

	@Test
	void aMemberWhoIsOutIsNoLongerInTheRun() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		DungeonRuns.Run r = run(1, 0, a, b);
		assertTrue(r.anyoneIn());
		r = r.withMember(r.member(a).withOut());
		assertTrue(r.anyoneIn());
		assertTrue(r.member(a).out());
		r = r.withMember(r.member(b).withOut());
		assertFalse(r.anyoneIn());
		DungeonRuns all = DungeonRuns.EMPTY.with(r);
		assertNull(all.byPlayer(a), "someone who is out is not found as a runner");
	}

	@Test
	void runsSurviveSaving() {
		UUID a = UUID.randomUUID();
		List<DungeonRuns.Room> rooms = RoomLayout.plan(1, pool(), new Random(9));
		DungeonRuns.Run withRooms = new DungeonRuns.Run(2, 1, "overgrown", 4, 7L, run(2, 1, UUID.randomUUID()).party(),
			List.of(rooms.get(0).withMarkers(List.of(new BlockPos(1, 2, 3)), List.of(new BlockPos(4, 5, 6)), List.of()),
				rooms.get(1).withState(DungeonRuns.Room.ACTIVE)));
		DungeonRuns r = DungeonRuns.EMPTY.with(run(1, 0, a)).with(withRooms).end(1);
		var json = DungeonRuns.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
		DungeonRuns back = DungeonRuns.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(r, back);
		assertEquals(DungeonRuns.EMPTY, DungeonRuns.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
		assertTrue(back.runs().get(0).rooms().get(0).built());
		assertFalse(back.runs().get(0).rooms().get(1).built());
		assertEquals(DungeonRuns.Room.ACTIVE, back.runs().get(0).rooms().get(1).state());
	}

	/** A pool like Overgrown's: four fighting shapes and one room for every other role. */
	private static java.util.function.Function<String, List<RoomLayout.Template>> pool() {
		return role -> switch (role) {
			case "combat" -> List.of(new RoomLayout.Template("a", "combat", "arena", 32), new RoomLayout.Template("b", "combat", "pillars", 32),
				new RoomLayout.Template("c", "combat", "pit", 32), new RoomLayout.Template("d", "combat", "split", 32));
			case "lieutenant" -> List.of(new RoomLayout.Template("l", "lieutenant", "arena", 40));
			case "boss" -> List.of(new RoomLayout.Template("x", "boss", "arena", 48));
			default -> List.of(new RoomLayout.Template(role, role, "hall", 32));
		};
	}

	@Test
	void aRunHasThirteenRoomsInTheAgreedOrder() {
		List<DungeonRuns.Room> rooms = RoomLayout.plan(0, pool(), new Random(4));
		assertEquals(13, rooms.size());
		assertEquals(RoomLayout.ORDER, rooms.stream().map(DungeonRuns.Room::role).toList());
		assertEquals(List.of("start", "combat", "combat", "combat", "combat", "combat", "combat", "lieutenant", "combat", "combat", "combat", "boss", "reward"),
			RoomLayout.ORDER);
		assertEquals(32, rooms.get(0).size());
		assertEquals(40, rooms.get(7).size());
		assertEquals(48, rooms.get(11).size());
	}

	@Test
	void roomsStandInALineWithCorridorsAndNeverOverlap() {
		for (int plot : new int[] {0, 1, 17, 50}) {
			for (long seed = 0; seed < 200; seed++) {
				List<DungeonRuns.Room> rooms = RoomLayout.plan(plot, pool(), new Random(seed));
				assertEquals(DungeonPlots.originX(plot) + 1, rooms.get(0).x());
				String lastCombat = "";
				for (int i = 0; i < rooms.size(); i++) {
					DungeonRuns.Room room = rooms.get(i);
					assertEquals(DungeonPlots.originZ(plot), room.centerZ(), "doors are on the middle line");
					assertEquals(DungeonRuns.Room.IDLE, room.state());
					assertFalse(room.built());
					if (i + 1 < rooms.size()) {
						DungeonRuns.Room next = rooms.get(i + 1);
						assertTrue(room.corridorAfter() >= RoomLayout.MIN_CORRIDOR && room.corridorAfter() <= RoomLayout.MAX_CORRIDOR, "corridor " + room.corridorAfter());
						assertEquals(room.eastWallX() + room.corridorAfter() + 1, next.westWallX(), "the corridor runs wall to wall");
					} else {
						assertEquals(0, room.corridorAfter());
					}
					if (room.role().equals("combat")) {
						assertFalse(room.template().equals(lastCombat), "no fighting room twice in a row");
						lastCombat = room.template();
					}
				}
				assertTrue(RoomLayout.endX(rooms) < DungeonPlots.originX(plot) + DungeonPlots.SPACING, "the run fits in its plot");
			}
		}
	}

	@Test
	void aThemeWithoutRoomsForARoleCannotBePlanned() {
		assertNull(RoomLayout.plan(0, role -> role.equals("boss") ? List.of() : pool().apply(role), new Random(1)));
	}

	@Test
	void everyRoomShapeCanBeWalkedFromDoorToDoor() {
		for (String shape : DungeonRoom.SHAPES) {
			for (int size : new int[] {32, 40, 48}) {
				int[][] g = RoomBuilder.floorMap(shape, size);
				int c = size / 2;
				for (int dz = c - 2; dz <= c + 2; dz++) {
					assertEquals(0, g[0][dz], shape + ": the west door opens onto the floor");
					assertEquals(0, g[size - 1][dz], shape + ": the east door opens onto the floor");
				}
				// Walk: a step may go up or down by one block (a jump).
				boolean[][] seen = new boolean[size][size];
				java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
				queue.add(new int[] {0, c});
				seen[0][c] = true;
				while (!queue.isEmpty()) {
					int[] cell = queue.poll();
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = cell[0] + d[0];
						int nz = cell[1] + d[1];
						if (nx >= 0 && nz >= 0 && nx < size && nz < size && !seen[nx][nz] && g[nx][nz] != RoomBuilder.BLOCKED
							&& Math.abs(g[nx][nz] - g[cell[0]][cell[1]]) <= 1) {
							seen[nx][nz] = true;
							queue.add(new int[] {nx, nz});
						}
					}
				}
				assertTrue(seen[size - 1][c], shape + " " + size + ": the east door can be reached from the west door");
				long free = 0;
				long reached = 0;
				for (int x = 0; x < size; x++) {
					for (int z = 0; z < size; z++) {
						if (g[x][z] != RoomBuilder.BLOCKED) {
							free++;
							if (seen[x][z]) {
								reached++;
							}
						}
					}
				}
				assertEquals(free, reached, shape + " " + size + ": every free cell can be reached");
			}
		}
	}

	@Test
	void pitsAreShallowAndPillarsAreTwoByTwo() {
		int[][] pit = RoomBuilder.floorMap("pit", 32);
		int low = 0;
		for (int[] col : pit) {
			for (int h : col) {
				low = Math.min(low, h);
			}
		}
		assertEquals(-3, low);
		int blocked = 0;
		for (int[] col : RoomBuilder.floorMap("arena", 32)) {
			for (int h : col) {
				blocked += h == RoomBuilder.BLOCKED ? 1 : 0;
			}
		}
		assertEquals(16, blocked, "four 2 x 2 pillars");
		blocked = 0;
		for (int[] col : RoomBuilder.floorMap("pillars", 32)) {
			for (int h : col) {
				blocked += h == RoomBuilder.BLOCKED ? 1 : 0;
			}
		}
		assertEquals(36, blocked, "nine 2 x 2 pillars");
	}

	@Test
	void roomGeometry() {
		List<DungeonRuns.Room> rooms = RoomLayout.plan(0, pool(), new Random(2));
		DungeonRuns.Room room = rooms.get(1);
		assertTrue(room.inside(room.x() + 5, DungeonPlots.FLOOR_Y + 1, room.centerZ(), 2.0));
		assertFalse(room.inside(room.x() + 1, DungeonPlots.FLOOR_Y + 1, room.centerZ(), 2.0), "the doorway is not inside yet");
		assertFalse(room.inside(room.x() - 3, DungeonPlots.FLOOR_Y + 1, room.centerZ(), 0.0));
		assertTrue(room.fighting());
		assertFalse(rooms.get(0).fighting());
		assertFalse(rooms.get(12).fighting());
		assertEquals(room.x() - 1, room.westWallX());
		assertEquals(room.x() + room.size(), room.eastWallX());
		assertEquals(14, RoomLayout.heightOf("boss"));
		assertEquals(10, RoomLayout.heightOf("combat"));
	}

	@Test
	void bundledRoomsDecodeAndEveryThemeHasAFullSet() throws IOException {
		Path rooms = Path.of("src/main/resources/data/vestiges/vestiges/dungeon_rooms");
		Path themes = Path.of("src/main/resources/data/vestiges/vestiges/dungeon_themes");
		Map<String, List<DungeonRoom>> byTheme = new HashMap<>();
		try (var files = Files.list(rooms)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				DungeonRoom room = DungeonRoom.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)))
					.getOrThrow(msg -> new IllegalStateException(f + ": " + msg));
				assertTrue(DungeonRoom.ROLES.contains(room.role()), f + " role " + room.role());
				assertTrue(DungeonRoom.SHAPES.contains(room.shape()), f + " shape " + room.shape());
				int expected = switch (room.role()) {
					case "lieutenant" -> 40;
					case "boss" -> 48;
					default -> 32;
				};
				assertEquals(expected, room.size(), f + " size");
				byTheme.computeIfAbsent(room.theme(), k -> new ArrayList<>()).add(room);
			}
		}
		try (var files = Files.list(themes)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				String theme = f.getFileName().toString().replace(".json", "");
				List<DungeonRoom> list = byTheme.getOrDefault(theme, List.of());
				for (String role : DungeonRoom.ROLES) {
					assertTrue(list.stream().anyMatch(r -> r.role().equals(role)), theme + " has a " + role + " room");
				}
				assertTrue(list.stream().filter(r -> r.role().equals("combat")).map(DungeonRoom::shape).distinct().count() >= 4,
					theme + " has at least 4 fighting shapes");
				DungeonTheme t = DungeonTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))).getOrThrow();
				assertFalse(t.mobs().isEmpty(), theme + " has monsters");
				assertFalse(t.palette().floor().isEmpty(), theme + " has a floor");
			}
		}
	}

	@Test
	void bundledThemesDecode() throws IOException {
		Path dir = Path.of("src/main/resources/data/vestiges/vestiges/dungeon_themes");
		try (var files = Files.list(dir)) {
			List<Path> list = files.filter(p -> p.toString().endsWith(".json")).toList();
			assertFalse(list.isEmpty());
			for (Path f : list) {
				DungeonTheme t = DungeonTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)))
					.getOrThrow(msg -> new IllegalStateException(f + ": " + msg));
				assertFalse(t.name().isBlank(), f.toString());
			}
		}
		DungeonTheme overgrown = DungeonTheme.CODEC.parse(JsonOps.INSTANCE,
			JsonParser.parseString(Files.readString(dir.resolve("overgrown.json"), StandardCharsets.UTF_8))).getOrThrow();
		assertTrue(overgrown.openFromStart());
		assertEquals("Mossbound", overgrown.lieutenant().name());
		assertEquals("The Rotwood Witch", overgrown.boss().name());
		assertEquals(1, overgrown.boss().count());
		assertEquals("dragonslayer", new DungeonTheme("End", "light_purple", "feat:dragonslayer").unlockFeat());
	}
}
