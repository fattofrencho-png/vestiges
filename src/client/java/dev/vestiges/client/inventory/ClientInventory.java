package dev.vestiges.client.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.client.tree.ClientTree;
import dev.vestiges.inventory.ExtraHolder;
import dev.vestiges.inventory.ExtraSlots;
import dev.vestiges.inventory.InventoryHooks;
import dev.vestiges.inventory.Selection;
import dev.vestiges.net.InventoryPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client side of the M2 inventory: the extra hotbar slots (belt on the left, Tool and Weapon on the right), scrolling into them, Ctrl +
 * Scroll inside a rack, Alt + Scroll for the offhand rack, auto-tool, the Bank total, page names and other players' elytra.
 */
public final class ClientInventory {
	private static final Map<Integer, ItemStack> ELYTRA = new HashMap<>();
	private static List<String> pageNames = List.of();
	private static int unlockedPages = 1;
	private static int totalPages = 25;
	public static KeyMapping zoomKey;
	public static KeyMapping toolKey;
	public static KeyMapping weaponKey;

	private ClientInventory() {
	}

	public static void register() {
		InventoryHooks.onClientBackToHotbar = () -> send("select", Selection.NONE, 0, "");
		ClientPlayNetworking.registerGlobalReceiver(InventoryPayloads.Elytra.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
			if (payload.stack().isEmpty()) {
				ELYTRA.remove(payload.entityId());
			} else {
				ELYTRA.put(payload.entityId(), payload.stack());
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ELYTRA.clear();
			pageNames = List.of();
		}));
	}

	public static boolean on() {
		JsonObject s = ClientTree.state();
		return !s.has("inventory") || s.get("inventory").getAsBoolean();
	}

	public static void send(String op, int a, int b, String text) {
		if (ClientPlayNetworking.canSend(InventoryPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new InventoryPayloads.Action(op, a, b, text));
		}
	}

	// ---- notices -----------------------------------------------------------------------------------------------------

	public static void onBank(JsonObject json) {
		Player p = Minecraft.getInstance().player;
		if (p != null) {
			((ExtraHolder) p).vestiges$setBank(json.get("amount").getAsLong());
		}
	}

	public static void onPages(JsonObject json) {
		List<String> names = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("names")) {
			names.add(e.getAsString());
		}
		pageNames = names;
		unlockedPages = json.get("unlocked").getAsInt();
		totalPages = json.get("total").getAsInt();
	}

	public static String pageName(int page) {
		String n = page < pageNames.size() ? pageNames.get(page) : "";
		return n.isEmpty() ? "Page " + (page + 1) : n;
	}

	public static int unlockedPages() {
		return unlockedPages;
	}

	public static int totalPages() {
		return totalPages;
	}

	public static ItemStack elytraOf(int entityId) {
		return ELYTRA.getOrDefault(entityId, ItemStack.EMPTY);
	}

	// ---- selection ---------------------------------------------------------------------------------------------------

	/** Picks a rack slot: keeps its last position if something is there, else the first filled one. */
	public static void selectExtra(Player player, int kind) {
		Selection sel = InventoryHooks.selection(player);
		ExtraSlots slots = InventoryHooks.slots(player);
		int index = sel.vestiges$rackIndex(kind);
		if (slots.getItem(ExtraSlots.rackStart(kind) + index).isEmpty()) {
			index = slots.nextFilled(kind, index, 1);
		}
		sel.vestiges$setRackIndex(kind, index);
		sel.vestiges$setExtra(kind);
		send("select", kind, index, "");
	}

	/** Puts a given rack position in the slot and selects it (Ctrl / Alt + number). */
	public static void selectRack(Player player, int kind, int index) {
		Selection sel = InventoryHooks.selection(player);
		sel.vestiges$setRackIndex(kind, index);
		sel.vestiges$setExtra(kind);
		send("select", kind, index, "");
	}

	/** Number keys (agreed controls): Ctrl + 1-8 = tool rack position, Alt + 1-6 = weapon rack position. True when handled. */
	public static boolean onNumberKey(Inventory inventory, int slot) {
		Minecraft mc = Minecraft.getInstance();
		if (!on() || mc.player != inventory.player) {
			return false;
		}
		boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
		boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
		if (ctrl && slot < ExtraSlots.TOOL_COUNT) {
			selectRack(inventory.player, Selection.TOOL, slot);
			return true;
		}
		if (alt && slot < ExtraSlots.WEAPON_COUNT) {
			selectRack(inventory.player, Selection.WEAPON, slot);
			return true;
		}
		return false;
	}

	/** The optional Tool slot / Weapon slot keys (unbound by default). */
	public static void tickKeys(Minecraft mc) {
		if (mc.player == null || !on()) {
			return;
		}
		while (toolKey != null && toolKey.consumeClick()) {
			selectExtra(mc.player, Selection.TOOL);
		}
		while (weaponKey != null && weaponKey.consumeClick()) {
			selectExtra(mc.player, Selection.WEAPON);
		}
	}

	private static void cycleRack(Player player, int dir) {
		Selection sel = InventoryHooks.selection(player);
		int kind = sel.vestiges$extra();
		int index = sel.vestiges$rackIndex(kind);
		int next = InventoryHooks.slots(player).nextFilled(kind, index, dir);
		if (next != index) {
			sel.vestiges$setRackIndex(kind, next);
			send("select", kind, next, "");
		}
	}

	/**
	 * Mouse wheel on the hotbar. Order: belt, 1-9, Tool, Weapon (and around). Ctrl + Scroll moves inside the selected rack; Alt + Scroll
	 * swaps the offhand rack. Returns true when it handled the scroll (vanilla's own change is skipped).
	 */
	public static boolean onScroll(Inventory inventory, int vanillaNext) {
		Minecraft mc = Minecraft.getInstance();
		Player player = inventory.player;
		if (!on() || mc.player != player) {
			return false;
		}
		int current = inventory.getSelectedSlot();
		int diff = vanillaNext - current;
		if (diff > 4) {
			diff -= 9;
		} else if (diff < -4) {
			diff += 9;
		}
		if (diff == 0) {
			return false;
		}
		int dir = Integer.signum(diff);
		boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
		boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
		Selection sel = InventoryHooks.selection(player);
		if (alt) {
			boolean light = !sel.vestiges$offhandB();
			sel.vestiges$setOffhandB(light);
			send("offhand_active", light ? 1 : 0, 0, "");
			return true;
		}
		if (ctrl && sel.vestiges$extra() != Selection.NONE) {
			cycleRack(player, dir);
			return true;
		}
		int pos = switch (sel.vestiges$extra()) {
			case Selection.BELT -> -1;
			case Selection.TOOL -> 9;
			case Selection.WEAPON -> 10;
			default -> current;
		};
		int next = pos + dir;
		if (next > 10) {
			next = -1;
		} else if (next < -1) {
			next = 10;
		}
		if (next >= 0 && next <= 8) {
			if (sel.vestiges$extra() != Selection.NONE) {
				sel.vestiges$setExtra(Selection.NONE);
				send("select", Selection.NONE, 0, "");
			}
			inventory.setSelectedSlot(next);
		} else {
			selectExtra(player, next == -1 ? Selection.BELT : next == 9 ? Selection.TOOL : Selection.WEAPON);
		}
		return true;
	}

	/** Auto-tool: with the Tool slot selected, starting to break a block picks the best tool on the rack (a setting). */
	public static void beforeBreaking(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null || !on() || !ClientConfigStore.get().autoTool) {
			return;
		}
		Selection sel = InventoryHooks.selection(player);
		if (sel.vestiges$extra() != Selection.TOOL) {
			return;
		}
		BlockState state = mc.level.getBlockState(pos);
		ExtraSlots slots = InventoryHooks.slots(player);
		int current = sel.vestiges$rackIndex(Selection.TOOL);
		int best = current;
		double bestScore = score(slots.getItem(ExtraSlots.TOOLS + current), state);
		for (int i = 0; i < ExtraSlots.TOOL_COUNT; i++) {
			double s = score(slots.getItem(ExtraSlots.TOOLS + i), state);
			if (s > bestScore) {
				best = i;
				bestScore = s;
			}
		}
		if (best != current) {
			sel.vestiges$setRackIndex(Selection.TOOL, best);
			send("select", Selection.TOOL, best, "");
		}
	}

	private static double score(ItemStack tool, BlockState state) {
		if (tool.isEmpty()) {
			return 0;
		}
		float speed = tool.getDestroySpeed(state);
		return (tool.isCorrectToolForDrops(state) ? 1000 : 0) + (speed > 1.0f ? speed : 0);
	}

	// ---- belt --------------------------------------------------------------------------------------------------------

	/** True when this belt item is on the local player's belt (map shows the minimap, compass the letters...). */
	public static boolean onBelt(int beltSlot) {
		Player p = Minecraft.getInstance().player;
		return p != null && !InventoryHooks.slots(p).getItem(ExtraSlots.BELT + beltSlot).isEmpty();
	}

	public static boolean zooming() {
		return on() && zoomKey != null && zoomKey.isDown() && onBelt(3);
	}
}
