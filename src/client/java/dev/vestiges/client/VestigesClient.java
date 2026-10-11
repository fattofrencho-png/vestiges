package dev.vestiges.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.Vestiges;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.client.hud.HeartsHud;
import dev.vestiges.client.hud.LookAtHud;
import dev.vestiges.client.hud.XpHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import dev.vestiges.client.map.ClientMapNet;
import dev.vestiges.client.map.ClientWaypoints;
import dev.vestiges.client.map.MapScanner;
import dev.vestiges.client.map.MapStore;
import dev.vestiges.client.map.MinimapHud;
import dev.vestiges.client.map.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class VestigesClient implements ClientModInitializer {
	private static KeyMapping hubKey;
	private static KeyMapping mapKey;
	private static KeyMapping utilityKey;
	private static KeyMapping treeKey;
	private static KeyMapping codexKey;
	private static boolean deathRecorded;
	private static int saveTimer;

	@Override
	public void onInitializeClient() {
		ClientConfigStore.load();
		ClientData.register();
		ClientMapNet.register();
		ClientNotices.register();
		dev.vestiges.client.tree.ClientTree.register();
		dev.vestiges.client.hud.GearTooltips.register();
		dev.vestiges.client.inventory.ClientInventory.register();
		dev.vestiges.client.mobs.DamageNumbers.register();
		dev.vestiges.client.codex.ClientCodex.register();
		dev.vestiges.client.farming.ClientMeals.register();
		MapScanner.register();
		KeyMapping.Category category = KeyMapping.Category.register(Vestiges.id("main"));
		hubKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.hub", InputConstants.KEY_H, category));
		mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.map", InputConstants.KEY_M, category));
		utilityKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.utility", InputConstants.KEY_V, category));
		treeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.tree", InputConstants.UNKNOWN.getValue(), category));
		codexKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.codex", InputConstants.UNKNOWN.getValue(), category));
		dev.vestiges.client.inventory.ClientInventory.zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.zoom", InputConstants.KEY_Z, category));
		dev.vestiges.client.inventory.ClientInventory.toolKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.tool_slot", InputConstants.UNKNOWN.getValue(), category));
		dev.vestiges.client.inventory.ClientInventory.weaponKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.weapon_slot", InputConstants.UNKNOWN.getValue(), category));
		HudElementRegistry.addLast(Vestiges.id("minimap"), new MinimapHud());
		HudElementRegistry.addLast(Vestiges.id("look_at"), new LookAtHud());
		HudElementRegistry.addLast(Vestiges.id("xp"), new XpHud());
		HudElementRegistry.addLast(Vestiges.id("thick_hide"), new dev.vestiges.client.hud.ThickHideHud());
		HudElementRegistry.addLast(Vestiges.id("meal"), new dev.vestiges.client.farming.ClientMeals());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Vestiges.id("extra_hotbar"), new dev.vestiges.client.hud.ExtraHotbarHud());
		// Hearts as a percentage: our 10-heart bar replaces vanilla's and takes one row, so the armour bar sits right above it.
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, old -> new HeartsHud());
		try {
			HudStatusBarHeightRegistry.addLeft(VanillaHudElements.HEALTH_BAR, player -> 10);
		} catch (RuntimeException e) {
			Vestiges.LOGGER.warn("Vestiges: could not set the health bar height", e);
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			MapStore.open();
			ClientWaypoints.load();
			MapScanner.reset();
			dev.vestiges.client.mobs.ClientMobs.reset();
			dev.vestiges.client.codex.ClientCodex.reset();
			dev.vestiges.client.farming.ClientMeals.reset();
			dev.vestiges.client.hud.EntityInfo.reset();
			deathRecorded = false;
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientWaypoints.save();
			MapStore.close();
			MapStore.forget();
			MapScanner.reset();
		}));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (hubKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new HubScreen());
				}
			}
			while (mapKey.consumeClick()) {
				if (client.gui.screen() == null && client.level != null) {
					client.setScreenAndShow(new WorldMapScreen());
				}
			}
			while (utilityKey.consumeClick()) {
				if (client.player != null) {
					dev.vestiges.client.tree.ClientTree.send("utility", "", "", 0);
				}
			}
			while (codexKey.consumeClick()) {
				if (client.gui.screen() == null && client.player != null) {
					client.setScreenAndShow(new dev.vestiges.client.codex.CodexScreen());
				}
			}
			while (treeKey.consumeClick()) {
				if (client.gui.screen() == null && client.player != null) {
					client.setScreenAndShow(new dev.vestiges.client.tree.TreeScreen());
				}
			}
			dev.vestiges.client.inventory.ClientInventory.tickKeys(client);
			MapScanner.tick(client);
			dev.vestiges.client.mobs.DamageNumbers.tick(client);
			dev.vestiges.client.mobs.ClientMobs.tick(client);
			dev.vestiges.client.loot.LootSparkles.tick(client);
			recordDeath(client);
			if (++saveTimer >= 20 * 60) {
				saveTimer = 0;
				MapStore.saveAll();
			}
		});
	}

	/** Drops a death marker where the player died (once per death). */
	private static void recordDeath(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (client.player.isDeadOrDying()) {
			if (!deathRecorded) {
				deathRecorded = true;
				ClientWaypoints.addDeath(client.level.dimension().identifier().toString(),
					client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
			}
		} else {
			deathRecorded = false;
		}
	}
}
