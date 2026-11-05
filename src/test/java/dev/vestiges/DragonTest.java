package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.dragon.DragonDef;
import dev.vestiges.dragon.DragonRules;
import dev.vestiges.dragon.DragonWorld;
import dev.vestiges.dragon.PlayerDragons;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The dragon rules that need no world: the formulas, the saved state and the six data files. */
class DragonTest {
	private static final Path DIR = Path.of("src/main/resources/data/vestiges/vestiges/dragons");

	private static List<DragonDef> load() throws IOException {
		List<DragonDef> out = new ArrayList<>();
		try (var files = Files.list(DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				out.add(DragonDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))).getOrThrow());
			}
		}
		out.sort(Comparator.comparingInt(DragonDef::number));
		return out;
	}

	@Test
	void healthAndDamageFollowTheAgreedFormulas() {
		assertEquals(1.0, DragonRules.healthMultiplier(1), 1e-9);
		assertEquals(3.0, DragonRules.healthMultiplier(6), 1e-9);
		assertEquals(2.0, DragonRules.damageMultiplier(6), 1e-9);
		// Dragon 6 is about 600 HP alone and about 1500 HP with four players (design: user, 2026-11-03).
		assertEquals(600, DragonRules.maxHealth(DragonRules.healthMultiplier(6), 1), 1e-9);
		assertEquals(1500, DragonRules.maxHealth(DragonRules.healthMultiplier(6), 4), 1e-9);
		// More than four players add nothing; zero counts as one.
		assertEquals(DragonRules.partyFactor(4), DragonRules.partyFactor(9), 1e-9);
		assertEquals(1.0, DragonRules.partyFactor(0), 1e-9);
		assertEquals(1.5, DragonRules.partyFactor(2), 1e-9);
	}

	@Test
	void oneHitNeverTakesMoreThanEightPercent() {
		assertEquals(16f, DragonRules.capHit(500f, 200), 1e-4);
		assertEquals(5f, DragonRules.capHit(5f, 200), 1e-4);
		assertEquals(48f, DragonRules.capHit(1000f, 600), 1e-4);
	}

	@Test
	void theGateIsFourTimesTheNumberAndDragonOneHasNone() {
		assertEquals(0, DragonRules.gateFor(1));
		assertEquals(8, DragonRules.gateFor(2));
		assertEquals(24, DragonRules.gateFor(6));
		assertFalse(DragonRules.gateOpen(7, 8));
		assertTrue(DragonRules.gateOpen(8, 8));
		assertTrue(DragonRules.gateOpen(1, 0));
	}

	@Test
	void theNextDragonIsTheKillCountPlusOne() {
		assertEquals(1, DragonRules.nextNumber(0));
		assertEquals(2, DragonRules.nextNumber(1));
		assertEquals(7, DragonRules.nextNumber(6));
		assertEquals(1, DragonRules.nextNumber(-3));
	}

	@Test
	void rewardsNeedAHitAndAPlaceNearThePortal() {
		assertTrue(DragonRules.rewarded(true, 149.9));
		assertFalse(DragonRules.rewarded(true, 150.1));
		assertFalse(DragonRules.rewarded(false, 3));
	}

	@Test
	void pagesGrowOnePerDragonUpToTheTotal() {
		assertEquals(1, DragonRules.pages(0, 25));
		assertEquals(7, DragonRules.pages(6, 25));
		assertEquals(25, DragonRules.pages(40, 25));
	}

	@Test
	void xpIsOneLevelsWorthAtTheDragonsLevel() {
		assertEquals(dev.vestiges.level.LevelCurve.need(8), DragonRules.xpFor(8, 1.0));
		assertTrue(DragonRules.xpFor(24, 1.0) > DragonRules.xpFor(4, 1.0));
	}

	@Test
	void sixDataFilesMatchTheFormulasAndTheApprovedNames() throws IOException {
		List<DragonDef> all = load();
		assertEquals(6, all.size());
		String[] names = {"Tulva", "Belaz", "Morst", "Dhars", "Ilvig", "Durhos"};
		String[] titles = {"the First", "the Ember", "the Gale", "the Crystal Keeper", "the Swarm Mother", "the Shade"};
		for (int i = 0; i < 6; i++) {
			DragonDef d = all.get(i);
			int n = i + 1;
			assertEquals(n, d.number());
			assertEquals(names[i], d.name());
			assertEquals(titles[i], d.title());
			assertEquals(4 * n, d.level(), "level of dragon " + n);
			assertEquals(DragonRules.gateFor(n), d.gate(), "gate of dragon " + n);
			assertEquals(DragonRules.healthMultiplier(n), d.health(), 1e-9, "health of dragon " + n);
			assertEquals(DragonRules.damageMultiplier(n), d.damage(), 1e-9, "damage of dragon " + n);
			assertEquals(32 * n, d.rewards().emeralds(), "emeralds of dragon " + n);
			assertTrue(d.rewards().book(), "dragon " + n + " rolls a Level VI book");
			assertEquals(n == 6, d.rewards().relic(), "only dragon 6 guarantees a Relic");
			assertEquals("lore/" + names[i].toLowerCase(), d.rewards().chapter());
		}
		assertEquals("Belaz, the Ember · Lv 8", all.get(1).display());
		assertEquals("Tulva, the First · Lv 4", all.get(0).display());
		assertTrue(all.get(0).rewards().feats().contains("dragonslayer"), "dragon 1 gives the Dragonslayer feat");
		assertTrue(all.get(5).rewards().feats().contains("dragon_6"), "dragon 6 gives its feat");
	}

	@Test
	void theKesmarNameNeverAppearsBeforeDragonTwentyFour() throws IOException {
		try (var files = Files.list(DIR)) {
			for (Path f : files.toList()) {
				assertFalse(Files.readString(f, StandardCharsets.UTF_8).toLowerCase().contains("kesmar"), f.getFileName() + " names Kesmar");
			}
		}
	}

	@Test
	void eachDragonKeepsTheMovesTheTableNames() throws IOException {
		List<DragonDef> all = load();
		assertEquals(List.of(), all.get(0).moves());
		assertEquals(List.of("fire_ring"), all.get(1).moves());
		assertEquals(List.of("wing_shove", "fire_ring"), all.get(2).moves());
		assertEquals(List.of("crystal_regrow", "wing_shove"), all.get(3).moves());
		assertEquals(List.of("phantoms", "fire_ring", "wing_shove"), all.get(4).moves());
		assertEquals(List.of("shade", "wing_shove", "phantoms"), all.get(5).moves());
	}

	@Test
	void theWorldStateSavesAndReloads() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		DragonWorld w = DragonWorld.EMPTY.withKills(2).fighting(3).withHurt(a).withHurt(b).withHurt(a);
		assertEquals(2, w.hurt().size());
		DragonWorld back = DragonWorld.CODEC.parse(JsonOps.INSTANCE, DragonWorld.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow()).getOrThrow();
		assertEquals(w, back);
		assertEquals(DragonWorld.EMPTY, DragonWorld.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
		DragonWorld idle = w.idle();
		assertEquals(2, idle.kills(), "ending a fight keeps the kill count");
		assertEquals(0, idle.active());
		assertTrue(idle.hurt().isEmpty());
		assertEquals(0, w.withKills(-5).kills());
	}

	@Test
	void thePlayerRecordKeepsDragonsSortedOnce() {
		PlayerDragons p = PlayerDragons.EMPTY.with(3).with(1).with(3);
		assertEquals(List.of(1, 3), p.done());
		assertEquals(3, p.best());
		assertEquals(2, p.count());
		assertTrue(p.has(1));
		assertFalse(p.has(2));
		assertEquals(0, PlayerDragons.EMPTY.best());
		PlayerDragons back = PlayerDragons.CODEC.parse(JsonOps.INSTANCE, PlayerDragons.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()).getOrThrow();
		assertEquals(p, back);
	}
}
