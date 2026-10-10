package dev.vestiges.client.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.tree.PlayerTree;
import dev.vestiges.tree.TreeDefs;
import dev.vestiges.tree.TreeLabels;
import dev.vestiges.tree.TreeRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The passive trees: the Fight and Craft wheels drawn from the server's data (drag to move, scroll to zoom), and the Ascendancy tab. Clicks
 * only ask the server; the screen redraws from the state it sends back.
 */
public final class TreeScreen extends Screen {
	private static final int HEADER = 28;
	private static final int INFO = 14;
	private static final int FOOTER = 22;
	private static final int GOLD = 0xFFE6C36A;
	private static final int GREY = 0xFFAAAAAA;
	private static final int DIM = 0xFF888888;

	enum Tab {
		FIGHT("fight", "Fight wheel"), CRAFT("craft", "Craft wheel"), ASC("asc", "Ascendancy");

		final String id;
		final String label;

		Tab(String id, String label) {
			this.id = id;
			this.label = label;
		}
	}

	/** A small window over the screen with a question and its buttons. */
	private record Popup(String title, List<String> lines, List<Choice> choices) {
	}

	private record Choice(String label, Runnable action) {
	}

	private static Tab lastTab = Tab.FIGHT;
	/** Remembered pan and zoom per wheel (x, y, zoom). */
	private static final Map<String, double[]> VIEWS = new HashMap<>();

	private Tab tab = lastTab;
	private Popup popup;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private double dragDistance;
	private String hoverNode;
	private String hoverAsc;
	private Map<String, Set<String>> adjacency = Map.of();
	private static String search = "";
	private TreeDefs.Wheel adjacencyFor;

	public TreeScreen() {
		super(Component.literal("Passive trees"));
		ClientTree.request();
	}

	public void onData() {
		rebuildWidgets();
	}

