package dev.vestiges.gametest;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.gear.GearLadder;
import dev.vestiges.gear.Rarity;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.loot.LootGear;
import dev.vestiges.loot.LootLevels;
import dev.vestiges.loot.LootRules;
import dev.vestiges.loot.MobDrops;
import dev.vestiges.loot.PersonalLoot;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** S6: loot by level band and rarity odds, personal chests (synthetic and in real structures), vanilla exceptions, mob drops, the loot switches. */
final class LootSuite {
	private LootSuite() {
	}

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void setLevel(TestServerContext server, int level) {
		server.runOnServer(s -> LevelService.set(player(s), PlayerLevel.START.withXp(level, 0, 0)));
	}

	private static ResourceKey<LootTable> key(String id) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(id));
	}

	private static LootParams chestParams(ServerPlayer p, Vec3 at) {
		return new LootParams.Builder(p.level()).withParameter(LootContextParams.ORIGIN, at).withParameter(LootContextParams.THIS_ENTITY, p)
			.create(LootContextParamSets.CHEST);
	}

	private static List<String> describe(Container c) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) {
				out.add(i + ":" + s.getItem() + "x" + s.getCount());
			}
		}
		return out;
	}

	private static String at(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	// ------------------------------------------------------------------ the suite

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		server.runCommand("clear @p");
		server.runCommand("vestiges tree reset @p");
		setLevel(server, 40);
		var snapshot = T.switches();

		bands(server, c);
		forcedRarity(server, c);
		BlockPos pad = T.pad(ctx, server, 300, 300);
		chestCommand(ctx, server, c, pad);
		everyTable(server, c, pad);
		personal(ctx, server, c, pad, snapshot);
		vanillaExceptions(ctx, server, c, pad);
		mobDrops(server, c);
		lootOff(ctx, server, c, snapshot);

		server.runCommand("gamemode creative @p");
		setLevel(server, 20);
	}

	// ------------------------------------------------------------------ bands and rarity

	private static final Pattern LINE1 = Pattern.compile("band (\\d+)\\), (\\d+) rolls: ([0-9.]+) stacks \\S+ ([0-9.]+) gear \\S+ ([0-9.]+) books \\S+ ([0-9.]+) emeralds \\S+ ([0-9.]+) diamonds");

	private static void bands(TestServerContext server, Checks c) {
		c.section("`vestiges loot test`: 1,000 rolls at each end of every loot band");
		LootRules rules = LootRules.get();
		int[] levels = {1, 4, 5, 9, 10, 14, 15, 19, 20, 29, 30, 39, 40, 49, 50, 64, 65, 79, 80, 89, 90, 100};
		c.check("there are 11 loot bands and 4 rarity rows", rules.bands().size() == 11 && rules.rarity().size() == 4,
			rules.bands().size() + " bands, " + rules.rarity().size() + " rows");
		double[] stacksAt = new double[101];
		double[] emeraldsAt = new double[101];
		for (int level : levels) {
			Checks.Result r = c.expectOk(server, "vestiges loot test " + level);
			Matcher m = LINE1.matcher(r.text());
			if (!m.find()) {
				c.check("loot level " + level + " answer can be read", false, r.text());
				continue;
			}
			int band = Integer.parseInt(m.group(1));
			double gear = Double.parseDouble(m.group(4));
			double books = Double.parseDouble(m.group(5));
			stacksAt[level] = Double.parseDouble(m.group(3));
			emeraldsAt[level] = Double.parseDouble(m.group(6));
			c.check("loot level " + level + " is in band " + rules.band(level), band == rules.band(level), "answered band " + band);
			double n = 1000.0 * (gear + books);
			List<Double> odds = rules.row(level).odds();
			double total = odds.stream().mapToDouble(Double::doubleValue).sum();
			StringBuilder bad = new StringBuilder();
			StringBuilder seen = new StringBuilder();
			for (Rarity.Tier t : Rarity.Tier.values()) {
				Matcher pm = Pattern.compile(t.label + " ([0-9.]+)%").matcher(r.text());
				if (!pm.find()) {
					bad.append(t.label).append(" missing; ");
					continue;
				}
				double got = Double.parseDouble(pm.group(1));
				double p = odds.get(t.ordinal()) / total;
				double tol = 100 * 4 * Math.sqrt(p * (1 - p) / Math.max(1, n)) + 1.0;
				seen.append(t.label).append(' ').append(got).append(" ");
				if (Math.abs(got - 100 * p) > tol) {
					bad.append(t.label).append(" ").append(got).append("% not ").append(String.format(java.util.Locale.ROOT, "%.1f", 100 * p)).append("% (±")
						.append(String.format(java.util.Locale.ROOT, "%.1f", tol)).append("); ");
				}
			}
			c.check("loot level " + level + ": rarity split matches the odds (" + (int) n + " items)", bad.length() == 0 && n >= 200, bad + (n < 200 ? "only " + n + " gear/books" : ""));
		}
		c.check("deeper chests hold more: level 100 has more stacks than level 1", stacksAt[100] > stacksAt[1], stacksAt[100] + " vs " + stacksAt[1]);
		c.check("deeper chests hold more emeralds: level 100 has more than level 1", emeraldsAt[100] > emeraldsAt[1], emeraldsAt[100] + " vs " + emeraldsAt[1]);
	}

	private static void forcedRarity(TestServerContext server, Checks c) {
		c.section("A chosen rarity comes out as that rarity (gear and books, 40 each)");
		for (Rarity.Tier t : Rarity.Tier.values()) {
			for (boolean book : new boolean[] {false, true}) {
				int hits = server.computeOnServer(s -> {
					ServerPlayer p = player(s);
					int ok = 0;
					for (int i = 0; i < 40; i++) {
						ItemStack stack = LootGear.build(p.level(), p.getRandom(), 50, book, null, t);
						if (Rarity.of(stack).tier() == t) {
							ok++;
						}
					}
					return ok;
				});
				boolean high = t.ordinal() >= Rarity.Tier.LEGENDARY.ordinal();
				c.check((book ? "books" : "gear") + " asked as " + t.label + " come out " + t.label + (high ? " at least once" : " at least 30 of 40 times"),
					high ? hits >= 1 : hits >= 30, hits + " of 40");
				if (hits < 40) {
					c.note((book ? "books" : "gear") + " asked as " + t.label + ": " + hits + " of 40 landed there (the rest stepped down a rarity)");
				}
			}
		}
	}

	// ------------------------------------------------------------------ `loot chest`

	private static void lookAt(TestServerContext server, Vec3 target) {
		server.runOnServer(s -> player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target));
	}

	private static int stacks(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> {
			BlockEntity be = player(s).level().getBlockEntity(pos);
			return be instanceof Container ct ? describe(ct).size() : -1;
		});
	}

	private static void chestCommand(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad) {
		c.section("`vestiges loot chest`");
		BlockPos chest = pad.offset(2, 0, 0);
		server.runCommand("execute in minecraft:overworld run setblock " + at(chest) + " minecraft:chest");
		lookAt(server, Vec3.atCenterOf(chest));
		ctx.waitTicks(3);
		c.note("looking at the chest: " + server.computeOnServer(s -> player(s).pick(6.0, 1.0f, false).getType() + " at " + player(s).pick(6.0, 1.0f, false).getLocation()
			+ " from " + player(s).position() + " rot " + player(s).getXRot() + "/" + player(s).getYRot()));
		int low = 0;
		int high = 0;
		for (int i = 0; i < 6; i++) {
			c.expectOk(server, "vestiges loot chest 1");
			low += stacks(server, chest);
			c.expectOk(server, "vestiges loot chest 100");
			high += stacks(server, chest);
		}
		c.check("the chest is filled at loot level 1", low > 0, "stacks over 6 fills: " + low);
		c.check("a loot level 100 chest holds more than a loot level 1 chest (6 fills each)", high > low, high + " vs " + low);
		lookAt(server, Vec3.atCenterOf(pad.below().offset(0, 0, 2)));
		ctx.waitTicks(2);
		c.expectRejected(server, "vestiges loot chest 10");
		server.runCommand("execute in minecraft:overworld run setblock " + at(chest) + " minecraft:air");
	}

	// ------------------------------------------------------------------ every vanilla chest table

	private static void everyTable(TestServerContext server, Checks c, BlockPos pad) {
		c.section("Every vanilla chest table still rolls items (with the scaled pool added)");
		List<ResourceKey<LootTable>> keys = server.computeOnServer(s -> s.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE)
			.listElementIds().filter(k -> k.identifier().getNamespace().equals("minecraft")
				&& k.identifier().getPath().startsWith("chests/")).sorted(java.util.Comparator.comparing(k -> k.identifier().toString())).toList());
		c.check("the game has chest tables to test", keys.size() >= 30, "found " + keys.size());
		List<String> empty = new ArrayList<>();
		for (ResourceKey<LootTable> k : keys) {
			String path = k.identifier().getPath();
			if (path.equals("chests/shipwreck_map")) {
				c.note("chests/shipwreck_map skipped: its treasure-map function searches the world for a structure");
				continue;
			}
			String result = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				LootTable table = s.reloadableRegistries().getLootTable(k);
				try {
					int stacks = 0;
					for (int i = 0; i < 12; i++) {
						stacks += table.getRandomItems(chestParams(p, Vec3.atCenterOf(pad)), p.getRandom()).size();
					}
					return stacks > 0 ? "ok" : "rolled nothing in 12 tries";
				} catch (RuntimeException e) {
					return "threw " + e;
				}
			});
			c.check("table " + path + " rolls items", result.equals("ok"), result);
			if (!result.equals("ok")) {
				empty.add(path);
			}
		}
	}

	// ------------------------------------------------------------------ personal chests

	private static void personal(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad, Map<VestigesSystem, Boolean> snapshot) {
		c.section("Personal copies in chests, barrels and chest minecarts");
		BlockPos chest = pad.offset(2, 0, 2);
		BlockPos barrel = pad.offset(-2, 0, 2);
		server.runCommand("execute in minecraft:overworld run setblock " + at(chest) + " minecraft:chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
		server.runCommand("execute in minecraft:overworld run setblock " + at(barrel) + " minecraft:barrel{LootTable:\"minecraft:chests/abandoned_mineshaft\"}");
		server.runCommand("execute in minecraft:overworld run summon minecraft:chest_minecart " + (pad.getX() + 0.5) + " " + pad.getY() + " " + (pad.getZ() - 2.5)
			+ " {LootTable:\"minecraft:chests/abandoned_mineshaft\"}");
		ctx.waitTicks(5);
		for (String kind : new String[] {"chest", "barrel", "chest minecart"}) {
			String[] res = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				Object holder;
				boolean empty;
				if (kind.equals("chest minecart")) {
					List<AbstractMinecartContainer> carts = level.getEntitiesOfClass(AbstractMinecartContainer.class, new AABB(pad).inflate(6));
					holder = carts.isEmpty() ? null : carts.get(0);
				} else {
					holder = level.getBlockEntity(kind.equals("chest") ? chest : barrel);
				}
				if (holder == null) {
					return new String[] {"no holder", "", "", ""};
				}
				AbstractContainerMenu one = PersonalLoot.open(holder, 41, p.getInventory(), p);
				if (!(one instanceof ChestMenu m1)) {
					return new String[] {"open returned " + one, "", "", ""};
				}
				List<String> first = describe(m1.getContainer());
				one.removed(p);
				ChestMenu m2 = (ChestMenu) PersonalLoot.open(holder, 42, p.getInventory(), p);
				List<String> second = describe(m2.getContainer());
				// Take the first item out of the copy: the next opening must remember that.
				int firstSlot = m2.getContainer().getContainerSize();
				for (int i = 0; i < m2.getContainer().getContainerSize(); i++) {
					if (!m2.getContainer().getItem(i).isEmpty()) {
						firstSlot = i;
						break;
					}
				}
				if (firstSlot < m2.getContainer().getContainerSize()) {
					m2.getContainer().setItem(firstSlot, ItemStack.EMPTY);
				}
				m2.removed(p);
				ChestMenu m3 = (ChestMenu) PersonalLoot.open(holder, 43, p.getInventory(), p);
				List<String> third = describe(m3.getContainer());
				m3.removed(p);
				net.fabricmc.fabric.api.attachment.v1.AttachmentTarget target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) holder;
				PersonalLoot.LootSource src = target.getAttached(PersonalLoot.SOURCE);
				boolean vanillaGone;
				boolean realEmpty = true;
				if (holder instanceof RandomizableContainerBlockEntity r) {
					vanillaGone = r.getLootTable() == null;
					for (int i = 0; i < r.getContainerSize(); i++) {
						realEmpty &= r.getItem(i).isEmpty();
					}
				} else {
					AbstractMinecartContainer cart = (AbstractMinecartContainer) holder;
					vanillaGone = cart.getContainerLootTable() == null;
					for (int i = 0; i < cart.getContainerSize(); i++) {
						realEmpty &= cart.getItem(i).isEmpty();
					}
				}
				String copies = String.valueOf(target.getAttachedOrElse(PersonalLoot.COPIES, PersonalLoot.LootCopies.EMPTY).byPlayer().keySet());
				return new String[] {"ok", first.size() + "|" + first.equals(second) + "|" + third.size() + "|" + (src != null && src.opened().contains(p.getUUID()))
					+ "|" + vanillaGone + "|" + realEmpty + "|" + copies.contains(p.getUUID().toString()) + "|" + (src == null ? "" : src.table()), "", ""};
			});
			if (!res[0].equals("ok")) {
				c.check(kind + " can be opened as a personal copy", false, res[0]);
				continue;
			}
			String[] v = res[1].split("\\|");
			c.check(kind + ": the player's copy is filled", Integer.parseInt(v[0]) > 0, v[0] + " stacks");
			c.check(kind + ": opening it again shows the same loot", v[1].equals("true"));
			c.check(kind + ": what the player took out stays gone (" + v[0] + " stacks -> " + v[2] + ")", Integer.parseInt(v[2]) == Integer.parseInt(v[0]) - 1);
			c.check(kind + ": the container remembers that this player opened it", v[3].equals("true"));
			c.check(kind + ": the vanilla loot table is gone and the real container is empty (no hopper or explosion tricks)", v[4].equals("true") && v[5].equals("true"));
			c.check(kind + ": the player's copy is saved under their id", v[6].equals("true"));
			c.note(kind + " source table: " + (v.length > 7 ? v[7] : "?"));
		}

		c.section("Sneak-break");
		BlockPos breakable = pad.offset(0, 0, 3);
		server.runCommand("execute in minecraft:overworld run setblock " + at(breakable) + " minecraft:chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
		ctx.waitTicks(5);
		server.runCommand("gamemode survival @p");
		boolean[] r = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			p.setShiftKeyDown(false);
			boolean plain = p.gameMode.destroyBlock(breakable);
			boolean stillThere = level.getBlockState(breakable).is(net.minecraft.world.level.block.Blocks.CHEST);
			p.setShiftKeyDown(true);
			boolean sneaked = p.gameMode.destroyBlock(breakable);
			boolean gone = level.getBlockState(breakable).isAir();
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(breakable).inflate(3));
			boolean onlyChest = !drops.isEmpty() && drops.stream().allMatch(d -> d.getItem().is(Items.CHEST));
			drops.forEach(ItemEntity::discard);
			p.setShiftKeyDown(false);
			return new boolean[] {plain, stillThere, sneaked, gone, onlyChest};
		});
		server.runCommand("gamemode creative @p");
		c.check("breaking a loot chest without sneaking does nothing", !r[0] && r[1]);
		c.check("breaking it while sneaking works", r[2] && r[3]);
		c.check("a sneak-broken loot chest drops only the chest, none of its loot", r[4]);

		c.section("Per-player chests switched off");
		BlockPos converted = pad.offset(3, 0, 0);
		server.runCommand("execute in minecraft:overworld run setblock " + at(converted) + " minecraft:chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
		ctx.waitTicks(5);
		boolean wasConverted = server.computeOnServer(s -> player(s).level().getBlockEntity(converted).getAttached(PersonalLoot.SOURCE) == null);
		server.runOnServer(s -> {
			// Opening once converts it for certain, whatever order the load events came in.
			ServerPlayer p = player(s);
			AbstractContainerMenu m = PersonalLoot.open(p.level().getBlockEntity(converted), 50, p.getInventory(), p);
			if (m != null) {
				m.removed(p);
			}
		});
		server.runCommand("vestiges config system per_player_loot false");
		ctx.waitTicks(3);
		boolean[] off = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockEntity be = p.level().getBlockEntity(converted);
			AbstractContainerMenu m = PersonalLoot.open(be, 51, p.getInventory(), p);
			RandomizableContainerBlockEntity rb = (RandomizableContainerBlockEntity) be;
			return new boolean[] {m == null, rb.getLootTable() != null, be.getAttached(PersonalLoot.SOURCE) == null};
		});
		c.check("with per-player chests off, a chest opens as the vanilla chest", off[0]);
		c.check("with per-player chests off, a converted chest gets its vanilla loot table back", off[1] && off[2]);
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		c.check("per-player chests are back on", ConfigStore.server().enabled(VestigesSystem.PER_PLAYER_LOOT));
		c.note("a second player getting a different copy is not tested (dropped from the plan)");
	}

	// ------------------------------------------------------------------ vanilla exceptions

	private static void vanillaExceptions(ClientGameTestContext ctx, TestServerContext server, Checks c, BlockPos pad) {
		c.section("Dispensers, the bonus chest and trial vaults stay vanilla");
		var mod = FabricLoader.getInstance().getModContainer("vestiges").orElseThrow();
		for (String path : new String[] {"chests/jungle_temple_dispenser", "chests/spawn_bonus_chest", "chests/trial_chambers/reward",
			"chests/trial_chambers/reward_ominous", "chests/trial_chambers/reward_unique", "chests/trial_chambers/reward_ominous_unique"}) {
			boolean overridden = mod.findPath("data/minecraft/loot_table/" + path + ".json").isPresent();
			c.check("the mod does not replace " + path, !overridden);
		}
		BlockPos dispenser = pad.offset(-3, 0, 0);
		server.runCommand("execute in minecraft:overworld run setblock " + at(dispenser) + " minecraft:dispenser{LootTable:\"minecraft:chests/jungle_temple_dispenser\"}");
		ctx.waitTicks(10);
		boolean[] d = server.computeOnServer(s -> {
			BlockEntity be = player(s).level().getBlockEntity(dispenser);
			return new boolean[] {be instanceof RandomizableContainerBlockEntity, be instanceof RandomizableContainerBlockEntity r && r.getLootTable() != null,
				be.getAttached(PersonalLoot.SOURCE) == null};
		});
		c.check("a dispenser with a loot table keeps it and gets no personal copy", d[0] && d[1] && d[2]);
		int[] bonus = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			LootTable table = s.reloadableRegistries().getLootTable(key("minecraft:chests/spawn_bonus_chest"));
			int enchanted = 0;
			int emeralds = 0;
			int stacks = 0;
			for (int i = 0; i < 100; i++) {
				for (ItemStack st : table.getRandomItems(chestParams(p, p.position()), p.getRandom())) {
					stacks++;
					enchanted += st.isEnchanted() ? 1 : 0;
					emeralds += st.is(Items.EMERALD) ? 1 : 0;
				}
			}
			return new int[] {stacks, enchanted, emeralds};
		});
		c.check("the bonus chest table rolls plain starter items (no enchanted gear, no emeralds in 100 rolls)", bonus[0] > 0 && bonus[1] == 0 && bonus[2] == 0,
			"stacks " + bonus[0] + ", enchanted " + bonus[1] + ", emeralds " + bonus[2]);
		for (String vault : new String[] {"chests/trial_chambers/reward", "chests/trial_chambers/reward_ominous"}) {
			int[] v = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				LootTable table = s.reloadableRegistries().getLootTable(key("minecraft:" + vault));
				int stacks = 0;
				int emeralds = 0;
				for (int i = 0; i < 50; i++) {
					for (ItemStack st : table.getRandomItems(chestParams(p, p.position()), p.getRandom())) {
						stacks++;
						emeralds += st.is(Items.EMERALD_BLOCK) ? 1 : 0;
					}
				}
				return new int[] {stacks, emeralds};
			});
			c.check(vault + " rolls its vanilla rewards", v[0] > 0, "stacks " + v[0]);
		}
		c.note("the real bonus chest at world creation and real trial vaults are covered by the table checks above; the structures suite opens real chests");
	}

	// ------------------------------------------------------------------ mob drops

	private static Mob husk(ServerPlayer p, int level, int rank) {
		Mob m = (Mob) MobSuite.type("husk").spawn(p.level(), p.blockPosition().offset(3, 0, 0), EntitySpawnReason.COMMAND);
		MobService.apply(m, new MobData(level, rank, List.of(), 1.0f, false));
		return m;
	}

	private static Set<String> dropIds(MinecraftServer s, int mobLevel, int rank, int rolls) {
		ServerPlayer p = player(s);
		Mob mob = husk(p, mobLevel, rank);
		LootTable table = s.reloadableRegistries().getLootTable(MobDrops.TABLE);
		LootParams params = new LootParams.Builder(p.level()).withParameter(LootContextParams.THIS_ENTITY, mob)
			.withParameter(LootContextParams.ORIGIN, mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE, p.level().damageSources().playerAttack(p))
			.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p).create(LootContextParamSets.ENTITY);
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < rolls; i++) {
			for (ItemStack st : table.getRandomItems(params, p.getRandom())) {
				ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
			}
		}
		mob.discard();
		return ids;
	}

	private static void mobDrops(TestServerContext server, Checks c) {
		c.section("Mob drops follow the mob's level");
		setLevel(server, 10);
		Set<String> low = server.computeOnServer(s -> dropIds(s, 5, 0, 800));
		Set<String> mid = server.computeOnServer(s -> dropIds(s, 25, 0, 800));
		Set<String> high = server.computeOnServer(s -> dropIds(s, 80, 0, 800));
		c.note("drops at mob level 5: " + low + "; level 25: " + mid + "; level 80: " + high);
		c.check("a level 5 monster drops copper or gold nuggets, never iron, gold ingots or diamonds (the player is level 10)",
			!low.isEmpty() && !low.contains("iron_ingot") && !low.contains("gold_ingot") && !low.contains("diamond"), low.toString());
		c.check("a level 25 monster can drop iron or gold ingots but no diamonds", (mid.contains("iron_ingot") || mid.contains("gold_ingot")) && !mid.contains("diamond"), mid.toString());
		c.check("a level 80 monster can drop diamonds and no copper", high.contains("diamond") && !high.contains("copper_ingot"), high.toString());
		c.check("the drops come from the monster's level, not the player's (player is level 10 in all three)", high.contains("diamond") && !low.contains("diamond"));

		String[] missingItems = {""};
		int[] direct = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Mob mob = husk(p, 30, 3);
			LootTable table = s.reloadableRegistries().getLootTable(MobDrops.TABLE);
			LootParams params = new LootParams.Builder(p.level()).withParameter(LootContextParams.THIS_ENTITY, mob)
				.withParameter(LootContextParams.ORIGIN, mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE, p.level().damageSources().playerAttack(p))
				.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p).create(LootContextParamSets.ENTITY);
			int noGear = 0;
			int plain = 0;
			Set<String> missing = new HashSet<>();
			for (int i = 0; i < 300; i++) {
				List<ItemStack> items = table.getRandomItems(params, p.getRandom());
				boolean gear = items.stream().anyMatch(st -> !st.is(Items.EMERALD) && !st.is(Items.ENCHANTED_BOOK) && Rarity.of(st).tier() != Rarity.Tier.COMMON);
				noGear += gear ? 0 : 1;
				if (!gear) {
					items.stream().filter(st -> !st.is(Items.EMERALD)).forEach(st -> missing.add(st.getItem().toString() + (st.isEnchanted() ? "*" : "")));
				}
				plain += items.stream().anyMatch(st -> GearLadder.isGear(st) && Rarity.of(st).tier() == Rarity.Tier.COMMON) ? 1 : 0;
			}
			mob.discard();
			missingItems[0] = missing.toString();
			return new int[] {noGear, plain};
		});
		c.check("rolling the Warlord drop table 300 times always gives a piece of gear or an enchanted tool above Common", direct[0] == 0, direct[0] + " rolls without gear; the other items there: " + missingItems[0]);
		c.check("the Warlord's gear is never Common (300 rolls)", direct[1] == 0, direct[1] + " rolls with Common gear");
		String[] lastDrops = {""};
		int[] warlords = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int emeraldsOk = 0;
			int gearOk = 0;
			for (int i = 0; i < 5; i++) {
				ServerLevel level = p.level();
				Mob mob = husk(p, 30, 3);
				mob.hurtServer(level, level.damageSources().playerAttack(p), 100000f);
				List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(mob.blockPosition()).inflate(4));
				int emeralds = drops.stream().filter(d -> d.getItem().is(Items.EMERALD)).mapToInt(d -> d.getItem().getCount()).sum();
				emeraldsOk += emeralds >= 10 ? 1 : 0;
				boolean gearHere = drops.stream().anyMatch(d -> !d.getItem().is(Items.EMERALD) && !d.getItem().is(Items.ENCHANTED_BOOK) && Rarity.of(d.getItem()).tier() != Rarity.Tier.COMMON);
				gearOk += gearHere ? 1 : 0;
				if (!gearHere) {
					lastDrops[0] += drops.stream().map(d -> d.getItem() + (GearLadder.isGear(d.getItem()) ? "(gear)" : "") + (d.getItem().isEnchanted() ? "*" : "")).toList() + " ";
				}
				drops.forEach(ItemEntity::discard);
			}
			return new int[] {emeraldsOk, gearOk};
		});
		c.check("5 real Warlord kills each drop at least 10 emeralds", warlords[0] == 5, warlords[0] + " of 5");
		int[] commons = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int common = 0;
			for (int i = 0; i < 300; i++) {
				ItemStack st = LootGear.build(p.level(), p.getRandom(), 30, false, Rarity.Tier.UNCOMMON, null);
				common += Rarity.of(st).tier() == Rarity.Tier.COMMON ? 1 : 0;
			}
			return new int[] {common};
		});
		c.check("elite gear (minimum Uncommon) is never Common in 300 builds", commons[0] == 0, commons[0] + " of 300 came out Common");
		c.check("5 real Warlord kills each drop a piece of gear above Common", warlords[1] == 5, warlords[1] + " of 5; without gear: " + lastDrops[0]);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (Mob m : p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(30))) {
				m.discard();
			}
		});
	}

	// ------------------------------------------------------------------ loot switched off

	private static void lootOff(ClientGameTestContext ctx, TestServerContext server, Checks c, Map<VestigesSystem, Boolean> snapshot) {
		c.section("Banded loot switched off: vanilla chest tables and vanilla drops");
		setLevel(server, 60);
		Set<String> on = server.computeOnServer(s -> itemIds(s, "minecraft:chests/simple_dungeon", 300));
		server.runCommand("vestiges config system loot false");
		ctx.waitTicks(3);
		Set<String> off = server.computeOnServer(s -> itemIds(s, "minecraft:chests/simple_dungeon", 300));
		c.note("simple_dungeon items with loot on: " + on + "; off: " + off);
		c.check("with loot on, a dungeon chest holds the banded loot (emeralds appear in 300 rolls)", on.contains("emerald"), on.toString());
		c.check("with loot off, a dungeon chest holds only its vanilla loot (no emeralds in 300 rolls)", !off.contains("emerald") && !off.isEmpty(), off.toString());
		c.check("with loot off, the vanilla dungeon items (saddle or name tag) come back", off.contains("saddle") || off.contains("name_tag"), off.toString());
		int emeralds = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			int total = 0;
			for (int i = 0; i < 5; i++) {
				Mob mob = husk(p, 30, 3);
				mob.hurtServer(level, level.damageSources().playerAttack(p), 100000f);
				List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(mob.blockPosition()).inflate(4));
				total += drops.stream().filter(d -> d.getItem().is(Items.EMERALD)).mapToInt(d -> d.getItem().getCount()).sum();
				drops.forEach(ItemEntity::discard);
			}
			return total;
		});
		c.check("with loot off, a Warlord drops no emeralds (vanilla drops only)", emeralds == 0, emeralds + " emeralds");
		T.restore(server, snapshot);
		ctx.waitTicks(3);
		Set<String> back = server.computeOnServer(s -> itemIds(s, "minecraft:chests/simple_dungeon", 300));
		c.check("with loot back on, dungeon chests hold the banded loot again", back.contains("emerald"), back.toString());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (Mob m : p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(30))) {
				m.discard();
			}
		});
	}

	private static Set<String> itemIds(MinecraftServer s, String table, int rolls) {
		ServerPlayer p = player(s);
		LootTable t = s.reloadableRegistries().getLootTable(key(table));
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < rolls; i++) {
			for (ItemStack st : t.getRandomItems(chestParams(p, p.position()), p.getRandom())) {
				ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
			}
		}
		return ids;
	}

	// ------------------------------------------------------------------ real structures

	private record Target(String name, String locate, boolean nether) {
	}

	private static final Pattern WHERE = Pattern.compile("\\[(-?\\d+), (~|-?\\d+), (-?\\d+)\\]");

	/** Structures to visit: this part, the surface ones. */
	static final List<Target> SURFACE = List.of(new Target("village", "#minecraft:village", false), new Target("desert temple", "minecraft:desert_pyramid", false),
		new Target("shipwreck", "#minecraft:shipwreck", false), new Target("buried treasure", "minecraft:buried_treasure", false),
		new Target("trail ruins", "minecraft:trail_ruins", false), new Target("mineshaft", "#minecraft:mineshaft", false));
	/** Underground and Nether ones. */
	static final List<Target> DEEP = List.of(new Target("stronghold", "minecraft:stronghold", false), new Target("ancient city", "minecraft:ancient_city", false),
		new Target("abandoned camp", "camp", false), new Target("bastion", "minecraft:bastion_remnant", true), new Target("nether fortress", "minecraft:fortress", true),
		new Target("trial chambers", "minecraft:trial_chambers", false));

	static void structuresSurface(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		structures(ctx, server, c, SURFACE);
	}

	static void structuresDeep(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		structures(ctx, server, c, DEEP);
	}

	private static void structures(ClientGameTestContext ctx, TestServerContext server, Checks c, List<Target> targets) {
		server.runCommand("gamemode creative @p");
		server.runCommand("clear @p");
		setLevel(server, 40);
		for (Target t : targets) {
			c.section("Structure: " + t.name());
			String dim = t.nether() ? "minecraft:the_nether" : "minecraft:overworld";
			if (t.nether()) {
				server.runCommand("execute in minecraft:the_nether run forceload add 0 0");
				server.waitFor(s -> s.getLevel(net.minecraft.world.level.Level.NETHER).getChunkSource().getChunkNow(0, 0) != null, 600);
				server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
				ctx.waitTicks(20);
			} else {
				server.runCommand("execute in minecraft:overworld run tp @p 0 200 0");
				ctx.waitTicks(10);
			}
			String locate = t.locate();
			if (locate.equals("camp")) {
				locate = server.computeOnServer(s -> s.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet().stream()
					.filter(id -> id.getPath().contains("camp")).map(Identifier::toString).findFirst().orElse(""));
				if (locate.isEmpty()) {
					c.note("no structure with 'camp' in its name exists in this game version; skipped");
					continue;
				}
			}
			Checks.Result found = c.run(server, "execute in " + dim + " run locate structure " + locate);
			Matcher m = WHERE.matcher(found.text());
			if (found.crashed() || found.refused() || !m.find()) {
				c.check(t.name() + " can be located near the start", false, found.text());
				continue;
			}
			int x = Integer.parseInt(m.group(1));
			int z = Integer.parseInt(m.group(3));
			c.note(t.name() + " located at " + x + ", " + z + " (" + locate + ")");
			int cx = x >> 4;
			int cz = z >> 4;
			int radius = 3;
			String loadCmd = "execute in " + dim + " run forceload add " + ((cx - radius) * 16) + " " + ((cz - radius) * 16) + " " + ((cx + radius) * 16 + 15) + " " + ((cz + radius) * 16 + 15);
			server.runCommand(loadCmd);
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey = t.nether() ? net.minecraft.world.level.Level.NETHER : net.minecraft.world.level.Level.OVERWORLD;
			server.waitFor(s -> {
				ServerLevel lv = s.getLevel(levelKey);
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (lv.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
							return false;
						}
					}
				}
				return true;
			}, 1200);
			ctx.waitTicks(40);
			List<String> found2 = server.computeOnServer(s -> {
				ServerLevel lv = s.getLevel(levelKey);
				ServerPlayer p = player(s);
				List<String> out = new ArrayList<>();
				int converted = 0;
				int unconverted = 0;
				int opened = 0;
				int filled = 0;
				int same = 0;
				Set<String> tables = new HashSet<>();
				StringBuilder unconvertedAt = new StringBuilder();
				List<Object> holders = new ArrayList<>();
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						LevelChunk chunk = lv.getChunkSource().getChunkNow(cx + dx, cz + dz);
						if (chunk == null) {
							continue;
						}
						for (BlockEntity be : chunk.getBlockEntities().values()) {
							if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
								holders.add(be);
							}
						}
					}
				}
				for (AbstractMinecartContainer cart : lv.getEntitiesOfClass(AbstractMinecartContainer.class, new AABB(cx * 16 - 60, -64, cz * 16 - 60, cx * 16 + 76, 320, cz * 16 + 76))) {
					holders.add(cart);
				}
				for (Object h : holders) {
					net.fabricmc.fabric.api.attachment.v1.AttachmentTarget target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) h;
					PersonalLoot.LootSource src = target.getAttached(PersonalLoot.SOURCE);
					boolean vanillaTable = h instanceof RandomizableContainerBlockEntity r ? r.getLootTable() != null : ((AbstractMinecartContainer) h).getContainerLootTable() != null;
					if (src != null) {
						converted++;
						tables.add(src.table().toString());
					} else if (vanillaTable) {
						unconverted++;
						String where = h instanceof BlockEntity b ? b.getBlockPos().toString() : ((AbstractMinecartContainer) h).blockPosition().toString();
						String which = h instanceof RandomizableContainerBlockEntity r2 ? String.valueOf(r2.getLootTable()) : "cart";
						unconvertedAt.append(where).append(' ').append(which).append("; ");
					}
					if (src != null && opened < 6) {
						AbstractContainerMenu m1 = PersonalLoot.open(h, 60, p.getInventory(), p);
						if (m1 instanceof ChestMenu c1) {
							opened++;
							List<String> a = describe(c1.getContainer());
							filled += a.isEmpty() ? 0 : 1;
							m1.removed(p);
							ChestMenu c2 = (ChestMenu) PersonalLoot.open(h, 61, p.getInventory(), p);
							same += describe(c2.getContainer()).equals(a) ? 1 : 0;
							c2.removed(p);
						}
					}
				}
				return List.of(String.valueOf(holders.size()), String.valueOf(converted), String.valueOf(unconverted), String.valueOf(opened), String.valueOf(filled),
					String.valueOf(same), tables.toString(), unconvertedAt.toString());
			});
			int total = Integer.parseInt(found2.get(0));
			c.note(t.name() + ": " + total + " loot-capable containers in the area; tables " + found2.get(6));
			c.check(t.name() + ": the area has chests, barrels or chest minecarts", total > 0, "none in 7 x 7 chunks around " + x + ", " + z);
			if (total > 0) {
				c.check(t.name() + ": every chest with a loot table was converted to personal loot", Integer.parseInt(found2.get(2)) == 0,
					found2.get(2) + " still hold a vanilla loot table, " + found2.get(1) + " converted; " + found2.get(7));
				int opened = Integer.parseInt(found2.get(3));
				c.check(t.name() + ": opened chests give the player a copy", opened > 0, "opened " + opened);
				c.check(t.name() + ": at least one opened copy is filled (" + found2.get(4) + " of " + opened + ")", Integer.parseInt(found2.get(4)) > 0);
				c.check(t.name() + ": reopening shows the same loot (" + found2.get(5) + " of " + opened + ")", Integer.parseInt(found2.get(5)) == opened);
			}
			server.runCommand("execute in " + dim + " run forceload remove all");
			ctx.waitTicks(5);
		}
		server.runCommand("execute in minecraft:overworld run tp @p 0 200 0");
		server.runCommand("effect give @p minecraft:slow_falling 30 0 true");
		ctx.waitTicks(40);
	}
}
