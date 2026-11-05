package dev.vestiges.client.features;

import dev.vestiges.client.UiTheme;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The welcome page's content: presets, then categories, their features and the help for the selected one, plus an FAQ and the recommended mods.
 * Used in the Create World screen's Vestiges tab and in the screen operators open from Settings. Works on a {@link ServerConfig} it doesn't own.
 */
public final class FeaturePanel extends AbstractWidget {
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_ON = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier CHECK = Identifier.withDefaultNamespace("widget/checkbox");
	private static final Identifier CHECK_ON = Identifier.withDefaultNamespace("widget/checkbox_selected");
	private static final int GOLD = 0xFFE6C36A;
	private static final int ROW_H = 13;
	private static final int CATEGORY_H = 16;
	private static final int CATEGORY_W = 104;
	private static final String FAQ = "FAQ";
	private static final String MODS = "Mods to add";
	private static final String[][] FAQ_LINES = {
		{"Can I change this later?", "Yes. Operators can change it under H, Settings, World. Enchant levels are the one exception."},
		{"What if I turn a feature off in a running world?", "Items and levels already in the world stay as they are. New ones follow the new rule."},
		{"Why can't I turn off the inventory and map?", "Everything else is built on them."},
		{"Do other players need the mod?", "Yes, on servers everyone needs it."},
	};
	private static final String[][] MOD_LINES = {
		{"Shaders", "Iris, with a shader pack"},
		{"Speed", "Sodium"},
		{"Moving light", "LambDynamicLights"},
		{"Sound", "Sound Physics Remastered, AmbientSounds"},
		{"Animation", "Not Enough Animations"},
	};

	private final ServerConfig config;
	private final boolean creating;
	private final Runnable onChange;
	private String category = VestigesSystem.Category.CORE.label;
	private VestigesSystem selected = VestigesSystem.INVENTORY;
	private int scroll;

	/** @param creating true while the world is being created: only then can creation-only features change */
	public FeaturePanel(ServerConfig config, boolean creating, Runnable onChange) {
		super(0, 0, 100, 100, Component.literal("Vestiges features"));
		this.config = config;
		this.creating = creating;
		this.onChange = onChange;
	}

	public void selectPreset(VestigesSystem.Preset preset) {
		if (!creating) {
			// Enchant levels stay what the world was created with.
			boolean levels = config.enabled(VestigesSystem.ENCHANT_LEVELS);
			config.applyPreset(preset);
			if (levels != config.enabled(VestigesSystem.ENCHANT_LEVELS)) {
				config.set(VestigesSystem.ENCHANT_LEVELS, levels);
			}
		} else {
			config.applyPreset(preset);
		}
		onChange.run();
	}

	/** One line of the list: a switch (indented under the one it needs), or a small heading when {@code system} is null. */
	private record Row(VestigesSystem system, String heading) {
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		String section = null;
		for (VestigesSystem.Category c : VestigesSystem.Category.values()) {
			if (!c.label.equals(category)) {
				continue;
			}
			for (VestigesSystem s : VestigesSystem.listed(c)) {
				if (s.depth() == 0 && s.section() != null && !s.section().equals(section)) {
					section = s.section();
					out.add(new Row(null, section));
				}
				out.add(new Row(s, null));
			}
		}
		return out;
	}

	private VestigesSystem firstSwitch() {
		for (Row r : rows()) {
			if (r.system() != null) {
				return r.system();
			}
		}
		return VestigesSystem.INVENTORY;
	}

	/** True while something this feature needs is switched off, so it is shown dimmed under it. */
	private boolean blocked(VestigesSystem s) {
		for (VestigesSystem n : s.needs()) {
			if (!config.enabled(n)) {
				return true;
			}
		}
		return false;
	}

	private List<String> categories() {
		List<String> out = new ArrayList<>();
		for (VestigesSystem.Category c : VestigesSystem.Category.values()) {
			out.add(c.label);
		}
		out.add(FAQ);
		out.add(MODS);
		return out;
	}

	private int presetY() {
		return getY() + 13;
	}

	private int bodyY() {
		return getY() + 37;
	}

	private int bodyH() {
		return getHeight() - 37;
	}

	private int listX() {
		return getX() + CATEGORY_W + 6;
	}

