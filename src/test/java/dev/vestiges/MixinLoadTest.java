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
		"net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition",
		"net.minecraft.world.entity.player.Inventory",
		"net.minecraft.world.inventory.InventoryMenu",
		"net.minecraft.server.network.ServerGamePacketListenerImpl",
		"net.minecraft.world.entity.player.PlayerEquipment",
		"net.minecraft.world.damagesource.CombatRules",
		"net.minecraft.world.entity.ai.attributes.Attributes",
		"net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity",
		"net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer");
	/** Client screens and HUD, checked when the tests run as a client. */
	private static final List<String> CLIENT_TARGETS = List.of(
		"net.minecraft.client.gui.screens.inventory.InventoryScreen",
		"net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen",
		"net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
		"net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
		"net.minecraft.client.gui.screens.inventory.EnchantmentScreen",
		"net.minecraft.client.gui.screens.inventory.AnvilScreen",
		"net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip",
		"net.minecraft.client.gui.Hud",
		"net.minecraft.client.MouseHandler",
		"net.minecraft.client.Minecraft",
		"net.minecraft.client.multiplayer.MultiPlayerGameMode",
		"net.minecraft.client.player.AbstractClientPlayer",
		"net.minecraft.client.renderer.entity.state.HumanoidRenderState",
		"net.minecraft.client.renderer.entity.player.AvatarRenderer",
		"net.minecraft.client.renderer.entity.layers.WingsLayer",
		"net.minecraft.client.renderer.entity.EntityRenderer",
		"net.minecraft.client.renderer.entity.MobRenderer",
		"net.minecraft.client.renderer.SubmitNodeCollection");

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

	@Test
	void everyClientMixinTargetLoads() {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
			System.out.println("MixinLoadTest: not a client environment, client targets skipped");
			return;
		}
		for (String name : CLIENT_TARGETS) {
			// Loading without initializing still applies the mixins (and fails if one no longer fits).
			assertDoesNotThrow(() -> Class.forName(name, false, MixinLoadTest.class.getClassLoader()), name);
		}
	}
}
