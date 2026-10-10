package dev.vestiges.client.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.ClientData;
import dev.vestiges.client.hud.LookAtProvider;
import dev.vestiges.client.hud.LookAtProviders;
import dev.vestiges.config.ClientConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
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
		CHARACTER("Character", ""),
		CODEX("Codex", "Blocks, Bestiary, Biomes, Places and Lore arrive in M4."),
		VILLAGERS("Villagers", "Your villager library and the villager screen arrive in M6."),
		SETTLEMENTS("Settlements", "Bells, renown, wards and banners arrive in M5."),
		FEATS("Feats", ""),
		MAP("Map", "Your map: drag to move, scroll to zoom, right-click to add a waypoint. Waystones and shared places show here too."),
		SETTINGS("Settings", "");

		final String label;
		final String placeholder;

		Tab(String label, String placeholder) {
			this.label = label;
			this.placeholder = placeholder;
		}
	}

	private static Tab lastTab = Tab.FEATS;
	private Tab tab = lastTab;
	private double scroll;
	private int contentHeight;

	public HubScreen() {
		super(Component.literal("Vestiges"));
		// One fresh snapshot per opening, so /reload and config changes show up next time the hub opens.
		ClientData.request("hub");
	}

	@Override
	protected void init() {
		buildHeader();
		if (tab == Tab.SETTINGS) {
			buildSettings();
		}
		if (tab == Tab.CHARACTER) {
			addRenderableWidget(Button.builder(Component.literal("Open the passive trees"), b -> {
				if (minecraft != null) {
					minecraft.setScreenAndShow(new dev.vestiges.client.tree.TreeScreen());
				}
			}).bounds(GAP, HEADER + 8 + 100, 180, 20).build());
		}
		if (tab == Tab.MAP) {
			addRenderableWidget(Button.builder(Component.literal("Open the world map (M)"), b -> {
				if (minecraft != null && minecraft.level != null) {
					minecraft.setScreenAndShow(new dev.vestiges.client.map.WorldMapScreen());
				}
			}).bounds(GAP, HEADER + 34, 160, 20).build());
		}
	}

	/** Called by ClientData when a fresh snapshot arrives. */
	public void onData() {
		rebuildWidgets();
	}

	private void buildHeader() {
		Tab[] tabs = Tab.values();
		int x = 70;
		int bw = Math.max(44, Math.min(74, (width - x - GAP) / tabs.length - 2));
		for (Tab t : tabs) {
			Button b = Button.builder(Component.literal(t.label), btn -> {
				tab = t;
				lastTab = t;
				scroll = 0;
				rebuildWidgets();
			}).bounds(x, 4, bw, 20).build();
			b.active = t != tab;
			addRenderableWidget(b);
			x += bw + 2;
		}
	}

	private void buildSettings() {
		ClientConfig c = ClientConfigStore.get();
		int x = GAP;
		int y = HEADER + 22;
		int w = Math.min(220, width / 2 - GAP * 2);
		addRenderableWidget(toggle(x, y, w, "Look-at HUD", c.lookAtEnabled, on -> c.lookAtEnabled = on));
		y += 24;
		for (LookAtProvider p : LookAtProviders.all()) {
			addRenderableWidget(toggle(x + 10, y, w - 10, p.label(), c.lineOn(p.id()), on -> c.setLine(p.id(), on)));
			y += 22;
		}
		addRenderableWidget(toggle(x + 10, y, w - 10, "\"New\" tag", c.lineOn(LookAtProviders.NEW_TAG), on -> c.setLine(LookAtProviders.NEW_TAG, on)));
		y += 30;
		addRenderableWidget(toggle(x, y, w, "Minimap", c.minimapEnabled, on -> c.minimapEnabled = on));
		y += 24;
		addRenderableWidget(cycle(x + 10, y, w - 10, "Size", new String[] {"96", "128", "160", "192"}, String.valueOf(c.minimapSize), v -> c.minimapSize = Integer.parseInt(v)));
		y += 22;
		addRenderableWidget(cycle(x + 10, y, w - 10, "Zoom", new String[] {"1", "2", "3", "4"}, String.valueOf(c.minimapZoom), v -> c.minimapZoom = Integer.parseInt(v)));
		y += 22;
		addRenderableWidget(cycle(x + 10, y, w - 10, "Corner", new String[] {"top_left", "top_right", "bottom_left", "bottom_right"}, c.minimapCorner, v -> c.minimapCorner = v));
		y += 26;
		addRenderableWidget(cycle(x, y, w, "Shared waypoints", new String[] {"ask", "off"}, c.shareMode, v -> c.shareMode = v));
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
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, "Vestiges", GAP, 10, 0xFFE6C36A);
		g.fill(0, HEADER, width, HEADER + 1, 0x55FFFFFF);
		g.fill(0, height - FOOTER, width, height - FOOTER + 1, 0x55FFFFFF);
		JsonObject s = ClientData.snapshot();
		String version = s.has("version") ? s.get("version").getAsString() : "";
		g.text(font, ClientData.loaded() ? "Vestiges " + version : "Waiting for the server...", GAP, height - FOOTER + 10, 0xFF888888);

		int top = HEADER + 8;
		switch (tab) {
			case FEATS -> renderFeats(g, top);
			case SETTINGS -> renderSettings(g, top);
			case CHARACTER -> renderCharacter(g, top, s);
			default -> renderPlaceholder(g, top);
		}
	}

	private void renderCharacter(GuiGraphicsExtractor g, int top, JsonObject s) {
		String name = s.has("profile") ? s.getAsJsonObject("profile").get("name").getAsString() : "";
		g.text(font, name, GAP, top, 0xFFFFFFFF);
		if (!s.has("level")) {
			g.text(font, "Loading...", GAP, top + 14, 0xFFAAAAAA);
			return;
		}
		JsonObject l = s.getAsJsonObject("level");
		int level = l.get("level").getAsInt();
		int paragon = l.get("paragon").getAsInt();
		long xp = l.get("xp").getAsLong();
		long need = l.get("need").getAsLong();
		g.text(font, "Level " + level + (paragon > 0 ? "  \u00B7  Paragon " + paragon : ""), GAP, top + 16, 0xFFE6C36A);
		int bw = Math.min(260, width - GAP * 2);
		g.fill(GAP, top + 30, GAP + bw, top + 36, 0xFF2A2F33);
		g.fill(GAP, top + 30, GAP + (int) (bw * Math.min(1.0, xp / (double) Math.max(1, need))), top + 36, 0xFF80FF20);
		g.text(font, xp + " / " + need + " XP", GAP, top + 40, 0xFFAAAAAA);
		g.text(font, "From your level: +" + (int) l.get("bonusHealth").getAsDouble() + " max health, +" + l.get("damagePct").getAsDouble()
			+ "% damage", GAP, top + 56, 0xFFFFFFFF);
		g.text(font, "Discoveries rewarded: " + l.get("discoveries").getAsInt(), GAP, top + 68, 0xFFAAAAAA);
		g.text(font, "Spend your points on the Fight and Craft wheels, then ascend (from level 20).", GAP, top + 86, 0xFF888888);
	}

	private void renderPlaceholder(GuiGraphicsExtractor g, int top) {
		g.text(font, tab.label, GAP, top, 0xFFFFFFFF);
		g.text(font, tab.placeholder, GAP, top + 12, 0xFFAAAAAA);
	}

	private void renderFeats(GuiGraphicsExtractor g, int top) {
		JsonObject s = ClientData.snapshot();
		if (!s.has("feats")) {
			g.text(font, "Loading feats...", GAP, top, 0xFFAAAAAA);
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
		g.text(font, "Feats earn every special item: Bells, Banners, Ward Lanterns, waystones, keys. (" + feats.size() + " loaded from data)", GAP, y, 0xFFAAAAAA);
		y += 16;
		for (Map.Entry<String, List<JsonObject>> chain : byChain.entrySet()) {
			if (chain.getValue().isEmpty()) {
				continue;
			}
			g.text(font, Component.literal(capitalize(chain.getKey())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), GAP, y, 0xFFE6C36A);
			y += 12;
			for (JsonObject f : chain.getValue()) {
				String line = (f.get("repeatable").getAsBoolean() ? "↻ " : "") + f.get("name").getAsString() + " - " + f.get("requirement").getAsString()
					+ "  →  " + f.get("reward").getAsString() + (f.get("xp").getAsInt() > 0 ? "  (+" + f.get("xp").getAsInt() + " XP)" : "");
				for (FormattedCharSequence part : font.split(Component.literal(line), textW)) {
					g.text(font, part, GAP + 8, y, 0xFFFFFFFF);
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
		g.text(font, "Your settings (this computer)", GAP, top, 0xFFFFFFFF);
		int x = Math.min(240, width / 2) + GAP;
		g.text(font, "Server systems", x, top, 0xFFFFFFFF);
		JsonObject s = ClientData.snapshot();
		int y = top + 14;
		if (s.has("systems")) {
			for (JsonElement e : s.getAsJsonArray("systems")) {
				JsonObject o = e.getAsJsonObject();
				boolean on = o.get("on").getAsBoolean();
				g.text(font, (on ? "✔ " : "✖ ") + o.get("label").getAsString(), x, y, on ? 0xFF8FD18F : 0xFFD18F8F);
				y += 10;
			}
			boolean op = s.has("operator") && s.get("operator").getAsBoolean();
			g.text(font, op ? "Change with /vestiges config system <id> true|false" : "Only operators can change these.", x, y + 4, 0xFF888888);
		}
	}

	// ---- input ---------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.FEATS) {
			double visible = height - FOOTER - HEADER - 10;
			double max = Math.max(0, contentHeight - visible);
			scroll = Math.max(0, Math.min(max, scroll - scrollY * 20));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
