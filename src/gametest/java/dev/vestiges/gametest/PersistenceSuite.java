package dev.vestiges.gametest;

import dev.vestiges.codex.CodexIndex;
import dev.vestiges.codex.CodexService;
import dev.vestiges.feats.FeatService;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.quest.QuestService;
import dev.vestiges.settlement.SettlementData;
import dev.vestiges.settlement.SettlementService;
import dev.vestiges.tree.TreeService;
import dev.vestiges.waystone.WaystoneItems;
import dev.vestiges.waystone.WaystoneStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * S10: what must survive a restart. The player's data is written the way a save does (the same code path as the player's .dat file), wiped,
 * and read back; world data (settlements, waystones) cannot be read back inside this test tool (closing the world stalls it), so after a
 * forced save the world folder is searched for what was written.
 */
final class PersistenceSuite {
	private PersistenceSuite() {
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String codexEntry() {
		return CodexIndex.get().entries.keySet().iterator().next();
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode creative @p");
		String entry = codexEntry();

		// ---------------------------------------------------------------- give the player something of everything
		c.section("Set up");
		server.runOnServer(s -> QuestService.adminReset(player(s), "easy_harvest"));
		server.runCommand("vestiges tree reset @p");
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges feat revoke @p founder");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			LevelService.set(p, PlayerLevel.START.withXp(17, 100, 0));
			((ExtraHolder) p).vestiges$slots().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
			((ExtraHolder) p).vestiges$slots().setItem(ExtraSlots.TABLE, new ItemStack(Items.CRAFTING_TABLE));
			((ExtraHolder) p).vestiges$setBank(777L);
			QuestService.adminStart(p, "easy_harvest");
			for (int i = 0; i < 3; i++) {
				QuestService.event(p, "harvest", "minecraft:wheat", 1);
			}
		});
		c.expectOk(server, "vestiges tree counter @p kills 321");
		c.expectOk(server, "vestiges codex set @p \"" + entry + "\" 2");
		c.expectOk(server, "vestiges feat grant @p founder");
		ctx.waitTicks(5);

