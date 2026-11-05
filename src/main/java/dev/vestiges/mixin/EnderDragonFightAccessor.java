package dev.vestiges.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The parts of the End fight that the dragons need: whether the dragon is dead, the respawn stage, the exit portal, the origin and the level. */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
	@Accessor("dragonKilled")
	boolean vestiges$killed();

	@Accessor("dragonKilled")
	void vestiges$setKilled(boolean killed);

	@Accessor("respawnStage")
	DragonRespawnStage vestiges$stage();

	@Accessor("exitPortalLocation")
	BlockPos vestiges$portal();

	@Accessor("origin")
	BlockPos vestiges$origin();

	@Accessor("level")
	ServerLevel vestiges$level();

	@Accessor("dragonEvent")
	ServerBossEvent vestiges$bar();
}