	private int helpX() {
		return getX() + getWidth() - helpW();
	}

	private int helpW() {
		return Math.max(130, (getWidth() - CATEGORY_W) * 45 / 100);
	}

	private int listW() {
		return helpX() - 8 - listX();
	}

	private int visibleRows() {
		return Math.max(1, bodyH() / ROW_H);
	}

	private int presetW() {
		return (getWidth() - 6) / 4;
	}

	private static int body() {
		return UiTheme.light() ? UiTheme.text() : 0xFFDDDDDD;
	}

	private static int divider() {
		return UiTheme.light() ? UiTheme.line() : 0xFF3A3A3A;
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		// Keeps the text readable over the bright title background.
		g.fill(getX() - 4, getY() - 3, getX() + getWidth() + 4, getY() + getHeight() + 2, UiTheme.light() ? UiTheme.panel() : 0xA0101010);
		g.text(font, "Welcome to Vestiges", getX(), getY(), UiTheme.head(), UiTheme.shadow());
		int count = 0;
		for (VestigesSystem s : VestigesSystem.values()) {
			count += config.enabled(s) ? 1 : 0;
		}
		String total = count + " of " + VestigesSystem.values().length + " on";
		g.text(font, total, getX() + getWidth() - font.width(total), getY(), UiTheme.muted(), UiTheme.shadow());

		VestigesSystem.Preset current = config.preset();
		for (int i = 0; i <= VestigesSystem.Preset.values().length; i++) {
			VestigesSystem.Preset p = i < VestigesSystem.Preset.values().length ? VestigesSystem.Preset.values()[i] : null;
			String label = p == null ? "Custom" : p.label;
			int x = getX() + i * (presetW() + 2);
			boolean picked = p == current;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, picked || in(mouseX, mouseY, x, presetY(), presetW(), 18) ? BUTTON_ON : BUTTON, x, presetY(), presetW(), 18);
			g.text(font, label, x + (presetW() - font.width(label)) / 2, presetY() + 5, picked ? GOLD : 0xFFFFFFFF);
			if (in(mouseX, mouseY, x, presetY(), presetW(), 18)) {
				g.setTooltipForNextFrame(font, Component.literal(p == null ? "Your own choice. Changing any switch selects this." : p.help), mouseX, mouseY);
			}
		}

		List<String> cats = categories();
		for (int i = 0; i < cats.size(); i++) {
			int y = bodyY() + i * CATEGORY_H;
			boolean picked = cats.get(i).equals(category);
			if (picked || in(mouseX, mouseY, getX(), y - 1, CATEGORY_W, 14)) {
				g.fill(getX(), y - 1, getX() + CATEGORY_W, y + 13, picked ? UiTheme.pickedRow() : UiTheme.hoverRow());
			}
			g.text(font, cats.get(i), getX() + 4, y + 2, picked ? UiTheme.text() : UiTheme.muted(), UiTheme.shadow());
		}
		g.fill(listX() - 3, bodyY() - 2, listX() - 2, getY() + getHeight(), divider());

