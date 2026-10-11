package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.tree.PlayerTree;
import dev.vestiges.tree.TreeDefs;
import dev.vestiges.tree.TreeLabels;
import dev.vestiges.tree.TreeRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The bundled wheels and ascendancies must match the approved tree, and the point rules must hold. */
class TreeTest {
	private static final Path DATA = Path.of("src/main/resources/data/vestiges/vestiges");

	private static TreeDefs.Wheel wheel(String name) throws IOException {
		JsonElement json = JsonParser.parseString(Files.readString(DATA.resolve("trees/" + name + ".json"), StandardCharsets.UTF_8));
		return TreeDefs.Wheel.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(name + ": " + msg));
	}

	private static List<TreeDefs.Ascendancy> ascendancies() throws IOException {
		try (Stream<Path> files = Files.list(DATA.resolve("ascendancies"))) {
			return files.filter(p -> p.toString().endsWith(".json")).map(p -> {
				try {
					JsonElement json = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
					return TreeDefs.Ascendancy.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(p + ": " + msg));
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			}).toList();
		}
	}

	private static void checkWheel(TreeDefs.Wheel w, int nodes, int choiceNodes) {
		assertEquals(nodes, w.nodes().size(), w.wheel() + " node count");
		Map<String, TreeDefs.Node> byId = new HashMap<>();
		for (TreeDefs.Node n : w.nodes()) {
			assertTrue(byId.put(n.id(), n) == null, "duplicate id " + n.id());
			assertFalse(n.name().isBlank(), n.id() + " has no name");
			for (TreeDefs.Effect e : n.effects()) {
				assertTrue(e.stat().isPresent() || e.mechanic().isPresent(), n.id() + " has an empty effect");
			}
		}
		assertEquals(choiceNodes, w.nodes().stream().filter(n -> !n.choices().isEmpty()).count(), w.wheel() + " choice nodes");
		for (TreeDefs.Node n : w.nodes()) {
			if (!n.choices().isEmpty()) {
				assertEquals(2, n.choices().size(), n.id() + " should offer 2 options");
			}
			n.rival().ifPresent(r -> {
				assertNotNull(byId.get(r), n.id() + " rival missing");
				assertEquals(n.id(), byId.get(r).rival().orElse(""), n.id() + " rival is not mutual");
			});
		}
		Set<Integer> starts = new HashSet<>();
		w.nodes().stream().filter(TreeDefs.Node::isStart).forEach(n -> starts.add(n.start().get()));
		assertEquals(Set.of(0, 1, 2), starts, w.wheel() + " starts");
		for (List<String> e : w.edges()) {
			assertEquals(2, e.size());
			assertNotNull(byId.get(e.get(0)), "edge to unknown " + e.get(0));
			assertNotNull(byId.get(e.get(1)), "edge to unknown " + e.get(1));
		}
		// Every node can be reached from any start.
		Map<String, Set<String>> adj = TreeRules.adjacency(w.edges());
		String start = w.nodes().stream().filter(n -> n.start().orElse(-1) == 0).findFirst().orElseThrow().id();
		Set<String> seen = new HashSet<>(Set.of(start));
		ArrayDeque<String> queue = new ArrayDeque<>(List.of(start));
		while (!queue.isEmpty()) {
			for (String n : adj.getOrDefault(queue.poll(), Set.of())) {
				if (seen.add(n)) {
					queue.add(n);
				}
			}
		}
		assertEquals(nodes, seen.size(), w.wheel() + " has unreachable nodes");
	}

	@Test
	void fightWheelMatchesTheApprovedTree() throws IOException {
		checkWheel(wheel("fight"), 295, 60);
	}

	@Test
	void craftWheelMatchesTheApprovedTree() throws IOException {
		checkWheel(wheel("craft"), 215, 40);
	}

	@Test
	void fiveAscendanciesWithTwoBranchesEach() throws IOException {
		List<TreeDefs.Ascendancy> all = ascendancies();
		assertEquals(Set.of("harvester", "woodsman", "delver", "wayfarer", "lancer"), new HashSet<>(all.stream().map(TreeDefs.Ascendancy::id).toList()));
		for (TreeDefs.Ascendancy a : all) {
			assertEquals(2, a.branches().size(), a.id());
			assertEquals(3, a.second().size(), a.id() + " second ascension steps");
			for (TreeDefs.Branch b : a.branches()) {
				assertEquals(List.of("travel", "crafting", "travel", "fighting"), b.nodes().stream().map(TreeDefs.AscNode::type).toList(), a.id());
				// Every crafting node is a utility power switched by V.
				assertTrue(b.nodes().get(1).effects().stream().anyMatch(e -> e.mechanic().map(TreeLabels.UTILITY::contains).orElse(false)),
					a.id() + " " + b.name() + " crafting node should be a utility power");
			}
		}
	}

	@Test
	void pointsAndRefunds() {
		assertEquals(20, TreeRules.fightPoints(20));
		assertEquals(10, TreeRules.craftPoints(20, 0));
		assertEquals(13, TreeRules.craftPoints(21, 3));
		assertEquals(1, TreeRules.refundCost(1));
		assertEquals(2, TreeRules.refundCost(10));
		assertEquals(11, TreeRules.refundCost(100));
	}

	@Test
	void reachAndConnection() {
		// start - a - b - c, and a - d
		Map<String, Set<String>> adj = TreeRules.adjacency(List.of(List.of("s", "a"), List.of("a", "b"), List.of("b", "c"), List.of("a", "d")));
		assertTrue(TreeRules.canReach("a", "s", Set.of(), adj));
		assertFalse(TreeRules.canReach("b", "s", Set.of(), adj));
		assertTrue(TreeRules.canReach("b", "s", Set.of("a"), adj));
		Set<String> taken = Set.of("a", "b", "c", "d");
		assertFalse(TreeRules.stillConnected("a", "s", taken, adj), "a holds everything");
		assertFalse(TreeRules.stillConnected("b", "s", taken, adj), "b holds c");
		assertTrue(TreeRules.stillConnected("c", "s", taken, adj));
		assertTrue(TreeRules.stillConnected("d", "s", taken, adj));
	}

	@Test
	void playerTreeSavesAndLoads() {
		PlayerTree t = PlayerTree.EMPTY.withWheel("fight", PlayerTree.WheelState.EMPTY.withStart(1).take("0:s0_2", 1).take("h1", null))
			.withAscension("delver", 1, Set.of("delver:Vein Sense:0")).addCounter("kills", 5).withUtility(false);
		JsonElement json = PlayerTree.CODEC.encodeStart(JsonOps.INSTANCE, t).getOrThrow();
		PlayerTree back = PlayerTree.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1, back.fight().start());
		assertEquals(Set.of("0:s0_2", "h1"), back.fight().taken());
		assertEquals(1, back.fight().choices().get("0:s0_2"));
		assertEquals(-1, back.craft().start());
		assertEquals("delver", back.ascendancy());
		assertEquals(5, back.counter("kills"));
		assertFalse(back.utilityOn());
	}

	@Test
	void pendingLabels() {
		TreeDefs.Effect placing = new TreeDefs.Effect(java.util.Optional.of("placing_speed"), java.util.Optional.empty(), 5, Map.of());
		TreeDefs.Effect codex = new TreeDefs.Effect(java.util.Optional.of("codex_xp"), java.util.Optional.empty(), 10, Map.of());
		TreeDefs.Effect enchant = new TreeDefs.Effect(java.util.Optional.of("enchant_cost"), java.util.Optional.empty(), -2, Map.of());
		TreeDefs.Effect damage = new TreeDefs.Effect(java.util.Optional.of("melee_damage"), java.util.Optional.empty(), 3, Map.of());
		assertEquals(Set.of("settlements (M5)"), TreeLabels.pending(List.of(placing, codex, enchant, damage)));
		assertTrue(TreeLabels.active(codex), "Codex nodes work since M4");
		assertTrue(TreeLabels.active(enchant), "enchanting cost works since M1c");
		assertTrue(TreeLabels.active(damage));
	}
}
