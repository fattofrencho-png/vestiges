package dev.vestiges;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.data.VestigesData;
import dev.vestiges.net.MapPayloads;
import dev.vestiges.net.VestigesPayloads;
import dev.vestiges.server.MapNet;
import dev.vestiges.server.PinManager;
import dev.vestiges.waystone.WaystoneService;
import dev.vestiges.server.HubNet;
import dev.vestiges.server.PlayerProfiles;
import dev.vestiges.server.ReloadNotice;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Vestiges implements ModInitializer {
	public static final String MOD_ID = "vestiges";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModAttachments.init();
		dev.vestiges.loot.LootTypes.register();
		VestigesData.register();
		VestigesPayloads.register();
		HubNet.register();
		PlayerProfiles.register();
		ReloadNotice.register();
		MapPayloads.register();
		MapNet.register();
		PinManager.register();
		WaystoneService.register();
		dev.vestiges.level.LevelService.register();
		dev.vestiges.level.XpSources.register();
		dev.vestiges.grave.GraveService.register();
		dev.vestiges.net.TreePayloads.register();
		dev.vestiges.tree.TreeService.register();
		dev.vestiges.tree.TreeTicker.register();
		dev.vestiges.tree.TreeCombat.register();
		dev.vestiges.tree.TreeCounters.register();
		dev.vestiges.tree.TreeDrops.register();
		dev.vestiges.tree.UtilityPowers.register();
		dev.vestiges.gear.GearService.register();
		dev.vestiges.net.InventoryPayloads.register();
		dev.vestiges.inventory.InventoryService.register();
		dev.vestiges.mobs.MobService.register();
		dev.vestiges.mobs.CombatFeedback.register();
		dev.vestiges.loot.MobDrops.register();
		dev.vestiges.loot.PersonalLoot.register();
		dev.vestiges.net.CodexPayloads.register();
		dev.vestiges.codex.CodexService.register();
		dev.vestiges.net.LookAtPayloads.register();
		dev.vestiges.farming.FarmingService.register();
		dev.vestiges.farming.MealService.register();
		// Every enchantment from I to VI. On by default; a server can turn it off with /datapack disable.
		net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod ->
			net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(id("enchant_levels"), mod,
				net.minecraft.network.chat.Component.literal("Vestiges enchant levels (I-VI)"),
				net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.DEFAULT_ENABLED));
		ServerLifecycleEvents.SERVER_STARTING.register(server -> ConfigStore.loadServer());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VestigesCommands.register(dispatcher));
		LOGGER.info("Vestiges loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
