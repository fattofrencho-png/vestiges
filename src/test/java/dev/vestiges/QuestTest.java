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
}
