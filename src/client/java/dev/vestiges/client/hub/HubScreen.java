package dev.vestiges.client.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.ClientData;
import dev.vestiges.client.UiTheme;
import dev.vestiges.client.ui.TabButton;
import dev.vestiges.client.features.ClientFeatures;
import dev.vestiges.client.hud.LookAtProvider;
import dev.vestiges.client.hud.LookAtProviders;
import dev.vestiges.config.ClientConfig;
import dev.vestiges.config.VestigesSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Vestiges hub (key H): one screen, one tab per system, in Hearthbook's style. M0 ships the shell: Feats and Settings work; the other tabs
 * say which milestone fills them.
 */
public final class HubScreen extends Screen {
	private static final int HEADER = 28;
	private static final int FOOTER = 28;
	private static final int GAP = 6;
	private static final String[] CHAINS = {"settler", "warden", "explorer", "gatekeeper", "dragonslayer"};

	enum Tab {
		CHARACTER("Character", "", VestigesSystem.CHARACTER),
		CODEX("Codex", "Blocks, Bestiary, Places and Items: everything you find fills it in.", VestigesSystem.CODEX),
		VILLAGERS("Villagers", "Your villager library and the villager screen arrive in M6.", VestigesSystem.VILLAGERS),
		SETTLEMENTS("Settlements", "Earn the Founder feat for your first Bell.", VestigesSystem.SETTLEMENTS),
		FEATS("Feats", "", VestigesSystem.FEATS),
		MAP("Map", "", VestigesSystem.MAP),
		SETTINGS("Settings", "", VestigesSystem.MAP);

		final String label;
		final String placeholder;
		final VestigesSystem system;

		Tab(String label, String placeholder, VestigesSystem system) {
			this.label = label;
			this.placeholder = placeholder;
			this.system = system;
		}

		boolean available() {
			return ClientFeatures.on(system);
		}
	}

	/** Settings categories, in order. World is only for operators. */
	private static final String[] CATEGORIES = {"Interface", "HUD", "Look-at", "Map", "Inventory", "Sharing", "World"};
	private static String settingsCategory = CATEGORIES[0];
	/** The page opened from a tab (trees, Codex, feats tree) that was still open when the hub was left: H goes back to it. */
	private static java.util.function.Supplier<Screen> openPage;
	private static double scroll;
	private static float mapZoom = 1.0f;
	private Tab tab = savedTab();
	private final List<Tab> tabs = new ArrayList<>();
	private int contentHeight;
	private double mapX;
	private double mapZ;
	private boolean mapDragging;
	private double dragX;
	private double dragY;
	private boolean markersOpen;
	private int settingsY;

	public HubScreen() {
		super(Component.literal("Vestiges"));
		openPage = null;
		// One fresh snapshot per opening, so /reload and config changes show up next time the hub opens.
		ClientData.request("hub");
		centerMap();
	}

	/** What the hub key opens: the page and tab you left. */
	public static Screen reopen() {
		return openPage != null ? openPage.get() : new HubScreen();
	}

	/** A new world starts from the hub itself. */
	public static void forgetPage() {
		openPage = null;
		scroll = 0;
	}

	private static Tab savedTab() {
		try {
			Tab t = Tab.valueOf(ClientConfigStore.get().hubTab);
			return t.available() ? t : Tab.MAP;
		} catch (IllegalArgumentException e) {
			return Tab.MAP;
		}
	}

	private void openPage(java.util.function.Supplier<Screen> page) {
		if (minecraft != null) {
			openPage = page;
			minecraft.setScreenAndShow(page.get());
		}
	}