	// ---- layout and widgets ------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		int x = 6;
		for (Tab t : Tab.values()) {
			Button b = Button.builder(Component.literal(t.label), btn -> {
				tab = t;
				lastTab = t;
				popup = null;
				rebuildWidgets();
			}).bounds(x, 4, 90, 20).build();
			b.active = t != tab;
			addRenderableWidget(b);
			x += 92;
		}
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreenAndShow(new HubScreen()))
			.bounds(width - 66, 4, 60, 20).build());
		if (tab != Tab.ASC) {
			addRenderableWidget(Button.builder(Component.literal("Center"), b -> {
				VIEWS.remove(tab.id);
			}).bounds(width - 130, 4, 60, 20).build());
		}
		if (tab != Tab.ASC) {
			int sx = 6 + Tab.values().length * 92;
			int sw = Math.min(150, width - 136 - sx);
			if (sw >= 60) {
				EditBox box = new EditBox(font, sx, 5, sw, 18, Component.literal("Search"));
				box.setMaxLength(40);
				box.setValue(search);
				box.setHint(Component.literal("Search nodes...").withStyle(ChatFormatting.DARK_GRAY));
				box.setResponder(v -> search = v);
				addRenderableWidget(box);
			}
		}
		if (popup != null) {
			buildPopup();
		} else if (tab == Tab.ASC) {
			buildAscendancy();
		}
	}

	private int popupW() {
		return Math.min(300, width - 20);
	}

	private void buildPopup() {
		int w = popupW();
		int x = (width - w) / 2;
		int y = popupTop() + popupTextHeight() + 8;
		for (Choice c : popup.choices()) {
			addRenderableWidget(Button.builder(Component.literal(c.label()), b -> {
				popup = null;
				c.action().run();
				rebuildWidgets();
			}).bounds(x + 10, y, w - 20, 20).build());
			y += 22;
		}
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
			popup = null;
			rebuildWidgets();
		}).bounds(x + 10, y, w - 20, 20).build());
	}

	private int popupTextHeight() {
		int h = 14;
		for (String line : popup.lines()) {
			h += font.split(Component.literal(line), popupW() - 20).size() * 10 + 2;
		}
		return h;
	}

	private int popupTop() {
		int total = popupTextHeight() + 8 + (popup.choices().size() + 1) * 22 + 6;
		return Math.max(HEADER + 4, (height - total) / 2);
	}

	private void open(Popup p) {
		popup = p;
		rebuildWidgets();
	}

	// ---- shared state helpers ---------------------------------------------------------------------------------------

	private static JsonObject state() {
		return ClientTree.state();
	}

	private static int refundCost() {
		return state().has("refundCost") ? state().get("refundCost").getAsInt() : 1;
	}

	private static boolean free() {
		return state().has("free") && state().get("free").getAsBoolean();
	}

	private static String costText() {
		int c = refundCost();
		return free() ? "free in creative" : c + " emerald" + (c == 1 ? "" : "s");
	}

	private static int[] points(String wheel) {
		if (!state().has("points")) {
			return new int[] {0, 0};
		}
		JsonObject o = state().getAsJsonObject("points").getAsJsonObject(wheel);
		return new int[] {o.get("total").getAsInt(), o.get("spent").getAsInt()};
	}

	// ---- wheels: geometry -------------------------------------------------------------------------------------------

	private int canvasTop() {
		return HEADER + INFO + 4;
	}

	private int canvasBottom() {
		return height - FOOTER;
	}

	private double[] view(TreeDefs.Wheel wheel) {
		return VIEWS.computeIfAbsent(tab.id, k -> {
			double fit = Math.min(width, canvasBottom() - canvasTop()) / 900.0;
			return new double[] {0, 0, Math.max(0.15, fit)};
		});
	}

	private double sx(double[] v, double x) {
		return width / 2.0 + v[0] + x * v[2];
	}

	private double sy(double[] v, double y) {
		return (canvasTop() + canvasBottom()) / 2.0 + v[1] + y * v[2];
	}

	/** Half the drawn size of a node, in pixels. */
	private static int half(TreeDefs.Node n, double zoom) {
		double units = switch (n.type()) {
			case "keystone" -> 12;
			case "mastery" -> 11;
			case "start" -> 11;
			case "notable" -> 8;
			default -> 5;
		};
		int min = switch (n.type()) {
			case "keystone", "mastery", "start" -> 4;
			case "notable" -> 3;
			default -> 2;
		};
		return Math.max(min, (int) Math.round(units * zoom));
	}

	private Map<String, Set<String>> adjacency(TreeDefs.Wheel wheel) {
		if (adjacencyFor != wheel) {
			adjacency = TreeRules.adjacency(wheel.edges());
			adjacencyFor = wheel;
		}
		return adjacency;
	}

	// ---- wheels: node status -----------------------------------------------------------------------------------------

	private enum Status { START_PICK, START, TAKEN, AVAILABLE, NO_POINTS, LOCKED, FAR }

	private static String startId(TreeDefs.Wheel wheel, PlayerTree.WheelState st) {
		for (TreeDefs.Node n : wheel.nodes()) {
			if (n.start().isPresent() && n.start().get() == st.start()) {
				return n.id();
			}
		}
		return null;
	}

	private Status status(TreeDefs.Wheel wheel, TreeDefs.Node n, PlayerTree.WheelState st, String startId, boolean pointsLeft) {
		if (startId == null) {
			return n.isStart() ? Status.START_PICK : Status.FAR;
		}
		if (n.id().equals(startId)) {
			return Status.START;
		}
		if (st.taken().contains(n.id())) {
			return Status.TAKEN;
		}
		if (n.rival().isPresent() && st.taken().contains(n.rival().get())) {
			return Status.LOCKED;
		}
		if (!TreeRules.canReach(n.id(), startId, st.taken(), adjacency(wheel))) {
			return Status.FAR;
		}
		return pointsLeft ? Status.AVAILABLE : Status.NO_POINTS;
	}

	/** Search: node name, text or one of its options contains the typed words (2 letters or more). */
	private static boolean matches(TreeDefs.Node n) {
		String q = search.trim().toLowerCase(java.util.Locale.ROOT);
		if (q.length() < 2) {
			return false;
		}
		if (n.name().toLowerCase(java.util.Locale.ROOT).contains(q) || n.text().toLowerCase(java.util.Locale.ROOT).contains(q)) {
			return true;
		}
		for (TreeDefs.Option o : n.choices()) {
			if (o.name().toLowerCase(java.util.Locale.ROOT).contains(q) || o.text().toLowerCase(java.util.Locale.ROOT).contains(q)) {
				return true;
			}
		}
		return false;
	}

	private static int regionColor(TreeDefs.Node n) {
		return switch (n.region().orElse("")) {
			case "melee" -> 0xFF8A3B33;
			case "defense" -> 0xFF3E5C8A;
			case "vitality" -> 0xFF8A3E6E;
			case "mobility" -> 0xFF3E8A86;
			case "ranged" -> 0xFF4E8A3E;
			case "mining" -> 0xFF7A6A55;
			case "crafting" -> 0xFF6A4E8A;
			case "building" -> 0xFF8A6A3E;
			case "gathering" -> 0xFF5A8A3E;
			case "exploring" -> 0xFF3E7A8A;
			default -> 0xFF5A5A5A;
		};
	}

	// ---- rendering -----------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, HEADER, width, HEADER + 1, 0x55FFFFFF);
		if (!ClientTree.loaded()) {
			g.text(font, "Waiting for the server...", 8, HEADER + 6, GREY);
			return;
		}
		if (!ClientTree.enabled()) {
			g.text(font, "The passive trees are switched off on this server.", 8, HEADER + 6, GREY);
			return;
		}
		hoverNode = null;
		hoverAsc = null;
		if (tab == Tab.ASC) {
			renderAscendancy(g, mouseX, mouseY);
		} else {
			renderWheel(g, mouseX, mouseY);
		}
		renderFooter(g);
		if (popup != null) {
			renderPopup(g);
		}
		// Widgets (tabs, buttons, popup buttons) go on top of the drawing.
		for (var child : children()) {
			if (child instanceof AbstractWidget w) {
				w.extractRenderState(g, mouseX, mouseY, delta);
			}
		}
	}

	private void renderFooter(GuiGraphicsExtractor g) {
		g.fill(0, height - FOOTER, width, height - FOOTER + 1, 0x55FFFFFF);
		String msg = ClientTree.message();
		if (!msg.isEmpty()) {
			boolean error = msg.startsWith("!");
			g.text(font, error ? msg.substring(1) : msg, 8, height - FOOTER + 7, error ? 0xFFFF7070 : 0xFF8FD18F);
			return;
		}
		String hint = tab == Tab.ASC
			? "Left-click: take the next node  ·  Right-click: refund the last one  ·  V: utility powers on/off"
			: "Drag: move  ·  Scroll: zoom  ·  Left-click: take  ·  Right-click: refund  ·  V: utility powers";
		g.text(font, font.plainSubstrByWidth(hint, width - 16), 8, height - FOOTER + 7, DIM);
	}

	private void renderWheel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		TreeDefs.Wheel wheel = ClientTree.wheel(tab.id);
		if (wheel == null) {
			g.text(font, "This wheel is missing from the server's data.", 8, HEADER + 6, GREY);
			return;
		}
		PlayerTree.WheelState st = ClientTree.tree().wheel(tab.id);
		String startId = startId(wheel, st);
		int[] pts = points(tab.id);
		boolean pointsLeft = pts[1] < pts[0];

		String info = startId == null
			? "Pick your starting point: click one of the three big nodes in the middle."
			: (tab == Tab.FIGHT ? "Fight" : "Craft") + " points: " + (pts[0] - pts[1]) + " of " + pts[0] + " left  ·  "
			+ (tab == Tab.FIGHT ? "1 per level" : "1 every 2 levels") + "  ·  Refund or switch: " + costText();
		long found = search.trim().length() >= 2 ? wheel.nodes().stream().filter(TreeScreen::matches).count() : -1;
		if (found >= 0) {
			info = found + " node" + (found == 1 ? "" : "s") + " found (yellow rings)  ·  " + info;
		}
		g.text(font, font.plainSubstrByWidth(info, width - 16), 8, HEADER + 5, startId == null ? 0xFF7CE0FF : 0xFFFFFFFF);

		double[] v = view(wheel);
		Map<String, TreeDefs.Node> byId = new HashMap<>();
		for (TreeDefs.Node n : wheel.nodes()) {
			byId.put(n.id(), n);
		}
		g.enableScissor(0, canvasTop(), width, canvasBottom());
		for (List<String> e : wheel.edges()) {
			TreeDefs.Node a = byId.get(e.get(0));
			TreeDefs.Node b = byId.get(e.get(1));
			if (a == null || b == null) {
				continue;
			}
			boolean ta = a.id().equals(startId) || st.taken().contains(a.id());
			boolean tb = b.id().equals(startId) || st.taken().contains(b.id());
			int color = ta && tb ? GOLD : ta || tb ? 0xFF8A8A8A : 0xFF3C3C3C;
			line(g, sx(v, a.x()), sy(v, a.y()), sx(v, b.x()), sy(v, b.y()), color, ta && tb ? 2 : 1);
		}
		double best = Double.MAX_VALUE;
		boolean inCanvas = popup == null && mouseY > canvasTop() && mouseY < canvasBottom();
		for (TreeDefs.Node n : wheel.nodes()) {
			int x = (int) Math.round(sx(v, n.x()));
			int y = (int) Math.round(sy(v, n.y()));
			int h = half(n, v[2]);
			if (x + h < 0 || x - h > width || y + h < canvasTop() || y - h > canvasBottom()) {
				continue;
			}
			Status s = status(wheel, n, st, startId, pointsLeft);
			drawNode(g, n, s, x, y, h);
			if (matches(n)) {
				int r = n.type().equals("keystone") || n.type().equals("mastery") ? h + 5 : h + 3;
				g.outline(x - r, y - r, 2 * r + 1, 2 * r + 1, 0xFFFFFF40);
				g.outline(x - r - 1, y - r - 1, 2 * r + 3, 2 * r + 3, 0xFFFFFF40);
			}
			if (inCanvas) {
				double d = Math.hypot(mouseX - x, mouseY - y);
				if (d <= h + 2 && d < best) {
					best = d;
					hoverNode = n.id();
				}
			}
		}
		g.disableScissor();
		if (hoverNode != null) {
			TreeDefs.Node n = byId.get(hoverNode);
			g.setTooltipForNextFrame(font, wrap(nodeTooltip(wheel, n, st, startId, status(wheel, n, st, startId, pointsLeft))), mouseX, mouseY);
		}
	}

	private void drawNode(GuiGraphicsExtractor g, TreeDefs.Node n, Status s, int x, int y, int h) {
		int fill;
		int border;
		switch (s) {
			case START, TAKEN -> {
				fill = GOLD;
				border = 0xFFFFF2C0;
			}
			case AVAILABLE -> {
				fill = darken(regionColor(n));
				border = 0xFF7CFC7C;
			}
			case START_PICK -> {
				fill = 0xFF2A4A5A;
				border = (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFF7CE0FF : 0xFFFFFFFF;
			}
			case LOCKED -> {
				fill = 0xFF4A1A1A;
				border = 0xFF8A3030;
			}
			case NO_POINTS -> {
				fill = darken(regionColor(n));
				border = 0xFFCCCCCC;
			}
			default -> {
				fill = darken(darken(regionColor(n)));
				border = darken(regionColor(n));
			}
		}
		g.fill(x - h, y - h, x + h + 1, y + h + 1, fill);
		g.outline(x - h - 1, y - h - 1, 2 * h + 3, 2 * h + 3, border);
		if (n.type().equals("keystone") || n.type().equals("mastery")) {
			g.outline(x - h - 3, y - h - 3, 2 * h + 7, 2 * h + 7, n.type().equals("mastery") ? 0xFFB07CFF : border);
		}
		if (!n.choices().isEmpty() && h >= 3) {
			int c = Math.max(1, h / 2);
			g.fill(x - c, y - c, x + c + 1, y + c + 1, s == Status.TAKEN ? 0xFF6A4A10 : 0xFF6FA8FF);
		}
	}

	private static int darken(int argb) {
		int r = ((argb >> 16) & 0xFF) * 3 / 5;
		int gr = ((argb >> 8) & 0xFF) * 3 / 5;
		int b = (argb & 0xFF) * 3 / 5;
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}

	/** A straight line: a thin rectangle turned to the right angle. */
	private static void line(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color, int thickness) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double len = Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate((float) x1, (float) y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, -thickness / 2, (int) Math.round(len), thickness - thickness / 2, color);
		g.pose().popMatrix();
	}

	private List<Component> nodeTooltip(TreeDefs.Wheel wheel, TreeDefs.Node n, PlayerTree.WheelState st, String startId, Status s) {
		List<Component> out = new ArrayList<>();
		String kind = switch (n.type()) {
			case "keystone" -> "Keystone";
			case "notable" -> n.choices().isEmpty() ? "Notable" : "Notable  ·  pick 1 of " + n.choices().size();
			case "mastery" -> "Mastery  ·  pick 1 of " + n.choices().size();
			case "start" -> "Starting point";
			default -> n.choices().isEmpty() ? "" : "Choice  ·  pick 1 of " + n.choices().size();
		};
		out.add(Component.literal(n.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (!kind.isEmpty()) {
			out.add(Component.literal(kind).withStyle(ChatFormatting.DARK_GRAY));
		}
		List<TreeDefs.Effect> effects = new ArrayList<>(n.effects());
		if (n.choices().isEmpty()) {
			out.add(Component.literal(n.text()).withStyle(ChatFormatting.WHITE));
		} else {
			effects.clear();
			int chosen = st.taken().contains(n.id()) ? st.choices().getOrDefault(n.id(), 0) : -1;
			for (int i = 0; i < n.choices().size(); i++) {
				TreeDefs.Option o = n.choices().get(i);
				boolean pick = i == chosen;
				out.add(Component.literal((pick ? "✔ " : "• ") + o.name() + ": " + o.text())
					.withStyle(pick ? ChatFormatting.GREEN : chosen >= 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE));
				if (chosen < 0 || pick) {
					effects.addAll(o.effects());
				}
			}
		}
		Set<String> pending = TreeLabels.pending(effects);
		if (!pending.isEmpty()) {
			out.add(Component.literal("Not active yet: comes with " + String.join(", ", pending) + ".").withStyle(ChatFormatting.GOLD));
		}
		if (effects.stream().anyMatch(e -> e.mechanic().map(TreeLabels.UTILITY::contains).orElse(false))) {
			out.add(Component.literal("Utility power: V switches all of them on or off.").withStyle(ChatFormatting.AQUA));
		}
		if (n.rival().isPresent()) {
			TreeDefs.Node rival = wheel.nodes().stream().filter(x -> x.id().equals(n.rival().get())).findFirst().orElse(null);
			out.add(Component.literal("Rival of " + (rival != null ? rival.name() : "another keystone") + ": taking one locks the other.")
				.withStyle(ChatFormatting.RED));
		}
		String line = switch (s) {
			case START_PICK -> "Click to start here.";
			case START -> st.taken().isEmpty() ? "Your starting point. Click another start to move it." : "Your starting point.";
			case TAKEN -> (n.choices().isEmpty() ? "" : "Click to switch (" + costText() + "). ") + "Right-click to refund (" + costText() + ").";
			case AVAILABLE -> "Click to take (1 point).";
			case NO_POINTS -> "No points left: level up for more.";
			case LOCKED -> "Locked by its rival.";
			case FAR -> n.isStart() && startId != null && st.taken().isEmpty() ? "Click to move your start here." : "Take a node next to it first.";
		};
		out.add(Component.literal(line).withStyle(s == Status.AVAILABLE || s == Status.START_PICK ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		return out;
	}

	private List<FormattedCharSequence> wrap(List<Component> lines) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) {
			out.addAll(font.split(c, 230));
		}
		return out;
	}

	private void renderPopup(GuiGraphicsExtractor g) {
		g.fill(0, 0, width, height, 0x88000000);
		int w = popupW();
		int x = (width - w) / 2;
		int y = popupTop();
		int h = popupTextHeight() + 8 + (popup.choices().size() + 1) * 22 + 6;
		g.fill(x, y, x + w, y + h, 0xF0181818);
		g.outline(x, y, w, h, GOLD);
		g.text(font, popup.title(), x + 10, y + 6, GOLD);
		int ty = y + 20;
		for (String line : popup.lines()) {
			for (FormattedCharSequence part : font.split(Component.literal(line), w - 20)) {
				g.text(font, part, x + 10, ty, 0xFFFFFFFF);
				ty += 10;
			}
			ty += 2;
		}
	}

	// ---- wheels: input -----------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (popup != null) {
			return true;
		}
		if (tab == Tab.ASC) {
			return clickAscendancy(event.button());
		}
		if (event.y() < canvasTop() || event.y() > canvasBottom()) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = true;
			dragX = event.x();
			dragY = event.y();
			dragDistance = 0;
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hoverNode != null) {
			rightClickNode(hoverNode);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging && tab != Tab.ASC) {
			TreeDefs.Wheel wheel = ClientTree.wheel(tab.id);
			if (wheel != null) {
				double[] v = view(wheel);
				v[0] += event.x() - dragX;
				v[1] += event.y() - dragY;
			}
			dragDistance += Math.abs(event.x() - dragX) + Math.abs(event.y() - dragY);
			dragX = event.x();
			dragY = event.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			if (dragDistance < 4 && hoverNode != null && popup == null) {
				leftClickNode(hoverNode);
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab != Tab.ASC && popup == null && mouseY > canvasTop() && mouseY < canvasBottom()) {
			TreeDefs.Wheel wheel = ClientTree.wheel(tab.id);
			if (wheel == null) {
				return false;
			}
			double[] v = view(wheel);
			double wx = (mouseX - width / 2.0 - v[0]) / v[2];
			double wy = (mouseY - (canvasTop() + canvasBottom()) / 2.0 - v[1]) / v[2];
			v[2] = Math.max(0.15, Math.min(3.0, scrollY > 0 ? v[2] * 1.25 : v[2] / 1.25));
			v[0] = mouseX - width / 2.0 - wx * v[2];
			v[1] = mouseY - (canvasTop() + canvasBottom()) / 2.0 - wy * v[2];
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void leftClickNode(String id) {
		TreeDefs.Wheel wheel = ClientTree.wheel(tab.id);
		if (wheel == null) {
			return;
		}
		TreeDefs.Node n = wheel.nodes().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
		if (n == null) {
			return;
		}
		PlayerTree.WheelState st = ClientTree.tree().wheel(tab.id);
		String startId = startId(wheel, st);
		int[] pts = points(tab.id);
		Status s = status(wheel, n, st, startId, pts[1] < pts[0]);
		String w = tab.id;
		if (n.isStart() && (startId == null || (st.taken().isEmpty() && !n.id().equals(startId)))) {
			open(new Popup(startId == null ? "Start at " + n.name() + "?" : "Move your start to " + n.name() + "?",
				List.of("Your path through the wheel grows from here. You can move it later only while this wheel is empty."),
				List.of(new Choice("Start here", () -> ClientTree.send("start", w, "", n.start().orElse(0))))));
			return;
		}
		if (s == Status.TAKEN && !n.choices().isEmpty()) {
			int chosen = st.choices().getOrDefault(n.id(), 0);
			List<Choice> options = new ArrayList<>();
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < n.choices().size(); i++) {
				TreeDefs.Option o = n.choices().get(i);
				lines.add((i == chosen ? "✔ " : "• ") + o.name() + ": " + o.text());
				if (i != chosen) {
					int pick = i;
					options.add(new Choice("Switch to " + o.name() + " (" + costText() + ")", () -> ClientTree.send("switch", w, n.id(), pick)));
				}
			}
			open(new Popup("Switch " + n.name() + "?", lines, options));
			return;
		}
		if (s != Status.AVAILABLE) {
			return;
		}
		if (!n.choices().isEmpty()) {
			List<Choice> options = new ArrayList<>();
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < n.choices().size(); i++) {
				TreeDefs.Option o = n.choices().get(i);
				lines.add("• " + o.name() + ": " + o.text());
				int pick = i;
				options.add(new Choice("Take " + o.name(), () -> ClientTree.send("take", w, n.id(), pick)));
			}
			lines.add("Switching later costs " + costText() + ".");
			open(new Popup(n.name() + ": pick one", lines, options));
			return;
		}
		if (n.type().equals("keystone") && n.rival().isPresent()) {
			TreeDefs.Node rival = wheel.nodes().stream().filter(x -> x.id().equals(n.rival().get())).findFirst().orElse(null);
			open(new Popup("Take " + n.name() + "?", List.of(n.text(), "This locks its rival, " + (rival != null ? rival.name() : "the other keystone")
				+ ", until you refund it."), List.of(new Choice("Take " + n.name(), () -> ClientTree.send("take", w, n.id(), -1)))));
			return;
		}
		ClientTree.send("take", w, n.id(), -1);
	}

	private void rightClickNode(String id) {
		TreeDefs.Wheel wheel = ClientTree.wheel(tab.id);
		PlayerTree.WheelState st = ClientTree.tree().wheel(tab.id);
		if (wheel == null || !st.taken().contains(id)) {
			return;
		}
		TreeDefs.Node n = wheel.nodes().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
		String w = tab.id;
		open(new Popup("Refund " + (n != null ? n.name() : id) + "?", List.of("You get the point back. Cost: " + costText() + ".",
			"Nodes further out that hang on this one must be refunded first."), List.of(new Choice("Refund", () -> ClientTree.send("refund", w, id, 0)))));
	}

	// ---- ascendancy ------------------------------------------------------------------------------------------------

	private List<JsonObject> checks(String which) {
		List<JsonObject> out = new ArrayList<>();
		if (state().has("asc")) {
			for (JsonElement e : state().getAsJsonObject("asc").getAsJsonArray(which)) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	private static boolean allDone(List<JsonObject> checks) {
		return !checks.isEmpty() && checks.stream().allMatch(c -> c.get("done").getAsBoolean());
	}

	private void buildAscendancy() {
		PlayerTree tree = ClientTree.tree();
		if (tree.ascendancy().isEmpty()) {
			boolean ready = allDone(checks("first"));
			int cardW = cardWidth();
			int x = cardsLeft();
			int y = canvasTop() + 4;
			int i = 0;
			for (TreeDefs.Ascendancy a : ClientTree.ascendancies()) {
				int cx = x + (i % 2) * (cardW + 6);
				int cy = y + (i / 2) * 76;
				Button b = Button.builder(Component.literal("Become a " + a.name()), btn -> open(new Popup("Become a " + a.name() + "?",
					List.of(a.kit() + ".", "You can change it later only with a Respec Tome (from dragon 12)."),
					List.of(new Choice("Ascend", () -> ClientTree.send("asc_choose", "", a.id(), 0)))))).bounds(cx + 4, cy + 52, cardW - 8, 18).build();
				b.active = ready;
				addRenderableWidget(b);
				i++;
			}
		} else if (tree.ascensions() == 1) {
			Button b = Button.builder(Component.literal("Ascend again (+2 points)"), btn -> ClientTree.send("asc_second", "", "", 0))
				.bounds(8, height - FOOTER - 24, 180, 20).build();
			b.active = allDone(checks("second"));
			addRenderableWidget(b);
		}
	}

	private int cardWidth() {
		return Math.max(150, Math.min(220, (width - checklistWidth() - 30) / 2));
	}

	private int checklistWidth() {
		return Math.min(240, width / 3);
	}

	private int cardsLeft() {
		return checklistWidth() + 16;
	}

	private void renderChecklist(GuiGraphicsExtractor g, String title, List<JsonObject> checks, int x, int y, int w) {
		g.text(font, title, x, y, GOLD);
		y += 13;
		for (JsonObject c : checks) {
			boolean done = c.get("done").getAsBoolean();
			String later = c.has("later") ? c.get("later").getAsString() : null;
			String line = (later != null ? "– " : done ? "✔ " : "✘ ") + c.get("text").getAsString()
				+ (later != null ? " (skipped: " + later + ")" : "  " + Math.min(c.get("have").getAsLong(), c.get("goal").getAsLong()) + "/" + c.get("goal").getAsLong());
			for (FormattedCharSequence part : font.split(Component.literal(line), w)) {
				g.text(font, part, x, y, later != null ? DIM : done ? 0xFF8FD18F : 0xFFD18F8F);
				y += 10;
			}
			y += 2;
		}
	}

	/** One node of the ascendancy tree and where it is drawn. */
	private record AscSpot(TreeDefs.AscNode node, int branch, int index, int x, int y) {
	}

	private static final int ASC_ROOT_X = 30;

	private int ascMidY() {
		return canvasTop() + 74;
	}

	/** Root on the left; the first branch grows to the right above it, the second below it. */
	private List<AscSpot> ascLayout(TreeDefs.Ascendancy asc) {
		List<AscSpot> out = new ArrayList<>();
		int left = ASC_ROOT_X + 80;
		int spacing = Math.max(64, Math.min(130, (width - left - 30) / 4));
		for (int b = 0; b < Math.min(2, asc.branches().size()); b++) {
			List<TreeDefs.AscNode> nodes = asc.branches().get(b).nodes();
			int y = ascMidY() + (b == 0 ? -34 : 34);
			for (int i = 0; i < nodes.size(); i++) {
				out.add(new AscSpot(nodes.get(i), b, i, left + i * spacing, y));
			}
		}
		return out;
	}

	private int ascSpacing() {
		int left = ASC_ROOT_X + 80;
		return Math.max(64, Math.min(130, (width - left - 30) / 4));
	}

	private void renderAscendancy(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		PlayerTree tree = ClientTree.tree();
		boolean utility = tree.utilityOn();
		String util = "Utility powers: " + (utility ? "On" : "Off") + " (V)";
		if (tree.ascendancy().isEmpty()) {
			g.text(font, "Ascend to pick a weapon and a tool to master. Each ascension gives 2 points.", 8, HEADER + 5, 0xFFFFFFFF);
			renderChecklist(g, "First ascension", checks("first"), 8, canvasTop() + 4, checklistWidth());
			int cardW = cardWidth();
			int i = 0;
			for (TreeDefs.Ascendancy a : ClientTree.ascendancies()) {
				int cx = cardsLeft() + (i % 2) * (cardW + 6);
				int cy = canvasTop() + 4 + (i / 2) * 76;
				g.fill(cx, cy, cx + cardW, cy + 72, 0xC0101010);
				g.outline(cx, cy, cardW, 72, 0xFF555555);
				g.text(font, a.name(), cx + 5, cy + 4, GOLD);
				g.text(font, font.plainSubstrByWidth(a.kit(), cardW - 10), cx + 5, cy + 15, GREY);
				int ly = cy + 27;
				for (TreeDefs.Branch b : a.branches()) {
					String crafting = b.nodes().size() > 1 ? b.nodes().get(1).name() : b.name();
					String fighting = b.nodes().size() > 3 ? b.nodes().get(3).name() : "";
					g.text(font, font.plainSubstrByWidth("• " + crafting + " → " + fighting, cardW - 10), cx + 5, ly, 0xFFFFFFFF);
					ly += 11;
				}
				i++;
			}
			return;
		}
		TreeDefs.Ascendancy asc = ClientTree.ascendancy(tree.ascendancy());
		if (asc == null) {
			g.text(font, "Your ascendancy is missing from the server's data.", 8, HEADER + 5, GREY);
			return;
		}
		JsonObject ascState = state().getAsJsonObject("asc");
		int total = ascState.get("total").getAsInt();
		int spent = ascState.get("spent").getAsInt();
		g.text(font, font.plainSubstrByWidth(asc.name() + "  ·  " + asc.kit() + "  ·  Ascension " + tree.ascensions() + " of 2  ·  Points: "
			+ (total - spent) + " of " + total + " left  ·  " + util + "  ·  Refund: " + costText(), width - 16), 8, HEADER + 5, 0xFFFFFFFF);

		List<AscSpot> spots = ascLayout(asc);
		int rootY = ascMidY();
		boolean hoverRoot = popup == null && Math.abs(mouseX - ASC_ROOT_X) <= 12 && Math.abs(mouseY - rootY) <= 12;
		// Links first, so the nodes sit on top of them.
		for (AscSpot sp : spots) {
			boolean taken = tree.ascTaken().contains(sp.node().id());
			int px = ASC_ROOT_X;
			int py = rootY;
			boolean prevTaken = true;
			if (sp.index() > 0) {
				AscSpot prev = spots.get(spots.indexOf(sp) - 1);
				px = prev.x();
				py = prev.y();
				prevTaken = tree.ascTaken().contains(prev.node().id());
			}
			int color = taken && prevTaken ? GOLD : prevTaken ? 0xFF8A8A8A : 0xFF3C3C3C;
			line(g, px, py, sp.x(), sp.y(), color, taken && prevTaken ? 2 : 1);
		}
		// The root: the ascendancy itself.
		g.fill(ASC_ROOT_X - 10, rootY - 10, ASC_ROOT_X + 11, rootY + 11, 0xFF4A3A6A);
		g.outline(ASC_ROOT_X - 11, rootY - 11, 23, 23, GOLD);
		g.outline(ASC_ROOT_X - 13, rootY - 13, 27, 27, 0xFFB07CFF);
		// Branch names, at the left of each row.
		for (int b = 0; b < Math.min(2, asc.branches().size()); b++) {
			int y = rootY + (b == 0 ? -34 : 34) - 4;
			g.text(font, font.plainSubstrByWidth(asc.branches().get(b).name(), ASC_ROOT_X + 80 - 22 - 6), 6, y + (b == 0 ? -14 : 14), GOLD);
		}
		int spacing = ascSpacing();
		for (AscSpot sp : spots) {
			TreeDefs.AscNode n = sp.node();
			List<TreeDefs.AscNode> branch = asc.branches().get(sp.branch()).nodes();
			boolean taken = tree.ascTaken().contains(n.id());
			boolean next = !taken && (sp.index() == 0 || tree.ascTaken().contains(branch.get(sp.index() - 1).id()));
			boolean canTake = next && spent < total;
			boolean travel = n.type().equals("travel");
			int h = travel ? 5 : 9;
			int base = travel ? 0xFF3A3050 : 0xFF4A3A6A;
			int fill = taken ? GOLD : next ? base : darken(base);
			int border = taken ? 0xFFFFF2C0 : canTake ? 0xFF7CFC7C : next ? 0xFFCCCCCC : 0xFF555555;
			g.fill(sp.x() - h, sp.y() - h, sp.x() + h + 1, sp.y() + h + 1, fill);
			g.outline(sp.x() - h - 1, sp.y() - h - 1, 2 * h + 3, 2 * h + 3, border);
			if (n.type().equals("fighting")) {
				g.outline(sp.x() - h - 3, sp.y() - h - 3, 2 * h + 7, 2 * h + 7, border);
			}
			// Name: above the node on the upper branch, below it on the lower one.
			List<FormattedCharSequence> lines = font.split(Component.literal(n.name()), spacing - 8);
			if (lines.size() > 2) {
				lines = lines.subList(0, 2);
			}
			int color = taken ? GOLD : travel ? GREY : 0xFFFFFFFF;
			int ly = sp.branch() == 0 ? sp.y() - h - 4 - lines.size() * 10 : sp.y() + h + 5;
			for (FormattedCharSequence part : lines) {
				g.text(font, part, sp.x() - font.width(part) / 2, ly, color);
				ly += 10;
			}
			if (popup == null && Math.abs(mouseX - sp.x()) <= h + 2 && Math.abs(mouseY - sp.y()) <= h + 2) {
				hoverAsc = n.id();
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(n.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				tip.add(Component.literal(asc.branches().get(sp.branch()).name() + " branch  ·  " + switch (n.type()) {
					case "crafting" -> "tool power";
					case "fighting" -> "weapon power";
					default -> "path";
				}).withStyle(ChatFormatting.DARK_GRAY));
				tip.add(Component.literal(n.text()).withStyle(ChatFormatting.WHITE));
				Set<String> pending = TreeLabels.pending(n.effects());
				if (!pending.isEmpty()) {
					tip.add(Component.literal("Not active yet: comes with " + String.join(", ", pending) + ".").withStyle(ChatFormatting.GOLD));
				}
				if (n.effects().stream().anyMatch(e -> e.mechanic().map(TreeLabels.UTILITY::contains).orElse(false))) {
					tip.add(Component.literal("Utility power: V switches all of them on or off.").withStyle(ChatFormatting.AQUA));
				}
				boolean last = taken && (sp.index() + 1 >= branch.size() || !tree.ascTaken().contains(branch.get(sp.index() + 1).id()));
				tip.add(Component.literal(taken ? (last ? "Taken. Right-click to refund (" + costText() + ")." : "Taken.")
					: canTake ? "Click to take (1 point)." : next ? "No ascendancy points left." : "Take the node before it first.")
					.withStyle(canTake ? ChatFormatting.GREEN : ChatFormatting.GRAY));
				g.setTooltipForNextFrame(font, wrap(tip), mouseX, mouseY);
			}
		}
		if (hoverRoot) {
			g.setTooltipForNextFrame(font, wrap(List.of(Component.literal(asc.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(asc.kit()).withStyle(ChatFormatting.GRAY),
				Component.literal("Ascension " + tree.ascensions() + " of 2  ·  " + (total - spent) + " of " + total + " points left").withStyle(ChatFormatting.WHITE),
				Component.literal("Each branch: path → tool power → path → weapon power.").withStyle(ChatFormatting.DARK_GRAY))), mouseX, mouseY);
		}
		int y = rootY + 34 + 9 + 26;
		if (tree.ascensions() == 1) {
			renderChecklist(g, "Second ascension (+2 points)", checks("second"), 8, y, Math.min(360, width - 16));
		} else {
			g.text(font, "You have ascended twice: all 4 ascendancy points are yours.", 8, y, GREY);
		}
	}

	private boolean clickAscendancy(int button) {
		if (hoverAsc == null) {
			return false;
		}
		PlayerTree tree = ClientTree.tree();
		String id = hoverAsc;
		if (button == InputConstants.MOUSE_BUTTON_LEFT && !tree.ascTaken().contains(id)) {
			ClientTree.send("asc_take", "", id, 0);
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && tree.ascTaken().contains(id)) {
			open(new Popup("Refund this node?", List.of("You get the point back. Cost: " + costText() + "."),
				List.of(new Choice("Refund", () -> ClientTree.send("asc_refund", "", id, 0)))));
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
