package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loads every game class Vestiges changes, so a mixin that no longer fits the game (a renamed method, a moved call) fails here instead of
 * crashing the game when the class is first used.
 */
class MixinLoadTest {
	private static final List<String> TARGETS = List.of(
		"net.minecraft.world.entity.LivingEntity",
		"net.minecraft.world.entity.player.Player",
		"net.minecraft.server.level.ServerPlayer",
		"net.minecraft.world.food.FoodData",
		"net.minecraft.world.item.ItemStack",
		"net.minecraft.world.item.BowItem",
		"net.minecraft.world.item.CrossbowItem",
		"net.minecraft.world.item.ProjectileWeaponItem",
		"net.minecraft.world.entity.projectile.arrow.AbstractArrow",
		"net.minecraft.world.inventory.EnchantmentMenu",
		"net.minecraft.world.inventory.AnvilMenu",
		"net.minecraft.world.level.storage.loot.functions.ApplyBonusCount",
		"net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction",
		"net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition",
		"net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition");

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void everyMixinTargetLoads() {
		for (String name : TARGETS) {
			assertDoesNotThrow(() -> Class.forName(name, true, MixinLoadTest.class.getClassLoader()), name);
		}
	}
}
