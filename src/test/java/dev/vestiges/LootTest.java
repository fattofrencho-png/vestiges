package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.gear.Rarity;
import dev.vestiges.loot.LootRules;
import dev.vestiges.loot.LootTypes;
import dev.vestiges.loot.RarityPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M3b: rarity planning and odds, the loot rules file, and every generated loot table against 26.3's own loot codec. */
class LootTest {
	private static final Path DATA = Path.of("src/main/resources/data");
	private static final Set<String> SWORD_GROUP = Set.of("sharpness", "smite", "bane_of_arthropods");
	private static final List<RarityPlanner.Option> SWORD = List.of(
		new RarityPlanner.Option("sharpness", 12), new RarityPlanner.Option("smite", 5), new RarityPlanner.Option("bane_of_arthropods", 5),
		new RarityPlanner.Option("knockback", 5), new RarityPlanner.Option("fire_aspect", 8), new RarityPlanner.Option("looting", 12),
		new RarityPlanner.Option("sweeping_edge", 8), new RarityPlanner.Option("unbreaking", 8), new RarityPlanner.Option("mending", 12));

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static boolean compatible(String a, String b) {
		return !(SWORD_GROUP.contains(a) && SWORD_GROUP.contains(b));
	}

	@Test
	void plannedItemsLandInTheirRarity() {
		Random random = new Random(7);
		for (Rarity.Tier target : Rarity.Tier.values()) {
			for (int material : new int[] {0, 10, 20, 30, 40}) {
				for (int i = 0; i < 200; i++) {
					Map<String, Integer> plan = RarityPlanner.plan(target, material, SWORD, LootTest::compatible, false, random);
					if (material > RarityPlanner.high(target)) {
						assertNull(plan, "material above the band");
						continue;
					}
					assertNotNull(plan, target + " on material " + material);
					assertEquals(target, Rarity.score(material, plan, Set.of()).tier(), target + " " + plan);
					int cap = target == Rarity.Tier.RELIC || target == Rarity.Tier.LEGENDARY ? 5 : 4;
					long sixes = plan.values().stream().filter(v -> v == 6).count();
					assertEquals(target == Rarity.Tier.RELIC ? 1 : 0, sixes, "one VI only on Relic");
					assertTrue(plan.values().stream().allMatch(v -> v <= cap || v == 6), "levels capped: " + plan);
					assertTrue(plan.keySet().stream().filter(SWORD_GROUP::contains).count() <= 1, "no clashing enchants: " + plan);
				}
			}
		}
	}

	@Test
	void booksAlwaysCarryAnEnchantment() {
		Random random = new Random(3);
		for (int i = 0; i < 500; i++) {
			Map<String, Integer> plan = RarityPlanner.plan(Rarity.Tier.COMMON, 0, SWORD, LootTest::compatible, true, random);
			assertNotNull(plan);
			assertTrue(!plan.isEmpty());
		}
	}

	@Test
	void rarityRollsFollowTheOdds() {
		List<Double> odds = List.of(5.0, 24.0, 40.0, 25.0, 5.4, 0.5, 0.1);
		Random random = new Random(11);
		int[] counts = new int[7];
		int n = 200_000;
		for (int i = 0; i < n; i++) {
			counts[RarityPlanner.roll(odds, random.nextDouble()).ordinal()]++;
		}
		for (int t = 0; t < 7; t++) {
			assertEquals(odds.get(t), 100.0 * counts[t] / n, Math.max(0.05, odds.get(t) * 0.05), "tier " + t);
		}
	}

