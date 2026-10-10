package dev.vestiges.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.Vestiges;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.client.hud.LookAtHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;

public class VestigesClient implements ClientModInitializer {
	private static KeyMapping hubKey;

	@Override
	public void onInitializeClient() {
		ClientConfigStore.load();
		ClientData.register();
		KeyMapping.Category category = KeyMapping.Category.register(Vestiges.id("main"));
		hubKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vestiges.hub", InputConstants.KEY_H, category));
		HudElementRegistry.addLast(Vestiges.id("look_at"), new LookAtHud());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (hubKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new HubScreen());
				}
			}
		});
	}
}