	private void centerMap() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mapX = mc.player.getX();
			mapZ = mc.player.getZ();
		}
	}

	@Override
	protected void init() {
		buildHeader();
		if (tab == Tab.SETTINGS) {
			buildSettings();
		}
		if (tab == Tab.CHARACTER) {
			addRenderableWidget(Button.builder(Component.literal("Open the passive trees"), b -> openPage(dev.vestiges.client.tree.TreeScreen::new))
				.bounds(GAP, HEADER + 8 + 100, 180, 20).build());
		}
		if (tab == Tab.FEATS) {
			addRenderableWidget(Button.builder(Component.literal("Open the feats tree"), b -> openPage(dev.vestiges.client.settlement.FeatScreen::new))
				.bounds(width - 170, HEADER + 4, 160, 20).build());
		}
		if (tab == Tab.SETTLEMENTS) {
			buildSettlements();
		}
		if (tab == Tab.CODEX) {
			addRenderableWidget(Button.builder(Component.literal("Open the Codex"), b -> openPage(dev.vestiges.client.codex.CodexScreen::new))
				.bounds(GAP, HEADER + 34, 160, 20).build());
		}
		if (tab == Tab.MAP) {
			buildMap();
		}
	}

	/** The simple map's buttons, in the footer: the full map (M) has the layers, the waypoint list and the sharing. */
	private void buildMap() {
		int y = height - FOOTER + 5;
		int x = width - GAP - 130;
		addRenderableWidget(Button.builder(Component.literal("Open the full map (M)"), b -> {
			if (minecraft != null && minecraft.level != null) {
				minecraft.setScreenAndShow(new dev.vestiges.client.map.WorldMapScreen());
			}
		}).bounds(x, y, 130, 20).build());
		x -= 62;
		addRenderableWidget(Button.builder(Component.literal("Markers"), b -> {
			markersOpen = !markersOpen;
			rebuildWidgets();
		}).bounds(x, y, 60, 20).build());
		if (markersOpen) {
			dev.vestiges.client.map.MapMarkers.addButtons(this::addRenderableWidget, x, height - FOOTER - dev.vestiges.client.map.MapMarkers.height() - 2, 110);
		}
		x -= 82;
		addRenderableWidget(Button.builder(Component.literal("Center on me"), b -> centerMap()).bounds(x, y, 80, 20).build());
	}

	/** Called by ClientData when a fresh snapshot arrives. */
	public void onData() {
		rebuildWidgets();
	}

	private void buildHeader() {
		tabs.clear();
		for (Tab t : Tab.values()) {
			if (t.available()) {
				tabs.add(t);
			}
		}
		if (!tab.available()) {
			tab = Tab.MAP;
		}
		int x = 54;
		for (Tab t : tabs) {
			int bw = TabButton.widthFor(font, t.label);
			addRenderableWidget(new TabButton(x, 4, bw, 20, t.label, t == tab, () -> {
				tab = t;
				ClientConfigStore.get().hubTab = t.name();
				ClientConfigStore.save();
				scroll = 0;
				rebuildWidgets();
			}));
			x += bw + 2;
		}
	}

	private void buildSettings() {
		int y = HEADER + 8;
		for (String c : CATEGORIES) {
			if (c.equals("World") && !ClientFeatures.operator()) {
				continue;
			}
			addRenderableWidget(new TabButton(GAP, y, 80, 18, c, c.equals(settingsCategory), () -> {
				settingsCategory = c;
				scroll = 0;
				rebuildWidgets();
			}));
			y += 20;
		}
		if (settingsCategory.equals("World") && !ClientFeatures.operator()) {
			settingsCategory = CATEGORIES[0];
		}
		ClientConfig c = ClientConfigStore.get();
		int x = GAP + 88;
		int w = Math.min(240, width - x - GAP);
		settingsY = HEADER + 22 - (int) scroll;
		switch (settingsCategory) {
			case "Interface" -> {
				row(cycle(x, settingsY, w, "Theme", new String[] {"dark", "light"}, c.theme, v -> {
					c.theme = v;
					rebuildWidgets();
				}));
			}
			case "HUD" -> {
				row(toggle(x, settingsY, w, "Mob health bars", c.healthBars, on -> c.healthBars = on));
				row(toggle(x, settingsY, w, "Damage numbers", c.damageNumbers, on -> c.damageNumbers = on));
			}
			case "Look-at" -> {
				row(toggle(x, settingsY, w, "Look-at HUD", c.lookAtEnabled, on -> c.lookAtEnabled = on));
				for (LookAtProvider p : LookAtProviders.all()) {
					row(toggle(x + 10, settingsY, w - 10, p.label(), c.lineOn(p.id()), on -> c.setLine(p.id(), on)));
				}
				row(toggle(x + 10, settingsY, w - 10, "\"New\" tag", c.lineOn(LookAtProviders.NEW_TAG), on -> c.setLine(LookAtProviders.NEW_TAG, on)));
			}
			case "Map" -> {
				row(toggle(x, settingsY, w, "Minimap", c.minimapEnabled, on -> c.minimapEnabled = on));
				row(cycle(x + 10, settingsY, w - 10, "Size", new String[] {"96", "128", "160", "192"}, String.valueOf(c.minimapSize), v -> c.minimapSize = Integer.parseInt(v)));
				row(cycle(x + 10, settingsY, w - 10, "Zoom", new String[] {"1", "2", "3", "4"}, String.valueOf(c.minimapZoom), v -> c.minimapZoom = Integer.parseInt(v)));
				row(cycle(x + 10, settingsY, w - 10, "Corner", new String[] {"top_left", "top_right", "bottom_left", "bottom_right"}, c.minimapCorner, v -> c.minimapCorner = v));
			}
			case "Inventory" -> row(toggle(x, settingsY, w, "Auto-tool", c.autoTool, on -> c.autoTool = on));
			case "Sharing" -> row(cycle(x, settingsY, w, "Shared waypoints", new String[] {"ask", "off"}, c.shareMode, v -> c.shareMode = v));
			case "World" -> {
				row(Button.builder(Component.literal("Change features..."), b -> {
					if (minecraft != null) {
						minecraft.setScreenAndShow(new dev.vestiges.client.features.FeaturesScreen(this, false));
					}
				}).bounds(x, settingsY, w, 20).build());
				String[] sleeps = {"100", "50", "1"};
				row(cycle(x, settingsY, w, "Sleep percentage", sleeps, String.valueOf(ClientFeatures.sleepPercent()), v -> ClientFeatures.send("sleep", v)));
			}
			default -> {
			}
		}
		contentHeight = settingsY + (int) scroll - HEADER - 10;
	}

	/** Adds one settings row and moves down to the next. */
	private void row(Button b) {
		addSetting(b);
		settingsY += 22;
	}

	/** Settings scroll: a button only shows while it fits between the header and the footer. */
	private void addSetting(Button b) {
		if (b.getY() >= HEADER + 22 && b.getY() + 20 <= height - FOOTER) {
			addRenderableWidget(b);
		}
	}

	/** A button that steps through a few values each click. */
	private Button cycle(int x, int y, int w, String label, String[] values, String current, java.util.function.Consumer<String> setter) {
		int[] index = {Math.max(0, java.util.Arrays.asList(values).indexOf(current))};
		return Button.builder(cycleText(label, values[index[0]]), btn -> {
			index[0] = (index[0] + 1) % values.length;
			setter.accept(values[index[0]]);
			ClientConfigStore.save();
			btn.setMessage(cycleText(label, values[index[0]]));
		}).bounds(x, y, w, 20).build();
	}

	private static Component cycleText(String label, String value) {
		return Component.literal(label + ": " + value.replace('_', ' '));
	}

	private Button toggle(int x, int y, int w, String label, boolean on, java.util.function.Consumer<Boolean> setter) {
		return Button.builder(toggleText(label, on), btn -> {
			boolean now = !btn.getMessage().getString().endsWith("On");
			setter.accept(now);
			ClientConfigStore.save();
			btn.setMessage(toggleText(label, now));
		}).bounds(x, y, w, 20).build();
	}

	private static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "On" : "Off"));
	}

	// ---- rendering -------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (tab == Tab.MAP) {
			renderMap(g, mouseX, mouseY);
		} else if (UiTheme.light()) {
			g.fill(0, 0, width, height, UiTheme.background());
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, "Vestiges", GAP, 10, UiTheme.head(), UiTheme.shadow());
		g.fill(0, HEADER, width, HEADER + 1, UiTheme.line());
		g.fill(0, height - FOOTER, width, height - FOOTER + 1, UiTheme.line());
		JsonObject s = ClientData.snapshot();
		String version = s.has("version") ? s.get("version").getAsString() : "";
		g.text(font, ClientData.loaded() ? "Vestiges " + version : "Waiting for the server...", GAP, height - FOOTER + 10, UiTheme.faint(), UiTheme.shadow());

		int top = HEADER + 8;
		switch (tab) {
			case FEATS -> renderFeats(g, top);
			case SETTLEMENTS -> renderSettlements(g, top, mouseX, mouseY);
			case SETTINGS -> renderSettings(g, top);
			case CHARACTER -> renderCharacter(g, top, s);
			case MAP -> {
			}
			default -> renderPlaceholder(g, top);
		}
	}

	private void renderCharacter(GuiGraphicsExtractor g, int top, JsonObject s) {
		String name = s.has("profile") ? s.getAsJsonObject("profile").get("name").getAsString() : "";
		g.text(font, name, GAP, top, UiTheme.text(), UiTheme.shadow());
		if (!s.has("level")) {
			g.text(font, "Loading...", GAP, top + 14, UiTheme.muted(), UiTheme.shadow());
			return;
		}
		JsonObject l = s.getAsJsonObject("level");
		int level = l.get("level").getAsInt();
		int paragon = l.get("paragon").getAsInt();
		long xp = l.get("xp").getAsLong();
		long need = l.get("need").getAsLong();
		g.text(font, "Level " + level + (paragon > 0 ? "  \u00B7  Paragon " + paragon : ""), GAP, top + 16, UiTheme.head(), UiTheme.shadow());
		int bw = Math.min(260, width - GAP * 2);
		g.fill(GAP, top + 30, GAP + bw, top + 36, 0xFF2A2F33);
		g.fill(GAP, top + 30, GAP + (int) (bw * Math.min(1.0, xp / (double) Math.max(1, need))), top + 36, 0xFF80FF20);
		g.text(font, xp + " / " + need + " XP", GAP, top + 40, UiTheme.muted(), UiTheme.shadow());
		g.text(font, "From your level: +" + (int) l.get("bonusHealth").getAsDouble() + " max health, +" + l.get("damagePct").getAsDouble()
			+ "% damage", GAP, top + 56, UiTheme.text(), UiTheme.shadow());
		g.text(font, "Discoveries rewarded: " + l.get("discoveries").getAsInt(), GAP, top + 68, UiTheme.muted(), UiTheme.shadow());
		g.text(font, "Spend your points on the Fight and Craft wheels, then ascend (from level 20).", GAP, top + 86, UiTheme.faint(), UiTheme.shadow());
	}

	private static final int SETTLEMENT_H = 92;
	/** Below this width (large GUI sizes) the settlement cards have no room for tools beside the text. */
	private static final int NARROW = 640;

	private List<JsonObject> settlements() {
		List<JsonObject> out = new ArrayList<>();
		JsonObject s = ClientData.snapshot();
		if (s.has("settlements") && s.getAsJsonObject("settlements").has("settlements")) {
			for (JsonElement e : s.getAsJsonObject("settlements").getAsJsonArray("settlements")) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	private void settlementAction(String op, String id, String arg) {
		if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.vestiges.net.SettlementPayloads.Action.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.vestiges.net.SettlementPayloads.Action(op, id, arg));
		}
	}

	/** Owner tools per settlement: name it (once), add a member by name, remove members. */
	private void buildSettlements() {
		int y = HEADER + 8 + 30;
		for (JsonObject s : settlements()) {
			if (s.get("mine").getAsBoolean()) {
				String id = s.get("id").getAsString();
				boolean narrow = width < NARROW;
				int x = narrow ? GAP : width - 230;
				int toolsY = narrow ? y + 70 : y;
				if ("Unnamed settlement".equals(s.get("name").getAsString()) && "active".equals(s.get("state").getAsString())) {
					addRenderableWidget(Button.builder(Component.literal("Name it"), b -> minecraft.setScreenAndShow(
						new dev.vestiges.client.settlement.NameScreen("settlement", id, "Name your settlement"))).bounds(x, toolsY, narrow ? 50 : 70, 18).build());
				}
				net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(font, narrow ? x + 54 : x, narrow ? toolsY : toolsY + 22, narrow ? 110 : 140, 18,
					Component.literal("Player name"));
				box.setMaxLength(32);
				box.setHint(Component.literal("Player name").withStyle(ChatFormatting.DARK_GRAY));
				addRenderableWidget(box);
				addRenderableWidget(Button.builder(Component.literal("Add member"), b -> settlementAction("add_member", id, box.getValue()))
					.bounds(narrow ? x + 168 : x + 144, narrow ? toolsY - 1 : toolsY + 21, 80, 20).build());
				int my = narrow ? toolsY : y + 46;
				int mx = narrow ? x + 252 : x;
				for (JsonElement m : s.getAsJsonArray("members")) {
					JsonObject mo = m.getAsJsonObject();
					String mid = mo.get("id").getAsString();
					addRenderableWidget(Button.builder(Component.literal("Remove " + mo.get("name").getAsString()),
						b -> settlementAction("remove_member", id, mid)).bounds(mx, my, narrow ? 110 : 140, 16).build());
					if (narrow) {
						mx += 112;
						if (mx + 110 > width) {
							break;
						}
						continue;
					}
					my += 18;
					if (my > y + SETTLEMENT_H - 10) {
						break;
					}
				}
			}
			y += SETTLEMENT_H;
		}
	}

	/** What each renown score counts and how to raise it (the "?" tooltips). */
	private static final String[][] SCORE_HELP = {
		{"Size", "Blocks placed by players inside the settlement.", "1 point per 100 blocks.",
			"Each kind of block counts fully up to 1,000 blocks,", "then 1 point per 1,000 more.", "Natural blocks and blocks you broke don't count."},
		{"Variety", "Kinds of blocks placed inside the settlement.", "5 points per kind with at least 16 placed.",
			"Build with many different blocks to raise it."},
		{"Homes", "Beds placed inside the settlement.", "3 points per bed.",
			"Later, every villager living here needs its own bed."},
		{"People", "Villagers living in the settlement.", "5 points per villager, +5 for each worker rank above Novice.",
			"Villagers join when you recruit them:", "this comes with the villagers update (M6)."},
		{"Deeds", "Points kept forever from what you do.", "Raid won here: 30 per Bad Omen level.",
			"Coming later: bounties +10, caravan wanted lists +20,", "10 trades +1, a Hero living here +50, a Legend +100."},
	};

	/** "Size 12 ?  ·  Variety 30 ?  ·  ..." with a tooltip on each score. */
	private void renderScores(GuiGraphicsExtractor g, JsonObject s, int x, int y, int mouseX, int mouseY) {
		for (int i = 0; i < SCORE_HELP.length; i++) {
			String[] help = SCORE_HELP[i];
			String label = help[0] + " " + s.get(help[0].toLowerCase(java.util.Locale.ROOT)).getAsInt();
			g.text(font, label, x, y, UiTheme.muted(), UiTheme.shadow());
			int qx = x + font.width(label) + 3;
			g.text(font, "?", qx, y, 0xFF6FD3E0, UiTheme.shadow());
			int end = qx + font.width("?");
			if (mouseX >= x && mouseX < end + 2 && mouseY >= y - 2 && mouseY < y + 10) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal(help[0]).withStyle(ChatFormatting.GOLD));
				for (int l = 1; l < help.length; l++) {
					lines.add(Component.literal(help[l]).withStyle(l == 1 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
				}
				g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
			}
			x = end;
			if (i < SCORE_HELP.length - 1) {
				g.text(font, "  \u00B7  ", x, y, 0xFF666666, UiTheme.shadow());
				x += font.width("  \u00B7  ");
			}
		}
	}

	private void renderSettlements(GuiGraphicsExtractor g, int top, int mouseX, int mouseY) {
		JsonObject root = ClientData.snapshot().has("settlements") ? ClientData.snapshot().getAsJsonObject("settlements") : new JsonObject();
		if (root.has("enabled") && !root.get("enabled").getAsBoolean()) {
			g.text(font, "Settlements are switched off on this server.", GAP, top, UiTheme.muted(), UiTheme.shadow());
			return;
		}
		if (root.has("markers")) {
			JsonObject m = root.getAsJsonObject("markers");
			g.text(font, "Bells " + m.get("bell").getAsInt() + "/3  \u00B7  Banners " + m.get("banner").getAsInt() + "/20  \u00B7  Ward Lanterns "
				+ m.get("ward").getAsInt() + "/10  \u00B7  Gold Blocks " + m.get("gold_block").getAsInt() + "/3", GAP, top, UiTheme.muted(), UiTheme.shadow());
		}
		List<JsonObject> list = settlements();
		if (list.isEmpty()) {
			g.text(font, "No settlement yet. The Founder feat gives your first Bell (level 10, 5 beds in one area, meet a villager).", GAP, top + 16, UiTheme.text(), UiTheme.shadow());
			return;
		}
		int y = top + 30;
		for (JsonObject s : list) {
			boolean mine = s.get("mine").getAsBoolean();
			g.text(font, s.get("name").getAsString() + (mine ? "" : "  (" + s.get("owner").getAsString() + "'s)"), GAP, y, UiTheme.head(), UiTheme.shadow());
			String state = s.get("state").getAsString();
			String line = switch (state) {
				case "setup" -> "Setting up: " + s.get("setupLeft").getAsInt() / 1200 + " min left (stay within 128 blocks, no fighting)";
				case "inactive" -> "Inactive: place its Bell again";
				default -> s.get("levelName").getAsString() + "  \u00B7  " + s.get("points").getAsInt() + " renown"
					+ (s.get("next").getAsInt() > 0 ? " (next level at " + s.get("next").getAsInt() + ")" : "") + "  \u00B7  radius " + s.get("radius").getAsInt();
			};
			g.text(font, line, GAP, y + 12, UiTheme.text(), UiTheme.shadow());
			renderScores(g, s, GAP, y + 24, mouseX, mouseY);
			int drop = s.get("dropIn").getAsInt();
			if (drop > 0) {
				g.text(font, "Below its level: drops in " + Math.max(1, drop / 24000) + " day(s) unless renown comes back.", GAP, y + 36, 0xFFFF8080, UiTheme.shadow());
			}
			StringBuilder members = new StringBuilder();
			for (JsonElement m : s.getAsJsonArray("members")) {
				members.append(members.length() > 0 ? ", " : "").append(m.getAsJsonObject().get("name").getAsString());
			}
			g.text(font, "Members: " + (members.length() > 0 ? members : "none"), GAP, y + 48, UiTheme.muted(), UiTheme.shadow());
			int next = root.has("nextCount") ? root.get("nextCount").getAsInt() : 10;
			g.text(font, "Renown is counted every 10 minutes while the area is loaded (next count in " + next + " min).", GAP, y + 60, UiTheme.faint(), UiTheme.shadow());
			y += SETTLEMENT_H;
		}
	}

	/** The simple map: the same tiles as the full map at your current layer, your markers and you; nothing else. */
	private void renderMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		int y0 = HEADER + 1;
		int h = height - FOOTER - y0;
		g.fill(0, y0, width, y0 + h, 0xFF0B0E11);
		if (mc.level == null) {
			return;
		}
		net.minecraft.resources.Identifier dim = mc.level.dimension().identifier();
		g.enableScissor(0, y0, width, y0 + h);
		dev.vestiges.client.map.MapView.drawTiles(g, dim, dev.vestiges.client.map.MapView.currentLayer(mc), 0, y0, width, h, mapX, mapZ, mapZoom);
		dev.vestiges.client.map.MapView.drawMarkers(g, font, dim.toString(), 0, y0, width, h, mapX, mapZ, mapZoom);
		if (mc.player != null) {
			int[] p = dev.vestiges.client.map.MapView.toScreen(0, y0, width, h, mapX, mapZ, mapZoom, mc.player.getX(), mc.player.getZ());
			dev.vestiges.client.map.MapIcons.player(g, p[0], p[1], 10, mc.player.getYRot());
		}
		g.disableScissor();
		String hint = "Drag to move \u00B7 scroll to zoom";
		if (mouseY > y0 && mouseY < y0 + h) {
			hint = "x " + (int) Math.floor(mapX + (mouseX - width / 2.0) / mapZoom) + "   z " + (int) Math.floor(mapZ + (mouseY - y0 - h / 2.0) / mapZoom);
		}
		g.text(font, hint, GAP, y0 + 4, 0xFFFFFFFF, true);
	}

	private void renderPlaceholder(GuiGraphicsExtractor g, int top) {
		g.text(font, tab.label, GAP, top, UiTheme.text(), UiTheme.shadow());
		g.text(font, tab.placeholder, GAP, top + 12, UiTheme.muted(), UiTheme.shadow());
	}

	private void renderFeats(GuiGraphicsExtractor g, int top) {
		JsonObject s = ClientData.snapshot();
		if (!s.has("feats")) {
			g.text(font, "Loading feats...", GAP, top, UiTheme.muted(), UiTheme.shadow());
			return;
		}
		Map<String, List<JsonObject>> byChain = new LinkedHashMap<>();
		for (String chain : CHAINS) {
			byChain.put(chain, new ArrayList<>());
		}
		JsonArray feats = s.getAsJsonArray("feats");
		for (JsonElement e : feats) {
			JsonObject f = e.getAsJsonObject();
			byChain.computeIfAbsent(f.get("chain").getAsString(), k -> new ArrayList<>()).add(f);
		}
		byChain.values().forEach(list -> list.sort((a, b) -> Integer.compare(a.get("order").getAsInt(), b.get("order").getAsInt())));

		int bottom = height - FOOTER - 2;
		int textW = width - GAP * 3;
		g.enableScissor(0, top, width, bottom);
		int y = top - (int) scroll;
		int start = y;
		g.text(font, "Feats earn every special item: Bells, Banners, Ward Lanterns, waystones, keys. (" + feats.size() + " loaded from data)", GAP, y, UiTheme.muted(), UiTheme.shadow());
		y += 16;
		for (Map.Entry<String, List<JsonObject>> chain : byChain.entrySet()) {
			if (chain.getValue().isEmpty()) {
				continue;
			}
			g.text(font, Component.literal(capitalize(chain.getKey())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), GAP, y, UiTheme.head(), UiTheme.shadow());
			y += 12;
			for (JsonObject f : chain.getValue()) {
				String line = (f.get("repeatable").getAsBoolean() ? "↻ " : "") + f.get("name").getAsString() + " - " + f.get("requirement").getAsString()
					+ "  →  " + f.get("reward").getAsString() + (f.get("xp").getAsInt() > 0 ? "  (+" + f.get("xp").getAsInt() + " XP)" : "");
				for (FormattedCharSequence part : font.split(Component.literal(line), textW)) {
					g.text(font, part, GAP + 8, y, UiTheme.text(), UiTheme.shadow());
					y += 10;
				}
				y += 2;
			}
			y += 6;
		}
		g.disableScissor();
		contentHeight = y - start;
	}

	private void renderSettings(GuiGraphicsExtractor g, int top) {
		g.text(font, settingsCategory, GAP + 88, top, UiTheme.head(), UiTheme.shadow());
		if (settingsCategory.equals("World")) {
			g.text(font, "Operators only. These change this world for everyone.", GAP + 88, height - FOOTER - 12, UiTheme.muted(), UiTheme.shadow());
		}
	}

	// ---- input ---------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (tab == Tab.MAP && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && event.y() > HEADER && event.y() < height - FOOTER) {
			mapDragging = true;
			dragX = event.x();
			dragY = event.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		if (mapDragging) {
			mapX -= (event.x() - dragX) / mapZoom;
			mapZ -= (event.y() - dragY) / mapZoom;
			dragX = event.x();
			dragY = event.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		mapDragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.MAP) {
			int y0 = HEADER + 1;
			int h = height - FOOTER - y0;
			// Zoom around the cursor.
			double bx = mapX + (mouseX - width / 2.0) / mapZoom;
			double bz = mapZ + (mouseY - y0 - h / 2.0) / mapZoom;
			mapZoom = (float) Math.max(0.125, Math.min(8.0, scrollY > 0 ? mapZoom * 2 : mapZoom / 2));
			mapX = bx - (mouseX - width / 2.0) / mapZoom;
			mapZ = bz - (mouseY - y0 - h / 2.0) / mapZoom;
			return true;
		}
		if (tab == Tab.FEATS || tab == Tab.SETTINGS) {
			double visible = height - FOOTER - HEADER - 10;
			double max = Math.max(0, contentHeight - visible);
			scroll = Math.max(0, Math.min(max, scroll - scrollY * 20));
			if (tab == Tab.SETTINGS) {
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
