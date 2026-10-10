package dev.vestiges.client.map;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.map.MapMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The world map (key M). Drag to move, scroll to zoom, right-click to place a waypoint. The side panel lists waypoints, waystones and the
 * "Shared with you" inbox, with Go there (pins it on the Locator Bar), Share and Delete.
 */
public final class WorldMapScreen extends Screen {
	private static final int HEADER = 26;
	private static final int PANEL = 170;
	private static final int ROWS = 8;

	private interface Entry {
		String label();

		String dimension();

		int x();

		int y();

		int z();

		int color();

		void icon(GuiGraphicsExtractor g, int cx, int cy, int size);
	}

	private record WaypointEntry(ClientWaypoints.Waypoint w) implements Entry {
		public String label() {
			Minecraft mc = Minecraft.getInstance();
			return ClientWaypoints.label(w, mc.level != null ? mc.level.getGameTime() : 0);
		}

		public void icon(GuiGraphicsExtractor g, int cx, int cy, int size) {
			MapIcons.forWaypoint(g, w, cx, cy, size);
		}

		public String dimension() {
			return w.dimension;
		}

		public int x() {
			return w.x;
		}

		public int y() {
			return w.y;
		}

		public int z() {
			return w.z;
		}

		public int color() {
			return w.color;
		}
	}

	private record WaystoneEntry(ClientWaypoints.WaystoneMarker m) implements Entry {
		public String label() {
			return m.name();
		}

		public void icon(GuiGraphicsExtractor g, int cx, int cy, int size) {
			MapIcons.waystone(g, cx, cy, size);
		}

		public String dimension() {
			return m.dimension();
		}

		public int x() {
			return m.x();
		}

		public int y() {
			return m.y() + 1;
		}

		public int z() {
			return m.z();
		}

		public int color() {
			return 0xFF55E6E6;
		}
	}

	private double cx;
	private double cz;
	private float zoom = 1.0f;
	private String layer;
	private final Identifier dimension;
	private int page;
	private Entry selected;
	private EditBox nameBox;
	private EditBox shareBox;
	private boolean dragging;
	private double dragStartX;
	private double dragStartY;
	private int[] pendingSpot;
	private final List<Object[]> rowIcons = new ArrayList<>();

	public WorldMapScreen() {
		super(Component.literal("Map"));
		Minecraft mc = Minecraft.getInstance();
		cx = mc.player != null ? mc.player.getX() : 0;
		cz = mc.player != null ? mc.player.getZ() : 0;
		layer = MapView.currentLayer(mc);
		dimension = mc.level != null ? mc.level.dimension().identifier() : Identifier.withDefaultNamespace("overworld");
	}

	private int mapW() {
		return width - PANEL;
	}

