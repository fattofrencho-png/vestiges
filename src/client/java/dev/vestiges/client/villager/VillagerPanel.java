package dev.vestiges.client.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.ClientData;
import dev.vestiges.client.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The hub's Villagers tab: the library of every villager you have talked to, newest first. A click opens the villager screen view-only. */
public final class VillagerPanel {
	private static final int ROW = 22;
	private static int page;

	private VillagerPanel() {
	}

	private static List<JsonObject> rows() {
		List<JsonObject> out = new ArrayList<>();
		JsonObject s = ClientData.snapshot();
		if (s.has("villagers") && s.get("villagers").isJsonArray()) {
			for (JsonElement e : s.getAsJsonArray("villagers")) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	/** Adds one button per villager on this page. {@code hub} is the screen to come back to. */
	public static void build(Consumer<AbstractWidget> add, Screen hub, int width, int height, int top, int bottom, Runnable rebuild) {
		List<JsonObject> rows = rows();
		int fit = Math.max(1, (bottom - top - 24) / ROW);
		page = Math.max(0, Math.min(page, Math.max(0, (rows.size() - 1) / fit)));
		int y = top + 16;
		for (JsonObject r : rows.subList(Math.min(rows.size(), page * fit), Math.min(rows.size(), (page + 1) * fit))) {
			String label = r.get("name").getAsString() + "  -  " + r.get("job").getAsString();
			add.accept(Button.builder(Component.literal(label), b -> Minecraft.getInstance().setScreenAndShow(new VillagerScreen(r, hub)))
				.bounds(6, y, Math.min(width - 12, 320), ROW - 2).build());
			y += ROW;
		}
		if (rows.size() > fit) {
			add.accept(Button.builder(Component.literal("<"), b -> {
				page = Math.max(0, page - 1);
				rebuild.run();
			}).bounds(6, bottom - 20, 30, 18).build());
			add.accept(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuild.run();
			}).bounds(40, bottom - 20, 30, 18).build());
		}
	}

	public static void render(GuiGraphicsExtractor g, Font font, int top) {
		JsonArray rows = ClientData.snapshot().has("villagers") ? ClientData.snapshot().getAsJsonArray("villagers") : new JsonArray();
		if (rows.isEmpty()) {
			g.text(font, "You have not met any villagers yet. Right-click one to meet them.", 6, top + 4, UiTheme.muted(), UiTheme.shadow());
		} else {
			g.text(font, rows.size() + " villagers met. Click one to read about them.", 6, top + 4, UiTheme.muted(), UiTheme.shadow());
		}
	}
}
