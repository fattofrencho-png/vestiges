package dev.vestiges.client.quest;

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
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The hub's Quests tab: running, available and finished quests, your standing with each group, and the title you wear. First version; the UI chat polishes it. */
public final class QuestPanel {
	private static final int LIST_W = 170;
	private static final int ROW = 20;
	private static final String[] VIEWS = {"Active", "Available", "Done", "Standing"};
	private static String view = VIEWS[0];
	private static String selected = "";
	private static int page;

	private QuestPanel() {
	}

	private static JsonObject root() {
		JsonObject s = ClientData.snapshot();
		return s.has("quests") ? s.getAsJsonObject("quests") : new JsonObject();
	}

	private static List<JsonObject> rows() {
		List<JsonObject> out = new ArrayList<>();
		JsonObject root = root();
		String key = view.toLowerCase(java.util.Locale.ROOT);
		if (root.has(key) && root.get(key).isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray(key)) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	private static JsonObject current(List<JsonObject> rows) {
		for (JsonObject q : rows) {
			if (q.get("id").getAsString().equals(selected)) {
				return q;
			}
		}
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** Adds the buttons. {@code rebuild} rebuilds the hub's widgets after a click that changes what shows. */
	public static void build(Consumer<Button> add, int width, int height, int top, int bottom, Runnable rebuild) {
		int x = 6;
		int bw = 52;
		for (String v : VIEWS) {
			Button b = Button.builder(Component.literal(v), btn -> {
				view = v;
				page = 0;
				rebuild.run();
			}).bounds(x, top, bw, 18).build();
			b.active = !v.equals(view);
			add.accept(b);
			x += bw + 2;
		}
		if (view.equals("Standing")) {
			buildTitle(add, top, rebuild);
			return;
		}
		List<JsonObject> rows = rows();
		JsonObject q = current(rows);
		int y = top + 22;
		int fit = Math.max(1, (bottom - y - 22) / ROW);
		page = Math.max(0, Math.min(page, Math.max(0, (rows.size() - 1) / fit)));
		for (JsonObject r : rows.subList(Math.min(rows.size(), page * fit), Math.min(rows.size(), (page + 1) * fit))) {
			String id = r.get("id").getAsString();
			Button b = Button.builder(Component.literal((r.has("tracked") && r.get("tracked").getAsBoolean() ? "* " : "") + r.get("name").getAsString()), btn -> {
				selected = id;
				rebuild.run();
			}).bounds(6, y, LIST_W, ROW - 2).build();
			b.active = q == null || !id.equals(q.get("id").getAsString());
			add.accept(b);
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
		if (q != null) {
			buildActions(add, q, LIST_W + 16, bottom - 22);
		}
	}

	private static void buildActions(Consumer<Button> add, JsonObject q, int x, int y) {
		String id = q.get("id").getAsString();
		if (view.equals("Available")) {
			add.accept(Button.builder(Component.literal("Accept"), b -> ClientQuests.send("accept", id, "")).bounds(x, y, 70, 20).build());
			return;
		}
		if (!view.equals("Active")) {
			return;
		}
		add.accept(Button.builder(Component.literal(q.get("tracked").getAsBoolean() ? "Tracked" : "Track"), b -> ClientQuests.send("track", id, "")).bounds(x, y, 70, 20).build());
		add.accept(Button.builder(Component.literal("Drop"), b -> ClientQuests.send("drop", id, "")).bounds(x + 72, y, 50, 20).build());
		for (JsonElement e : q.getAsJsonArray("steps")) {
			JsonObject s = e.getAsJsonObject();
			if ("now".equals(s.get("state").getAsString())) {
				if ("bring".equals(s.get("type").getAsString())) {
					Button hand = Button.builder(Component.literal("Hand in"), b -> ClientQuests.send("hand_in", id, "")).bounds(x + 124, y, 70, 20).build();
					hand.active = s.get("have").getAsInt() >= s.get("need").getAsInt();
					add.accept(hand);
				}
				if (s.has("choices")) {
					JsonArray choices = s.getAsJsonArray("choices");
					add.accept(Button.builder(Component.literal("Decide"), b -> Minecraft.getInstance().setScreenAndShow(QuestBookScreen.choice(id, q.get("name").getAsString(),
						q.get("giver").getAsString(), s.get("text").getAsString(), choices))).bounds(x + 124, y, 70, 20).build());
				}
			}
		}
	}

	private static void buildTitle(Consumer<Button> add, int top, Runnable rebuild) {
		JsonObject root = root();
		if (!root.has("titles")) {
			return;
		}
		List<String> titles = new ArrayList<>();
		titles.add("");
		for (JsonElement e : root.getAsJsonArray("titles")) {
			titles.add(e.getAsString());
		}
		String worn = root.get("title").getAsString();
		add.accept(Button.builder(Component.literal("Title: " + (worn.isEmpty() ? "none" : worn)), b -> {
			int next = (titles.indexOf(worn) + 1) % titles.size();
			ClientQuests.send("title", titles.get(next), "");
		}).bounds(6, top + 22, LIST_W + 40, 20).build());
	}

	public static void render(GuiGraphicsExtractor g, Font font, int width, int top, int bottom) {
		if (view.equals("Standing")) {
			renderStanding(g, font, top + 48);
			return;
		}
		List<JsonObject> rows = rows();
		int x = LIST_W + 16;
		if (rows.isEmpty()) {
			g.text(font, switch (view) {
				case "Active" -> "No quests running. Look in Available.";
				case "Available" -> "Nothing to start right now.";
				default -> "No quests finished yet.";
			}, x, top + 4, UiTheme.muted(), UiTheme.shadow());
			return;
		}
		JsonObject q = current(rows);
		g.fill(LIST_W + 10, top, LIST_W + 11, bottom, UiTheme.line());
		int w = width - x - 6;
		int y = top;
		g.text(font, q.get("name").getAsString(), x, y, UiTheme.head(), UiTheme.shadow());
		y += 11;
		String giver = q.get("giver").getAsString();
		String kind = q.get("kind").getAsString();
		int comeback = q.get("comeback").getAsInt();
		g.text(font, label(kind) + (comeback > 1 ? " (time " + comeback + ")" : "") + (giver.isEmpty() ? "" : "  -  " + giver), x, y, UiTheme.muted(), UiTheme.shadow());
		y += 13;
		String intro = q.get("intro").getAsString();
		if (!intro.isEmpty()) {
			for (FormattedCharSequence line : font.split(Component.literal(intro), w)) {
				g.text(font, line, x, y, UiTheme.text(), UiTheme.shadow());
				y += 9;
			}
			y += 5;
		}
		for (JsonElement e : q.getAsJsonArray("steps")) {
			JsonObject s = e.getAsJsonObject();
			String state = s.get("state").getAsString();
			boolean done = view.equals("Done") || "done".equals(state);
			g.fill(x, y, x + 7, y + 7, 0xFF555555);
			g.fill(x + 1, y + 1, x + 6, y + 6, done ? 0xFF55FF55 : 0xFF1B1B1B);
			int need = s.get("need").getAsInt();
			int color = done ? 0xFF8FD18F : "now".equals(state) ? UiTheme.text() : UiTheme.faint();
			String count = need > 1 ? (done ? need : s.get("have").getAsInt()) + "/" + need : "";
			List<FormattedCharSequence> lines = font.split(Component.literal(s.get("text").getAsString()), w - 12 - font.width(count) - 4);
			g.text(font, count, x + w - font.width(count), y, color, UiTheme.shadow());
			for (FormattedCharSequence line : lines) {
				g.text(font, line, x + 11, y, color, UiTheme.shadow());
				y += 9;
			}
			y += 3;
		}
		y += 5;
		String reward = q.get("reward").getAsString();
		if (!reward.isEmpty()) {
			for (FormattedCharSequence line : font.split(Component.literal("Reward: " + reward), w)) {
				g.text(font, line, x, y, 0xFF7EFC20, UiTheme.shadow());
				y += 9;
			}
		}
	}

	private static String label(String kind) {
		return switch (kind) {
			case "guide" -> "Guide";
			case "side" -> "Side quest";
			case "note" -> "Found note";
			case "story" -> "Story";
			default -> kind;
		};
	}

	private static void renderStanding(GuiGraphicsExtractor g, Font font, int y) {
		JsonObject root = root();
		if (!root.has("standing")) {
			return;
		}
		for (java.util.Map.Entry<String, JsonElement> e : root.getAsJsonObject("standing").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			int v = o.get("value").getAsInt();
			g.text(font, o.get("name").getAsString(), 6, y, UiTheme.text(), UiTheme.shadow());
			g.text(font, (v > 0 ? "+" : "") + v + "  " + o.get("band").getAsString(), 130, y, UiTheme.muted(), UiTheme.shadow());
			y += 12;
		}
	}
}