	@Test
	void lootRulesFileMatchesTheDesign() throws IOException {
		JsonElement json = JsonParser.parseString(Files.readString(DATA.resolve("vestiges/vestiges/loot_rules/default.json"), StandardCharsets.UTF_8));
		LootRules rules = LootRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(11, rules.bands().size());
		assertEquals(1, rules.band(0));
		assertEquals(1, rules.band(4));
		assertEquals(5, rules.band(25));
		assertEquals(8, rules.band(50));
		assertEquals(11, rules.band(500));
		assertEquals(List.of(60.0, 35.0, 4.9, 0.1, 0.0, 0.0, 0.0), rules.row(8).odds());
		assertEquals(List.of(5.0, 24.0, 40.0, 25.0, 5.4, 0.5, 0.1), rules.row(50).odds());
		assertEquals(LootRules.FALLBACK.rarity(), rules.rarity(), "fallback odds match the file");
		// No elytra in chests (End dungeons only); netherite only from band 7 (loot level 40).
		assertTrue(rules.ladder().stream().noneMatch(b -> b.items().containsKey("minecraft:elytra")));
		LootRules.Base netherite = rules.ladder().stream().filter(b -> b.items().containsKey("minecraft:netherite_sword")).findFirst().orElseThrow();
		assertEquals(0, netherite.weightAt(6));
		assertTrue(netherite.weightAt(7) > 0);
	}

	@Test
	void customLootPiecesDecode() {
		LootTypes.LootLevel.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min\":5,\"max\":9}")).getOrThrow();
		LootTypes.LootEnabled.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"value\":true}")).getOrThrow();
		LootTypes.MobRank.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"rank\":3}")).getOrThrow();
		LootTypes.Gear.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"kind\":\"book\",\"rarity\":\"relic\"}")).getOrThrow();
	}

	@Test
	void everyGeneratedLootTableDecodes() throws IOException {
		HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
		RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
		List<Path> files;
		try (Stream<Path> a = Files.walk(DATA.resolve("vestiges/loot_table")); Stream<Path> b = Files.walk(DATA.resolve("minecraft/loot_table/chests"))) {
			files = Stream.concat(a, b).filter(p -> p.toString().endsWith(".json")).toList();
		}
		assertTrue(files.size() > 100, "generated tables found: " + files.size());
		for (Path f : files) {
			JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			int custom = swapCustom(json);
			LootTable.DIRECT_CODEC.parse(ops, json).getOrThrow(msg -> new IllegalStateException(f + ": " + msg));
			if (f.toString().replace('\\', '/').contains("loot_table/chests/")) {
				assertTrue(custom > 0, "chest override uses the loot switch: " + f);
			}
		}
	}

	/**
	 * Swaps Vestiges' own loot types for vanilla ones of the same shape (they are registered only in game), counting them. References to other
	 * tables need the game's loot registry, so they are checked against the files on disk and then emptied.
	 */
	private static int swapCustom(JsonElement e) {
		int n = 0;
		if (e instanceof JsonObject o && o.has("type") && "minecraft:loot_table".equals(o.get("type").getAsString()) && o.has("value")) {
			String ref = o.get("value").getAsString();
			String[] parts = ref.split(":", 2);
			assertEquals("vestiges", parts[0], "only Vestiges tables are referenced: " + ref);
			assertTrue(Files.exists(DATA.resolve("vestiges/loot_table/" + parts[1] + ".json")), "referenced table exists: " + ref);
			o.remove("value");
			o.addProperty("type", "minecraft:empty");
			return 0;
		}
		if (e instanceof JsonObject o) {
			if (o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().startsWith("vestiges:")) {
				boolean function = o.get("type").getAsString().equals("vestiges:loot_gear");
				for (String k : List.copyOf(o.keySet())) {
					o.remove(k);
				}
				if (function) {
					o.addProperty("type", "minecraft:set_count");
					o.addProperty("count", 1);
				} else {
					o.addProperty("type", "minecraft:random_chance");
					o.addProperty("chance", 0.5);
				}
				return 1;
			}
			for (String k : o.keySet()) {
				n += swapCustom(o.get(k));
			}
		} else if (e instanceof JsonArray a) {
			for (JsonElement x : a) {
				n += swapCustom(x);
			}
		}
		return n;
	}
}
