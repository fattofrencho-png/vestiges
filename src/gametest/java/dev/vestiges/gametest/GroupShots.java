package dev.vestiges.gametest;

import dev.vestiges.client.features.FeaturePanel;
import dev.vestiges.config.VestigesSystem;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * Screenshots of every switch group on the welcome page (VESTIGES_SUITE=groups): the Create World screen is opened and each group is shown with
 * a parent selected, then the test ends without creating a world. Look at them in build/run/clientGameTest/screenshots.
 */
public final class GroupShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"groups".equals(System.getenv("VESTIGES_SUITE"))) {
			return;
		}
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> selectTab((CreateWorldScreen) mc.gui.screen()));
		ctx.waitTicks(5);
		for (VestigesSystem.Category c : VestigesSystem.Category.values()) {
			ctx.runOnClient(mc -> set(panel(mc), "category", c.label));
			ctx.waitTicks(3);
			ctx.takeScreenshot("groups_" + c.name().toLowerCase());
		}
		ctx.runOnClient(mc -> {
			set(panel(mc), "category", VestigesSystem.Category.WORLD.label);
			set(panel(mc), "selected", VestigesSystem.WORKERS);
		});
		ctx.waitTicks(3);
		ctx.takeScreenshot("groups_world_workers_selected");
		ctx.runOnClient(mc -> {
			set(panel(mc), "category", VestigesSystem.Category.CHARACTER.label);
			set(panel(mc), "selected", VestigesSystem.CHARACTER);
		});
		ctx.runOnClient(mc -> {
			// Turn "Levels and XP" off in the panel: the trees dim under it. Then back on: they return.
			Object config = get(panel(mc), "config");
			((dev.vestiges.config.ServerConfig) config).setWithNeeds(VestigesSystem.CHARACTER, false);
		});
		ctx.waitTicks(3);
		ctx.takeScreenshot("groups_character_levels_off");
		ctx.runOnClient(mc -> ((dev.vestiges.config.ServerConfig) get(panel(mc), "config")).setWithNeeds(VestigesSystem.CHARACTER, true));
		ctx.waitTicks(3);
		ctx.takeScreenshot("groups_character_levels_back_on");
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("autocheck-finished.txt"), "done");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		System.out.println("[autocheck] finished");
	}

	private static void selectTab(CreateWorldScreen screen) {
		try {
			Field f = CreateWorldScreen.class.getDeclaredField("tabNavigationBar");
			f.setAccessible(true);
			((TabNavigationBar) f.get(screen)).selectTab(3, false);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static FeaturePanel panel(Minecraft mc) {
		for (GuiEventListener child : mc.gui.screen().children()) {
			if (child instanceof FeaturePanel p) {
				return p;
			}
		}
		throw new IllegalStateException("no feature panel on " + mc.gui.screen());
	}

	private static void set(Object owner, String field, Object value) {
		try {
			Field f = owner.getClass().getDeclaredField(field);
			f.setAccessible(true);
			f.set(owner, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object get(Object owner, String field) {
		try {
			Field f = owner.getClass().getDeclaredField(field);
			f.setAccessible(true);
			return f.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
