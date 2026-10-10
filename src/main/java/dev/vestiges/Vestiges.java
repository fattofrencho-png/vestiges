package dev.vestiges;

import dev.vestiges.config.ConfigStore;
import dev.vestiges.data.VestigesData;
import dev.vestiges.net.VestigesPayloads;
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
		VestigesData.register();
		VestigesPayloads.register();
		HubNet.register();
		PlayerProfiles.register();
		ReloadNotice.register();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> ConfigStore.loadServer());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VestigesCommands.register(dispatcher));
		LOGGER.info("Vestiges loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
