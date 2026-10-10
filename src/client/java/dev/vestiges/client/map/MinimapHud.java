package dev.vestiges.client.map;

import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.config.ClientConfig;
import dev.vestiges.map.MapMath;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** The minimap: a square north-up map around the player, with waypoints, waystones, coordinates and the layer name. */
public final class MinimapHud implements HudElement {
	private static final int MARGIN = 4;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClientConfig cfg = ClientConfigStore.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.minimapEnabled || mc.level == null || mc.player == null || MapStore.worldDir() == null) {
			return;
		}
		Font font = mc.font;
		int size = cfg.minimapSize;
		float zoom = cfg.minimapZoom;
		boolean right = cfg.minimapCorner.endsWith("right");
		boolean bottom = cfg.minimapCorner.startsWith("bottom");
		int textH = font.lineHeight * 2 + 4;
		int x0 = right ? g.guiWidth() - size - MARGIN : MARGIN;
		int y0 = bottom ? g.guiHeight() - size - MARGIN - textH : MARGIN;

		Identifier dim = mc.level.dimension().identifier();
		String layer = MapView.currentLayer(mc);
		double px = mc.player.getX();
		double pz = mc.player.getZ();

		g.fill(x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, 0xFF000000);
		g.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, 0xFF6B6B6B);
		g.fill(x0, y0, x0 + size, y0 + size, 0xFF101418);
		g.enableScissor(x0, y0, x0 + size, y0 + size);
		MapView.drawTiles(g, dim, layer, x0, y0, size, size, px, pz, zoom);
		String dimId = dim.toString();
		for (ClientWaypoints.Waypoint w : ClientWaypoints.all()) {
			if (w.dimension.equals(dimId)) {
				int[] p = clamp(MapView.toScreen(x0, y0, size, size, px, pz, zoom, w.x + 0.5, w.z + 0.5), x0, y0, size);
				MapIcons.forWaypoint(g, w, p[0], p[1], 8);
			}
		}
		for (ClientWaypoints.WaystoneMarker w : ClientWaypoints.waystones()) {
			if (w.dimension().equals(dimId)) {
				int[] p = clamp(MapView.toScreen(x0, y0, size, size, px, pz, zoom, w.x() + 0.5, w.z() + 0.5), x0, y0, size);
				MapIcons.waystone(g, p[0], p[1], 8);
			}
		}
		MapIcons.player(g, x0 + size / 2, y0 + size / 2, 8, mc.player.getYRot());
		g.disableScissor();
		g.text(font, "N", x0 + size / 2 - font.width("N") / 2, y0 + 2, 0xFFFFFFFF, true);

		String coords = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
		String label = MapMath.label(layer, MapView.isNether(mc));
		int ty = y0 + size + 4;
		g.text(font, coords, x0 + (size - font.width(coords)) / 2, ty, 0xFFFFFFFF, true);
		g.text(font, label, x0 + (size - font.width(label)) / 2, ty + font.lineHeight + 1, 0xFFAAAAAA, true);
	}

	/** Keeps an off-map marker on the minimap's edge so it still shows the direction. */
	private static int[] clamp(int[] p, int x0, int y0, int size) {
		p[0] = Math.max(x0 + 5, Math.min(x0 + size - 5, p[0]));
		p[1] = Math.max(y0 + 5, Math.min(y0 + size - 5, p[1]));
		return p;
	}
}
