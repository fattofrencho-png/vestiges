package dev.vestiges.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** What the crosshair is on: either a block (state + position) or an entity. {@code id} is the block or entity type id. */
public record LookAtTarget(Identifier id, BlockState state, BlockPos pos, Entity entity) {
	public boolean isBlock() {
		return state != null;
	}

	public boolean isEntity() {
		return entity != null;
	}
}
