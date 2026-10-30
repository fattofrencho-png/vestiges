package dev.vestiges.gametest;

import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.features.FeaturePanel;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.client.map.ClientWaypoints;
import dev.vestiges.client.map.WorldMapScreen;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Plays through the screens in a real world and saves screenshots, so a layout can be looked at without a playtest. The world is made through
 * the real Create World screen. Set VESTIGES_TOUR=core to create it with the Core preset instead of Full RPG.
 */
public final class ScreenshotTour implements FabricClientGameTest {
	private static final String SEED = "vestiges";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		boolean core = "core".equals(System.getenv("VESTIGES_TOUR"));
		ctx.getInput().resizeWindow(1920, 1080);
		welcome(ctx, core);
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 3600);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		ctx.runOnClient(mc -> System.out.println("[tour] enchant levels " + ConfigStore.server().enabled(VestigesSystem.ENCHANT_LEVELS)
			+ ", levels " + ConfigStore.server().enabled(VestigesSystem.CHARACTER)
			+ ", packs " + mc.getSingleplayerServer().getPackRepository().getSelectedIds()));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode creative @p");
		explore(ctx, server);
		if (core) {
			waypoints(ctx);
			server.runCommand("tp @p 0 " + (ground(server, 0, -6) + 1) + " -6 0 20");
			ctx.waitTicks(60);
		} else {
			placeMarkers(ctx, server);
		}
		gear(ctx, server);

		server.runCommand("gamemode survival @p");
		ctx.getInput().setCursorPos(1, 1);
		String mode = core ? "core_" : "full_";
		shot(ctx, mode + "world");
		hub(ctx, mode, core);
		if (!core) {
			worldMap(ctx);
		}
		inventory(ctx, mode);
		if (core) {
			// An operator switches levels on in the running world.
			ctx.runOnClient(mc -> dev.vestiges.client.features.ClientFeatures.send("apply", "{\"character\":true}"));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> System.out.println("[tour] after the switch: levels " + ConfigStore.server().enabled(VestigesSystem.CHARACTER)
				+ ", client sees " + dev.vestiges.client.features.ClientFeatures.on(VestigesSystem.CHARACTER)));
			shot(ctx, "core_levels_switched_on");
		}
		ctx.setScreen(() -> new dev.vestiges.client.features.FeaturesScreen(null, false));
		ctx.waitTicks(5);
		ctx.takeScreenshot(mode + "features_screen");
		ctx.setScreen(() -> null);
		System.out.println("[tour] finished");
	}

	/** The welcome page in the Create World screen, then Create. */
	private static void welcome(ClientGameTestContext ctx, boolean core) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> {
			CreateWorldScreen screen = (CreateWorldScreen) mc.gui.screen();
			screen.getUiState().setSeed(SEED);
			selectTab(screen);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("welcome_core_page");
		ctx.runOnClient(mc -> set(panel(mc), "category", VestigesSystem.Category.WORLD.label));
		ctx.waitTicks(2);
		ctx.takeScreenshot("welcome_world_page");
		ctx.runOnClient(mc -> {
			FeaturePanel panel = panel(mc);
			set(panel, "category", VestigesSystem.Category.COMFORT.label);
			set(panel, "selected", VestigesSystem.FREECAM);
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("welcome_comfort_page");
		ctx.runOnClient(mc -> set(panel(mc), "category", "FAQ"));
		ctx.waitTicks(2);
		ctx.takeScreenshot("welcome_faq");
		if (core) {
			ctx.runOnClient(mc -> panel(mc).selectPreset(VestigesSystem.Preset.CORE));
			// Leaving out the enchant levels reloads the data packs, then the screen comes back on this tab.
			ctx.waitTicks(40);
			ctx.waitFor(mc -> mc.gui.screen() instanceof CreateWorldScreen, 1200);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				FeaturePanel panel = panel(mc);
				set(panel, "category", VestigesSystem.Category.CHARACTER.label);
				set(panel, "selected", VestigesSystem.ENCHANT_LEVELS);
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("welcome_core_preset");
		}
		ctx.clickScreenButton("selectWorld.create");
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

	/** Flies over the area so the map has something to show. */
	private static void explore(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
		});
		for (int x = -240; x <= 240; x += 160) {
			for (int z = -240; z <= 240; z += 160) {
				server.runCommand("tp @p " + x + " 170 " + z);
				ctx.waitTicks(100);
			}
		}
		server.runCommand("tp @p 0 170 0");
		ctx.waitTicks(100);
	}

	private static int ground(TestServerContext server, int x, int z) {
		return server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
	}

	/** Stands on the ground, takes the item in the first hotbar slot and right-clicks the block just ahead. */
	private static void place(ClientGameTestContext ctx, TestServerContext server, String give, int x, int z) {
		server.runCommand("clear @p");
		server.runCommand(give);
		server.runCommand("tp @p " + x + " " + ground(server, x, z) + " " + z + " 0 60");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			if (mc.hitResult instanceof BlockHitResult hit) {
				InteractionResult r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
				System.out.println("[tour] " + give + " at " + hit.getBlockPos() + " holding " + mc.player.getMainHandItem() + ": " + r);
			} else {
				System.out.println("[tour] " + give + ": not looking at a block");
			}
		});
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> System.out.println("[tour] message: " + dev.vestiges.client.map.ClientMapNet.lastMessage));
		// A marker that asks for its name opens a screen: Later.
		ctx.setScreen(() -> null);
		ctx.waitTicks(5);
	}

	private static void waypoints(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			String dim = mc.level.dimension().identifier().toString();
			ClientWaypoints.add("Home mine", dim, 60, 70, -90, ClientWaypoints.PALETTE[1], "waypoint");
			ClientWaypoints.add("Big cave", dim, -130, 70, -60, ClientWaypoints.PALETTE[2], "waypoint");
			ClientWaypoints.addDeath(dim, 130, 70, 80);
		});
	}

	private static void placeMarkers(ClientGameTestContext ctx, TestServerContext server) {
		place(ctx, server, "vestiges give marker bell @p", 0, 0);
		server.runCommand("execute as @p run vestiges settlement finish");
		ctx.waitTicks(10);
		place(ctx, server, "vestiges give waystone @p", 8, 0);
		place(ctx, server, "vestiges give marker gold_block @p", -8, 0);
		place(ctx, server, "vestiges give marker banner16 @p", 90, 60);
		place(ctx, server, "vestiges give marker ward30 @p", -90, 60);
		server.runCommand("clear @p");
		waypoints(ctx);
		server.runCommand("tp @p 0 " + (ground(server, 0, -6) + 1) + " -6 0 20");
		ctx.waitTicks(60);
	}

	private static void gear(ClientGameTestContext ctx, TestServerContext server) {
		for (String[] g : new String[][] {{"armor.head", "diamond_helmet"}, {"armor.chest", "diamond_chestplate"}, {"armor.legs", "diamond_leggings"}, {"armor.feet", "diamond_boots"}}) {
			server.runCommand("item replace entity @p " + g[0] + " with minecraft:" + g[1]);
		}
		for (String g : new String[] {"cobblestone 64", "oak_planks 48", "torch 32", "bread 12", "apple 5", "ender_pearl 16", "coal 27", "iron_ingot 9", "stick 21", "shield 1"}) {
			server.runCommand("give @p minecraft:" + g);
		}
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			ExtraSlots x = InventoryHooks.slots(p);
			Item[] tools = {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.SHEARS, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.BRUSH};
			for (int i = 0; i < tools.length; i++) {
				x.setItem(ExtraSlots.TOOLS + i, new ItemStack(tools[i]));
			}
			Item[] weapons = {Items.IRON_SWORD, Items.MACE, Items.TRIDENT, Items.BOW, Items.CROSSBOW};
			for (int i = 0; i < weapons.length; i++) {
				x.setItem(ExtraSlots.WEAPONS + i, new ItemStack(weapons[i]));
			}
			x.setItem(ExtraSlots.ARROWS, new ItemStack(Items.ARROW, 64));
			Item[] belt = {Items.COMPASS, Items.CLOCK, Items.MAP, Items.SPYGLASS};
			for (int i = 0; i < belt.length; i++) {
				x.setItem(ExtraSlots.BELT + i, new ItemStack(belt[i]));
			}
			x.setItem(ExtraSlots.ELYTRA, new ItemStack(Items.ELYTRA));
			x.setItem(ExtraSlots.ENDER, new ItemStack(Items.ENDER_CHEST));
			x.setItem(ExtraSlots.TOTEM, new ItemStack(Items.TOTEM_OF_UNDYING));
		});
		ctx.waitTicks(20);
	}

	private static void hub(ClientGameTestContext ctx, String mode, boolean core) {
		ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "MAP");
		ctx.setScreen(HubScreen::new);
		ctx.waitTicks(20);
		shot(ctx, mode + "hub_map");
		ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "SETTINGS");
		ctx.setScreen(HubScreen::new);
		ctx.waitTicks(10);
		for (String category : new String[] {"Interface", "Look-at", "World"}) {
			ctx.runOnClient(mc -> {
				setStatic(HubScreen.class, "settingsCategory", category);
				((HubScreen) mc.gui.screen()).onData();
			});
			ctx.waitTicks(5);
			shot(ctx, mode + "settings_" + category.toLowerCase(java.util.Locale.ROOT).replace("-", ""));
		}
		if (!core) {
			ctx.runOnClient(mc -> {
				ClientConfigStore.get().theme = "light";
				setStatic(HubScreen.class, "settingsCategory", "Interface");
				((HubScreen) mc.gui.screen()).onData();
			});
			ctx.waitTicks(5);
			shot(ctx, "full_settings_light");
			ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "SETTLEMENTS");
			ctx.setScreen(HubScreen::new);
			ctx.waitTicks(20);
			shot(ctx, "full_settlements_light");
			ctx.runOnClient(mc -> ClientConfigStore.get().theme = "dark");
		}
		ctx.setScreen(() -> null);
	}

	private static void worldMap(ClientGameTestContext ctx) {
		ctx.setScreen(WorldMapScreen::new);
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> {
			set(mc.gui.screen(), "zoom", 3.0f);
			set(mc.gui.screen(), "cx", 10.0);
			set(mc.gui.screen(), "cz", 60.0);
			rebuild(mc.gui.screen());
		});
		ctx.waitTicks(10);
		shot(ctx, "full_shadows_zoom");
		ctx.runOnClient(mc -> {
			set(mc.gui.screen(), "zoom", 1.0f);
			set(mc.gui.screen(), "cx", 0.0);
			set(mc.gui.screen(), "cz", 0.0);
			rebuild(mc.gui.screen());
		});
		ctx.waitTicks(10);
		shot(ctx, "full_shadows");
		ctx.setScreen(() -> null);
	}

	private static void inventory(ClientGameTestContext ctx, String mode) {
		ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(10);
		shot(ctx, mode + "inventory");
		ctx.setScreen(() -> null);
	}

	private static void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTicks(2);
		ctx.takeScreenshot(name);
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

	private static void setStatic(Class<?> owner, String field, Object value) {
		try {
			Field f = owner.getDeclaredField(field);
			f.setAccessible(true);
			f.set(null, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void rebuild(Screen screen) {
		try {
			Method m = Screen.class.getDeclaredMethod("rebuildWidgets");
			m.setAccessible(true);
			m.invoke(screen);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
