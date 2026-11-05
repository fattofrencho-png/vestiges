package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.data.Feat;
import dev.vestiges.settlement.MarkerType;
import dev.vestiges.settlement.Renown;
import dev.vestiges.settlement.SettlementData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** M5: renown scores and levels, level drops after 3 days, saved settlement data, marker limits and the feat data. */
class SettlementTest {
	@Test
	void levelsAndRadii() {
		assertEquals(1, Renown.level(0));
		assertEquals(1, Renown.level(79));
		assertEquals(2, Renown.level(80));
		assertEquals(4, Renown.level(600));
		assertEquals(7, Renown.level(5000));
		assertEquals("Hamlet", Renown.name(3));
		assertEquals(24, Renown.radius(1));
		assertEquals(160, Renown.radius(7));
		assertEquals(250, Renown.next(2));
		assertEquals(0, Renown.next(7));
	}

	@Test
	void sizeAndVariety() {
		// 1 point per 100 blocks; a type counts fully up to 1,000, then a tenth as much.
		assertEquals(10, Renown.size(Map.of("minecraft:stone", 1000)));
		assertEquals(11, Renown.size(Map.of("minecraft:stone", 2000)));
		assertEquals(20, Renown.size(Map.of("minecraft:stone", 1000, "minecraft:oak_planks", 1000)));
		assertEquals(5, Renown.variety(Map.of("minecraft:stone", 16, "minecraft:glass", 15)));
		assertEquals(15, Renown.homes(5));
		assertEquals(30, Renown.people(4, 2));
	}

	@Test
	void levelUpAtOnceDownAfterThreeDays() {
		SettlementData.Stats s = new SettlementData.Stats(0, 0, 0, 0, 300, 1, 1, 0, 0);
		SettlementData.Stats up = Renown.advance(s, 12000);
		assertEquals(3, up.level(), "300 renown = Hamlet at once");
		SettlementData.Stats fallen = new SettlementData.Stats(0, 0, 0, 0, 100, 3, 3, 0, 0);
		SettlementData.Stats day1 = Renown.advance(fallen, 24000);
		assertEquals(3, day1.level(), "still a Hamlet after one day below");
		SettlementData.Stats day3 = Renown.advance(Renown.advance(day1, 24000), 24000);
		assertEquals(2, day3.level(), "drops after 3 days");
		assertEquals(3, day3.bestLevel(), "the best level stays for feats");
		SettlementData.Stats back = Renown.advance(new SettlementData.Stats(0, 0, 0, 0, 260, 3, 3, 48000, 0), 12000);
		assertEquals(0, back.belowTicks(), "renown back above the line resets the timer");
	}

	@Test
	void worldSavesAndLoads() {
		UUID owner = UUID.randomUUID();
		SettlementData.Settlement s = new SettlementData.Settlement("abc", "Oscat", owner, List.of(UUID.randomUUID()), "minecraft:overworld", 12345L,
			SettlementData.ACTIVE, 0, new SettlementData.Stats(10, 20, 15, 0, 30, 2, 2, 0, 75),
			new SettlementData.Shared(Map.of("blocks/stone", 70), Map.of("biomes/plains", List.of(1L, 2L))));
		SettlementData.Marker m = new SettlementData.Marker(3, "ward12", owner, "minecraft:overworld", 999L, "", 6000);
		SettlementData.World w = SettlementData.World.EMPTY.put(s).add(m);
		JsonElement json = SettlementData.World.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow();
		SettlementData.World back = SettlementData.World.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(s, back.settlement("abc"));
		assertEquals(m, back.marker(3));
		assertEquals(4, back.nextId());
		assertTrue(back.settlement("abc").member(owner));
		assertEquals(32, back.settlement("abc").radius(), "Settlement level radius");
	}

	@Test
	void markerLimits() {
		assertEquals(3, MarkerType.BELL.limit);
		assertEquals(20, MarkerType.BANNER16.limit);
		assertEquals(10, MarkerType.WARD30.limit);
		assertEquals(3, MarkerType.GOLD_BLOCK.limit);
		assertEquals(10 * 60 * 20, MarkerType.BELL.setupTicks);
		assertEquals(5 * 60 * 20, MarkerType.WARD6.setupTicks);
		assertEquals("ward", MarkerType.WARD12.family());
	}

	@Test
	void everyFeatIsCheckableOrWaits() throws IOException {
		Map<String, Feat> feats = new HashMap<>();
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/data/vestiges/vestiges/feats"))) {
			for (Path f : files.toList()) {
				Feat feat = Feat.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))).getOrThrow();
				feats.put(f.getFileName().toString().replace(".json", ""), feat);
			}
		}
		assertEquals(24, feats.size());
		Set<String> types = Set.of("level", "biomes", "codex", "elites", "villagers", "beds", "renown", "renown_two", "raids", "raid_omen", "renown_levels");
		for (Map.Entry<String, Feat> e : feats.entrySet()) {
			Feat f = e.getValue();
			boolean checkable = !f.goals().isEmpty() || !f.counter().isEmpty();
			assertTrue(checkable || !f.later().isEmpty(), e.getKey() + " has goals, a counter or a later system");
			f.goals().forEach(g -> assertTrue(types.contains(g.type()), e.getKey() + ": " + g.type()));
			assertTrue(f.counter().isEmpty() || types.contains(f.counter()), e.getKey());
			assertTrue(f.item().isEmpty() || "waystone".equals(f.item()) || "dungeon_key".equals(f.item()) || MarkerType.byId(f.item()) != null, e.getKey() + " item " + f.item());
		}
		assertEquals("bell", feats.get("founder").item());
		assertEquals(10, feats.get("elite_hunter").every());
		assertTrue(feats.get("first_key").later().isEmpty(), "First Key is earnable now that dungeons exist");
		assertEquals("dungeon_key", feats.get("first_key").item());
		assertEquals("blueprint_32", feats.get("charted").owed());
	}
}
