package dev.vestiges.client.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Map icons drawn from vanilla textures (no new art): the skeleton's face for deaths, the lodestone top for waystones, a vanilla map banner
 * tinted with the waypoint's colour, and the vanilla map player arrow.
 */
public final class MapIcons {
	private static final Identifier LODESTONE = Identifier.withDefaultNamespace("textures/block/lodestone_top.png");
	private static final Identifier SKELETON = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
	private static final Identifier BANNER = Identifier.withDefaultNamespace("textures/map/decorations/white_banner.png");
	private static final Identifier PLAYER = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
	private static final int WHITE = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF000000;

	private MapIcons() {
	}

	/** Draws part of a texture as a {@code size}×{@code size} square centred on (cx, cy), tinted by {@code color}. */
	private static void blit(GuiGraphicsExtractor g, Identifier tex, int cx, int cy, int size, float u, float v, int uw, int vh, int texW, int texH, int color) {
		int x = cx - size / 2;
		int y = cy - size / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, size, size, uw, vh, texW, texH, color);
	}

	/** Waystone: the lodestone's top face with its compass-rose figure, framed in black. */
	public static void waystone(GuiGraphicsExtractor g, int cx, int cy, int size) {
		int h = size / 2;
		g.fill(cx - h - 1, cy - h - 1, cx - h + size + 1, cy - h + size + 1, SHADOW);
		blit(g, LODESTONE, cx, cy, size, 0, 0, 16, 16, 16, 16, WHITE);
	}

	/** Death marker: the skeleton's face, framed in black. */
	public static void death(GuiGraphicsExtractor g, int cx, int cy, int size) {
		int h = size / 2;
		g.fill(cx - h - 1, cy - h - 1, cx - h + size + 1, cy - h + size + 1, SHADOW);
		blit(g, SKELETON, cx, cy, size, 8, 8, 8, 8, 64, 32, WHITE);
	}

	/** Waypoint: a vanilla map banner in the waypoint's colour, with a dark shadow so it reads on any terrain. */
	public static void waypoint(GuiGraphicsExtractor g, int cx, int cy, int size, int color) {
		blit(g, BANNER, cx + 1, cy + 1, size, 0, 0, 8, 8, 8, 8, SHADOW);
		blit(g, BANNER, cx, cy, size, 0, 0, 8, 8, 8, 8, 0xFF000000 | color);
	}

	/** The player: the vanilla map arrow, turned to where the player looks (north is up). */
	public static void player(GuiGraphicsExtractor g, int cx, int cy, int size, float yaw) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(yaw - 180.0f));
		g.blit(RenderPipelines.GUI_TEXTURED, PLAYER, -size / 2, -size / 2, 0, 0, size, size, 8, 8, 8, 8, WHITE);
		g.pose().popMatrix();
	}

	/** The right icon for a waypoint: skull for deaths, banner for the rest. */
	public static void forWaypoint(GuiGraphicsExtractor g, ClientWaypoints.Waypoint w, int cx, int cy, int size) {
		if ("death".equals(w.type)) {
			death(g, cx, cy, size);
		} else {
			waypoint(g, cx, cy, size, w.color);
		}
	}
}
