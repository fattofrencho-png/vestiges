package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.codex.CodexEntry;
import dev.vestiges.codex.CodexProgress;
import dev.vestiges.codex.CodexRules;
import dev.vestiges.farming.MealDef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M4: every Codex entry decodes and points at real game ids; the agreed stage numbers; meal data; saved progress. */
class CodexTest {
	private static final Path DATA = Path.of("src/main/resources/data/vestiges/vestiges");
	private static final List<CodexEntry> ENTRIES = new ArrayList<>();

	@BeforeAll
	static void boot() throws IOException {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		try (Stream<Path> files = Files.walk(DATA.resolve("codex"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				ENTRIES.add(CodexEntry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(m -> new IllegalStateException(f + ": " + m)));
			}
		}
	}

	@Test
	void allApprovedEntriesArePresent() {
		Map<String, Integer> bySection = new HashMap<>();
		ENTRIES.forEach(e -> bySection.merge(e.section(), 1, Integer::sum));
		assertEquals(Map.of("blocks", 180, "mobs", 88, "biomes", 62, "structures", 32, "items", 52), bySection);
		assertTrue(ENTRIES.stream().allMatch(e -> !e.line1().isEmpty() && !e.line2().isEmpty()), "every entry has its two lines");
	}

	@Test
	void membersAreRealGameIds() {
		HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
		for (CodexEntry e : ENTRIES) {
			for (String m : e.members()) {
				Identifier id = Identifier.parse(m);
				boolean ok = switch (e.section()) {
					case "blocks" -> BuiltInRegistries.BLOCK.containsKey(id);
					case "mobs" -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
					case "items" -> BuiltInRegistries.ITEM.containsKey(id);
					case "biomes" -> lookup.lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, id)).isPresent();
					default -> lookup.lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, id)).isPresent();
				};
				assertTrue(ok, e.name() + ": " + m);
			}
			assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(e.icon())), e.name() + " icon " + e.icon());
			if (e.section().equals("structures")) {
				assertTrue(!e.members().isEmpty() || !e.detect().isEmpty() || !e.later().isEmpty(), e.name() + " can be found");
			}
		}
	}

	@Test
	void blockStagesFollowTheClasses() {
		assertArrayEquals(new int[] {64, 256}, CodexRules.blockNeeds("common", 0));
		assertArrayEquals(new int[] {48, 192}, CodexRules.blockNeeds("uncommon", 0));
		assertArrayEquals(new int[] {32, 128}, CodexRules.blockNeeds("rare", 0));
		assertArrayEquals(new int[] {16, 64}, CodexRules.blockNeeds("very_rare", 0));
		assertArrayEquals(new int[] {54, 218}, CodexRules.blockNeeds("common", 15), "Naturalist: 15% fewer");
		CodexEntry stone = new CodexEntry("blocks", "Stone", "common", List.of(), "", "a", "b", false, false, "", "");
		assertEquals(0, CodexRules.stageFor(stone, 0, 0));
		assertEquals(1, CodexRules.stageFor(stone, 1, 0));
		assertEquals(2, CodexRules.stageFor(stone, 64, 0));
		assertEquals(3, CodexRules.stageFor(stone, 256, 0));
		assertEquals(10, CodexRules.xpFor(stone, 1));
		assertEquals(20, CodexRules.xpFor(stone, 2));
		assertEquals(50, CodexRules.xpFor(stone, 3));
		CodexEntry egg = new CodexEntry("blocks", "Dragon Egg", "unique", List.of(), "", "a", "b", false, false, "", "");
		assertEquals(1, CodexRules.maxStage(egg));
		assertEquals(150, CodexRules.xpFor(egg, 1));
	}

	@Test
	void bestiaryItemsAndPlaces() {
		CodexEntry zombie = new CodexEntry("mobs", "Zombie", "", List.of(), "", "a", "b", false, false, "", "");
		assertEquals(2, CodexRules.stageFor(zombie, 10, 0));
		assertEquals(4, CodexRules.stageFor(zombie, 250, 0));
		assertEquals(6, CodexRules.bestiaryDamage(4));
		assertEquals(0.02, CodexRules.bestiaryDrops(4), 1e-9);
		CodexEntry wither = new CodexEntry("mobs", "Wither", "", List.of(), "", "a", "b", true, false, "", "");
		assertEquals(4, CodexRules.stageFor(wither, 10, 0), "bosses master at 10");
		CodexEntry beacon = new CodexEntry("items", "Beacon", "", List.of(), "", "a", "b", false, true, "", "");
		assertEquals(3, CodexRules.stageFor(beacon, 3, 0), "rare items master at 3");
		CodexEntry plains = new CodexEntry("biomes", "Plains", "", List.of(), "", "a", "b", false, false, "", "");
		assertEquals(1, CodexRules.stageFor(plains, 31, 0));
		assertEquals(3, CodexRules.stageFor(plains, 32, 0));
		assertEquals(0, CodexRules.xpFor(plains, 1), "biome discovery XP stays with the level system");
		assertEquals(100, CodexRules.xpFor(plains, 3));
	}

	@Test
	void bonusPoints() {
		assertEquals(0, CodexRules.bonusPoints(99, false));
		assertEquals(1, CodexRules.bonusPoints(100, false));
		assertEquals(3, CodexRules.bonusPoints(500, false));
		assertEquals(1, CodexRules.bonusPoints(80, true), "Lorekeeper");
		assertEquals(250, CodexRules.nextPointAt(120, false));
	}

	@Test
	void progressSavesAndLoads() {
		CodexProgress p = CodexProgress.EMPTY.withCount("blocks/stone", 70).withStage("blocks/stone", 2).withSpot("biomes/plains", 42L)
			.withHeld(Map.of("items/map", 1));
		JsonElement json = CodexProgress.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
		CodexProgress back = CodexProgress.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(70, back.count("blocks/stone"));
		assertEquals(2, back.stage("blocks/stone"));
		assertEquals(List.of(42L), back.spots("biomes/plains"));
		assertEquals(1, back.discovered());
		assertFalse(back.held().isEmpty());
	}

	@Test
	void mealsMatchTheDesign() throws IOException {
		Map<String, MealDef> meals = new HashMap<>();
		try (Stream<Path> files = Files.list(DATA.resolve("meals"))) {
			for (Path f : files.toList()) {
				MealDef d = MealDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))).getOrThrow();
				meals.put(d.item(), d);
				assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.parse(d.item())), d.item());
			}
		}
		assertEquals(7, meals.size());
		assertEquals(10, meals.get("minecraft:mushroom_stew").minutes());
		assertEquals(5.0, meals.get("minecraft:mushroom_stew").stats().get("mining_speed"));
		assertEquals(-15.0, meals.get("minecraft:pumpkin_pie").stats().get("hunger_drain"));
		assertEquals(6.0, meals.get("minecraft:rabbit_stew").stats().get("max_health"));
	}
}