	private int mapH() {
		return height - HEADER;
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		String dim = dimension.toString();
		for (ClientWaypoints.Waypoint w : ClientWaypoints.all()) {
			if (w.dimension.equals(dim)) {
				list.add(new WaypointEntry(w));
			}
		}
		for (ClientWaypoints.WaystoneMarker m : ClientWaypoints.waystones()) {
			if (m.dimension().equals(dim)) {
				list.add(new WaystoneEntry(m));
			}
		}
		return list;
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		boolean nether = MapView.isNether(mc);
		int x = 4;
		if (!nether) {
			Button surface = Button.builder(Component.literal("Surface"), b -> {
				layer = MapMath.SURFACE;
				rebuildWidgets();
			}).bounds(x, 3, 60, 20).build();
			surface.active = !MapMath.isSurface(layer);
			addRenderableWidget(surface);
			x += 62;
		}
		Button under = Button.builder(Component.literal(nether ? "Nether" : "Underground"), b -> {
			layer = MapMath.sliceKey(MapMath.slice(mc.player != null ? mc.player.getBlockY() : 0));
			rebuildWidgets();
		}).bounds(x, 3, 76, 20).build();
		under.active = MapMath.isSurface(layer) || nether;
		addRenderableWidget(under);
		x += 78;
		if (!MapMath.isSurface(layer)) {
			addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
				layer = MapMath.sliceKey(MapMath.sliceOf(layer) + 1);
				rebuildWidgets();
			}).bounds(x, 3, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
				layer = MapMath.sliceKey(MapMath.sliceOf(layer) - 1);
				rebuildWidgets();
			}).bounds(x + 22, 3, 20, 20).build());
			x += 44;
		}
		addRenderableWidget(Button.builder(Component.literal("Center on me"), b -> {
			if (mc.player != null) {
				cx = mc.player.getX();
				cz = mc.player.getZ();
			}
		}).bounds(x, 3, 80, 20).build());

		// Side panel.
		int px = width - PANEL + 4;
		int pw = PANEL - 8;
		int y = HEADER + 14;
		List<Entry> list = entries();
		rowIcons.clear();
		int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
		for (int i = page * ROWS; i < Math.min(list.size(), page * ROWS + ROWS); i++) {
			Entry e = list.get(i);
			rowIcons.add(new Object[] {e, px + 10, y + 9});
			Button row = Button.builder(Component.literal(font.plainSubstrByWidth(e.label(), pw - 30)), b -> {
				selected = e;
				cx = e.x() + 0.5;
				cz = e.z() + 0.5;
				rebuildWidgets();
			}).bounds(px, y, pw, 18).build();
			row.active = selected == null || !same(selected, e);
			addRenderableWidget(row);
			y += 19;
		}
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(px, y, 20, 18).build();
			prev.active = page > 0;
			addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(px + pw - 20, y, 20, 18).build();
			next.active = page < pages - 1;
			addRenderableWidget(next);
		}
		y = HEADER + 14 + ROWS * 19 + 22;

		if (selected != null) {
			Entry sel = selected;
			addRenderableWidget(Button.builder(Component.literal("Go there"), b -> pin(sel)).bounds(px, y, pw / 2 - 1, 20).build());
			if (sel instanceof WaypointEntry we) {
				addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
					ClientWaypoints.remove(we.w());
					selected = null;
					rebuildWidgets();
				}).bounds(px + pw / 2 + 1, y, pw / 2 - 1, 20).build());
				y += 22;
				shareBox = new EditBox(font, px, y, pw - 52, 18, Component.literal("Player"));
				shareBox.setMaxLength(16);
				addRenderableWidget(shareBox);
				addRenderableWidget(Button.builder(Component.literal("Share"), b -> {
					JsonObject a = new JsonObject();
					a.addProperty("target", shareBox.getValue());
					a.add("waypoint", we.w().toJson());
					ClientMapNet.send("share", a);
				}).bounds(px + pw - 50, y, 50, 18).build());
			}
			y += 22;
		}
		addRenderableWidget(Button.builder(Component.literal("Clear pin"), b -> ClientMapNet.send("unpin", new JsonObject()))
			.bounds(px, y, pw, 18).build());
		y += 22;

		// Shared with you.
		for (ClientWaypoints.Shared s : List.copyOf(ClientWaypoints.inbox())) {
			if (y > height - 44) {
				break;
			}
			addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
				ClientWaypoints.accept(s);
				rebuildWidgets();
			}).bounds(px + pw - 72, y + 10, 34, 16).build());
			addRenderableWidget(Button.builder(Component.literal("No"), b -> {
				ClientWaypoints.decline(s);
				rebuildWidgets();
			}).bounds(px + pw - 36, y + 10, 34, 16).build());
			y += 30;
		}

		// New waypoint (after a right-click on the map).
		if (pendingSpot != null) {
			int bx = 6;
			int by = height - 26;
			nameBox = new EditBox(font, bx + 150, by, 120, 20, Component.literal("Name"));
			nameBox.setMaxLength(32);
			nameBox.setValue("Waypoint " + (ClientWaypoints.all().size() + 1));
			addRenderableWidget(nameBox);
			setInitialFocus(nameBox);
			addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
				ClientWaypoints.add(nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue().strip(), dimension.toString(),
					pendingSpot[0], pendingSpot[1], pendingSpot[2], ClientWaypoints.nextColor(), "user");
				pendingSpot = null;
				rebuildWidgets();
			}).bounds(bx + 274, by, 50, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
				pendingSpot = null;
				rebuildWidgets();
			}).bounds(bx + 326, by, 50, 20).build());
		}
	}

	private static boolean same(Entry a, Entry b) {
		return a.x() == b.x() && a.z() == b.z() && a.label().equals(b.label());
	}

	private void pin(Entry e) {
		JsonObject a = new JsonObject();
		a.addProperty("dimension", e.dimension());
		a.addProperty("x", e.x());
		a.addProperty("y", e.y());
		a.addProperty("z", e.z());
		a.addProperty("color", e.color() & 0xFFFFFF);
		a.addProperty("label", e.label());
		ClientMapNet.send("pin", a);
	}

	// ---- rendering --------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		g.fill(0, 0, width, height, 0xFF0B0E11);
		int mw = mapW();
		int mh = mapH();
		g.enableScissor(0, HEADER, mw, HEADER + mh);
		MapView.drawTiles(g, dimension, layer, 0, HEADER, mw, mh, cx, cz, zoom);
		for (Entry e : entries()) {
			int[] p = MapView.toScreen(0, HEADER, mw, mh, cx, cz, zoom, e.x() + 0.5, e.z() + 0.5);
			e.icon(g, p[0], p[1], 10);
			if (zoom >= 0.5f || selected != null && same(selected, e)) {
				g.text(font, e.label(), p[0] + 8, p[1] - 4, 0xFFFFFFFF, true);
			}
		}
		if (mc.player != null && mc.level != null && mc.level.dimension().identifier().equals(dimension)) {
			int[] p = MapView.toScreen(0, HEADER, mw, mh, cx, cz, zoom, mc.player.getX(), mc.player.getZ());
			MapIcons.player(g, p[0], p[1], 10, mc.player.getYRot());
		}
		g.disableScissor();

		// Header and panel backgrounds, then widgets on top.
		g.fill(0, 0, width, HEADER, 0xE0101418);
		g.fill(width - PANEL, HEADER, width, height, 0xE0101418);
		g.fill(width - PANEL, HEADER, width - PANEL + 1, height, 0x55FFFFFF);
		super.extractRenderState(g, mouseX, mouseY, delta);
		for (Object[] r : rowIcons) {
			((Entry) r[0]).icon(g, (int) r[1], (int) r[2], 10);
		}

		String title = MapMath.label(layer, MapView.isNether(mc));
		g.text(font, title, mw - font.width(title) - 6, 9, 0xFFE6C36A);
		int px = width - PANEL + 4;
		g.text(font, "Waypoints and waystones", px, HEADER + 3, 0xFFFFFFFF);
		if (!ClientWaypoints.inbox().isEmpty()) {
			int y = HEADER + 14 + ROWS * 19 + 22 + (selected != null ? (selected instanceof WaypointEntry ? 44 : 22) : 0) + 22;
			for (ClientWaypoints.Shared s : ClientWaypoints.inbox()) {
				if (y > height - 44) {
					break;
				}
				g.text(font, "Shared by " + s.from() + ":", px, y, 0xFFAAAAAA);
				g.text(font, font.plainSubstrByWidth(s.waypoint().name, PANEL - 84), px, y + 14, 0xFFFFFFFF);
				y += 30;
			}
		}

		// Footer: cursor position, hints, last server message.
		if (mouseX < mw && mouseY > HEADER) {
			int bx = (int) Math.floor(cx + (mouseX - mw / 2.0) / zoom);
			int bz = (int) Math.floor(cz + (mouseY - HEADER - mh / 2.0) / zoom);
			g.text(font, "x " + bx + "   z " + bz, 6, height - 12, 0xFFFFFFFF, true);
		}
		if (pendingSpot != null) {
			g.text(font, "New waypoint at " + pendingSpot[0] + ", " + pendingSpot[1] + ", " + pendingSpot[2], 6, height - 38, 0xFFE6C36A, true);
		} else {
			String hint = "Drag to move · scroll to zoom · right-click to add a waypoint";
			g.text(font, hint, 6, height - 24, 0xFF888888, true);
		}
		if (!ClientMapNet.lastMessage.isEmpty()) {
			g.text(font, font.plainSubstrByWidth(ClientMapNet.lastMessage, PANEL - 8), px, height - 12, ClientMapNet.lastError ? 0xFFFF5555 : 0xFF55FF55);
		}
	}

	// ---- input -------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (mx >= mapW() || my < HEADER || (pendingSpot != null && my > height - 30)) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			int bx = (int) Math.floor(cx + (mx - mapW() / 2.0) / zoom);
			int bz = (int) Math.floor(cz + (my - HEADER - mapH() / 2.0) / zoom);
			pendingSpot = new int[] {bx, heightAt(bx, bz), bz};
			rebuildWidgets();
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = true;
			dragStartX = mx;
			dragStartY = my;
			return true;
		}
		return false;
	}

	private int heightAt(int x, int z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 64;
		}
		if (!MapMath.isSurface(layer)) {
			return MapMath.sliceOf(layer) * MapMath.SLICE_HEIGHT + 1;
		}
		int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		return h <= mc.level.getMinY() ? (mc.player != null ? mc.player.getBlockY() : 64) : h;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			cx -= (event.x() - dragStartX) / zoom;
			cz -= (event.y() - dragStartY) / zoom;
			dragStartX = event.x();
			dragStartY = event.y();
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < mapW() && mouseY > HEADER) {
			// Zoom around the cursor.
			double bx = cx + (mouseX - mapW() / 2.0) / zoom;
			double bz = cz + (mouseY - HEADER - mapH() / 2.0) / zoom;
			float old = zoom;
			zoom = (float) Math.max(0.125, Math.min(8.0, scrollY > 0 ? zoom * 2 : zoom / 2));
			if (zoom != old) {
				cx = bx - (mouseX - mapW() / 2.0) / zoom;
				cz = bz - (mouseY - HEADER - mapH() / 2.0) / zoom;
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
