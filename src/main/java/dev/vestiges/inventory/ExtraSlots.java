package dev.vestiges.inventory;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The new inventory slots (M2, approved layout A): tool rack (8), weapon rack (6), arrows, tool belt (4), the second offhand slot, elytra,
 * totem and ender chest. Each rack position takes one kind of item, so there is one of each type. Lives on the player and in its
 * inventory menu, so vanilla keeps the client in sync.
 */
public final class ExtraSlots extends SimpleContainer {
	public static final int TOOLS = 0;
	public static final int TOOL_COUNT = 8;
	public static final int WEAPONS = 8;
	public static final int WEAPON_COUNT = 6;
	public static final int ARROWS = 14;
	public static final int BELT = 15;
	public static final int BELT_COUNT = 4;
	public static final int OFFHAND2 = 19;
	public static final int ELYTRA = 20;
	public static final int TOTEM = 21;
	public static final int ENDER = 22;
	public static final int SIZE = 23;

	public static final Codec<List<ItemStack>> CODEC = ItemStack.OPTIONAL_CODEC.listOf();

	/** What each slot accepts, in slot order. */
	private static final List<Predicate<ItemStack>> RULES = List.of(
		s -> s.is(ItemTags.PICKAXES), s -> s.is(ItemTags.AXES), s -> s.is(ItemTags.SHOVELS), s -> s.is(ItemTags.HOES),
		s -> s.is(Items.SHEARS), s -> s.is(Items.FISHING_ROD), s -> s.is(Items.FLINT_AND_STEEL), s -> s.is(Items.BRUSH),
		s -> s.is(ItemTags.SWORDS), s -> s.is(Items.MACE), s -> s.is(ItemTags.SPEARS), s -> s.is(Items.TRIDENT), s -> s.is(Items.BOW),
		s -> s.is(Items.CROSSBOW),
		s -> s.is(ItemTags.ARROWS),
		s -> s.is(Items.COMPASS) || s.is(Items.RECOVERY_COMPASS), s -> s.is(Items.CLOCK), s -> s.is(Items.MAP) || s.is(Items.FILLED_MAP),
		s -> s.is(Items.SPYGLASS),
		s -> true,
		s -> s.has(DataComponents.GLIDER),
		s -> s.has(DataComponents.DEATH_PROTECTION),
		s -> s.is(Items.ENDER_CHEST));

	/** Outline icons for empty slots: vanilla's where it has one, Vestiges' own (same style) for the rest. */
	private static final String[] ICONS = {"pickaxe", "axe", "shovel", "hoe", "vestiges:shears", "vestiges:fishing_rod", "vestiges:flint_and_steel",
		"vestiges:brush", "sword", "vestiges:mace", "spear", "vestiges:trident", "vestiges:bow", "vestiges:crossbow", "vestiges:arrow",
		"vestiges:compass", "vestiges:clock", "vestiges:map", "vestiges:spyglass", "shield", "vestiges:elytra", "vestiges:totem",
		"vestiges:ender_chest"};

	/** What each slot is called (empty-slot tooltips). */
	public static final String[] NAMES = {"Pickaxe", "Axe", "Shovel", "Hoe", "Shears", "Fishing rod", "Flint and steel", "Brush", "Sword",
		"Mace", "Spear", "Trident", "Bow", "Crossbow", "Arrows", "Compass", "Clock", "Map", "Spyglass", "Second offhand", "Elytra", "Totem",
		"Ender chest"};

	public ExtraSlots() {
		super(SIZE);
	}

	public static boolean accepts(int slot, ItemStack stack) {
		return stack.isEmpty() || (slot >= 0 && slot < SIZE && RULES.get(slot).test(stack));
	}

	/** The rack slot this item belongs in (tools, weapons, arrows, belt, elytra, totem, ender chest), or -1. Never the second offhand. */
	public static int homeFor(ItemStack stack) {
		for (int i = 0; i < SIZE; i++) {
			if (i != OFFHAND2 && !stack.isEmpty() && RULES.get(i).test(stack)) {
				return i;
			}
		}
		return -1;
	}

	public static int maxStack(int slot) {
		return slot == ARROWS || slot == OFFHAND2 ? 64 : 1;
	}

	public static Identifier icon(int slot) {
		String name = slot >= 0 && slot < ICONS.length ? ICONS[slot] : null;
		if (name == null) {
			return null;
		}
		return name.startsWith("vestiges:") ? Identifier.fromNamespaceAndPath("vestiges", "container/slot/" + name.substring(9))
			: Identifier.withDefaultNamespace("container/slot/" + name);
	}

	/** First slot and size of a rack ({@link Selection#TOOL}, {@link Selection#WEAPON}, {@link Selection#BELT}). */
	public static int rackStart(int kind) {
		return switch (kind) {
			case Selection.TOOL -> TOOLS;
			case Selection.WEAPON -> WEAPONS;
			case Selection.BELT -> BELT;
			default -> -1;
		};
	}

	public static int rackSize(int kind) {
		return switch (kind) {
			case Selection.TOOL -> TOOL_COUNT;
			case Selection.WEAPON -> WEAPON_COUNT;
			case Selection.BELT -> BELT_COUNT;
			default -> 0;
		};
	}

	/** The next filled position in a rack after {@code from} (in direction {@code dir}), or {@code from} when none is filled. */
	public int nextFilled(int kind, int from, int dir) {
		int size = rackSize(kind);
		int start = rackStart(kind);
		for (int step = 1; step <= size; step++) {
			int i = Math.floorMod(from + dir * step, size);
			if (!getItem(start + i).isEmpty()) {
				return i;
			}
		}
		return from;
	}

	public List<ItemStack> toList() {
		List<ItemStack> out = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			out.add(getItem(i));
		}
		return out;
	}

	public void fromList(List<ItemStack> list) {
		for (int i = 0; i < SIZE; i++) {
			setItem(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
		}
	}

	/** Takes every item out (for the gravestone or a vanilla drop). */
	public NonNullList<ItemStack> drain() {
		NonNullList<ItemStack> out = NonNullList.create();
		for (int i = 0; i < SIZE; i++) {
			ItemStack s = removeItemNoUpdate(i);
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}
}
