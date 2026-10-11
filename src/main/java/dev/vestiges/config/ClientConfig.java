package dev.vestiges.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client settings, saved as {@code config/vestiges/client.json} and changed from the hub's Settings tab. Pure data (lives in the main source
 * set so it is unit-tested); only the client reads or writes it.
 */
public final class ClientConfig {
	public int schema = 1;
	/** Master switch for the look-at HUD. */
	public boolean lookAtEnabled = true;
	/** Each look-at line by provider id; a provider the file does not mention defaults to on. */
	public Map<String, Boolean> lookAtLines = new LinkedHashMap<>();

	/** Minimap: on/off, size in GUI pixels, pixels per block, and which corner (top_left by default). */
	public boolean minimapEnabled = true;
	public int minimapSize = 128;
	public int minimapZoom = 1;
	public String minimapCorner = "top_left";
	/** Waypoints shared by other players: "ask" (Add or Decline) or "off". Blocked players are always ignored. */
	public String shareMode = "ask";
	/** M2: with the Tool slot selected, breaking a block picks the right tool from the rack. */
	public boolean autoTool = true;
	public java.util.List<String> blockedPlayers = new java.util.ArrayList<>();

	public ClientConfig normalize() {
		if (lookAtLines == null) {
			lookAtLines = new LinkedHashMap<>();
		}
		if (blockedPlayers == null) {
			blockedPlayers = new java.util.ArrayList<>();
		}
		minimapSize = Math.max(64, Math.min(256, minimapSize));
		minimapZoom = Math.max(1, Math.min(4, minimapZoom));
		if (!"ask".equals(shareMode) && !"off".equals(shareMode)) {
			shareMode = "ask";
		}
		if (minimapCorner == null || !java.util.Set.of("top_left", "top_right", "bottom_left", "bottom_right").contains(minimapCorner)) {
			minimapCorner = "top_left";
		}
		return this;
	}

	public boolean lineOn(String providerId) {
		Boolean v = lookAtLines.get(providerId);
		return v == null || v;
	}

	public void setLine(String providerId, boolean on) {
		lookAtLines.put(providerId, on);
	}
}
