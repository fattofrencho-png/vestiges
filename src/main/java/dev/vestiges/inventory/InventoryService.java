package dev.vestiges.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import dev.vestiges.net.InventoryPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Server side of the M2 inventory: rack selection, the offhand rack, the Bank, opening the ender chest from its slot, page names, keeping
 * things on respawn, ticking the items on the racks (compasses, maps) and telling other players about elytra in the elytra slot.
 */
public final class InventoryService {
	public static final int PAGES = 25;
	private static final Map<UUID, Integer> ELYTRA_SEEN = new HashMap<>();

	private InventoryService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.INVENTORY);
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(InventoryPayloads.Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			sendBank(handler.player);
			sendPages(handler.player);
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ELYTRA_SEEN.remove(handler.player.getUUID()));
		ServerPlayerEvents.COPY_FROM.register(InventoryService::copyFrom);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ELYTRA_SEEN.remove(newPlayer.getUUID());
			sendBank(newPlayer);
		});
		EntityTrackingEvents.START_TRACKING.register((entity, tracker) -> {
			if (entity instanceof ServerPlayer other) {
				send(tracker, other);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(InventoryService::tick);
	}

	// ---- actions ---------------------------------------------------------------------------------------------------

	private static void handle(ServerPlayer player, InventoryPayloads.Action a) {
		if (!enabled() || player.isSpectator()) {
			return;
		}
		ExtraHolder holder = (ExtraHolder) player;
		ExtraSlots slots = holder.vestiges$slots();
		Selection sel = InventoryHooks.selection(player);
		switch (a.op()) {
			case "select" -> {
				int kind = a.a();
				if (kind == Selection.NONE) {
					sel.vestiges$setExtra(Selection.NONE);
				} else if (kind >= Selection.TOOL && kind <= Selection.BELT && a.b() >= 0 && a.b() < ExtraSlots.rackSize(kind)) {
					if (player.isUsingItem()) {
						player.stopUsingItem();
					}
					sel.vestiges$setRackIndex(kind, a.b());
					sel.vestiges$setExtra(kind);
				}
			}
			case "offhand_active" -> {
				if (player.isUsingItem()) {
					player.stopUsingItem();
				}
				sel.vestiges$setOffhandB(a.a() == 1);
			}
			case "bank_deposit" -> {
				ItemStack carried = player.containerMenu.getCarried();
				if (player.containerMenu == player.inventoryMenu && carried.is(Items.EMERALD)) {
					holder.vestiges$setBank(holder.vestiges$bank() + carried.getCount());
					player.containerMenu.setCarried(ItemStack.EMPTY);
					player.containerMenu.broadcastChanges();
					sendBank(player);
				}
			}
			case "bank_withdraw" -> {
				ItemStack carried = player.containerMenu.getCarried();
				if (player.containerMenu != player.inventoryMenu || (!carried.isEmpty() && !carried.is(Items.EMERALD))) {
					return;
				}
				int room = carried.isEmpty() ? 64 : carried.getMaxStackSize() - carried.getCount();
				int take = (int) Math.min(Math.min(Math.max(1, a.b()), room), holder.vestiges$bank());
				if (take <= 0) {
					return;
				}
				holder.vestiges$setBank(holder.vestiges$bank() - take);
				if (carried.isEmpty()) {
					player.containerMenu.setCarried(new ItemStack(Items.EMERALD, take));
				} else {
					carried.grow(take);
				}
				player.containerMenu.broadcastChanges();
				sendBank(player);
			}
			case "ender_open" -> {
				if (slots.getItem(ExtraSlots.ENDER).is(Items.ENDER_CHEST)) {
					player.getEnderChestInventory().setActiveChest(null);
					player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, player.getEnderChestInventory()),
						Component.translatable("container.enderchest")));
				}
			}
			case "page_rename" -> {
				int page = a.a();
				if (page < 0 || page >= unlockedPages(player)) {
					return;
				}
				String name = a.text().strip();
				List<String> names = new ArrayList<>(holder.vestiges$pageNames());
				while (names.size() <= page) {
					names.add("");
				}
				names.set(page, name.length() > 24 ? name.substring(0, 24) : name);
				holder.vestiges$setPageNames(names);
				sendPages(player);
			}
			default -> {
			}
		}
	}

	/** Ender chest pages you can use: 1 now, one more per dragon (dragons arrive in M9). */
	public static int unlockedPages(ServerPlayer player) {
		return 1;
	}

	// ---- sync ------------------------------------------------------------------------------------------------------

	public static void sendBank(ServerPlayer player) {
		JsonObject o = new JsonObject();
		o.addProperty("amount", ((ExtraHolder) player).vestiges$bank());
		LevelService.notice(player, "bank", o);
	}

	public static void sendPages(ServerPlayer player) {
		JsonObject o = new JsonObject();
		JsonArray names = new JsonArray();
		((ExtraHolder) player).vestiges$pageNames().forEach(names::add);
		o.add("names", names);
		o.addProperty("unlocked", unlockedPages(player));
		o.addProperty("total", PAGES);
		LevelService.notice(player, "pages", o);
	}

	/** Tells {@code to} what is in {@code about}'s elytra slot. */
	private static void send(ServerPlayer to, ServerPlayer about) {
		if (ServerPlayNetworking.canSend(to, InventoryPayloads.Elytra.TYPE)) {
			ServerPlayNetworking.send(to, new InventoryPayloads.Elytra(about.getId(), InventoryHooks.slots(about).getItem(ExtraSlots.ELYTRA).copy()));
		}
	}

	private static void tick(MinecraftServer server) {
		boolean every10 = server.getTickCount() % 10 == 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ExtraSlots slots = InventoryHooks.slots(p);
			// Items on the racks and belt tick like items in the inventory (compasses track lodestones, maps update while held).
			int held = InventoryHooks.selection(p).vestiges$heldExtraSlot();
			for (int i = 0; i < ExtraSlots.SIZE; i++) {
				ItemStack s = slots.getItem(i);
				if (!s.isEmpty()) {
					s.inventoryTick(p.level(), p, i == held ? EquipmentSlot.MAINHAND : null);
				}
			}
			if (every10) {
				ItemStack elytra = slots.getItem(ExtraSlots.ELYTRA);
				int sig = Objects.hash(elytra.getItem(), elytra.isEmpty(), elytra.hasFoil());
				if (!Objects.equals(ELYTRA_SEEN.put(p.getUUID(), sig), sig)) {
					for (ServerPlayer tracker : PlayerLookup.tracking(p)) {
						send(tracker, p);
					}
				}
			}
		}
	}

	/** Respawn: the Bank and page names always stay; the slots stay when returning from the End or with keepInventory. */
	private static void copyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		ExtraHolder from = (ExtraHolder) oldPlayer;
		ExtraHolder to = (ExtraHolder) newPlayer;
		to.vestiges$setBank(from.vestiges$bank());
		to.vestiges$setPageNames(from.vestiges$pageNames());
		Selection oldSel = InventoryHooks.selection(oldPlayer);
		Selection newSel = InventoryHooks.selection(newPlayer);
		for (int kind = Selection.TOOL; kind <= Selection.BELT; kind++) {
			newSel.vestiges$setRackIndex(kind, oldSel.vestiges$rackIndex(kind));
		}
		boolean keep = alive || newPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY);
		if (keep) {
			to.vestiges$slots().fromList(from.vestiges$slots().toList());
		}
	}
}
