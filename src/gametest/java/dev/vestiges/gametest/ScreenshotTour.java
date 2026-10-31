package dev.vestiges.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.codex.ClientCodex;
import dev.vestiges.client.codex.CodexScreen;
import dev.vestiges.client.features.FeaturePanel;
import dev.vestiges.client.hub.HubScreen;
import dev.vestiges.client.map.ClientWaypoints;
import dev.vestiges.client.map.WorldMapScreen;
import dev.vestiges.client.settlement.FeatScreen;
import dev.vestiges.client.settlement.NameScreen;
import dev.vestiges.client.tree.ClientTree;
import dev.vestiges.client.tree.TreeScreen;
import dev.vestiges.client.ui.TabButton;
import dev.vestiges.client.waystone.WaystoneTerminalScreen;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.tree.TreeDefs;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * the real Create World screen. Set VESTIGES_TOUR=off to create it with every feature switched off instead of Full RPG.
 */
public final class ScreenshotTour implements FabricClientGameTest {
	private static final String SEED = "vestiges";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		boolean off = "off".equals(System.getenv("VESTIGES_TOUR"));
		ctx.getInput().resizeWindow(1920, 1080);
		welcome(ctx, off);
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 3600);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		ctx.runOnClient(mc -> System.out.println("[tour] enchant levels " + ConfigStore.server().enabled(VestigesSystem.ENCHANT_LEVELS)
			+ ", levels " + ConfigStore.server().enabled(VestigesSystem.CHARACTER)
			+ ", packs " + mc.getSingleplayerServer().getPackRepository().getSelectedIds()));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode creative @p");
		explore(ctx, server);
		if (off) {
			waypoints(ctx);
			server.runCommand("tp @p 0 " + (ground(server, 0, -6) + 1) + " -6 0 20");
			ctx.waitTicks(60);
		} else {
			placeMarkers(ctx, server);
		}
		gear(ctx, server);

		server.runCommand("gamemode survival @p");
		ctx.getInput().setCursorPos(1, 1);
		String mode = off ? "off_" : "full_";
		shot(ctx, mode + "world");
		hub(ctx, mode, off);
		if (!off) {
			worldMap(ctx);
			quests(ctx, server);
		}
		inventory(ctx, mode);
		if (!off) {
			everyScreen(ctx, server);
		}
		if (off) {
			// An operator switches levels on in the running world.
			ctx.runOnClient(mc -> dev.vestiges.client.features.ClientFeatures.send("apply", "{\"character\":true}"));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> System.out.println("[tour] after the switch: levels " + ConfigStore.server().enabled(VestigesSystem.CHARACTER)
				+ ", client sees " + dev.vestiges.client.features.ClientFeatures.on(VestigesSystem.CHARACTER)));
			shot(ctx, "off_levels_switched_on");
		}
		ctx.setScreen(() -> new dev.vestiges.client.features.FeaturesScreen(null, false));
		ctx.waitTicks(5);
		ctx.takeScreenshot(mode + "features_screen");
		ctx.setScreen(() -> null);
		System.out.println("[tour] finished");
	}

	/** The welcome page in the Create World screen, then Create. */
	private static void welcome(ClientGameTestContext ctx, boolean off) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> {
			CreateWorldScreen screen = (CreateWorldScreen) mc.gui.screen();
			screen.getUiState().setSeed(SEED);
			selectTab(screen);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("welcome_always_on_page");
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
		if (off) {
			ctx.runOnClient(mc -> allOff(panel(mc)));
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
			ctx.takeScreenshot("welcome_all_off");
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

	/** The quest screens: a running quest on the tracker and in the tab, then the offer page and the choice page. */
	private static void quests(ClientGameTestContext ctx, TestServerContext server) {
		ctx.setScreen(() -> null);
		server.runCommand("execute as @p run vestiges quest start side_farmer");
		server.runCommand("execute as @p run vestiges quest start test_choice");
		server.runCommand("execute as @p run vestiges quest start your_first_point");
		ctx.waitTicks(60);
		shot(ctx, "full_quest_tracker");
		ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "QUESTS");
		ctx.setScreen(HubScreen::new);
		ctx.waitTicks(20);
		shot(ctx, "full_quests_active");
		com.google.gson.JsonObject offer = com.google.gson.JsonParser.parseString("{\"id\":\"side_farmer\",\"name\":\"A Poor Harvest\",\"giver\":\"Farmer\",\"intro\":\"Half the field has rotted, and something walks the rows at night.\",\"steps\":[\"Bring 24 Wheat.\",\"Kill 5 monsters.\"],\"reward\":\"50 XP, 6 emeralds\"}").getAsJsonObject();
		ctx.setScreen(() -> dev.vestiges.client.quest.QuestBookScreen.offer(offer));
		ctx.waitTicks(5);
		shot(ctx, "full_quest_offer");
		com.google.gson.JsonArray choices = com.google.gson.JsonParser.parseString("[{\"label\":\"Side with the Bright Path\",\"effects\":\"the Bright Path +20, the Promise -10, the Vitera -10\"},{\"label\":\"Side with the Vitera\",\"effects\":\"the Vitera +20\"}]").getAsJsonArray();
		ctx.setScreen(() -> dev.vestiges.client.quest.QuestBookScreen.choice("test_choice", "Test choice", "Test", "Pick one to test standing.", choices));
		ctx.waitTicks(5);
		shot(ctx, "full_quest_choice");
		// The real flow on the server: hand in, kill, finish with the reward, and a choice moving standing.
		server.runCommand("clear @p");
		for (String item : new String[] {"wheat", "carrot", "potato", "beetroot"}) {
			server.runCommand("give @p minecraft:" + item + " 40");
		}
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> dev.vestiges.client.quest.ClientQuests.send("hand_in", "side_farmer", ""));
		ctx.runOnClient(mc -> dev.vestiges.client.quest.ClientQuests.send("choose", "test_choice", "0"));
		ctx.waitTicks(20);
		server.runCommand("execute at @p run summon minecraft:zombie ~2 ~ ~ {Health:1f}");
		printQuests(ctx, server, "after hand-in and choice");
		for (int i = 0; i < 5; i++) {
			server.runCommand("execute at @p run summon minecraft:zombie ~2 ~ ~");
			server.runCommand("execute as @p run damage @e[type=minecraft:zombie,limit=1,sort=nearest] 1000 minecraft:player_attack by @p");
			ctx.waitTicks(10);
		}
		ctx.waitTicks(60);
		printQuests(ctx, server, "after five kills");
		ctx.setScreen(() -> null);
	}

	private static void printQuests(ClientGameTestContext ctx, TestServerContext server, String when) {
		ctx.waitTicks(5);
		String state = server.computeOnServer(s -> {
			net.minecraft.server.level.ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			dev.vestiges.quest.QuestState st = dev.vestiges.quest.QuestService.get(p);
			return "running " + st.active().keySet() + ", finished " + st.finished() + ", standing " + st.standing() + ", emeralds " + dev.vestiges.server.Emeralds.count(p);
		});
		System.out.println("[tour] quests " + when + ": " + state);
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

	private static void hub(ClientGameTestContext ctx, String mode, boolean off) {
		ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "MAP");
		ctx.setScreen(HubScreen::new);
		ctx.waitTicks(20);
		shot(ctx, mode + "hub_map");
		if (!off) {
			ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = "QUESTS");
			ctx.setScreen(HubScreen::new);
			ctx.waitTicks(20);
			shot(ctx, mode + "hub_quests");
		}
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
		if (!off) {
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

	/** Every screen in the dark and light themes, in a small window (427 x 240) and at GUI scale 2 (960 x 540), to check layouts and colours. */
	private static void everyScreen(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("vestiges level set @p 25");
		server.runCommand("vestiges feat grant @p first_waystone");
		server.runCommand("vestiges feat grant @p founder");
		ctx.runOnClient(mc -> ClientCodex.request());
		ctx.waitTicks(20);
		List<ClientCodex.Entry> entries = ctx.computeOnClient(mc -> List.copyOf(ClientCodex.entries()));
		Map<String, Integer> perSection = new HashMap<>();
		for (ClientCodex.Entry e : entries) {
			int n = perSection.merge(e.section(), 1, Integer::sum);
			if (n <= 9) {
				server.runCommand("vestiges codex set @p \"" + e.id() + "\" " + (n % 3 == 0 ? 4 : n % 3));
			}
		}
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> {
			List<TreeDefs.Node> starts = ClientTree.wheel("fight").nodes().stream().filter(TreeDefs.Node::isStart).toList();
			ClientTree.send("start", "fight", "", starts.get(0).start().orElse(0));
		});
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			TreeDefs.Wheel wheel = ClientTree.wheel("fight");
			String start = wheel.nodes().stream().filter(TreeDefs.Node::isStart).findFirst().orElseThrow().id();
			for (List<String> edge : wheel.edges()) {
				if (edge.contains(start)) {
					ClientTree.send("take", "fight", edge.get(0).equals(start) ? edge.get(1) : edge.get(0), -1);
				}
			}
		});
		ctx.waitTicks(10);
		screens(ctx, "dark");
		ctx.runOnClient(mc -> ClientConfigStore.get().theme = "light");
		screens(ctx, "light");
		ctx.runOnClient(mc -> ClientConfigStore.get().theme = "dark");
		ctx.getInput().resizeWindow(854, 480);
		ctx.waitTicks(5);
		screens(ctx, "small");
		ctx.runOnClient(mc -> mc.options.guiScale().set(2));
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.waitTicks(5);
		screens(ctx, "wide");
		ctx.runOnClient(mc -> mc.options.guiScale().set(0));
		ctx.getInput().resizeWindow(1919, 1080);
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.waitTicks(5);
	}

	private static void screens(ClientGameTestContext ctx, String tag) {
		for (String tab : new String[] {"CHARACTER", "CODEX", "QUESTS", "VILLAGERS", "SETTLEMENTS", "FEATS", "MAP", "SETTINGS"}) {
			ctx.runOnClient(mc -> ClientConfigStore.get().hubTab = tab);
			ctx.setScreen(HubScreen::new);
			ctx.waitTicks(15);
			shot(ctx, tag + "_hub_" + tab.toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> clearViews());
		for (String tab : new String[] {"FIGHT", "CRAFT", "ASC"}) {
			ctx.runOnClient(mc -> setStaticEnum(TreeScreen.class, "lastTab", tab));
			ctx.setScreen(TreeScreen::new);
			ctx.waitTicks(15);
			shot(ctx, tag + "_tree_" + tab.toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> setStaticEnum(TreeScreen.class, "lastTab", "CRAFT"));
		ctx.setScreen(TreeScreen::new);
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> treePopup(mc.gui.screen()));
		ctx.waitTicks(3);
		shot(ctx, tag + "_tree_popup");
		String[][] sections = {{"BLOCKS", "blocks"}, {"MOBS", "mobs"}, {"PLACES", "biomes"}, {"ITEMS", "items"}};
		for (String[] section : sections) {
			ctx.runOnClient(mc -> {
				setStaticEnum(CodexScreen.class, "section", section[0]);
				ClientCodex.entries().stream().filter(e -> e.section().equals(section[1]) && e.known() && e.need() > 0).findFirst()
					.ifPresent(e -> setStatic(CodexScreen.class, "selected", e.id()));
			});
			ctx.setScreen(CodexScreen::new);
			ctx.waitTicks(15);
			shot(ctx, tag + "_codex_" + section[0].toLowerCase(java.util.Locale.ROOT));
		}
		ctx.setScreen(FeatScreen::new);
		ctx.waitTicks(15);
		shot(ctx, tag + "_feats_tree");
		ctx.setScreen(() -> new WaystoneTerminalScreen(sampleWaystones()));
		ctx.waitTicks(5);
		shot(ctx, tag + "_waystone");
		ctx.setScreen(() -> new NameScreen("settlement", "tour", "Name your settlement"));
		ctx.waitTicks(5);
		shot(ctx, tag + "_name");
		ctx.setScreen(WorldMapScreen::new);
		ctx.waitTicks(15);
		shot(ctx, tag + "_full_map");
		ctx.runOnClient(mc -> {
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof TabButton row && row.getMessage().getString().equals("Home mine")) {
					row.onClick(null, false);
					break;
				}
			}
		});
		ctx.waitTicks(3);
		shot(ctx, tag + "_full_map_selected");
		ctx.setScreen(() -> new dev.vestiges.client.features.FeaturesScreen(null, false));
		ctx.waitTicks(5);
		shot(ctx, tag + "_features");
		ctx.setScreen(() -> null);
		ctx.waitTicks(2);
	}

	/** Enough waystones for two pages, some too far to pay for and one in another dimension. */
	private static JsonObject sampleWaystones() {
		JsonArray list = new JsonArray();
		for (int i = 1; i <= 8; i++) {
			JsonObject w = new JsonObject();
			w.addProperty("id", i);
			w.addProperty("name", i == 3 ? "The very long road to the northern harbour" : "Waystone " + i);
			w.addProperty("distance", i * 120);
			w.addProperty("cost", i);
			w.addProperty("sameDimension", i != 8);
			w.addProperty("mine", i != 2);
			w.addProperty("owner", "Friend");
			list.add(w);
		}
		JsonObject data = new JsonObject();
		data.add("waystones", list);
		data.addProperty("inCombat", false);
		data.addProperty("free", false);
		data.addProperty("emeralds", 4);
		data.addProperty("from", 0);
		data.addProperty("fromMine", true);
		data.addProperty("fromName", "Home");
		return data;
	}

	/** Clicks a starting point on the untouched Craft wheel, which asks whether to start there. */
	private static void treePopup(Screen screen) {
		try {
			String id = ClientTree.wheel("craft").nodes().stream().filter(TreeDefs.Node::isStart).findFirst().orElseThrow().id();
			Method m = TreeScreen.class.getDeclaredMethod("leftClickNode", String.class);
			m.setAccessible(true);
			m.invoke(screen, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The wheels remember their zoom, which depends on the window size. */
	private static void clearViews() {
		try {
			Field f = TreeScreen.class.getDeclaredField("VIEWS");
			f.setAccessible(true);
			((Map<?, ?>) f.get(null)).clear();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTicks(2);
		ctx.takeScreenshot(name);
	}

	/** The Custom way to a world with only the inventory and map. */
	private static void allOff(FeaturePanel panel) {
		try {
			Field config = FeaturePanel.class.getDeclaredField("config");
			config.setAccessible(true);
			for (VestigesSystem s : VestigesSystem.values()) {
				((ServerConfig) config.get(panel)).set(s, false);
			}
			Field onChange = FeaturePanel.class.getDeclaredField("onChange");
			onChange.setAccessible(true);
			((Runnable) onChange.get(panel)).run();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setStaticEnum(Class<?> owner, String field, String name) {
		try {
			Field f = owner.getDeclaredField(field);
			f.setAccessible(true);
			f.set(null, Enum.valueOf((Class) f.getType(), name));
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