		// A settlement and a waystone (world data).
		BlockPos at = T.pad(ctx, server, 3300, 3300);
		int waystonesBefore = server.computeOnServer(s -> WaystoneStore.get(s).waystones().size());
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			SettlementData.World w = SettlementService.world(s);
			SettlementData.Settlement st = new SettlementData.Settlement("persistence-test", "Persisted Hollow", p.getUUID(), List.of(), "minecraft:overworld",
				at.offset(4, 0, 4).asLong(), SettlementData.ACTIVE, 0, SettlementData.Stats.START, SettlementData.Shared.EMPTY);
			SettlementService.save(s, w.put(st));
			p.setItemInHand(InteractionHand.MAIN_HAND, WaystoneItems.create(1));
			BlockPos floor = at.offset(-3, -1, 0);
			p.gameMode.useItemOn(p, p.level(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		ctx.waitTicks(10);
		int waystonesAfter = server.computeOnServer(s -> WaystoneStore.get(s).waystones().size());
		c.check("a waystone was placed for the test", waystonesAfter == waystonesBefore + 1, waystonesBefore + " -> " + waystonesAfter);

		// ---------------------------------------------------------------- player data: save, wipe, load
		c.section("Player data: written like a save, wiped, read back");
		CompoundTag saved = server.computeOnServer(s -> {
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, s.registryAccess());
			player(s).saveWithoutId(out);
			return out.buildResult();
		});
		c.check("the player's save holds the racks", saved.contains("vestiges_slots"));
		c.check("the player's save holds the Bank", saved.contains("vestiges_bank"));
		String keys = saved.toString();
		for (String id : List.of("vestiges:profile", "vestiges:level", "vestiges:tree", "vestiges:codex", "vestiges:quests", "vestiges:feats")) {
			c.check("the player's save holds " + id, keys.contains(id));
		}

		server.runCommand("vestiges tree reset @p");
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges feat revoke @p founder");
		server.runOnServer(s -> QuestService.adminReset(player(s), "easy_harvest"));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			LevelService.set(p, PlayerLevel.START);
			((ExtraHolder) p).vestiges$slots().setItem(0, ItemStack.EMPTY);
			((ExtraHolder) p).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY);
			((ExtraHolder) p).vestiges$setBank(0L);
		});
		ctx.waitTicks(3);
		String wiped = server.computeOnServer(s -> "level " + LevelService.get(player(s)).level() + ", bank " + ((ExtraHolder) player(s)).vestiges$bank()
			+ ", quest running " + QuestService.get(player(s)).active().containsKey("easy_harvest"));
		c.check("the wipe worked (level 1, no Bank, no quest)", wiped.equals("level 1, bank 0, quest running false"), wiped);

		server.runOnServer(s -> player(s).load(TagValueInput.create(ProblemReporter.DISCARDING, s.registryAccess(), saved)));
		ctx.waitTicks(5);
		c.check("level 17 is back", server.computeOnServer(s -> LevelService.get(player(s)).level() == 17),
			"level " + server.computeOnServer(s -> LevelService.get(player(s)).level()));
		c.check("the tree counters are back (kills 321)", server.computeOnServer(s -> TreeService.get(player(s)).counter("kills") == 321),
			"kills " + server.computeOnServer(s -> TreeService.get(player(s)).counter("kills")));
		c.check("the Codex stage is back", server.computeOnServer(s -> CodexService.get(player(s)).stage(entry) == 2),
			"stage " + server.computeOnServer(s -> CodexService.get(player(s)).stage(entry)));
		c.check("the feat is back", server.computeOnServer(s -> FeatService.get(player(s)).done().contains("founder")));
		c.check("the running quest is back with its count (3)", server.computeOnServer(s -> {
			var a = QuestService.get(player(s)).active().get("easy_harvest");
			return a != null && a.count() == 3;
		}));
		c.check("the tool rack item is back", server.computeOnServer(s -> ((ExtraHolder) player(s)).vestiges$slots().getItem(0).is(Items.DIAMOND_PICKAXE)));
		c.check("the crafting table slot is back", server.computeOnServer(s -> ((ExtraHolder) player(s)).vestiges$slots().hasTable()));
		c.check("the Bank is back (777)", server.computeOnServer(s -> ((ExtraHolder) player(s)).vestiges$bank() == 777L),
			"bank " + server.computeOnServer(s -> ((ExtraHolder) player(s)).vestiges$bank()));

		// ---------------------------------------------------------------- world data on disk
		c.section("World data: written to the world folder by a save");
		boolean saved2 = server.computeOnServer(s -> s.saveEverything(true, true, true));
		c.check("the server saved everything (the same call a quit makes)", saved2);
		ctx.waitTicks(60);
		Path root = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT));
		String all = readAll(root);
		c.note("world folder " + root + ": " + listing(root));
		c.check("the world folder holds the settlements", all.contains("vestiges:settlements") && all.contains("Persisted Hollow"));
		c.check("the world folder holds the waystones", all.contains("vestiges:waystones"));
		c.check("the player file holds the profile", all.contains("vestiges:profile"));
		c.check("the player file holds the racks and the Bank", all.contains("vestiges_slots") && all.contains("vestiges_bank"));

		// Clean up what this suite made.
		server.runOnServer(s -> QuestService.adminReset(player(s), "easy_harvest"));
		server.runCommand("vestiges tree reset @p");
		server.runCommand("vestiges codex reset @p");
		server.runCommand("vestiges feat revoke @p founder");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			LevelService.set(p, PlayerLevel.START);
			((ExtraHolder) p).vestiges$slots().setItem(0, ItemStack.EMPTY);
			((ExtraHolder) p).vestiges$slots().setItem(ExtraSlots.TABLE, ItemStack.EMPTY);
			((ExtraHolder) p).vestiges$setBank(0L);
			SettlementService.save(s, without(SettlementService.world(s)));
		});
		c.note("not covered: leaving and re-entering the world for real (the test tool stalls when the world closes), chunk data (placed blocks, a Gold Block's forced chunks), a dedicated server");
	}

	private static SettlementData.World without(SettlementData.World w) {
		return new SettlementData.World(w.settlements().stream().filter(s -> !s.id().equals("persistence-test")).toList(), w.markers(), w.nextId());
	}

	private static String listing(Path root) {
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile).filter(f -> !f.toString().contains("region") && !f.toString().contains("entities")).limit(30)
				.map(f -> root.relativize(f) + " (" + f.toFile().length() + ")").toList().toString();
		} catch (IOException e) {
			return e.toString();
		}
	}

	/** Every .dat file under the world folder (and its playerdata), unzipped and read as text, so ids and names can be searched for. */
	private static String readAll(Path root) {
		StringBuilder sb = new StringBuilder();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path f : files.filter(f -> f.toString().endsWith(".dat")).toList()) {
				byte[] raw = Files.readAllBytes(f);
				try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
					sb.append(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1)).append('\n');
				} catch (IOException notZipped) {
					sb.append(new String(raw, StandardCharsets.ISO_8859_1)).append('\n');
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return sb.toString();
	}
}
