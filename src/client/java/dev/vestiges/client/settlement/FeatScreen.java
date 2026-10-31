package dev.vestiges.client.settlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.client.ClientData;
import dev.vestiges.client.hub.HubScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Feats (M5, approved): a vanilla-advancement style tree you can drag, one row per chain (Settler, Warden, Explorer, Gatekeeper,
 * Dragonslayer). Gold frame = done; white = in progress; grey = comes with a later system. Hover for the requirement, progress and reward.
 */
public final class FeatScreen extends Screen {
	private static final String[] CHAINS = {"settler", "warden", "explorer", "gatekeeper", "dragonslayer"};
	private static final int HEADER = 28;
	private static final int ROW = 56;
	private static final int COL = 76;
	private static final int NODE = 26;
	private double panX = 40;
	private double panY;
	private boolean dragging;
	private JsonObject hover;

	public FeatScreen() {
		super(Component.literal("Feats"));
		ClientData.request("hub");
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreenAndShow(new HubScreen())).bounds(width - 66, 4, 60, 20).build());
	}

	private List<JsonObject> feats() {
		List<JsonObject> out = new ArrayList<>();
		JsonObject s = ClientData.snapshot();
		if (s.has("feats")) {
			for (JsonElement e : s.getAsJsonArray("feats")) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	private static int row(String chain) {
		for (int i = 0; i < CHAINS.length; i++) {
			if (CHAINS[i].equals(chain)) {
				return i;
			}
		}
		return CHAINS.length;
	}

	private int nodeX(JsonObject f) {
		return (int) (panX + 110 + (f.get("order").getAsInt() - 1) * COL);
	}

	private int nodeY(JsonObject f) {
		return (int) (panY + HEADER + 24 + row(f.get("chain").getAsString()) * ROW);
	}

	private static boolean done(JsonObject f) {
		return f.has("done") && f.get("done").getAsBoolean() || f.has("times") && f.get("times").getAsInt() > 0 && f.get("repeatable").getAsBoolean();
	}

	private static boolean later(JsonObject f) {
		return f.has("later") && !f.get("later").getAsString().isEmpty();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, HEADER, width, height, 0xFF14110C);
		g.fill(0, HEADER, width, HEADER + 1, 0x55FFFFFF);
		g.text(font, "Feats", 8, 10, 0xFFE6C36A);
		g.text(font, "Drag to look around. Each feat gives its item, a toast and XP.", 50, 10, 0xFF888888);
		List<JsonObject> feats = feats();
		hover = null;
		g.enableScissor(0, HEADER + 1, width, height);
		for (int r = 0; r < CHAINS.length; r++) {
			int y = (int) (panY + HEADER + 24 + r * ROW);
			String name = Character.toUpperCase(CHAINS[r].charAt(0)) + CHAINS[r].substring(1);
			g.text(font, name, (int) panX, y + 9, 0xFFC8B48A, true);
		}
		for (JsonObject a : feats) {
			for (JsonObject b : feats) {
				if (a.get("chain").getAsString().equals(b.get("chain").getAsString()) && b.get("order").getAsInt() == a.get("order").getAsInt() + 1) {
					int y = nodeY(a) + NODE / 2;
					g.fill(nodeX(a) + NODE, y, nodeX(b), y + 2, done(a) ? 0xFFE6C36A : 0xFF555044);
				}
			}
		}
		for (JsonObject f : feats) {
			int x = nodeX(f);
			int y = nodeY(f);
			boolean d = done(f);
			boolean l = later(f);
			g.fill(x, y, x + NODE, y + NODE, d ? 0xFF4A3A16 : l ? 0xFF1C1B19 : 0xFF2A2721);
			g.outline(x, y, NODE, NODE, d ? 0xFFE6C36A : l ? 0xFF444038 : 0xFFDDDDDD);
			Identifier icon = f.has("icon") ? Identifier.tryParse(f.get("icon").getAsString()) : null;
			if (icon != null) {
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
				g.item(stack, x + 5, y + 5);
			}
			if (l) {
				g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0x99000000);
			}
			if (f.get("repeatable").getAsBoolean() && f.has("times") && f.get("times").getAsInt() > 0) {
				String t = "x" + f.get("times").getAsInt();
				g.text(font, t, x + NODE - font.width(t), y + NODE - 8, 0xFFFFFFFF, true);
			}
			if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE && mouseY > HEADER) {
				hover = f;
			}
		}
		g.disableScissor();
		for (var child : children()) {
			if (child instanceof AbstractWidget w) {
				w.extractRenderState(g, mouseX, mouseY, delta);
			}
		}
		if (hover != null) {
			g.setComponentTooltipForNextFrame(font, tooltip(hover), mouseX, mouseY);
		}
	}

	private static List<Component> tooltip(JsonObject f) {
		List<Component> lines = new ArrayList<>();
		boolean d = done(f);
		lines.add(Component.literal(f.get("name").getAsString()).withStyle(d ? ChatFormatting.GOLD : ChatFormatting.WHITE));
		lines.add(Component.literal(f.get("requirement").getAsString()).withStyle(ChatFormatting.GRAY));
		if (later(f)) {
			lines.add(Component.literal(f.has("off") && f.get("off").getAsBoolean() ? "Off in this world" : "Comes with " + f.get("later").getAsString()).withStyle(ChatFormatting.DARK_GRAY));
		} else if (f.has("goals")) {
			JsonArray goals = f.getAsJsonArray("goals");
			for (JsonElement e : goals) {
				JsonObject g = e.getAsJsonObject();
				int have = g.get("have").getAsInt();
				int need = g.get("need").getAsInt();
				boolean met = have >= need || d && !f.get("repeatable").getAsBoolean();
				lines.add(Component.literal("  " + g.get("label").getAsString() + ": " + Math.min(have, need) + " / " + need)
					.withStyle(met ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			}
		}
		lines.add(Component.literal("Reward: " + f.get("reward").getAsString()).withStyle(ChatFormatting.AQUA));
		lines.add(Component.literal(f.get("xp").getAsInt() + " XP").withStyle(ChatFormatting.GREEN));
		if (f.get("repeatable").getAsBoolean()) {
			lines.add(Component.literal("Repeats  ·  earned " + (f.has("times") ? f.get("times").getAsInt() : 0) + " times").withStyle(ChatFormatting.DARK_AQUA));
		} else if (d) {
			lines.add(Component.literal("Done").withStyle(ChatFormatting.GOLD));
		}
		return lines;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() > HEADER) {
			dragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			panX += dx;
			panY += dy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
