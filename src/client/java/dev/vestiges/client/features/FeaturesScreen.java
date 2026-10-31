package dev.vestiges.client.features;

import com.google.gson.JsonObject;
import dev.vestiges.client.UiTheme;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The welcome page for a world that already exists: from Settings, or for the first operator on a dedicated server's new world. */
public final class FeaturesScreen extends Screen {
	private static final int MARGIN = 8;
	private final Screen parent;
	private final boolean firstRun;
	private final ServerConfig config = ClientFeatures.copy();
	private FeaturePanel panel;

	public FeaturesScreen(Screen parent, boolean firstRun) {
		super(Component.literal("Vestiges features"));
		this.parent = parent;
		this.firstRun = firstRun;
	}

	@Override
	protected void init() {
		panel = new FeaturePanel(config, false, () -> {
		});
		panel.setRectangle(width - 2 * MARGIN, height - 40, MARGIN, MARGIN);
		addRenderableWidget(panel);
		int y = height - 26;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
			JsonObject wanted = new JsonObject();
			for (VestigesSystem s : VestigesSystem.values()) {
				wanted.addProperty(s.id, config.enabled(s));
			}
			ClientFeatures.send("apply", wanted.toString());
			leave();
		}).bounds(width / 2 - 154, y, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, y, 150, 20).build());
	}

	private void leave() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public void onClose() {
		if (firstRun) {
			ClientFeatures.send("done", "");
		}
		leave();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiTheme.paintBackground(g, width, height);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}
}
