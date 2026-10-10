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
		CHARACTER("Character", "Levels, the Fight and Craft wheels and your ascendancy arrive in M1."),
		CODEX("Codex", "Blocks, Bestiary, Biomes, Places and Lore arrive in M4."),
		VILLAGERS("Villagers", "Your villager library and the villager screen arrive in M6."),
		SETTLEMENTS("Settlements", "Bells, renown, wards and banners arrive in M5."),
		FEATS("Feats", ""),
		MAP("Map", "The world map and waystones come right after this foundation."),
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
			case CHARACTER -> {
				renderPlaceholder(g, top);
				if (s.has("profile")) {
					JsonObject p = s.getAsJsonObject("profile");
					g.text(font, "Player: " + p.get("name").getAsString(), GAP, top + 24, 0xFFFFFFFF);
				}
			}
			default -> renderPlaceholder(g, top);
		}
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
