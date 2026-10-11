package dev.vestiges.client.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Meal buffs on the client (M4, approved): a small food icon with the minutes left, right of the hunger bar (not in the potion list), and a
 * "Meal: ..." line on the tooltip of every food that gives one.
 */
public final class ClientMeals implements HudElement {
	private static final Map<String, String> DEFS = new HashMap<>();
	private static ItemStack current = ItemStack.EMPTY;
	private static String text = "";
	private static long endsAt;

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			String def = DEFS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			if (def != null) {
				lines.add(Component.literal("Meal: " + def).withStyle(ChatFormatting.DARK_GREEN));
			}
		});
	}

	public static void onDefs(JsonObject json) {
		DEFS.clear();
		for (JsonElement e : json.getAsJsonArray("meals")) {
			JsonObject o = e.getAsJsonObject();
			DEFS.put(o.get("item").getAsString(), o.get("text").getAsString() + " for " + o.get("minutes").getAsInt() + " min");
		}
	}

	public static void onMeal(JsonObject json) {
		String item = json.get("item").getAsString();
		Identifier id = item.isEmpty() ? null : Identifier.tryParse(item);
		current = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
		text = json.get("text").getAsString();
		endsAt = System.currentTimeMillis() + json.get("seconds").getAsLong() * 1000L;
	}

	public static void reset() {
		current = ItemStack.EMPTY;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (current.isEmpty() || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || mc.gui.screen() != null) {
			return;
		}
		long left = endsAt - System.currentTimeMillis();
		if (left <= 0) {
			current = ItemStack.EMPTY;
			return;
		}
		int x = g.guiWidth() / 2 + 93;
		int y = g.guiHeight() - 41;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.item(current, 0, 0);
		g.pose().popMatrix();
		long minutes = left / 60000;
		String t = minutes >= 1 ? minutes + "m" : (left / 1000) + "s";
		g.text(mc.font, t, x + 13, y + 3, 0xFFB8E0A0, true);
	}

	public static String text() {
		return text;
	}
}
