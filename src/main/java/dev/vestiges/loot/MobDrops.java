package dev.vestiges.loot;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.mobs.MobService;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Mob drops (M3b, approved): when a levelled monster's own loot table rolls, {@code vestiges:entities/monster_drops} rolls too with the same
 * context. That table holds the emeralds, the ingots by mob level and the elite drops, all only for player kills. Vanilla drops stay.
 */
public final class MobDrops {
	public static final ResourceKey<LootTable> TABLE = ResourceKey.create(Registries.LOOT_TABLE, Vestiges.id("entities/monster_drops"));

	private MobDrops() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register(MobDrops::modify);
	}

	private static void modify(Holder<?> table, LootContext ctx, List<ItemStack> drops) {
		if (!ConfigStore.server().enabled(VestigesSystem.LOOT)) {
			return;
		}
		Entity entity = ctx.getOptional(LootContextParams.THIS_ENTITY);
		if (!(entity instanceof Mob mob) || MobService.data(mob) == null || !ctx.hasParameter(LootContextParams.DAMAGE_SOURCE)) {
			return;
		}
		ResourceKey<?> key = table.unwrapKey().orElse(null);
		if (key == null || key.equals(TABLE) || !key.identifier().getPath().startsWith("entities/") || !key.equals(mob.getLootTable().orElse(null))) {
			return;
		}
		LootTable extra = ctx.getLevel().getServer().reloadableRegistries().getLootTable(TABLE);
		extra.getRandomItemsRaw(ctx, drops::add);
	}
}
