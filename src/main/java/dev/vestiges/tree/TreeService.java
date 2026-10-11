package dev.vestiges.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.level.LevelService;
import dev.vestiges.level.PlayerLevel;
import dev.vestiges.net.TreePayloads;
import dev.vestiges.server.Emeralds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The passive trees on the server: the player's choices (an attachment), every rule for spending, refunding and switching points, the
 * ascendancy and its checklists, and the messages to the client. Every action is checked here again, whatever the client shows.
 */
public final class TreeService {
	public static final AttachmentType<PlayerTree> TREE = AttachmentRegistry.create(Vestiges.id("tree"),
		builder -> builder.persistent(PlayerTree.CODEC).copyOnDeath());
	/** The agreed order of the ascendancies on screen. */
	private static final List<String> ASC_ORDER = List.of("harvester", "woodsman", "delver", "wayfarer", "lancer");
	/** Level needed for the first and the second ascension. */
	public static final int FIRST_ASCENSION_LEVEL = 20;
	public static final int SECOND_ASCENSION_LEVEL = 60;

	/** Per wheel: the wheel object it was built from, its node index and its links (rebuilt when /reload swaps the wheel). */
	private record Index(TreeDefs.Wheel wheel, Map<String, TreeDefs.Node> nodes, Map<String, Set<String>> adjacency) {
	}

	private static final Map<String, Index> INDEX = new HashMap<>();

