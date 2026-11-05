package dev.vestiges.client.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.UiTheme;
import dev.vestiges.client.ui.TabButton;
import dev.vestiges.net.VillagerPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The villager screen (first version; the UI chat polishes it): Overview, Trades, About, and Quests when this villager has one for you. Opens on
 * right-click and replaces vanilla trading. Opened from the library it is view-only: Overview and About only. Drawn from a JSON view the server sends.
 */
public final class VillagerScreen extends Screen {
	private static final int W = 340;
	private static final int H = 230;
	private static final int ROW = 22;

	private enum Tab {
		OVERVIEW("Overview"), TRADES("Trades"), ABOUT("About"), QUESTS("Quests");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private JsonObject view;
	private final Screen parent;
	private Tab tab = Tab.OVERVIEW;
	private int page;

	public VillagerScreen(JsonObject view, Screen parent) {
		super(Component.literal("Villager"));
		this.view = view;
		this.parent = parent;
	}

	/** The server sent a fresh view of the same villager (after a purchase): keep the tab and page. */
	public void update(JsonObject fresh) {
		this.view = fresh;
		rebuildWidgets();
	}

	/** Switches to a tab by name (used by the automatic checks to take screenshots of each tab). */
	public void showTab(String name) {
		for (Tab t : tabs()) {
			if (t.label.equalsIgnoreCase(name)) {
				tab = t;
				page = 0;
				rebuildWidgets();
			}
		}
	}

