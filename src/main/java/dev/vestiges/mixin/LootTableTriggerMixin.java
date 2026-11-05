package dev.vestiges.mixin;

import dev.vestiges.quest.QuestService;
import net.minecraft.advancements.triggers.LootTableTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's first open of a natural chest generates its loot and fires this trigger (vanilla and per-player chests alike): the loot_chest quest step. */
@Mixin(LootTableTrigger.class)
public abstract class LootTableTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void vestiges$questChest(ServerPlayer player, ResourceKey<LootTable> table, CallbackInfo ci) {
		if (table.identifier().getPath().startsWith("chests/")) {
			QuestService.event(player, "loot_chest", "", 1);
		}
	}
}
