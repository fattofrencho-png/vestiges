package dev.vestiges.client.waystone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.UiTheme;
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
	private static final int TOP = 46;
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

	/** As many rows as fit above the page row, the owner's tools and the message line, so everything stays on screen. */
	private int rows() {
		int tools = data.get("fromMine").getAsBoolean() ? 58 : 0;
		return Math.max(1, Math.min(ROWS, (height - TOP - ROW_H - 2 - tools - 14) / ROW_H));
	}

	private static int dist(JsonObject o) {
		return o.has("distance") ? o.get("distance").getAsInt() : Integer.MAX_VALUE;
	}

	@Override
	protected void init() {
		int w = Math.min(320, width - 20);
		int x = (width - w) / 2;
		int y = TOP;
		boolean combat = data.get("inCombat").getAsBoolean();
		boolean free = data.get("free").getAsBoolean();
		int emeralds = data.get("emeralds").getAsInt();
		int from = data.get("from").getAsInt();
		int rows = rows();
		int pages = Math.max(1, (targets.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		int start = page * rows;
		for (int i = start; i < Math.min(targets.size(), start + rows); i++) {
			JsonObject t = targets.get(i);
			boolean same = t.get("sameDimension").getAsBoolean();
			String suffix;
			boolean ok;
			if (same) {
				int cost = t.get("cost").getAsInt();
				suffix = "  -  " + t.get("distance").getAsInt() + " blocks  -  " + (free ? "free" : cost + " emerald" + (cost == 1 ? "" : "s"));
				ok = !combat && (free || emeralds >= cost);
			} else {
				suffix = "  -  other dimension";
				ok = false;
			}
			if (!t.get("mine").getAsBoolean()) {
				suffix += "  (" + t.get("owner").getAsString() + ")";
			}
			// A long name gets shortened, never the price after it.
			String label = font.plainSubstrByWidth(t.get("name").getAsString(), w - 10 - font.width(suffix)) + suffix;
			int to = t.get("id").getAsInt();
			Button b = Button.builder(Component.literal(label), btn -> {
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
		int pageY = TOP + rows * ROW_H;
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(x, pageY, 20, 20).build();
			prev.active = page > 0;
			addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(x + w - 20, pageY, 20, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(next);
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x + w / 2 - 40, pageY, 80, 20).build());

		if (data.get("fromMine").getAsBoolean()) {
			int by = pageY + 34;
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
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiTheme.paintBackground(g, width, height);
		super.extractRenderState(g, mouseX, mouseY, delta);
		int w = Math.min(320, width - 20);
		int x = (width - w) / 2;
		centered(g, "Waystone: " + data.get("fromName").getAsString(), 12, UiTheme.info());
		String info = data.get("free").getAsBoolean() ? "Creative: trips are free" : "You carry " + data.get("emeralds").getAsInt() + " emeralds";
		centered(g, info, 26, UiTheme.muted());
		if (data.get("inCombat").getAsBoolean()) {
			centered(g, "You can't travel during a fight.", 36, UiTheme.error());
		}
		if (targets.isEmpty()) {
			centered(g, "No other waystone yet. Place another one, or ask a friend to share theirs.", 60, UiTheme.muted());
		}
		if (data.get("fromMine").getAsBoolean()) {
			g.text(font, "Rename this waystone / share it with a player:", x, TOP + rows() * ROW_H + 23, UiTheme.muted(), UiTheme.shadow());
		}
		String message = ClientMapNet.message();
		if (!message.isEmpty()) {
			centered(g, message, height - 11, ClientMapNet.lastError ? UiTheme.error() : UiTheme.good());
		}
	}

	private void centered(GuiGraphicsExtractor g, String text, int y, int color) {
		g.text(font, text, (width - font.width(text)) / 2, y, color, UiTheme.shadow());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
