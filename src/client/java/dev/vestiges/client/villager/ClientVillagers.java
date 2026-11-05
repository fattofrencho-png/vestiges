package dev.vestiges.client.villager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vestiges.Vestiges;
import dev.vestiges.net.VillagerPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Receives the villager screen's views from the server: opens the screen, or refreshes it after a purchase. */
public final class ClientVillagers {
	private static volatile JsonObject last;

	private ClientVillagers() {
	}

	/** The latest view the server sent (used by the automatic checks). */
	public static JsonObject last() {
		return last;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VillagerPayloads.View.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
			try {
				JsonObject view = JsonParser.parseString(payload.json()).getAsJsonObject();
				last = view;
				Minecraft mc = ctx.client();
				if (mc.gui.screen() instanceof VillagerScreen open && open.villagerId().equals(view.get("id").getAsString())) {
					open.update(view);
				} else {
					mc.setScreenAndShow(new VillagerScreen(view, null));
				}
			} catch (RuntimeException e) {
				Vestiges.LOGGER.error("Vestiges: bad villager view", e);
			}
		}));
	}
}
