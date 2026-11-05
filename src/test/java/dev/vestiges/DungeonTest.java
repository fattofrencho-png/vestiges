package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.dungeon.DungeonPlots;
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

	private static DungeonRuns.Run run(int id, int plot, UUID... players) {
		List<DungeonRuns.Member> party = new ArrayList<>();
		for (UUID u : players) {
			party.add(new DungeonRuns.Member(u, "p", 3, false, "minecraft:overworld", new BlockPos(1, 2, 3)));
		}
		return new DungeonRuns.Run(id, plot, "overgrown", 1, 100L, party);
	}

	@Test
	void plotsAreHandedOutLowestFirstAndReused() {
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
		assertEquals(List.of(0), r.freePlots());
		assertEquals(0, r.peekPlot(), "the freed plot comes back before a new one");
		assertNull(r.byPlayer(a));
		r = r.with(run(r.nextRun(), r.peekPlot(), a));
		assertEquals(List.of(), r.freePlots());
		assertEquals(2, r.nextPlot());
		assertEquals(3, r.runs().get(1).id(), "run numbers keep counting");
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
		DungeonRuns r = DungeonRuns.EMPTY.with(run(1, 0, a)).with(run(2, 1, UUID.randomUUID())).end(1);
		var json = DungeonRuns.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
		DungeonRuns back = DungeonRuns.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(r, back);
		assertEquals(DungeonRuns.EMPTY, DungeonRuns.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
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
		assertEquals("dragonslayer", new DungeonTheme("End", "light_purple", "feat:dragonslayer").unlockFeat());
	}
}
