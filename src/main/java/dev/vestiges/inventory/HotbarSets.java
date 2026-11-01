package dev.vestiges.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Three saved hotbars per player. A set remembers which kind of item sits in each hotbar slot, not the items: switching saves the current layout
 * into the active set, then swaps the wanted items in from the inventory. Items are only swapped, never created; an item you no longer have
 * leaves the slot as it is.
 */
public final class HotbarSets {
	public static final int SETS = 3;
	private static final int HOTBAR = 9;
	private static final int MAIN_END = 36;

	public record Data(int active, List<List<String>> sets) {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("active").forGetter(Data::active),
			Codec.STRING.listOf().listOf().fieldOf("sets").forGetter(Data::sets)).apply(i, Data::new));

		static Data empty() {
			List<List<String>> sets = new ArrayList<>();
			for (int i = 0; i < SETS; i++) {
				sets.add(emptySet());
			}
			return new Data(0, sets);
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(Vestiges.id("hotbar_sets"), b -> b.persistent(Data.CODEC).copyOnDeath());

	private HotbarSets() {
	}

	private static List<String> emptySet() {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < HOTBAR; i++) {
			out.add("");
		}
		return out;
	}

	private static String id(ItemStack s) {
		return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
	}

	private static Data data(ServerPlayer p) {
		return p.getAttachedOrCreate(DATA, Data::empty);
	}

	public static void switchTo(ServerPlayer p, int set) {
		Data d = data(p);
		if (set < 0 || set >= SETS || set == d.active() || p.containerMenu != p.inventoryMenu) {
			return;
		}
		if (p.isUsingItem()) {
			p.stopUsingItem();
		}
		Inventory inv = p.getInventory();
		List<String> current = new ArrayList<>();
		for (int i = 0; i < HOTBAR; i++) {
			current.add(id(inv.getItem(i)));
		}
		List<List<String>> sets = new ArrayList<>(d.sets());
		sets.set(d.active(), current);
		List<String> wanted = set < sets.size() ? sets.get(set) : emptySet();
		for (int i = 0; i < HOTBAR; i++) {
			String want = i < wanted.size() ? wanted.get(i) : "";
			if (want.equals(id(inv.getItem(i)))) {
				continue;
			}
			int from = find(inv, want, wanted);
			if (from >= 0) {
				ItemStack here = inv.getItem(i);
				inv.setItem(i, inv.getItem(from));
				inv.setItem(from, here);
			}
		}
		p.setAttached(DATA, new Data(set, sets));
		p.inventoryMenu.broadcastChanges();
	}

	/** Where to take the wanted item from: the main inventory first (an empty slot for an empty wish), then a hotbar slot that doesn't want what it holds. */
	private static int find(Inventory inv, String want, List<String> wanted) {
		for (int j = HOTBAR; j < MAIN_END; j++) {
			if (want.equals(id(inv.getItem(j)))) {
				return j;
			}
		}
		if (want.isEmpty()) {
			return -1;
		}
		for (int j = 0; j < HOTBAR; j++) {
			String holds = id(inv.getItem(j));
			if (want.equals(holds) && !holds.equals(j < wanted.size() ? wanted.get(j) : "")) {
				return j;
			}
		}
		return -1;
	}

	/** The hotbar set key: on to the next set, with a short message naming it. */
	public static void next(ServerPlayer p) {
		int set = (data(p).active() + 1) % SETS;
		switchTo(p, set);
		if (data(p).active() == set) {
			p.sendOverlayMessage(Component.literal("Hotbar set " + (set + 1)));
		}
	}
}
