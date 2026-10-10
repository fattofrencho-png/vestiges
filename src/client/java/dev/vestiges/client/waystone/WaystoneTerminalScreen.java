package dev.vestiges.client.waystone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.map.ClientMapNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The waystone terminal, opened by right-clicking a waystone: every waystone you may travel to, with its distance and emerald cost. The owner
 * can also rename this waystone and share it with another player.
 */
public final class WaystoneTerminalScreen extends Screen {
	private static final int ROW_H = 22;
	private static final int ROWS = 6;
	private final JsonObject data;
	private final List<JsonObject> targets = new ArrayList<>();
	private int page;
	private EditBox renameBox;
	private EditBox shareBox;

	public WaystoneTerminalScreen(JsonObject data) {
		super(Component.literal("Waystone"));
		this.data = data;
		JsonArray arr = data.getAsJsonArray("waystones");
		for (JsonElement e : arr) {
			targets.add(e.getAsJsonObject());
		}
		targets.sort((a, b) -> Integer.compare(dist(a), dist(b)));
	}

	private static int dist(JsonObject o) {
		return o.has("distance") ? o.get("distance").getAsInt() : Integer.MAX_VALUE;
	}

	@Override
	protected void init() {
		int w = Math.min(320, width - 20);
		int x = (width - w) / 2;
		int y = 46;
		boolean combat = data.get("inCombat").getAsBoolean();
		boolean free = data.get("free").getAsBoolean();
		int emeralds = data.get("emeralds").getAsInt();
		int from = data.get("from").getAsInt();
		int start = page * ROWS;
		for (int i = start; i < Math.min(targets.size(), start + ROWS); i++) {
			JsonObject t = targets.get(i);
			boolean same = t.get("sameDimension").getAsBoolean();
			String label;
			boolean ok;
			if (same) {
				int cost = t.get("cost").getAsInt();
				label = t.get("name").getAsString() + "  -  " + t.get("distance").getAsInt() + " blocks  -  " + (free ? "free" : cost + " emerald" + (cost == 1 ? "" : "s"));
				ok = !combat && (free || emeralds >= cost);
			} else {
				label = t.get("name").getAsString() + "  -  other dimension";
				ok = false;
			}
			if (!t.get("mine").getAsBoolean()) {
				label += "  (" + t.get("owner").getAsString() + ")";
			}
			int to = t.get("id").getAsInt();
			Button b = Button.builder(Component.literal(font.plainSubstrByWidth(label, w - 10)), btn -> {
				JsonObject a = new JsonObject();
				a.addProperty("from", from);
				a.addProperty("to", to);
				ClientMapNet.send("waystone_travel", a);
				onClose();
			}).bounds(x, y, w, 20).build();
			b.active = ok;
			addRenderableWidget(b);
			y += ROW_H;
		}
		int pages = Math.max(1, (targets.size() + ROWS - 1) / ROWS);
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(x, 46 + ROWS * ROW_H, 20, 20).build();
			prev.active = page > 0;
			addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(x + w - 20, 46 + ROWS * ROW_H, 20, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(next);
		}

		int by = 46 + ROWS * ROW_H + 28;
		if (data.get("fromMine").getAsBoolean()) {
			renameBox = new EditBox(font, x, by, w - 84, 20, Component.literal("Name"));
			renameBox.setMaxLength(32);
			renameBox.setValue(data.get("fromName").getAsString());
			addRenderableWidget(renameBox);
			addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
				JsonObject a = new JsonObject();
				a.addProperty("id", from);
				a.addProperty("name", renameBox.getValue());
				ClientMapNet.send("waystone_rename", a);
			}).bounds(x + w - 80, by, 80, 20).build());
			by += 24;
			shareBox = new EditBox(font, x, by, w - 84, 20, Component.literal("Player name"));
			shareBox.setMaxLength(16);
			addRenderableWidget(shareBox);
			addRenderableWidget(Button.builder(Component.literal("Share"), b -> {
				JsonObject a = new JsonObject();
				a.addProperty("id", from);
				a.addProperty("player", shareBox.getValue());
				ClientMapNet.send("waystone_share", a);
			}).bounds(x + w - 80, by, 80, 20).build());
			by += 24;
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x + w / 2 - 40, by + 4, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		int w = Math.min(320, width - 20);
		int x = (width - w) / 2;
		g.centeredText(font, "Waystone: " + data.get("fromName").getAsString(), width / 2, 12, 0xFF55E6E6);
		String info = data.get("free").getAsBoolean() ? "Creative: trips are free" : "You carry " + data.get("emeralds").getAsInt() + " emeralds";
		g.centeredText(font, info, width / 2, 26, 0xFFAAAAAA);
		if (data.get("inCombat").getAsBoolean()) {
			g.centeredText(font, "You can't travel during a fight.", width / 2, 36, 0xFFFF5555);
		}
		if (targets.isEmpty()) {
			g.centeredText(font, "No other waystone yet. Place another one, or ask a friend to share theirs.", width / 2, 60, 0xFFAAAAAA);
		}
		if (data.get("fromMine").getAsBoolean()) {
			int by = 46 + ROWS * ROW_H + 18;
			g.text(font, "Rename this waystone / share it with a player:", x, by, 0xFFAAAAAA);
		}
		if (!ClientMapNet.lastMessage.isEmpty()) {
			g.centeredText(font, ClientMapNet.lastMessage, width / 2, height - 14, ClientMapNet.lastError ? 0xFFFF5555 : 0xFF55FF55);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
