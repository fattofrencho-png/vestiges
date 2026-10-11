package dev.vestiges.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.client.hub.HubScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The Codex screen (M4, approved): Blocks · Bestiary · Places · Items, a search box and a filter (all / in progress / mastered). The grid shows
 * each entry's icon ("?" until found) framed by its stage; the side card shows the selected entry's class, progress, flavour lines and
 * what it unlocks.
 */
public final class CodexScreen extends Screen {
	private enum Section {
		BLOCKS("Blocks", "blocks"), MOBS("Bestiary", "mobs"), PLACES("Places", "biomes"), ITEMS("Items", "items");

		final String label;
		final String id;

		Section(String label, String id) {
			this.label = label;
			this.id = id;
		}
	}

	private static final String[] FILTERS = {"All", "In progress", "Mastered"};
	private static final int HEADER = 30;
	private static final int FOOTER = 22;
	private static final int CELL = 22;
	private static final int GOLD = 0xFFE6C36A;
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xFF777777;

	private static Section section = Section.BLOCKS;
	private static int filter;
	private static String search = "";
	private static String selected = "";
	private double scroll;
	private int seenVersion = -1;
	/** Rows to draw: a group title (entry null) or a row of entries. */
	private final List<Object[]> rows = new ArrayList<>();
	private ClientCodex.Entry hover;

	public CodexScreen() {
		super(Component.literal("Codex"));
	}

	@Override
	protected void init() {
		ClientCodex.request();
		int x = 6;
		for (Section s : Section.values()) {
			Button b = Button.builder(Component.literal(s.label), btn -> {
				section = s;
				scroll = 0;
				rebuildWidgets();
			}).bounds(x, 4, 64, 20).build();
			b.active = s != section;
			addRenderableWidget(b);
			x += 66;
		}
		int sw = Math.min(140, width - x - 160);
		if (sw >= 60) {
			EditBox box = new EditBox(font, x + 4, 5, sw, 18, Component.literal("Search"));
			box.setMaxLength(40);
			box.setValue(search);
			box.setHint(Component.literal("Search...").withStyle(ChatFormatting.DARK_GRAY));
			box.setResponder(v -> {
				search = v;
				scroll = 0;
			});
			addRenderableWidget(box);
			x += sw + 8;
		}
		addRenderableWidget(Button.builder(Component.literal(FILTERS[filter]), btn -> {
			filter = (filter + 1) % FILTERS.length;
			btn.setMessage(Component.literal(FILTERS[filter]));
			scroll = 0;
		}).bounds(width - 158, 4, 86, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreenAndShow(new HubScreen()))
			.bounds(width - 66, 4, 60, 20).build());
	}

	private int cardWidth() {
		return Math.min(210, width / 3);
	}

