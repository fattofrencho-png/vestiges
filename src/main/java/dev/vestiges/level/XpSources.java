package dev.vestiges.level;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Where level XP comes from (agreed list): hostile kills by mob level with the level-gap rule, ores, first visits (biomes, structure types,
 * the Nether, the End). Feats, dungeons, dragons and the Codex add theirs in their milestones. Crops, breeding and smelting give none.
 */
public final class XpSources {
	private static final Map<Identifier, Integer> ORES = Map.ofEntries(
		ore("coal_ore", 2), ore("deepslate_coal_ore", 2), ore("copper_ore", 2), ore("deepslate_copper_ore", 2),
		ore("iron_ore", 4), ore("deepslate_iron_ore", 4), ore("redstone_ore", 4), ore("deepslate_redstone_ore", 4),
		ore("nether_quartz_ore", 4), ore("nether_gold_ore", 4), ore("lapis_ore", 6), ore("deepslate_lapis_ore", 6),
		ore("gold_ore", 8), ore("deepslate_gold_ore", 8), ore("diamond_ore", 25), ore("deepslate_diamond_ore", 25),
		ore("emerald_ore", 30), ore("deepslate_emerald_ore", 30), ore("ancient_debris", 60));
	private static final int BIOME_XP = 100;
	private static final int STRUCTURE_XP = 150;
	private static final int NETHER_XP = 500;
	private static final int END_XP = 1000;

	private XpSources() {
	}

	private static Map.Entry<Identifier, Integer> ore(String id, int xp) {
		return Map.entry(Identifier.withDefaultNamespace(id), xp);
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(XpSources::onKill);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
				onBreak(sp, (ServerLevel) level, state.getBlock());
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> discoverDimension(player, to));
		ServerTickEvents.END_SERVER_TICK.register(XpSources::tickDiscovery);
	}

	// ---- kills -----------------------------------------------------------------------------------------------------

	private static void onKill(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(dead instanceof Enemy) || dead instanceof ServerPlayer) {
			return;
		}
		ServerLevel level = (ServerLevel) dead.level();
		int mobLevel = mobLevelAt(level, dead.blockPosition());
		int playerLevel = LevelService.get(killer).level();
		long xp = Math.round(LevelCurve.killXp(mobLevel) * LevelCurve.gapMultiplier(playerLevel, mobLevel));
		LevelService.addXp(killer, xp, "kill");
	}

	/** The agreed mob-level formula at a spot (M3 stores it on each mob at spawn). */
	public static int mobLevelAt(ServerLevel level, BlockPos pos) {
		ServerConfig.Numbers n = ConfigStore.server().numbers;
		double sum = 0;
		int count = 0;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().distSqr(pos) <= 64 * 64) {
				sum += LevelService.get(p).level();
				count++;
			}
		}
		double avg = count == 0 ? 1 : sum / count;
		return MobLevels.compute(avg, layerAt(level, pos), n.mobLevelShare, n.cavesLevelBonus, n.deepslateLevelBonus,
			n.surfaceLevelCap, n.cavesLevelCap, n.deepslateLevelCap);
	}

	/** Surface, caves (underground, y 0 and up) or deepslate (below y 0). The Nether and the End count as deepslate. */
	public static MobLevels.Layer layerAt(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || pos.getY() < 0) {
			return MobLevels.Layer.DEEPSLATE;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return pos.getY() < surface - 3 ? MobLevels.Layer.CAVES : MobLevels.Layer.SURFACE;
	}

	// ---- ores ------------------------------------------------------------------------------------------------------

	private static void onBreak(ServerPlayer player, ServerLevel level, Block block) {
		Integer xp = ORES.get(BuiltInRegistries.BLOCK.getKey(block));
		if (xp == null) {
			return;
		}
		Holder<Enchantment> silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		if (EnchantmentHelper.getItemEnchantmentLevel(silk, player.getMainHandItem()) > 0) {
			return;
		}
		LevelService.addXp(player, xp, "ore");
	}

	// ---- discovery -------------------------------------------------------------------------------------------------

	private static void discoverDimension(ServerPlayer player, ServerLevel to) {
		if (to.dimension() == Level.NETHER) {
			first(player, "dim:minecraft:the_nether", NETHER_XP, "discovery");
		} else if (to.dimension() == Level.END) {
			first(player, "dim:minecraft:the_end", END_XP, "discovery");
		}
	}

	private static void tickDiscovery(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0 || !LevelService.enabled()) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			ServerLevel level = (ServerLevel) p.level();
			BlockPos pos = p.blockPosition();
			level.getBiome(pos).unwrapKey().ifPresent(key -> first(p, "biome:" + key.identifier(), BIOME_XP, "discovery"));
			Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> around = level.structureManager().getAllStructuresAt(pos);
			for (Structure s : around.keySet()) {
				StructureStart start = level.structureManager().getStructureAt(pos, s);
				if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
					Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(s);
					if (id != null) {
						first(p, "structure:" + id, STRUCTURE_XP, "discovery");
					}
				}
			}
		}
	}

	/** Gives the XP once per player per key. */
	private static void first(ServerPlayer player, String key, int xp, String reason) {
		PlayerLevel l = LevelService.get(player);
		if (l.firsts().contains(key)) {
			return;
		}
		player.setAttached(LevelService.LEVEL, l.withFirst(key));
		long gained = LevelService.addXp(player, xp, reason);
		if (gained > 0) {
			// A short toast in the middle of the screen ("New biome discovered  +100 XP").
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("key", key);
			o.addProperty("xp", gained);
			LevelService.notice(player, "discovery", o);
		}
	}
}
