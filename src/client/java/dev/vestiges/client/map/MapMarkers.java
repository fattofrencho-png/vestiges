package dev.vestiges.client.map;

import dev.vestiges.client.ClientConfigStore;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** What the maps show: one switch per kind of marker, shared by the minimap, the world map and the hub's map. */
public final class MapMarkers {
	public static final String NAMES = "names";
	public static final int BUTTON_H = 16;
	private static final String[][] KINDS = {
		{"waypoint", "Waypoints"},
		{"death", "Death markers"},
		{"waystone", "Waystones"},
		{"settlement", "Settlements"},
		{"banner", "Banners"},
		{"ward", "Ward Lanterns"},
		{"gold", "Gold Blocks"},
		{NAMES, "Names"},
	};

	private MapMarkers() {
	}

	public static boolean on(String kind) {
		return ClientConfigStore.get().markerOn(kind);
	}

	public static boolean shown(ClientWaypoints.Waypoint w) {
		return on("death".equals(w.type) ? "death" : "waypoint");
	}

	public static int height() {
		return KINDS.length * BUTTON_H;
	}

	/** Adds one on/off button per kind, in a column starting at (x, y). */
	public static void addButtons(Consumer<Button> add, int x, int y, int w) {
		for (String[] kind : KINDS) {
			add.accept(Button.builder(text(kind[1], on(kind[0])), b -> {
				boolean now = !on(kind[0]);
				ClientConfigStore.get().setMarker(kind[0], now);
				ClientConfigStore.save();
				b.setMessage(text(kind[1], now));
			}).bounds(x, y, w, BUTTON_H).build());
			y += BUTTON_H;
		}
	}

	private static Component text(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "On" : "Off"));
	}
}
