package dev.vestiges.gametest;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.level.LevelCurve;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.MobLevels;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.level.XpSources;
import dev.vestiges.mobs.ArmorFormula;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import dev.vestiges.tree.TreeCombat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

/** S5: every monster gets its level, rank, size and powers once; health, damage, armor and kill XP follow the agreed formulas; switches leave vanilla. */
final class MobSuite {
	private static final Identifier LEVEL_HEALTH = Vestiges.id("mob_level");
	private static final Identifier SWIFT = Vestiges.id("power_swift");
	private static final Identifier ARMORED = Vestiges.id("power_armored");

	private MobSuite() {
	}

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void setLevel(TestServerContext server, int level) {
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START.withXp(level, 0, 0)));
	}

	private static long totalXp(ServerPlayer p) {
		PlayerLevel l = LevelService.get(p);
		return LevelCurve.totalTo(l.level()) + l.xp();
	}

	/** Removes every monster-ish mob near the player so a test only sees what it made. */
	private static void sweepMobs(ServerLevel level, BlockPos around) {
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(around).inflate(60))) {
			m.discard();
		}
	}

	private static List<EntityType<?>> monsterTypes(ServerLevel level) {
		List<EntityType<?>> out = new ArrayList<>();
		for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
			if (!t.canSummon()) {
				continue;
			}
			Entity e = t.create(level, EntitySpawnReason.COMMAND);
			if (e instanceof Mob m && MobService.scales(m)) {
				out.add(t);
			}
			if (e != null) {
				e.discard();
			}
		}
		return out;
	}

	static EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
	}

	private static Mob spawn(ServerLevel level, EntityType<?> type, BlockPos pos) {
		Entity e = type.spawn(level, pos, EntitySpawnReason.COMMAND);
		return e instanceof Mob m ? m : null;
	}

	/** The level a new monster at a spot must get, for the rank it was dealt. */
	private static int expectedLevel(ServerLevel level, BlockPos pos, double playerLevel, int rank) {
		ServerConfig.Numbers n = ConfigStore.server().numbers;
		MobLevels.Layer layer = XpSources.layerAt(level, pos);
		int raw = MobLevels.raw(playerLevel, layer, n.mobLevelShare, n.cavesLevelBonus, n.deepslateLevelBonus);
		int cap = MobLevels.cap(layer, n.surfaceLevelCap, n.cavesLevelCap, n.deepslateLevelCap);
		return Math.max(1, Math.min(raw, cap + MobLevels.overCap(rank)));
	}

	private static double expectedDamage(MobData d) {
		return (1 + 0.06 * d.level()) * d.size() * d.strength() * (d.has("brute") ? 1.4 : 1.0);
	}

	/** Everything that must hold for one freshly made monster. Returns the problems, empty when fine; {@code level} -1 = do not check the level. */
	private static String verify(ServerLevel level, Mob mob, LivingEntity dummy, double playerLevel, int level0) {
		MobData d = MobService.data(mob);
		if (d == null) {
			return "no Vestiges data";
		}
		StringBuilder bad = new StringBuilder();
		int want = level0 >= 0 ? level0 : expectedLevel(level, mob.blockPosition(), playerLevel, d.rank());
		if (d.level() != want) {
			bad.append("level ").append(d.level()).append(" not ").append(want).append("; ");
		}
		if (d.rank() < 0 || d.rank() > 3) {
			bad.append("rank ").append(d.rank()).append("; ");
		}
		if (d.powers().size() != d.rank()) {
			bad.append(d.powers().size()).append(" powers for rank ").append(d.rank()).append("; ");
		}
		if (new HashSet<>(d.powers()).size() != d.powers().size() || !MobService.POWERS.containsAll(d.powers())) {
			bad.append("bad powers ").append(d.powers()).append("; ");
		}
		boolean deep = XpSources.layerAt(level, mob.blockPosition()) == MobLevels.Layer.DEEPSLATE;
		if (!deep && d.has("withering")) {
			bad.append("withering above deepslate; ");
		}
		if (d.size() < 0.799f || d.size() > 1.251f) {
			bad.append("size ").append(d.size()).append("; ");
		}
		double base = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
		double expect = Math.min(1024.0, base * MobService.healthMultiplier(d));
		if (Math.abs(mob.getMaxHealth() - expect) > 0.01 + expect * 0.005) {
			bad.append("max health ").append(mob.getMaxHealth()).append(" not ").append(expect).append("; ");
		}
		if (Math.abs(mob.getHealth() - mob.getMaxHealth()) > 0.01) {
			bad.append("health ").append(mob.getHealth()).append(" below max; ");
		}
		if (!mob.isBaby() && Math.abs(mob.getAttributeValue(Attributes.SCALE) - d.size()) > 0.01) {
			bad.append("scale ").append(mob.getAttributeValue(Attributes.SCALE)).append(" not size ").append(d.size()).append("; ");
		}
		boolean swiftMod = mob.getAttribute(Attributes.MOVEMENT_SPEED) != null && mob.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(SWIFT) != null;
		if (swiftMod != d.has("swift")) {
			bad.append("swift modifier ").append(swiftMod).append(" but powers ").append(d.powers()).append("; ");
		}
		boolean armorMod = mob.getAttribute(Attributes.ARMOR) != null && mob.getAttribute(Attributes.ARMOR).getModifier(ARMORED) != null;
		if (armorMod != d.has("armored")) {
			bad.append("armored modifier ").append(armorMod).append(" but powers ").append(d.powers()).append("; ");
		}
		double mult = MobService.damageMultiplier(mob);
		if (Math.abs(mult - expectedDamage(d)) > 0.001) {
			bad.append("damage x").append(mult).append(" not ").append(expectedDamage(d)).append("; ");
		}
		if (dummy != null) {
			DamageSource hit = level.damageSources().mobAttack(mob);
			float through = TreeCombat.modifyDamage(dummy, hit, 10f);
			if (Math.abs(through - 10f * expectedDamage(d)) > 0.02 * Math.max(1, through)) {
				bad.append("a 10-damage hit became ").append(through).append(" not ").append(10f * expectedDamage(d)).append("; ");
			}
		}
		return bad.toString();
	}

	private static Mob dummy(ServerLevel level, BlockPos pos) {
		return spawn(level, type("cow"), pos);
	}

	// ------------------------------------------------------------------ the suite

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("clear @p");
		server.runCommand("vestiges tree reset @p");
		server.runCommand("vestiges codex reset @p");
		T.pad(ctx, server, 0, 0);
		setLevel(server, 10);
		ctx.waitTicks(5);
		var snapshot = T.switches();

		everyType(server, c);
		layersAndLevels(server, c);
		rankRates(server, c);
		armor(server, c);
		rankCommand(server, c);
		killXp(server, c);
		persistence(ctx, server, c);
		babies(server, c);
		switchesOff(ctx, server, c, snapshot);

		server.runOnServer(s -> sweepMobs(player(s).level(), player(s).blockPosition()));
		setLevel(server, 20);
	}

	private static void everyType(TestServerContext server, Checks c) {
		c.section("Every monster type at player level 10");
		List<EntityType<?>> types = server.computeOnServer(s -> monsterTypes(player(s).level()));
		c.note(types.size() + " monster types found: " + types.stream().map(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath()).toList());
		c.check("there are monster types to test", types.size() >= 30, "found " + types.size());
		int[] ranks = new int[4];
		for (EntityType<?> type : types) {
			String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			String problem = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos at = p.blockPosition().offset(3, 0, 0);
				Mob dummy = dummy(level, p.blockPosition().offset(-4, 0, 0));
				Mob mob = spawn(level, type, at);
				if (mob == null) {
					dummy.discard();
					return "could not be summoned";
				}
				String bad = verify(level, mob, dummy, 10, -1);
				MobData d = MobService.data(mob);
				if (d != null) {
					ranks[d.rank()]++;
				}
				sweepMobs(level, p.blockPosition());
				return bad;
			});
			c.check("monster " + name + " gets level, rank, size, powers, health and damage right", problem.isEmpty(), problem);
		}
		c.note("ranks dealt to the " + types.size() + " monsters: normal " + ranks[0] + ", champion " + ranks[1] + ", elite " + ranks[2] + ", warlord " + ranks[3]);
	}

	private static void layersAndLevels(TestServerContext server, Checks c) {
		c.section("Level by player level and layer (surface cap 20, caves 40, deepslate 50)");
		int[] playerLevels = {1, 10, 30, 60, 100};
		String[] layers = {"surface", "caves", "deepslate"};
		for (int pl : playerLevels) {
			setLevel(server, pl);
			for (String layer : layers) {
				String result = server.computeOnServer(s -> {
					ServerPlayer p = player(s);
					ServerLevel level = p.level();
					int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX() + 3, p.getBlockZ());
					int y = switch (layer) {
						case "surface" -> p.getBlockY();
						case "caves" -> Math.max(5, ground - 12);
						default -> -20;
					};
					BlockPos at = new BlockPos(p.getBlockX() + 3, y, p.getBlockZ());
					Mob dummy = dummy(level, p.blockPosition().offset(-4, 0, 0));
					StringBuilder bad = new StringBuilder();
					int top = 0;
					for (int i = 0; i < 40; i++) {
						Mob mob = spawn(level, type("zombie"), at);
						String problem = verify(level, mob, dummy, pl, -1);
						if (!problem.isEmpty() && bad.length() < 200) {
							bad.append(problem);
						}
						MobData d = MobService.data(mob);
						top = d == null ? top : Math.max(top, d.level());
						mob.discard();
					}
					dummy.discard();
					sweepMobs(level, p.blockPosition());
					return (bad.length() == 0 ? "ok" : bad.toString()) + "|" + top + "|" + XpSources.layerAt(level, at);
				});
				String[] parts = result.split("\\|");
				c.check("player level " + pl + ", " + layer + ": 40 zombies follow the level formula and cap", parts[0].equals("ok"), parts[0]);
				c.check("player level " + pl + ", " + layer + ": the spot really is in the " + layer + " layer", parts[2].equalsIgnoreCase(layer.equals("surface") ? "SURFACE" : layer.equals("caves") ? "CAVES" : "DEEPSLATE"),
					parts[2]);
				c.note("player level " + pl + ", " + layer + ": highest zombie level " + parts[1]);
			}
		}
		setLevel(server, 10);
	}

	private static void rankRates(TestServerContext server, Checks c) {
		c.section("Rank rates (champion 3% anywhere, elite 1% in caves and deeper, warlord 0.2% in deepslate)");
		for (String layer : new String[] {"surface", "deepslate"}) {
			int[] counts = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos at = layer.equals("surface") ? p.blockPosition().offset(3, 0, 0) : new BlockPos(p.getBlockX() + 3, -20, p.getBlockZ());
				int[] out = new int[4];
				for (int i = 0; i < 2000; i++) {
					Mob mob = spawn(level, type("zombie"), at);
					out[MobService.data(mob).rank()]++;
					mob.discard();
				}
				sweepMobs(level, p.blockPosition());
				return out;
			});
			c.note(layer + " 2000 zombies: normal " + counts[0] + ", champion " + counts[1] + ", elite " + counts[2] + ", warlord " + counts[3]);
			c.check(layer + ": champions are about 3% (30 to 95 of 2000)", counts[1] >= 30 && counts[1] <= 95, "champions " + counts[1]);
			if (layer.equals("surface")) {
				c.check("surface: no elites and no warlords", counts[2] == 0 && counts[3] == 0, "elites " + counts[2] + ", warlords " + counts[3]);
			} else {
				c.check("deepslate: elites are about 1% (6 to 45 of 2000)", counts[2] >= 6 && counts[2] <= 45, "elites " + counts[2]);
				c.check("deepslate: warlords are rare (at most 16 of 2000)", counts[3] <= 16, "warlords " + counts[3]);
			}
		}
	}

	private static void armor(TestServerContext server, Checks c) {
		c.section("Armor formula: blocked = armor / (armor + 20), toughness half an armor point, at most 85%");
		float[][] cases = {{0, 0}, {5, 0}, {10, 0}, {20, 0}, {20, 8}, {40, 0}, {100, 0}, {1000, 0}, {30, 20}};
		for (float[] cs : cases) {
			float armor = cs[0];
			float tough = cs[1];
			float got = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				Mob target = dummy(level, p.blockPosition().offset(-4, 0, 0));
				float out = CombatRules.getDamageAfterAbsorb(target, 10f, level.damageSources().generic(), armor, tough);
				target.discard();
				return out;
			});
			float eff = armor + tough / 2f;
			float blocked = Math.min(0.85f, eff / (eff + 20f));
			float expect = 10f * (1f - blocked);
			c.check("armor " + armor + " toughness " + tough + ": 10 damage becomes " + expect, Math.abs(got - expect) < 0.01, "was " + got);
			c.check("ArmorFormula.blocked(" + armor + ", " + tough + ") matches", Math.abs(ArmorFormula.blocked(armor, tough) - blocked) < 0.0001);
		}
		float capped = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob target = dummy(p.level(), p.blockPosition().offset(-4, 0, 0));
			float out = CombatRules.getDamageAfterAbsorb(target, 100f, p.level().damageSources().generic(), 100000f, 0f);
			target.discard();
			return out;
		});
		c.check("no amount of armor blocks more than 85%", Math.abs(capped - 15f) < 0.01, "100 damage became " + capped);
	}

	private static void rankCommand(TestServerContext server, Checks c) {
		c.section("vestiges mob rank");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			sweepMobs(p.level(), p.blockPosition());
			Mob zombie = spawn(p.level(), type("husk"), p.blockPosition().offset(3, 0, 0));
			MobService.apply(zombie, new MobData(5, 0, List.of(), 1.0f, false));
		});
		String[] ranks = {"normal", "champion", "elite", "warlord"};
		for (int r : new int[] {2, 3, 1, 0}) {
			Checks.Result res = c.expectOk(server, "vestiges mob rank " + ranks[r] + " 30");
			String problem = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Mob mob = p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(8)).get(0);
				return verify(p.level(), mob, null, 10, 30);
			});
			c.check("after `mob rank " + ranks[r] + " 30` the mob is level 30, rank " + r + " with " + r + " power(s) and matching health", problem.isEmpty(), problem);
			c.check("`mob info` names the rank " + ranks[r], res.text().contains(ranks[r]) && res.text().contains("level 30"), res.text());
		}
		Checks.Result keep = c.expectOk(server, "vestiges mob rank elite");
		c.check("a rank without a level keeps the mob's level", keep.text().contains("level 30"), keep.text());
		c.expectRejected(server, "vestiges mob rank boss");
		server.runOnServer(s -> sweepMobs(player(s).level(), player(s).blockPosition()));
		c.expectRejected(server, "vestiges mob rank elite");
		c.expectRejected(server, "vestiges mob info");
	}

	private static void killXp(TestServerContext server, Checks c) {
		c.section("Kill XP = 10 + 4 x mob level, times the level-gap rule, times rank (champion 2, elite 3, warlord 5)");
		int[][] cases = {{10, 10, 0}, {10, 10, 1}, {10, 10, 2}, {10, 10, 3}, {10, 1, 0}, {30, 40, 0}, {50, 5, 0}};
		for (int[] cs : cases) {
			int playerLevel = cs[0];
			int mobLevel = cs[1];
			int rank = cs[2];
			setLevel(server, playerLevel);
			long gained = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				sweepMobs(level, p.blockPosition());
				Mob mob = spawn(level, type("husk"), p.blockPosition().offset(3, 0, 0));
				MobService.apply(mob, new MobData(mobLevel, rank, List.of(), 1.0f, false));
				long before = totalXp(p);
				mob.hurtServer(level, level.damageSources().playerAttack(p), 100000f);
				long after = totalXp(p);
				boolean dead = mob.isDeadOrDying();
				sweepMobs(level, p.blockPosition());
				return dead ? after - before : -1;
			});
			double strength = new MobData(mobLevel, rank, List.of(), 1f, false).strength();
			long expect = Math.round(LevelCurve.killXp(mobLevel) * LevelCurve.gapMultiplier(playerLevel, mobLevel) * strength);
			c.check("player " + playerLevel + " kills a level " + mobLevel + " rank " + rank + " monster: " + expect + " XP", gained == expect,
				gained == -1 ? "the monster did not die" : "gained " + gained);
		}
		setLevel(server, 10);
	}

	private static void persistence(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("Set once and saved");
		MobData[] first = new MobData[1];
		java.util.UUID[] tracked = new java.util.UUID[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			sweepMobs(p.level(), p.blockPosition());
			Mob mob = spawn(p.level(), type("husk"), p.blockPosition().offset(3, 0, 0));
			MobService.setRank(mob, MobData.ELITE, 12);
			first[0] = MobService.data(mob);
			tracked[0] = mob.getUUID();
		});
		setLevel(server, 90);
		ctx.waitTicks(60);
		MobData later = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			// The very mob made above (a natural spawn next to it must not be mistaken for it).
			net.minecraft.world.entity.Entity e = p.level().getEntity(tracked[0]);
			return e instanceof Mob mob ? MobService.data(mob) : null;
		});
		c.check("60 ticks and a new player level later, the monster's data is unchanged", first[0] != null && first[0].equals(later), first[0] + " vs " + later);
		var encoded = MobData.CODEC.encodeStart(NbtOps.INSTANCE, first[0]).result();
		MobData round = encoded.flatMap(t -> MobData.CODEC.parse(NbtOps.INSTANCE, t).result()).orElse(null);
		c.check("the data survives being saved and loaded (codec round trip)", first[0].equals(round), "got " + round);
		c.note("the save-and-reload of a whole entity is not driven here; the data is a persistent attachment and its attribute modifiers are permanent");
		setLevel(server, 10);
		server.runOnServer(s -> sweepMobs(player(s).level(), player(s).blockPosition()));
	}

	private static void babies(TestServerContext server, Checks c) {
		c.section("Babies take the size of nearby adults");
		float[] sizes = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			sweepMobs(level, p.blockPosition());
			Mob adult = spawn(level, type("zombie"), p.blockPosition().offset(3, 0, 0));
			MobService.apply(adult, new MobData(5, 0, List.of(), 1.2f, false));
			Zombie baby = (Zombie) type("zombie").create(level, EntitySpawnReason.COMMAND);
			baby.setBaby(true);
			baby.setPos(p.getX() + 5, p.getY(), p.getZ());
			level.addFreshEntity(baby);
			float near = MobService.data(baby).size();
			Set<Float> far = new HashSet<>();
			for (int i = 0; i < 30; i++) {
				Zombie lone = (Zombie) type("zombie").create(level, EntitySpawnReason.COMMAND);
				lone.setBaby(true);
				lone.setPos(p.getX() + 40, p.getY(), p.getZ() + 40);
				level.addFreshEntity(lone);
				far.add(MobService.data(lone).size());
			}
			sweepMobs(level, p.blockPosition());
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition().offset(40, 0, 40)).inflate(10))) {
				m.discard();
			}
			return new float[] {near, far.size(), far.contains(1.2f) ? 1 : 0};
		});
		c.check("a baby zombie next to an adult of size 120% is also 120%", Math.abs(sizes[0] - 1.2f) < 0.0001, "size " + sizes[0]);
		c.check("babies far from any adult roll their own sizes (30 babies, several different sizes)", sizes[1] >= 5, sizes[1] + " different sizes");
	}

	private static void switchesOff(ClientGameTestContext ctx, TestServerContext server, Checks c, java.util.Map<dev.vestiges.config.VestigesSystem, Boolean> snapshot) {
		c.section("Switches: elites off, then mob levels off");
		server.runCommand("vestiges config system elites false");
		ctx.waitTicks(3);
		int[] withElitesOff = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			int ranked = 0;
			int noData = 0;
			for (int y : new int[] {p.getBlockY(), -20}) {
				for (int i = 0; i < 600; i++) {
					Mob mob = spawn(level, type("zombie"), new BlockPos(p.getBlockX() + 3, y, p.getBlockZ()));
					MobData d = MobService.data(mob);
					if (d == null) {
						noData++;
					} else if (d.rank() != 0 || !d.powers().isEmpty()) {
						ranked++;
					}
					mob.discard();
				}
			}
			sweepMobs(level, p.blockPosition());
			return new int[] {ranked, noData};
		});
		c.check("with elites off, 1200 monsters (surface and deepslate) are all rank normal with no powers", withElitesOff[0] == 0, withElitesOff[0] + " ranked");
		c.check("with elites off, monsters still have levels", withElitesOff[1] == 0, withElitesOff[1] + " without data");
		T.restore(server, snapshot);
		ctx.waitTicks(3);

		server.runCommand("vestiges config system mob_scaling false");
		ctx.waitTicks(3);
		c.check("elites went off with mob levels (it needs them)", !ConfigStore.server().enabled(dev.vestiges.config.VestigesSystem.ELITES));
		List<EntityType<?>> types = server.computeOnServer(s -> monsterTypes(player(s).level()));
		String bad = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			StringBuilder out = new StringBuilder();
			for (EntityType<?> t : types) {
				Mob mob = spawn(level, t, p.blockPosition().offset(3, 0, 0));
				if (mob == null) {
					continue;
				}
				String name = BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath();
				if (MobService.data(mob) != null) {
					out.append(name).append(" has data; ");
				}
				if (mob.getAttribute(Attributes.MAX_HEALTH).getModifier(LEVEL_HEALTH) != null) {
					out.append(name).append(" has the level health modifier; ");
				}
				if (Math.abs(mob.getAttributeValue(Attributes.SCALE) - 1.0) > 0.0001 && !mob.isBaby() && mob.getAttributeBaseValue(Attributes.SCALE) == 1.0) {
					out.append(name).append(" is resized; ");
				}
				if (MobService.damageMultiplier(mob) != 1.0) {
					out.append(name).append(" hits harder; ");
				}
				mob.discard();
			}
			sweepMobs(level, p.blockPosition());
			return out.toString();
		});
		c.check("with mob levels off, all " + types.size() + " monster types are vanilla (no data, health, size or damage change)", bad.isEmpty(), bad);
		c.check("with mob levels off, a monster's health is its vanilla health", server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob z = spawn(p.level(), type("zombie"), p.blockPosition().offset(3, 0, 0));
			boolean ok = Math.abs(z.getMaxHealth() - 20.0) < 0.001;
			z.discard();
			return ok;
		}));
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		boolean back = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob z = spawn(p.level(), type("zombie"), p.blockPosition().offset(3, 0, 0));
			boolean ok = MobService.data(z) != null;
			z.discard();
			return ok;
		});
		c.check("after turning mob levels back on, new monsters are levelled again", back);
	}
}