	private boolean shown(ClientCodex.Entry e) {
		boolean inSection = section == Section.PLACES ? e.section().equals("biomes") || e.section().equals("structures") : e.section().equals(section.id);
		if (!inSection) {
			return false;
		}
		if (filter == 1 && (!e.known() || e.mastered())) {
			return false;
		}
		if (filter == 2 && !e.mastered()) {
			return false;
		}
		return search.isBlank() || e.known() && e.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT).trim());
	}

	private void layout() {
		rows.clear();
		int perRow = Math.max(1, (width - cardWidth() - 24) / CELL);
		List<String[]> groups = section == Section.PLACES ? List.of(new String[] {"biomes", "Biomes"}, new String[] {"structures", "Structures"})
			: List.<String[]>of(new String[] {section.id, ""});
		for (String[] group : groups) {
			List<ClientCodex.Entry> list = new ArrayList<>();
			for (ClientCodex.Entry e : ClientCodex.entries()) {
				if (e.section().equals(group[0]) && shown(e)) {
					list.add(e);
				}
			}
			if (list.isEmpty()) {
				continue;
			}
			if (!group[1].isEmpty()) {
				rows.add(new Object[] {group[1]});
			}
			for (int i = 0; i < list.size(); i += perRow) {
				rows.add(list.subList(i, Math.min(list.size(), i + perRow)).toArray());
			}
		}
	}

	private static int frame(ClientCodex.Entry e) {
		if (!e.known()) {
			return 0xFF3A3A3A;
		}
		if (e.mastered()) {
			return GOLD;
		}
		return e.stage() >= 2 ? 0xFF73CCFF : 0xFFDDDDDD;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, HEADER, width, HEADER + 1, 0x55FFFFFF);
		g.fill(0, height - FOOTER, width, height - FOOTER + 1, 0x55FFFFFF);
		if (!ClientCodex.loaded()) {
			g.text(font, "Waiting for the server...", 8, HEADER + 6, GREY);
		} else if (!ClientCodex.enabled()) {
			g.text(font, "The Codex is switched off on this server.", 8, HEADER + 6, GREY);
		} else {
			if (seenVersion != ClientCodex.version()) {
				seenVersion = ClientCodex.version();
			}
			layout();
			renderGrid(g, mouseX, mouseY);
			renderCard(g);
			String next = ClientCodex.nextPoint() > 0 ? " (next at " + ClientCodex.nextPoint() + ")" : "";
			g.text(font, "Found " + ClientCodex.discovered() + " of " + ClientCodex.total() + " entries  ·  Codex bonus Craft points: "
				+ ClientCodex.points() + next, 8, height - FOOTER + 7, GREY);
		}
		for (var child : children()) {
			if (child instanceof AbstractWidget w) {
				w.extractRenderState(g, mouseX, mouseY, delta);
			}
		}
		if (hover != null) {
			g.setComponentTooltipForNextFrame(font, List.of(Component.literal(hover.known() ? hover.name() : "???"),
				Component.literal(hover.label()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
		}
	}

	private void renderGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int top = HEADER + 6;
		int bottom = height - FOOTER - 4;
		int maxScroll = Math.max(0, rows.size() * CELL - (bottom - top));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		hover = null;
		g.enableScissor(0, top, width - cardWidth() - 12, bottom);
		int y = top - (int) scroll;
		for (Object[] row : rows) {
			if (row.length == 1 && row[0] instanceof String title) {
				g.text(font, title, 8, y + 7, GOLD);
				y += CELL;
				continue;
			}
			int x = 8;
			for (Object o : row) {
				ClientCodex.Entry e = (ClientCodex.Entry) o;
				boolean sel = e.id().equals(selected);
				g.fill(x, y, x + CELL - 2, y + CELL - 2, sel ? 0xFF2E3A44 : 0xFF181C20);
				g.outline(x, y, CELL - 2, CELL - 2, frame(e));
				if (e.known() && !e.icon().getDefaultInstance().isEmpty()) {
					g.item(new ItemStack(e.icon()), x + 2, y + 2);
				} else {
					g.text(font, "?", x + 8, y + 6, DIM);
				}
				if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2 && mouseY >= top && mouseY < bottom) {
					hover = e;
				}
				x += CELL;
			}
			y += CELL;
		}
		g.disableScissor();
		if (rows.isEmpty()) {
			g.text(font, search.isBlank() ? "Nothing here yet." : "No entry matches.", 8, top + 4, DIM);
		}
	}

	private void renderCard(GuiGraphicsExtractor g) {
		int w = cardWidth();
		int x = width - w - 6;
		int top = HEADER + 6;
		g.fill(x, top, x + w, height - FOOTER - 4, 0xC0101418);
		g.outline(x, top, w, height - FOOTER - 4 - top, 0x60FFFFFF);
		ClientCodex.Entry e = null;
		for (ClientCodex.Entry c : ClientCodex.entries()) {
			if (c.id().equals(selected)) {
				e = c;
			}
		}
		int tx = x + 8;
		int y = top + 8;
		if (e == null) {
			g.text(font, "Pick an entry.", tx, y, DIM);
			return;
		}
		if (e.known()) {
			g.pose().pushMatrix();
			g.pose().translate(tx, y);
			g.pose().scale(2f, 2f);
			g.item(new ItemStack(e.icon()), 0, 0);
			g.pose().popMatrix();
		}
		int nx = tx + (e.known() ? 40 : 0);
		g.text(font, e.known() ? e.name() : "???", nx, y + 4, 0xFFFFFFFF);
		g.text(font, e.cls().isEmpty() ? e.label() : className(e.cls()) + "  ·  " + e.label(), nx, y + 16, frame(e));
		y += 40;
		if (!e.later().isEmpty()) {
			g.text(font, "Comes with " + e.later() + ".", tx, y, DIM);
			return;
		}
		if (e.need() > 0) {
			int bw = w - 16;
			g.fill(tx, y, tx + bw, y + 5, 0xFF2A2F33);
			g.fill(tx, y, tx + (int) (bw * Math.min(1.0, e.count() / (double) e.need())), y + 5, 0xFF80C0FF);
			g.text(font, e.count() + " / " + e.need() + " toward the next stage", tx, y + 8, GREY);
			y += 24;
		}
		y = paragraph(g, e.line1().isEmpty() ? "???" : e.line1(), tx, y, w - 16, e.line1().isEmpty() ? DIM : 0xFFE0D8C8);
		y = paragraph(g, e.line2().isEmpty() ? "??? (Mastered)" : e.line2(), tx, y + 4, w - 16, e.line2().isEmpty() ? DIM : 0xFFE0D8C8);
		y += 8;
		for (Map.Entry<String, Boolean> u : e.unlocks()) {
			g.text(font, (u.getValue() ? "✔ " : "✖ ") + u.getKey(), tx, y, u.getValue() ? 0xFF8FD18F : DIM);
			y += 11;
		}
	}

	private int paragraph(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
		for (FormattedCharSequence line : font.split(Component.literal(text).withStyle(ChatFormatting.ITALIC), w)) {
			g.text(font, line, x, y, color);
			y += 10;
		}
		return y;
	}

	private static String className(String cls) {
		return switch (cls) {
			case "uncommon" -> "Uncommon";
			case "rare" -> "Rare";
			case "very_rare" -> "Very rare";
			case "unique" -> "Unique";
			default -> "Common";
		};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hover != null) {
			selected = hover.id();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * CELL;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