		if (category.equals(FAQ)) {
			drawFaq(g, font);
		} else if (category.equals(MODS)) {
			drawMods(g, font);
		} else {
			drawFeatures(g, font, mouseX, mouseY);
		}
	}

	private void drawFaq(GuiGraphicsExtractor g, Font font) {
		int y = bodyY();
		int w = getX() + getWidth() - listX();
		for (String[] q : FAQ_LINES) {
			g.text(font, q[0], listX(), y, UiTheme.head(), UiTheme.shadow());
			y = para(g, font, q[1], listX(), y + 10, w, body()) + 6;
		}
	}

	private void drawMods(GuiGraphicsExtractor g, Font font) {
		int w = getX() + getWidth() - listX();
		int y = para(g, font, "Things we can't easily build ourselves. All optional. This first list still has to be checked against 26.3.", listX(), bodyY(), w, body()) + 6;
		for (String[] m : MOD_LINES) {
			g.text(font, m[0], listX(), y, UiTheme.head(), UiTheme.shadow());
			g.text(font, m[1], listX() + 80, y, UiTheme.text(), UiTheme.shadow());
			y += 13;
		}
	}

	private void drawFeatures(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		List<Row> rows = rows();
		int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows()));
		for (int i = first; i < rows.size() && i - first < visibleRows(); i++) {
			VestigesSystem s = rows.get(i).system();
			int y = bodyY() + (i - first) * ROW_H;
			if (s == null) {
				g.text(font, rows.get(i).heading(), listX() + 2, y + 2, UiTheme.muted(), UiTheme.shadow());
				continue;
			}
			int indent = s.depth() * 12;
			if (s == selected || in(mouseX, mouseY, listX(), y - 1, listW(), ROW_H)) {
				g.fill(listX(), y - 1, listX() + listW(), y + 12, s == selected ? UiTheme.pickedRow() : UiTheme.hoverRow());
			}
			if (indent > 0) {
				g.fill(listX() + indent - 6, y - 1, listX() + indent - 5, y + 7, divider());
				g.fill(listX() + indent - 6, y + 6, listX() + indent, y + 7, divider());
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, config.enabled(s) ? CHECK_ON : CHECK, listX() + 2 + indent, y, 11, 11);
			boolean locked = s.core() || s.creationOnly() && !creating;
			int color = locked ? UiTheme.head() : s.built() && !blocked(s) ? UiTheme.text() : UiTheme.muted();
			g.text(font, s.label, listX() + 16 + indent, y + 2, color, UiTheme.shadow());
			if (!s.built()) {
				g.text(font, "later", listX() + listW() - font.width("later") - 2, y + 2, UiTheme.faint(), UiTheme.shadow());
			}
		}
		g.fill(helpX() - 5, bodyY() - 2, helpX() - 4, getY() + getHeight(), divider());
		VestigesSystem s = selected.category.label.equals(category) ? selected : firstSwitch();
		g.text(font, s.label, helpX(), bodyY(), UiTheme.head(), UiTheme.shadow());
		int y = para(g, font, s.help, helpX(), bodyY() + 13, helpW(), body());
		if (!s.needs().isEmpty()) {
			StringBuilder needs = new StringBuilder("Needs: ");
			for (int i = 0; i < s.needs().size(); i++) {
				needs.append(i > 0 ? ", " : "").append(s.needs().get(i).label);
			}
			y = para(g, font, needs.toString(), helpX(), y + 5, helpW(), UiTheme.muted());
		}
		if (s.core()) {
			para(g, font, "Always on.", helpX(), y + 5, helpW(), UiTheme.muted());
		} else if (s.creationOnly() && !creating) {
			para(g, font, "This world was created with it " + (config.enabled(s) ? "on." : "off."), helpX(), y + 5, helpW(), UiTheme.muted());
		} else if (!s.built()) {
			para(g, font, "Comes in a later update.", helpX(), y + 5, helpW(), UiTheme.muted());
		}
	}

	private static int para(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
		for (String line : text.split("\n", -1)) {
			if (line.isEmpty()) {
				y += 5;
				continue;
			}
			for (FormattedCharSequence part : font.split(Component.literal(line), w)) {
				g.text(font, part, x, y, color, UiTheme.shadow());
				y += 10;
			}
		}
		return y;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		VestigesSystem.Preset[] presets = VestigesSystem.Preset.values();
		for (int i = 0; i < presets.length; i++) {
			if (in(mx, my, getX() + i * (presetW() + 2), presetY(), presetW(), 18)) {
				selectPreset(presets[i]);
				return;
			}
		}
		List<String> cats = categories();
		for (int i = 0; i < cats.size(); i++) {
			if (in(mx, my, getX(), bodyY() + i * CATEGORY_H - 1, CATEGORY_W, 14)) {
				category = cats.get(i);
				scroll = 0;
				if (!rows().isEmpty()) {
					selected = firstSwitch();
				}
				return;
			}
		}
		if (category.equals(FAQ) || category.equals(MODS)) {
			return;
		}
		List<Row> rows = rows();
		int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows()));
		for (int i = first; i < rows.size() && i - first < visibleRows(); i++) {
			if (rows.get(i).system() != null && in(mx, my, listX(), bodyY() + (i - first) * ROW_H - 1, listW(), ROW_H)) {
				VestigesSystem s = rows.get(i).system();
				selected = s;
				if (!s.core() && (creating || !s.creationOnly())) {
					config.setWithNeeds(s, !config.enabled(s));
					onChange.run();
				}
				return;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, rows().size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