	private TreeService() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(TreePayloads.Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			sendDefs(handler.player);
			refresh(handler.player);
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TreeTicker.forget(handler.player);
			StatEngine.invalidate(handler.player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			TreeTicker.forget(oldPlayer);
			refresh(newPlayer);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			INDEX.clear();
			StatEngine.invalidateAll();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				sendDefs(p);
				refresh(p);
			}
		});
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.PASSIVE_TREE);
	}

	public static PlayerTree get(ServerPlayer player) {
		PlayerTree t = player.getAttached(TREE);
		return t != null ? t : PlayerTree.EMPTY;
	}

	/** Saves a new tree, re-applies its stats and tells the client. */
	public static void set(ServerPlayer player, PlayerTree tree, String message) {
		player.setAttached(TREE, tree);
		refresh(player, message);
	}

	public static void refresh(ServerPlayer player) {
		refresh(player, "");
	}

	public static void refresh(ServerPlayer player, String message) {
		StatEngine.invalidate(player);
		StatEngine.apply(player);
		sendState(player, message);
	}

	// ---- content ---------------------------------------------------------------------------------------------------

	public static TreeDefs.Wheel wheel(String id) {
		for (TreeDefs.Wheel w : VestigesData.TREES.all().values()) {
			if (w.wheel().equals(id)) {
				return w;
			}
		}
		return null;
	}

	public static TreeDefs.Ascendancy ascendancy(String id) {
		for (TreeDefs.Ascendancy a : VestigesData.ASCENDANCIES.all().values()) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}

	public static List<TreeDefs.Ascendancy> ascendancies() {
		List<TreeDefs.Ascendancy> list = new ArrayList<>(VestigesData.ASCENDANCIES.all().values());
		list.sort(Comparator.comparingInt(a -> {
			int i = ASC_ORDER.indexOf(a.id());
			return i < 0 ? ASC_ORDER.size() : i;
		}));
		return list;
	}

	private static Index index(String wheelId) {
		TreeDefs.Wheel w = wheel(wheelId);
		if (w == null) {
			return null;
		}
		Index idx = INDEX.get(wheelId);
		if (idx == null || idx.wheel() != w) {
			Map<String, TreeDefs.Node> nodes = new HashMap<>();
			for (TreeDefs.Node n : w.nodes()) {
				nodes.put(n.id(), n);
			}
			idx = new Index(w, nodes, TreeRules.adjacency(w.edges()));
			INDEX.put(wheelId, idx);
		}
		return idx;
	}

	/** The id of the start node the player picked on this wheel, or null. */
	static String startId(Index idx, PlayerTree.WheelState state) {
		if (state.start() < 0) {
			return null;
		}
		for (TreeDefs.Node n : idx.wheel().nodes()) {
			if (n.start().isPresent() && n.start().get() == state.start()) {
				return n.id();
			}
		}
		return null;
	}

	// ---- points ----------------------------------------------------------------------------------------------------

	public static int points(ServerPlayer player, String wheelId) {
		int level = LevelService.get(player).level();
		return "craft".equals(wheelId) ? TreeRules.craftPoints(level, dev.vestiges.codex.CodexService.bonusPoints(player)) : TreeRules.fightPoints(level);
	}

	/** Points spent on a wheel: taken nodes that still exist in the data (the picked start is free). */
	public static int spent(String wheelId, PlayerTree.WheelState state) {
		Index idx = index(wheelId);
		if (idx == null) {
			return 0;
		}
		int n = 0;
		for (String id : state.taken()) {
			if (idx.nodes().containsKey(id)) {
				n++;
			}
		}
		return n;
	}

	public static int ascPoints(PlayerTree tree) {
		return 2 * Math.max(0, Math.min(2, tree.ascensions()));
	}

	public static int refundCost(ServerPlayer player) {
		return TreeRules.refundCost(LevelService.get(player).level());
	}

	// ---- actions ---------------------------------------------------------------------------------------------------

	static void handle(ServerPlayer player, TreePayloads.Action a) {
		if (!enabled()) {
			sendState(player, "The passive trees are switched off on this server.");
			return;
		}
		// Every action answers by itself: a change sends the new state (set), a refusal sends the state with the reason (fail).
		switch (a.op()) {
			case "start" -> start(player, a.wheel(), a.value());
			case "take" -> take(player, a.wheel(), a.node(), a.value());
			case "refund" -> refund(player, a.wheel(), a.node());
			case "switch" -> switchChoice(player, a.wheel(), a.node(), a.value());
			case "asc_choose" -> chooseAscendancy(player, a.node());
			case "asc_second" -> secondAscension(player);
			case "asc_take" -> ascTake(player, a.node());
			case "asc_refund" -> ascRefund(player, a.node());
			case "utility" -> toggleUtility(player);
			default -> sendState(player, "");
		}
	}

	private static String fail(ServerPlayer player, String message) {
		sendState(player, "!" + message);
		return "";
	}

	private static String start(ServerPlayer player, String wheelId, int startIndex) {
		Index idx = index(wheelId);
		if (idx == null) {
			return fail(player, "That wheel is missing from the server's data.");
		}
		PlayerTree tree = get(player);
		PlayerTree.WheelState state = tree.wheel(wheelId);
		if (spent(wheelId, state) > 0) {
			return fail(player, "Refund your nodes on this wheel before you change your start.");
		}
		TreeDefs.Node node = null;
		for (TreeDefs.Node n : idx.wheel().nodes()) {
			if (n.start().isPresent() && n.start().get() == startIndex) {
				node = n;
			}
		}
		if (node == null) {
			return fail(player, "That is not a starting point.");
		}
		set(player, tree.withWheel(wheelId, new PlayerTree.WheelState(startIndex, Set.of(), Map.of())), "You start at " + node.name() + ".");
		return null;
	}

	private static String take(ServerPlayer player, String wheelId, String nodeId, int choice) {
		Index idx = index(wheelId);
		if (idx == null) {
			return fail(player, "That wheel is missing from the server's data.");
		}
		PlayerTree tree = get(player);
		PlayerTree.WheelState state = tree.wheel(wheelId);
		String startId = startId(idx, state);
		if (startId == null) {
			return fail(player, "Pick a starting point first.");
		}
		TreeDefs.Node node = idx.nodes().get(nodeId);
		if (node == null) {
			return fail(player, "That node no longer exists.");
		}
		if (nodeId.equals(startId) || state.taken().contains(nodeId)) {
			return fail(player, "You already have " + node.name() + ".");
		}
		if (spent(wheelId, state) >= points(player, wheelId)) {
			return fail(player, "craft".equals(wheelId) ? "No Craft points left: you get 1 every 2 levels." : "No Fight points left: you get 1 per level.");
		}
		if (node.rival().isPresent() && state.taken().contains(node.rival().get())) {
			TreeDefs.Node rival = idx.nodes().get(node.rival().get());
			return fail(player, "Locked: you took its rival, " + (rival != null ? rival.name() : "the other keystone") + ".");
		}
		if (!TreeRules.canReach(nodeId, startId, state.taken(), idx.adjacency())) {
			return fail(player, "Not connected yet: take a node next to it first.");
		}
		Integer pick = null;
		if (!node.choices().isEmpty()) {
			if (choice < 0 || choice >= node.choices().size()) {
				return fail(player, "Pick one of the options.");
			}
			pick = choice;
		}
		String name = pick != null ? node.choices().get(pick).name() + " (" + node.name() + ")" : node.name();
		set(player, tree.withWheel(wheelId, state.take(nodeId, pick)), "Took " + name + ".");
		return null;
	}

	private static String refund(ServerPlayer player, String wheelId, String nodeId) {
		Index idx = index(wheelId);
		if (idx == null) {
			return fail(player, "That wheel is missing from the server's data.");
		}
		PlayerTree tree = get(player);
		PlayerTree.WheelState state = tree.wheel(wheelId);
		if (!state.taken().contains(nodeId)) {
			return fail(player, "You don't have that node.");
		}
		String startId = startId(idx, state);
		if (startId != null && !TreeRules.stillConnected(nodeId, startId, state.taken(), idx.adjacency())) {
			return fail(player, "Other nodes hang on this one: refund the outer ones first.");
		}
		int cost = refundCost(player);
		if (!Emeralds.payWithBank(player, cost)) {
			return fail(player, "A refund costs " + cost + " emerald" + (cost == 1 ? "" : "s") + " (you have " + Emeralds.countWithBank(player) + ").");
		}
		TreeDefs.Node node = idx.nodes().get(nodeId);
		set(player, tree.withWheel(wheelId, state.drop(nodeId)), "Refunded " + (node != null ? node.name() : nodeId) + paid(player, cost) + ".");
		return null;
	}

	private static String switchChoice(ServerPlayer player, String wheelId, String nodeId, int choice) {
		Index idx = index(wheelId);
		if (idx == null) {
			return fail(player, "That wheel is missing from the server's data.");
		}
		PlayerTree tree = get(player);
		PlayerTree.WheelState state = tree.wheel(wheelId);
		TreeDefs.Node node = idx.nodes().get(nodeId);
		if (node == null || !state.taken().contains(nodeId) || node.choices().isEmpty()) {
			return fail(player, "You can only switch a choice node you have.");
		}
		if (choice < 0 || choice >= node.choices().size() || choice == state.choices().getOrDefault(nodeId, 0)) {
			return fail(player, "Pick the other option.");
		}
		int cost = refundCost(player);
		if (!Emeralds.payWithBank(player, cost)) {
			return fail(player, "Switching costs " + cost + " emerald" + (cost == 1 ? "" : "s") + " (you have " + Emeralds.countWithBank(player) + ").");
		}
		set(player, tree.withWheel(wheelId, state.choose(nodeId, choice)), "Switched to " + node.choices().get(choice).name() + paid(player, cost) + ".");
		return null;
	}

	private static String paid(ServerPlayer player, int cost) {
		return Emeralds.free(player) ? "" : " for " + cost + " emerald" + (cost == 1 ? "" : "s");
	}

	// ---- ascendancy ------------------------------------------------------------------------------------------------

	/** One line of an ascension checklist. {@code later} = the system behind it is not in the game yet, so the step is skipped. */
	public record Check(String text, long have, long goal, String later) {
		public boolean done() {
			return later != null || have >= goal;
		}
	}

	public static List<Check> firstChecklist(ServerPlayer player, PlayerTree tree) {
		PlayerLevel l = LevelService.get(player);
		List<Check> out = new ArrayList<>();
		out.add(new Check("Reach level " + FIRST_ASCENSION_LEVEL, l.level(), FIRST_ASCENSION_LEVEL, null));
		out.add(new Check("Kill 100 hostile mobs", tree.counter("kills"), 100, null));
		out.add(new Check("Kill 1 elite", tree.counter("elites"), 1, null));
		out.add(new Check("Mine 64 ores", tree.counter("ores"), 64, null));
		out.add(new Check("Reach the deepslate layer (below y=0)", tree.counter("deepslate"), 1, null));
		out.add(new Check("Find 25 Codex entries", dev.vestiges.codex.CodexService.discovered(player), 25, null));
		return out;
	}

	public static List<Check> secondChecklist(ServerPlayer player, PlayerTree tree, TreeDefs.Ascendancy asc) {
		PlayerLevel l = LevelService.get(player);
		List<Check> out = new ArrayList<>();
		out.add(new Check("Reach level " + SECOND_ASCENSION_LEVEL, l.level(), SECOND_ASCENSION_LEVEL, null));
		if (asc != null) {
			for (TreeDefs.Step s : asc.second()) {
				String text = s.goal() + " " + s.text();
				switch (s.counter()) {
					case "elites" -> out.add(new Check(text, tree.counter("elites"), s.goal(), null));
					case "biomes" -> out.add(new Check(text, l.firsts().stream().filter(k -> k.startsWith("biome:")).count(), s.goal(), null));
					default -> out.add(new Check(text, tree.counter(s.counter()), s.goal(), null));
				}
			}
		}
		return out;
	}

	private static boolean allDone(List<Check> checks) {
		return checks.stream().allMatch(Check::done);
	}

	private static String chooseAscendancy(ServerPlayer player, String ascId) {
		PlayerTree tree = get(player);
		if (!tree.ascendancy().isEmpty()) {
			return fail(player, "You already ascended. The Respec Tome (from dragon 12) lets you change your ascendancy.");
		}
		TreeDefs.Ascendancy asc = ascendancy(ascId);
		if (asc == null) {
			return fail(player, "That ascendancy is missing from the server's data.");
		}
		if (!allDone(firstChecklist(player, tree))) {
			return fail(player, "Finish the checklist first.");
		}
		set(player, tree.withAscension(asc.id(), 1, Set.of()), "You ascended: you are a " + asc.name() + ". +2 ascendancy points.");
		player.sendSystemMessage(Component.literal("You ascended as a " + asc.name() + " (" + asc.kit() + ").").withStyle(ChatFormatting.GOLD));
		return null;
	}

	private static String secondAscension(ServerPlayer player) {
		PlayerTree tree = get(player);
		TreeDefs.Ascendancy asc = ascendancy(tree.ascendancy());
		if (asc == null || tree.ascensions() != 1) {
			return fail(player, tree.ascensions() >= 2 ? "You already ascended twice." : "Ascend a first time before.");
		}
		if (!allDone(secondChecklist(player, tree, asc))) {
			return fail(player, "Finish the checklist first.");
		}
		set(player, tree.withAscension(asc.id(), 2, tree.ascTaken()), "Second ascension: +2 ascendancy points.");
		player.sendSystemMessage(Component.literal("Your second ascension as a " + asc.name() + " is done.").withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** Branch and position of an ascendancy node, or null. */
	private static int[] ascPosition(TreeDefs.Ascendancy asc, String nodeId) {
		for (int b = 0; b < asc.branches().size(); b++) {
			List<TreeDefs.AscNode> nodes = asc.branches().get(b).nodes();
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).id().equals(nodeId)) {
					return new int[] {b, i};
				}
			}
		}
		return null;
	}

	private static int ascSpent(PlayerTree tree, TreeDefs.Ascendancy asc) {
		int n = 0;
		for (String id : tree.ascTaken()) {
			if (ascPosition(asc, id) != null) {
				n++;
			}
		}
		return n;
	}

	private static String ascTake(ServerPlayer player, String nodeId) {
		PlayerTree tree = get(player);
		TreeDefs.Ascendancy asc = ascendancy(tree.ascendancy());
		if (asc == null) {
			return fail(player, "Choose an ascendancy first.");
		}
		int[] pos = ascPosition(asc, nodeId);
		if (pos == null || tree.ascTaken().contains(nodeId)) {
			return fail(player, pos == null ? "That node is not in your ascendancy." : "You already have it.");
		}
		if (ascSpent(tree, asc) >= ascPoints(tree)) {
			return fail(player, tree.ascensions() < 2 ? "No ascendancy points left: the second ascension gives 2 more." : "No ascendancy points left.");
		}
		List<TreeDefs.AscNode> branch = asc.branches().get(pos[0]).nodes();
		if (pos[1] > 0 && !tree.ascTaken().contains(branch.get(pos[1] - 1).id())) {
			return fail(player, "Take the node before it in this branch first.");
		}
		Set<String> taken = new HashSet<>(tree.ascTaken());
		taken.add(nodeId);
		set(player, tree.withAscension(tree.ascendancy(), tree.ascensions(), taken), "Took " + branch.get(pos[1]).name() + ".");
		return null;
	}

	private static String ascRefund(ServerPlayer player, String nodeId) {
		PlayerTree tree = get(player);
		TreeDefs.Ascendancy asc = ascendancy(tree.ascendancy());
		int[] pos = asc == null ? null : ascPosition(asc, nodeId);
		if (pos == null || !tree.ascTaken().contains(nodeId)) {
			return fail(player, "You don't have that node.");
		}
		List<TreeDefs.AscNode> branch = asc.branches().get(pos[0]).nodes();
		if (pos[1] + 1 < branch.size() && tree.ascTaken().contains(branch.get(pos[1] + 1).id())) {
			return fail(player, "Refund the next node in this branch first.");
		}
		int cost = refundCost(player);
		if (!Emeralds.payWithBank(player, cost)) {
			return fail(player, "A refund costs " + cost + " emerald" + (cost == 1 ? "" : "s") + " (you have " + Emeralds.countWithBank(player) + ").");
		}
		Set<String> taken = new HashSet<>(tree.ascTaken());
		taken.remove(nodeId);
		set(player, tree.withAscension(tree.ascendancy(), tree.ascensions(), taken), "Refunded " + branch.get(pos[1]).name() + paid(player, cost) + ".");
		return null;
	}

	private static String toggleUtility(ServerPlayer player) {
		PlayerTree tree = get(player);
		boolean on = !tree.utilityOn();
		player.setAttached(TREE, tree.withUtility(on));
		player.sendOverlayMessage(Component.literal("Utility powers: " + (on ? "On" : "Off")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		sendState(player, "");
		return null;
	}

	/** Admin: back to an empty tree (no start, no ascendancy). Counters stay. */
	public static void reset(ServerPlayer player) {
		PlayerTree old = get(player);
		set(player, new PlayerTree(PlayerTree.WheelState.EMPTY, PlayerTree.WheelState.EMPTY, "", 0, Set.of(), old.counters(), old.utilityOn()),
			"Your passive trees were reset.");
	}

	public static void setCounter(ServerPlayer player, String key, long value) {
		player.setAttached(TREE, get(player).setCounter(key, value));
		sendState(player, "");
	}

	// ---- client sync -----------------------------------------------------------------------------------------------

	public static void sendDefs(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, TreePayloads.Defs.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new TreePayloads.Defs("clear", ""));
		for (TreeDefs.Wheel w : VestigesData.TREES.all().values()) {
			TreeDefs.Wheel.CODEC.encodeStart(JsonOps.INSTANCE, w).result()
				.ifPresent(json -> ServerPlayNetworking.send(player, new TreePayloads.Defs("wheel", json.toString())));
		}
		JsonArray ascs = new JsonArray();
		for (TreeDefs.Ascendancy a : ascendancies()) {
			TreeDefs.Ascendancy.CODEC.encodeStart(JsonOps.INSTANCE, a).result().ifPresent(ascs::add);
		}
		ServerPlayNetworking.send(player, new TreePayloads.Defs("ascendancies", ascs.toString()));
	}

	/** The full tree state for the screen. A message starting with "!" is an error. */
	public static void sendState(ServerPlayer player, String message) {
		if (!ServerPlayNetworking.canSend(player, TreePayloads.State.TYPE)) {
			return;
		}
		PlayerTree tree = get(player);
		PlayerLevel l = LevelService.get(player);
		JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled());
		root.addProperty("gear", dev.vestiges.gear.GearService.enabled() && LevelService.enabled());
		root.addProperty("inventory", dev.vestiges.inventory.InventoryService.enabled());
		root.addProperty("level", l.level());
		root.addProperty("refundCost", refundCost(player));
		root.addProperty("emeralds", Emeralds.countWithBank(player));
		root.addProperty("free", Emeralds.free(player));
		root.addProperty("message", message);
		JsonElement treeJson = PlayerTree.CODEC.encodeStart(JsonOps.INSTANCE, tree).result().orElseGet(JsonObject::new);
		root.add("tree", treeJson);
		JsonObject pts = new JsonObject();
		for (String w : List.of("fight", "craft")) {
			JsonObject o = new JsonObject();
			o.addProperty("total", points(player, w));
			o.addProperty("spent", spent(w, tree.wheel(w)));
			pts.add(w, o);
		}
		root.add("points", pts);
		TreeDefs.Ascendancy asc = ascendancy(tree.ascendancy());
		JsonObject ascJson = new JsonObject();
		ascJson.addProperty("total", ascPoints(tree));
		ascJson.addProperty("spent", asc == null ? 0 : ascSpent(tree, asc));
		ascJson.add("first", checks(firstChecklist(player, tree)));
		ascJson.add("second", checks(secondChecklist(player, tree, asc)));
		ascJson.addProperty("secondLevel", SECOND_ASCENSION_LEVEL);
		root.add("asc", ascJson);
		ServerPlayNetworking.send(player, new TreePayloads.State(root.toString()));
	}

	private static JsonArray checks(List<Check> list) {
		JsonArray arr = new JsonArray();
		for (Check c : list) {
			JsonObject o = new JsonObject();
			o.addProperty("text", c.text());
			o.addProperty("have", c.have());
			o.addProperty("goal", c.goal());
			o.addProperty("done", c.done());
			if (c.later() != null) {
				o.addProperty("later", c.later());
			}
			arr.add(o);
		}
		return arr;
	}
}
