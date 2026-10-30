package dev.vestiges.client.settlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vestiges.client.map.MapView;
import dev.vestiges.tree.BuildingNodes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/**
 * The client's view of its settlements (M5): the circles on the minimap and world map (settlements it belongs to, its own Banners, Ward
 * Lanterns and Gold Blocks), and what the building nodes need on this side (claimed areas, blocks placed this session).
 */
public final class ClientSettlements {
	public record Circle(String kind, String name, String dimension, int x, int z, int radius, boolean active) {
	}

	private static List<Circle> circles = List.of();
	private static final LongOpenHashSet PLACED = new LongOpenHashSet();

	private ClientSettlements() {
	}

	public static void register() {
		BuildingNodes.clientPlaced = pos -> PLACED.contains(pos.asLong());
		BuildingNodes.clientPlacedAdd = pos -> {
			if (PLACED.size() > 20000) {
				PLACED.clear();
			}
			PLACED.add(pos.asLong());
		};
		BuildingNodes.clientClaimed = ClientSettlements::claimed;
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> PLACED.remove(pos.asLong()));
	}

	public static void reset() {
		circles = List.of();
		PLACED.clear();
	}

	public static void receive(JsonObject json) {
		List<Circle> list = new ArrayList<>();
		JsonArray arr = json.getAsJsonArray("circles");
		for (JsonElement e : arr) {
			JsonObject o = e.getAsJsonObject();
			list.add(new Circle(o.get("kind").getAsString(), o.get("name").getAsString(), o.get("dimension").getAsString(), o.get("x").getAsInt(),
				o.get("z").getAsInt(), o.get("radius").getAsInt(), o.get("active").getAsBoolean()));
		}
		circles = List.copyOf(list);
	}

	private static boolean claimed(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		String dim = mc.level.dimension().identifier().toString();
		for (Circle c : circles) {
			if (c.dimension().equals(dim) && ("settlement".equals(c.kind()) || "ward".equals(c.kind()))) {
				long dx = pos.getX() - c.x();
				long dz = pos.getZ() - c.z();
				if (dx * dx + dz * dz <= (long) c.radius() * c.radius()) {
					return true;
				}
			}
		}
		return false;
	}

	private static int color(Circle c) {
		int rgb = switch (c.kind()) {
			case "settlement" -> 0xE6C36A;
			case "ward" -> 0x6FD3E0;
			case "gold" -> 0xFFD700;
			default -> 0xFFFFFF;
		};
		return (c.active() ? 0xFF000000 : 0x90000000) | rgb;
	}

	/** Dotted circles (and names when {@code labels}) for every area in this dimension. */
	public static void draw(GuiGraphicsExtractor g, Font font, String dimension, int x0, int y0, int w, int h, double cx, double cz, float zoom,
							boolean labels) {
		for (Circle c : circles) {
			if (!c.dimension().equals(dimension) || !dev.vestiges.client.map.MapMarkers.on(c.kind())) {
				continue;
			}
			int color = color(c);
			double screenR = c.radius() * zoom;
			int points = (int) Math.max(24, Math.min(240, screenR * 1.2));
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				int[] p = MapView.toScreen(x0, y0, w, h, cx, cz, zoom, c.x() + 0.5 + Math.cos(a) * c.radius(), c.z() + 0.5 + Math.sin(a) * c.radius());
				g.fill(p[0], p[1], p[0] + 1, p[1] + 1, color);
			}
			if (labels && screenR > 12 && dev.vestiges.client.map.MapMarkers.on(dev.vestiges.client.map.MapMarkers.NAMES)) {
				int[] p = MapView.toScreen(x0, y0, w, h, cx, cz, zoom, c.x() + 0.5, c.z() + 0.5);
				g.text(font, c.name(), p[0] - font.width(c.name()) / 2, p[1] - 4, color, true);
			}
		}
	}
}
