package dev.vestiges.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.level.LevelService;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.StatEngine;
import dev.vestiges.tree.TreeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Codex (M4, approved): blocks you gather, mobs you kill or breed, places you find and items you obtain fill in entries stage by stage, each
 * stage paying XP; discovered entries give bonus Craft points at 100 / 250 / 500. Player-placed blocks never count. Peaceful mobs, Unique
 * blocks and features (geodes, fossils, monster rooms, desert wells) are discovered by seeing them.
 */
public final class CodexService {
	public static final AttachmentType<CodexProgress> PROGRESS = AttachmentRegistry.create(Vestiges.id("codex"),
		b -> b.persistent(CodexProgress.CODEC).copyOnDeath());

	private CodexService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.CODEX);
	}

	public static CodexProgress get(ServerPlayer player) {
		return player.getAttachedOrElse(PROGRESS, CodexProgress.EMPTY);
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!(level instanceof ServerLevel sl)) {
				return;
			}
			boolean placed = PlacedBlocks.take(sl, pos);
			if (!placed && player instanceof ServerPlayer sp && counts(sp)) {
				onGathered(sp, state.getBlock());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && dead != killer && counts(killer)) {
				onMob(killer, dead.getType());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CodexService::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			backfillPlaces(handler.player);
			sendSnapshot(handler.player);
		}));
		ServerPlayNetworking.registerGlobalReceiver(dev.vestiges.net.CodexPayloads.Request.TYPE,
			(payload, ctx) -> ctx.server().execute(() -> sendSnapshot(ctx.player())));
	}

	private static boolean counts(ServerPlayer p) {
		return enabled() && !p.isSpectator() && !p.getAbilities().instabuild;
	}

	private static PlayerStats stats(ServerPlayer p) {
		return StatEngine.get(p);
	}

	// ---- what counts ---------------------------------------------------------------------------------------------------

	private static void onGathered(ServerPlayer p, Block block) {
		String id = CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(block));
		if (id != null) {
			addCount(p, id, 1);
		}
	}

	/** A crop harvested by right-click: counts like breaking it, unless a player planted it. */
	public static void harvested(ServerPlayer p, ServerLevel level, BlockPos pos, Block block) {
		if (counts(p) && !PlacedBlocks.isPlaced(level, pos)) {
			onGathered(p, block);
		}
	}

	/** A kill, or a breeding for animals (both count toward the Bestiary milestones). */
	public static void onMob(ServerPlayer p, EntityType<?> type) {
		if (!counts(p)) {
			return;
		}
		String id = CodexIndex.get().find(CodexEntry.MOBS, BuiltInRegistries.ENTITY_TYPE.getKey(type));
		if (id != null) {
			addCount(p, id, 1);
		}
	}

	/** A biome the player stands in (from the discovery tick): discovered, and each new chunk of it counts toward Mastered. */
	public static void onBiome(ServerPlayer p, Identifier biome, long chunk) {
		if (counts(p)) {
			addSpot(p, CodexIndex.get().find(CodexEntry.BIOMES, biome), chunk, CodexRules.BIOME_CHUNKS, 0);
		}
	}

	/** A structure the player stands in; each different one (by its start chunk) counts toward Mastered. */
	public static void onStructure(ServerPlayer p, Identifier structure, long startChunk) {
		if (counts(p)) {
			addSpot(p, CodexIndex.get().find(CodexEntry.STRUCTURES, structure), startChunk, CodexRules.STRUCTURES_FOUND, 0);
		}
	}

	private static void addCount(ServerPlayer p, String id, int amount) {
		CodexProgress prog = get(p);
		prog = prog.withCount(id, prog.count(id) + amount);
		save(p, settle(p, prog, id));
	}

	/** Adds a place spot unless one within {@code minDistance} blocks (0 = same spot) is already known. */
	private static void addSpot(ServerPlayer p, String id, long spot, int cap, int minDistance) {
		if (id == null) {
			return;
		}
		CodexProgress prog = get(p);
		List<Long> known = prog.spots(id);
		if (known.size() >= cap || known.contains(spot)) {
			return;
		}
		if (minDistance > 0) {
			BlockPos at = BlockPos.of(spot);
			for (long k : known) {
				if (BlockPos.of(k).distSqr(at) < (double) minDistance * minDistance) {
					return;
				}
			}
		}
		save(p, settle(p, prog.withSpot(id, spot), id));
	}

	/** Discovered by seeing it (peaceful mobs, Unique blocks). */
	private static void sight(ServerPlayer p, String id) {
		if (id == null) {
			return;
		}
		CodexProgress prog = get(p);
		if (prog.stage(id) > 0) {
			return;
		}
		CodexEntry e = CodexIndex.get().entry(id);
		if (e != null) {
			save(p, reach(p, prog, id, e, 1));
		}
	}

	private static void save(ServerPlayer p, CodexProgress prog) {
		if (prog != get(p)) {
			p.setAttached(PROGRESS, prog);
		}
	}

	/** Moves an entry to the stage its count reaches. */
	private static CodexProgress settle(ServerPlayer p, CodexProgress prog, String id) {
		CodexEntry e = CodexIndex.get().entry(id);
		if (e == null) {
			return prog;
		}
		int count = e.isPlace() ? prog.spots(id).size() : prog.count(id);
		return reach(p, prog, id, e, CodexRules.stageFor(e, count, stats(p).get("codex_gathering")));
	}

	/** Raises an entry to a stage, paying the XP of every stage passed, with one toast. */
	private static CodexProgress reach(ServerPlayer p, CodexProgress prog, String id, CodexEntry e, int target) {
		int old = prog.stage(id);
		target = Math.min(target, CodexRules.maxStage(e));
		if (target <= old) {
			return prog;
		}
		int before = prog.discovered();
		prog = prog.withStage(id, target);
		long xp = 0;
		for (int s = old + 1; s <= target; s++) {
			xp += CodexRules.xpFor(e, s);
		}
		long gained = 0;
		if (xp > 0) {
			gained = LevelService.addXp(p, Math.round(xp * (1 + stats(p).get("codex_xp") / 100.0)), "codex");
		}
		JsonObject o = new JsonObject();
		o.addProperty("entry", id);
		o.addProperty("name", e.name());
		o.addProperty("icon", e.icon());
		o.addProperty("label", CodexRules.label(e, target));
		o.addProperty("xp", gained);
		LevelService.notice(p, "codex_step", o);
		if (old == 0) {
			boolean lore = stats(p).has("lorekeeper");
			if (CodexRules.bonusPoints(prog.discovered(), lore) > CodexRules.bonusPoints(before, lore)) {
				p.setAttached(PROGRESS, prog);
				TreeService.sendState(p, "Codex bonus point: +1 Craft point");
				LevelService.notice(p, "codex_point", new JsonObject());
			}
		}
		return prog;
	}

	// ---- the per-second look: items held, things seen ----------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || !enabled()) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (counts(p)) {
				scanItems(p);
				look(p);
			}
		}
	}

	/** Items obtained: whatever the inventory (and the racks and belt) holds more of than last time. */
	private static void scanItems(ServerPlayer p) {
		CodexIndex index = CodexIndex.get();
		Map<String, Integer> now = new HashMap<>();
		tally(p.getInventory(), index, now);
		if (p instanceof ExtraHolder holder) {
			tally(holder.vestiges$slots(), index, now);
		}
		CodexProgress prog = get(p);
		if (now.equals(prog.held())) {
			return;
		}
		Map<String, Integer> held = prog.held();
		CodexProgress next = prog.withHeld(now);
		for (Map.Entry<String, Integer> e : now.entrySet()) {
			int gained = e.getValue() - held.getOrDefault(e.getKey(), 0);
			if (gained > 0) {
				next = settle(p, next.withCount(e.getKey(), next.count(e.getKey()) + gained), e.getKey());
			}
		}
		save(p, next);
	}

	private static void tally(Container c, CodexIndex index, Map<String, Integer> out) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) {
				String id = index.find(CodexEntry.ITEMS, BuiltInRegistries.ITEM.getKey(s.getItem()));
				if (id != null) {
					out.merge(id, s.getCount(), Integer::sum);
				}
			}
		}
	}

	/** What the player can see: peaceful mobs nearby, the block in front (Unique blocks and the features). */
	private static void look(ServerPlayer p) {
		CodexIndex index = CodexIndex.get();
		CodexProgress prog = get(p);
		for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12),
			m -> m.getType().getCategory() != MobCategory.MONSTER)) {
			String id = index.find(CodexEntry.MOBS, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
			if (id != null && prog.stage(id) == 0 && p.hasLineOfSight(mob)) {
				sight(p, id);
				prog = get(p);
			}
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1.0f).scale(10));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		ServerLevel level = p.level();
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		String blockEntry = index.find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
		CodexEntry be = blockEntry == null ? null : index.entry(blockEntry);
		if (be != null && CodexRules.unique(be)) {
			sight(p, blockEntry);
		}
		String feature = feature(level, pos, state);
		if (feature != null) {
			addSpot(p, index.detect(feature), pos.asLong(), CodexRules.STRUCTURES_FOUND, "desert_well".equals(feature) ? 16 : 32);
		}
	}

	/** Features with no structure id, spotted by their telltale block. */
	private static String feature(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.BUDDING_AMETHYST)) {
			return "geode";
		}
		if (state.is(Blocks.SPAWNER)) {
			return "monster_room";
		}
		if (state.is(Blocks.BONE_BLOCK) && level.dimension() == Level.OVERWORLD
			&& pos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()) - 2) {
			return "fossil";
		}
		if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource() && level.getBiome(pos).is(BiomeTags.HAS_DESERT_PYRAMID)) {
			int sandstone = 0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockState n = level.getBlockState(pos.relative(d));
				if (BuiltInRegistries.BLOCK.getKey(n.getBlock()).getPath().contains("sandstone")) {
					sandstone++;
				}
			}
			if (sandstone >= 3) {
				return "desert_well";
			}
		}
		return null;
	}

	/** Biomes and structures found before the Codex existed still count as discovered. */
	private static void backfillPlaces(ServerPlayer p) {
		if (!enabled()) {
			return;
		}
		CodexIndex index = CodexIndex.get();
		CodexProgress prog = get(p);
		for (String key : LevelService.get(p).firsts()) {
			String[] parts = key.split(":", 2);
			Identifier gameId = parts.length == 2 ? Identifier.tryParse(parts[1]) : null;
			if (gameId == null) {
				continue;
			}
			String id = "biome".equals(parts[0]) ? index.find(CodexEntry.BIOMES, gameId)
				: "structure".equals(parts[0]) ? index.find(CodexEntry.STRUCTURES, gameId) : null;
			if (id != null && prog.stage(id) == 0) {
				prog = prog.withStage(id, 1);
			}
		}
		save(p, prog);
	}

	// ---- what other systems ask -------------------------------------------------------------------------------------

	public static int discovered(ServerPlayer p) {
		return get(p).discovered();
	}

	/** Bonus Craft points from the Codex (0 while the Codex is off). */
	public static int bonusPoints(ServerPlayer p) {
		return enabled() ? CodexRules.bonusPoints(get(p).discovered(), stats(p).has("lorekeeper")) : 0;
	}

	/** Can cluster harvest take this block? (Discovered in the Codex; always yes while the Codex is off.) */
	public static boolean discoveredBlock(ServerPlayer p, Block block) {
		if (!enabled()) {
			return true;
		}
		String id = CodexIndex.get().find(CodexEntry.BLOCKS, BuiltInRegistries.BLOCK.getKey(block));
		return id == null || get(p).stage(id) > 0;
	}

	private static int mobStage(ServerPlayer p, Entity target) {
		String id = CodexIndex.get().find(CodexEntry.MOBS, BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
		return id == null ? 0 : get(p).stage(id);
	}

	/** Bestiary: extra damage against this kind of mob, as a multiplier. */
	public static double damageMultiplier(ServerPlayer p, LivingEntity target) {
		return enabled() ? 1 + CodexRules.bestiaryDamage(mobStage(p, target)) / 100.0 : 1;
	}

	/** Bestiary: added to this mob's emerald and ingot drop chances. */
	public static double dropBonus(ServerPlayer p, Entity target) {
		return enabled() ? CodexRules.bestiaryDrops(mobStage(p, target)) : 0;
	}

	// ---- admin and screen ------------------------------------------------------------------------------------------------

	/** Sets an entry to a stage (admin), with the matching count; no XP. */
	public static boolean setStage(ServerPlayer p, String id, int stage) {
		if (!setStageQuiet(p, id, stage)) {
			return false;
		}
		refresh(p);
		return true;
	}

	/** Tree points and the Codex screen after admin changes. */
	public static void refresh(ServerPlayer p) {
		TreeService.sendState(p, "");
		sendSnapshot(p);
	}

	/** Same as {@link #setStage} without telling the client (for changing many entries at once). */
	public static boolean setStageQuiet(ServerPlayer p, String id, int stage) {
		CodexEntry e = CodexIndex.get().entry(id);
		if (e == null) {
			return false;
		}
		int s = Math.max(0, Math.min(stage, CodexRules.maxStage(e)));
		int count = 0;
		for (int c : new int[] {1, 3, 10, 16, 32, 50, 64, 128, 250, 256}) {
			if (CodexRules.stageFor(e, c, 0) <= s) {
				count = c;
			}
		}
		CodexProgress prog = get(p).withStage(id, s);
		prog = e.isPlace() ? prog : prog.withCount(id, s == 0 ? 0 : count);
		p.setAttached(PROGRESS, prog);
		return true;
	}

	public static void reset(ServerPlayer p) {
		p.setAttached(PROGRESS, CodexProgress.EMPTY);
		TreeService.sendState(p, "");
		sendSnapshot(p);
	}

	/** Everything the Codex screen shows; hidden lines and names are never sent before they are earned. */
	public static void sendSnapshot(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, dev.vestiges.net.CodexPayloads.Data.TYPE)) {
			return;
		}
		CodexIndex index = CodexIndex.get();
		CodexProgress prog = get(p);
		double gathering = stats(p).get("codex_gathering");
		boolean lore = stats(p).has("lorekeeper");
		JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled());
		int discovered = prog.discovered();
		root.addProperty("discovered", discovered);
		root.addProperty("total", index.entries.size());
		root.addProperty("points", CodexRules.bonusPoints(discovered, lore));
		root.addProperty("nextPoint", CodexRules.nextPointAt(discovered, lore));
		JsonArray entries = new JsonArray();
		index.entries.forEach((id, e) -> {
			int stage = prog.stage(id);
			boolean known = stage > 0;
			boolean hostile = CodexEntry.MOBS.equals(e.section()) && hostile(e);
			JsonObject o = new JsonObject();
			o.addProperty("id", id);
			o.addProperty("section", e.section());
			o.addProperty("name", known ? e.name() : "???");
			o.addProperty("cls", known ? e.cls() : "");
			o.addProperty("icon", known ? e.icon() : "");
			o.addProperty("stage", stage);
			o.addProperty("max", CodexRules.maxStage(e));
			o.addProperty("count", e.isPlace() ? prog.spots(id).size() : prog.count(id));
			o.addProperty("need", CodexRules.nextNeed(e, stage, gathering));
			o.addProperty("label", CodexRules.label(e, stage));
			o.addProperty("line1", CodexRules.line1Visible(e, stage, hostile) ? e.line1() : "");
			o.addProperty("line2", CodexRules.line2Visible(e, stage) ? e.line2() : "");
			o.addProperty("later", e.later());
			JsonArray unlocks = new JsonArray();
			if (CodexEntry.BLOCKS.equals(e.section()) && !CodexRules.unique(e)) {
				unlocks.add(unlock("Cluster harvest can take it", stage >= 1));
				unlocks.add(unlock("Villagers can sell it (M6)", stage >= 2));
				unlocks.add(unlock("Workers can make it (M7)", stage >= 3));
			} else if (CodexEntry.MOBS.equals(e.section())) {
				unlocks.add(unlock("+" + CodexRules.bestiaryDamage(Math.max(2, Math.min(stage, 4))) + "% damage against it", stage >= 2));
				unlocks.add(unlock("+4% damage, +1% drop chance", stage >= 3));
				unlocks.add(unlock("+6% damage, +2% drop chance", stage >= 4));
			}
			o.add("unlocks", unlocks);
			entries.add(o);
		});
		root.add("entries", entries);
		JsonObject look = new JsonObject();
		index.lookAtIndex().forEach(look::addProperty);
		root.add("index", look);
		JsonArray known = new JsonArray();
		prog.stages().forEach((id, s) -> {
			if (s > 0) {
				known.add(id);
			}
		});
		root.add("known", known);
		ServerPlayNetworking.send(p, new dev.vestiges.net.CodexPayloads.Data(root.toString()));
	}

	private static JsonArray unlock(String text, boolean done) {
		JsonArray a = new JsonArray();
		a.add(text);
		a.add(done);
		return a;
	}

	private static boolean hostile(CodexEntry e) {
		if (e.members().isEmpty()) {
			return false;
		}
		Identifier id = Identifier.tryParse(e.members().get(0));
		return id != null && BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> t.getCategory() == MobCategory.MONSTER).orElse(false);
	}
}
