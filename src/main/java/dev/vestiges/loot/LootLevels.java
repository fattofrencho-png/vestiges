package dev.vestiges.loot;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.MobLevels;
import dev.vestiges.level.XpSources;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Loot level (approved): 75% of the opener's level plus the layer bonus, capped per layer (Surface 20, Caves 40, Deepslate 50; the Nether and End
 * count as deepslate), the same rule as mob levels. A mob's drops use the mob's own level.
 */
public final class LootLevels {
	/** Admin tools force a level while they roll. */
	private static final ThreadLocal<Integer> FORCED = new ThreadLocal<>();

	private LootLevels() {
	}

	public static void force(Integer level) {
		if (level == null) {
			FORCED.remove();
		} else {
			FORCED.set(level);
		}
	}

	public static int forPlayer(ServerPlayer player, Vec3 at) {
		return at(player.level(), at, LevelService.get(player).level());
	}

	private static int at(ServerLevel level, Vec3 at, double playerLevel) {
		ServerConfig.Numbers n = ConfigStore.server().numbers;
		return MobLevels.compute(playerLevel, XpSources.layerAt(level, BlockPos.containing(at)), n.mobLevelShare, n.cavesLevelBonus,
			n.deepslateLevelBonus, n.surfaceLevelCap, n.cavesLevelCap, n.deepslateLevelCap);
	}

	/** The loot level for whatever is being rolled: a mob's level, the opening player's, or the nearest player's at the chest. */
	public static int of(LootContext ctx) {
		Integer forced = FORCED.get();
		if (forced != null) {
			return forced;
		}
		Entity entity = ctx.getOptional(LootContextParams.THIS_ENTITY);
		MobData mob = MobService.data(entity);
		if (mob != null) {
			return mob.level();
		}
		Vec3 origin = ctx.getOptional(LootContextParams.ORIGIN);
		if (entity instanceof ServerPlayer player) {
			return forPlayer(player, origin != null ? origin : player.position());
		}
		Vec3 at = origin != null ? origin : entity != null ? entity.position() : null;
		if (at == null) {
			return 1;
		}
		ServerLevel level = ctx.getLevel();
		net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(at.x, at.y, at.z, 64, false);
		return at(level, at, nearest instanceof ServerPlayer sp ? LevelService.get(sp).level() : 1);
	}
}
