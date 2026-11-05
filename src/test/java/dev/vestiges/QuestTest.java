package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.data.QuestDef;
import dev.vestiges.quest.QuestRules;
import dev.vestiges.quest.QuestState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Quest files decode and only use steps the game can run; rewards, comebacks and standing follow the agreed rules. */
class QuestTest {
	private static final Path DIR = Path.of("src/main/resources/data/vestiges/vestiges/quests");

	private static Map<String, QuestDef> loadAll() throws IOException {
		Map<String, QuestDef> out = new HashMap<>();
		try (Stream<Path> files = Files.list(DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				out.put(f.getFileName().toString().replace(".json", ""), QuestDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
			}
		}
		return out;
	}

	@Test
	void everyQuestFileDecodes() throws IOException {
		Map<String, QuestDef> all = loadAll();
		assertEquals(25, all.values().stream().filter(q -> "guide".equals(q.kind())).count());
		assertEquals(13, all.values().stream().filter(QuestDef::side).count());
		for (Map.Entry<String, QuestDef> e : all.entrySet()) {
			assertFalse(e.getValue().steps().isEmpty(), e.getKey());
		}
	}

	@Test
	void everyStepTypeIsOneTheGameRuns() throws IOException {
		for (Map.Entry<String, QuestDef> e : loadAll().entrySet()) {
			for (QuestDef.Step s : e.getValue().steps()) {
				assertTrue(QuestRules.STEP_TYPES.contains(s.type()), e.getKey() + " uses " + s.type());
				assertEquals("choice".equals(s.type()), !s.choices().isEmpty(), e.getKey());
			}
		}
	}

	@Test
	void eachGuideChainEndsWithATitle() throws IOException {
		Map<String, QuestDef> all = loadAll();
		assertEquals(4, all.values().stream().filter(q -> "guide".equals(q.kind()) && !q.reward().title().isEmpty()).count());
	}

	@Test
	void comebacksGrowAndPayLess() {
		assertEquals(10, QuestRules.goal(10, 1));
		assertEquals(15, QuestRules.goal(10, 2));
		assertEquals(30, QuestRules.goal(10, 5));
		assertEquals(30, QuestRules.goal(10, 9), "stops at the last comeback");
		assertEquals(936, QuestRules.xp(0, 0.4, 2340, true, 1));
		assertEquals(234, QuestRules.xp(0, 0.4, 2340, true, 2));
		assertEquals(6, QuestRules.emeralds(6, true, 1));
		assertEquals(1, QuestRules.emeralds(6, true, 2));
		assertEquals(100, QuestRules.xp(100, 0, 2340, false, 1), "guide quests pay their flat amount");
	}

	@Test
	void sideQuestsReturnAfterTheWait() {
		assertFalse(QuestRules.canReturn(10, 16));
		assertTrue(QuestRules.canReturn(10, 17));
	}

	@Test
	void rivalsLoseHalfOfAGain() {
		Map<String, Integer> s = QuestRules.applyChoice(Map.of(), Map.of("bright_path", 20));
		assertEquals(20, s.get("bright_path"));
		assertEquals(-10, s.get("vitera"));
		assertEquals(-10, s.get("promise"));
		assertEquals(null, s.get("primacy"), "the two big faiths aren't rivals");
		Map<String, Integer> named = QuestRules.applyChoice(Map.of(), Map.of("bright_path", 20, "vitera", -20));
		assertEquals(-20, named.get("vitera"), "a choice that names a rival wins");
		Map<String, Integer> loss = QuestRules.applyChoice(Map.of(), Map.of("vitera", -5));
		assertEquals(1, loss.size(), "a loss doesn't help rivals");
	}

	@Test
	void standingStaysOnTheScale() {
		assertEquals(100, QuestRules.applyChoice(Map.of("promise", 95), Map.of("promise", 20)).get("promise"));
		assertEquals(-100, QuestRules.applyChoice(Map.of("promise", -95), Map.of("promise", -20)).get("promise"));
	}

	@Test
	void bandsAndRanks() {
		assertEquals("Unknown", QuestRules.band(0));
		assertEquals("Known", QuestRules.band(20));
		assertEquals("Trusted", QuestRules.band(60));
		assertEquals("Distrusted", QuestRules.band(-20));
		assertEquals("Hunted", QuestRules.band(-60));
		assertEquals("", QuestRules.rank("bright_path", 19));
		assertEquals("Lantern of the Bright Path", QuestRules.rank("bright_path", 65));
		assertEquals("", QuestRules.rank("vitera", 90), "no approved Vitera ranks yet");
	}

	@Test
	void finishingKeepsTheCountAndClearsTheQuest() {
		QuestState.Active a = new QuestState.Active(0, 0, 0, "", List.of(), 1);
		QuestState s = QuestState.EMPTY.withActive("q", a);
		assertEquals("q", s.tracked(), "the first quest is tracked");
		s = s.finish("q", 12);
		assertFalse(s.active().containsKey("q"));
		assertTrue(s.done("q"));
		assertEquals(1, s.finished().get("q"));
		assertEquals(12L, s.finishedDay().get("q"));
		assertEquals("", s.tracked());
		assertFalse(s.reset("q").done("q"));
	}

	@Test
	void emeraldsFollowThePriceTier() {
		assertEquals(1, QuestRules.priceTier(1));
		assertEquals(1, QuestRules.priceTier(9));
		assertEquals(2, QuestRules.priceTier(10));
		assertEquals(2, QuestRules.priceTier(24));
		assertEquals(4, QuestRules.priceTier(25));
		assertEquals(4, QuestRules.priceTier(49));
		assertEquals(8, QuestRules.priceTier(50));
		assertEquals(8, QuestRules.priceTier(74));
		assertEquals(12, QuestRules.priceTier(75));
		assertEquals(12, QuestRules.priceTier(100));
		assertEquals(30, QuestRules.emeralds(30, false, 1, 9));
		assertEquals(360, QuestRules.emeralds(30, false, 1, 80));
		assertEquals(12, QuestRules.emeralds(12, false, 1, 1));
		assertEquals(24, QuestRules.emeralds(12, false, 1, 10));
		// A side quest's comeback pays the small amount, scaled the same way.
		assertEquals(2 * 4, QuestRules.emeralds(8, true, 2, 30));
		assertEquals(0, QuestRules.emeralds(0, true, 2, 80));
	}

	@Test
	void everyNewStepNamesItsFeature() {
		assertEquals(null, QuestRules.needs("stat"));
		assertEquals(null, QuestRules.needs("advancement"));
		assertEquals(null, QuestRules.needs("trade"));
		assertEquals(null, QuestRules.needs("loot_chest"));
		assertEquals(null, QuestRules.needs("hub_open"));
		assertEquals(null, QuestRules.needs("track_quest"));
		assertEquals(null, QuestRules.needs("table_slot"));
		assertEquals(null, QuestRules.needs("grid_craft"));
		assertEquals(dev.vestiges.config.VestigesSystem.MOB_SCALING, QuestRules.needs("kill_high_level"));
		assertEquals(dev.vestiges.config.VestigesSystem.ELITES, QuestRules.needs("kill_rank"));
		assertEquals(dev.vestiges.config.VestigesSystem.ELITES, QuestRules.needs("kill_power"));
		assertEquals(dev.vestiges.config.VestigesSystem.PASSIVE_TREE, QuestRules.needs("ascension_counter"));
		assertEquals(dev.vestiges.config.VestigesSystem.CODEX, QuestRules.needs("codex_section"));
		assertEquals(dev.vestiges.config.VestigesSystem.SETTLEMENTS, QuestRules.needs("area_blocks"));
		assertEquals(dev.vestiges.config.VestigesSystem.DRAGONS, QuestRules.needs("dragon_kill"));
	}

	@Test
	void aLevelGapCountsFromTheAskedMinimum() {
		assertTrue(QuestRules.atLeast("5", "3"));
		assertTrue(QuestRules.atLeast("3", "3"));
		assertFalse(QuestRules.atLeast("2", "3"));
		assertFalse(QuestRules.atLeast("x", "3"));
		assertFalse(QuestRules.atLeast("5", ""));
	}

	@Test
	void rewardsCanNameGearAndRefunds() {
		JsonElement json = JsonParser.parseString("{\"xp\":10,\"emeralds\":5,\"gear\":\"uncommon\",\"refunds\":2}");
		QuestDef.Reward r = QuestDef.Reward.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals("uncommon", r.gear());
		assertEquals(2, r.refunds());
		assertEquals("", QuestDef.Reward.NONE.gear());
		assertEquals(0, QuestDef.Reward.NONE.refunds());
	}
}