	public String villagerId() {
		return str(view, "id");
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private boolean viewOnly() {
		return view.has("viewOnly") && view.get("viewOnly").getAsBoolean();
	}

	private List<Tab> tabs() {
		List<Tab> list = new ArrayList<>();
		list.add(Tab.OVERVIEW);
		if (!viewOnly()) {
			list.add(Tab.TRADES);
		}
		list.add(Tab.ABOUT);
		if (!viewOnly() && view.has("quest")) {
			list.add(Tab.QUESTS);
		}
		return list;
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	private JsonArray trades() {
		return view.has("trades") ? view.getAsJsonArray("trades") : new JsonArray();
	}

	private int rowsPerPage() {
		return Math.max(1, (H - 108) / ROW);
	}

	@Override
	protected void init() {
		if (!tabs().contains(tab)) {
			tab = Tab.OVERVIEW;
		}
		int x = left() + 8;
		for (Tab t : tabs()) {
			int bw = TabButton.widthFor(font, t.label);
			addRenderableWidget(new TabButton(x, top() + 30, bw, 18, t.label, t == tab, () -> {
				tab = t;
				page = 0;
				rebuildWidgets();
			}));
			x += bw + 2;
		}
		if (tab == Tab.TRADES) {
			buildTrades();
		}
		if (tab == Tab.QUESTS && view.has("quest")) {
			JsonObject q = view.getAsJsonObject("quest");
			Button hear = Button.builder(Component.literal("Hear the offer"), b -> send("quest", str(q, "id"))).bounds(left() + 8, top() + H - 28, 110, 20).build();
			hear.active = !(q.has("full") && q.get("full").getAsBoolean());
			addRenderableWidget(hear);
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(left() + W - 58, top() + H - 28, 50, 20).build());
	}

	private void buildTrades() {
		JsonArray list = trades();
		int fit = rowsPerPage();
		page = Math.max(0, Math.min(page, Math.max(0, (list.size() - 1) / fit)));
		int y = top() + 76;
		for (int i = page * fit; i < Math.min(list.size(), (page + 1) * fit); i++) {
			JsonObject t = list.get(i).getAsJsonObject();
			boolean locked = t.get("locked").getAsBoolean();
			boolean soldOut = t.get("left").getAsInt() <= 0;
			boolean poor = t.get("price").getAsInt() > view.get("emeralds").getAsInt();
			int index = t.get("index").getAsInt();
			Button buy = Button.builder(Component.literal(soldOut ? "Sold out" : "Buy"), b -> send("buy", index + (minecraft != null && minecraft.options.keyShift.isDown() ? ":all" : ""))).bounds(left() + W - 64, y, 54, ROW - 3).build();
			buy.active = !locked && !soldOut && !poor;
			addRenderableWidget(buy);
			y += ROW;
		}
		if (list.size() > fit) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(left() + 8, top() + H - 28, 24, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(left() + 36, top() + H - 28, 24, 20).build());
		}
	}

	private void send(String op, String arg) {
		if (ClientPlayNetworking.canSend(VillagerPayloads.Act.TYPE)) {
			ClientPlayNetworking.send(new VillagerPayloads.Act(op, villagerId(), arg));
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = left();
		int y = top();
		g.fill(x, y, x + W, y + H, UiTheme.background());
		g.fill(x, y, x + W, y + 1, UiTheme.line());
		g.fill(x, y + H - 1, x + W, y + H, UiTheme.line());
		g.fill(x, y, x + 1, y + H, UiTheme.line());
		g.fill(x + W - 1, y, x + W, y + H, UiTheme.line());
		g.text(font, str(view, "name"), x + 8, y + 8, UiTheme.head(), UiTheme.shadow());
		String job = str(view, "job");
		g.text(font, job, x + W - 8 - font.width(job), y + 8, UiTheme.muted(), UiTheme.shadow());
		g.fill(x, y + 52, x + W, y + 53, UiTheme.line());
		int cy = y + 60;
		switch (tab) {
			case OVERVIEW -> renderOverview(g, x + 8, cy);
			case TRADES -> renderTrades(g, x + 8, cy);
			case ABOUT -> renderAbout(g, x + 8, cy);
			case QUESTS -> renderQuest(g, x + 8, cy);
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private int paragraph(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		for (FormattedCharSequence part : font.split(Component.literal(text), W - 24)) {
			g.text(font, part, x, y, color, UiTheme.shadow());
			y += 10;
		}
		return y;
	}

	private void renderOverview(GuiGraphicsExtractor g, int x, int y) {
		if (view.has("overview")) {
			for (JsonElement e : view.getAsJsonArray("overview")) {
				y = paragraph(g, e.getAsString(), x, y, UiTheme.flavour()) + 4;
			}
		}
		JsonObject about = view.has("about") ? view.getAsJsonObject("about") : new JsonObject();
		if (!str(about, "hook").isEmpty()) {
			y = paragraph(g, str(about, "hook"), x, y + 4, UiTheme.info());
		}
		if (viewOnly() && view.has("where")) {
			JsonObject w = view.getAsJsonObject("where");
			paragraph(g, "Last met on day " + w.get("day").getAsLong() + " at " + w.get("x").getAsInt() + ", " + w.get("y").getAsInt() + ", " + w.get("z").getAsInt()
				+ " (" + str(w, "dim").replace("minecraft:", "") + ").", x, y + 10, UiTheme.muted());
		} else if (!viewOnly() && !"trader".equals(str(view, "kind")) && view.has("quest")) {
			paragraph(g, "This villager has a quest for you: see the Quests tab.", x, y + 10, UiTheme.good());
		}
	}

	private void renderAbout(GuiGraphicsExtractor g, int x, int y) {
		JsonObject a = view.has("about") ? view.getAsJsonObject("about") : new JsonObject();
		if (str(a, "birthplace").isEmpty()) {
			paragraph(g, "Nobody knows where this trader comes from.", x, y, UiTheme.muted());
			return;
		}
		y = paragraph(g, "Born in " + str(a, "birthplace") + ", of " + str(a, "family") + ".", x, y, UiTheme.text()) + 4;
		y = paragraph(g, "Once " + str(a, "turning") + ".", x, y, UiTheme.text()) + 4;
		y = paragraph(g, "Now wants " + str(a, "wants") + ".", x, y, UiTheme.text()) + 6;
		if (a.has("traits")) {
			for (JsonElement e : a.getAsJsonArray("traits")) {
				y = paragraph(g, e.getAsString(), x, y, UiTheme.flavour());
			}
		}
		if (!str(a, "hook").isEmpty()) {
			paragraph(g, str(a, "hook"), x, y + 6, UiTheme.info());
		}
	}

	private void renderQuest(GuiGraphicsExtractor g, int x, int y) {
		JsonObject q = view.getAsJsonObject("quest");
		g.text(font, str(q, "name"), x, y, UiTheme.head(), UiTheme.shadow());
		y = paragraph(g, "From " + str(q, "giver"), x, y + 12, UiTheme.muted()) + 4;
		y = paragraph(g, str(q, "intro"), x, y, UiTheme.flavour());
		if (q.has("full") && q.get("full").getAsBoolean()) {
			paragraph(g, "You already have the most side quests you can keep.", x, y + 8, UiTheme.bad());
		}
	}

	private void renderTrades(GuiGraphicsExtractor g, int x, int y) {
		JsonArray list = trades();
		if (list.isEmpty()) {
			paragraph(g, "This villager has nothing to sell.", x, y, UiTheme.muted());
			return;
		}
		boolean trader = "trader".equals(str(view, "kind"));
		g.text(font, "Emeralds: " + view.get("emeralds").getAsInt() + (trader ? "" : "     Your level: " + view.get("level").getAsInt()
			+ "     Shift + Buy: as many as you can"), x, y, UiTheme.muted(), UiTheme.shadow());
		int fit = rowsPerPage();
		int ry = y + 16;
		for (int i = page * fit; i < Math.min(list.size(), (page + 1) * fit); i++) {
			JsonObject t = list.get(i).getAsJsonObject();
			boolean locked = t.get("locked").getAsBoolean();
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(t.get("item").getAsString())).orElse(Items.BARRIER);
			ItemStack stack = new ItemStack(item, Math.max(1, t.get("count").getAsInt()));
			g.item(stack, x, ry + 1);
			g.itemDecorations(font, stack, x, ry + 1);
			int color = locked ? UiTheme.faint() : UiTheme.text();
			g.text(font, stack.getHoverName().getString(), x + 22, ry + 3, color, UiTheme.shadow());
			String right;
			if (locked) {
				right = "Level " + t.get("minLevel").getAsInt();
			} else {
				int price = t.get("price").getAsInt();
				right = price + (price == 1 ? " emerald" : " emeralds") + "  (" + t.get("left").getAsInt() + " left)";
			}
			g.text(font, right, x + W - 16 - 64 - font.width(right) , ry + 3, locked ? UiTheme.faint() : UiTheme.good(), UiTheme.shadow());
			ry += ROW;
		}
	}
}
