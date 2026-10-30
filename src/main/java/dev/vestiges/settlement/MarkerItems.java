package dev.vestiges.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Marker items are vanilla blocks with a name, a glint and a Vestiges tag (no new items, per the vanilla-style rule). A Bell that already
 * founded a settlement remembers it, and a Banner remembers its area name, so placing them again keeps both.
 */
public final class MarkerItems {
	private static final String TAG = "vestiges_marker";
	private static final String SETTLEMENT = "vestiges_settlement";
	private static final String AREA = "vestiges_area";

	/** What a marker item carries. */
	public record Info(MarkerType type, String settlement, String area) {
	}

	private MarkerItems() {
	}

	public static ItemStack create(MarkerType type) {
		return create(type, "", "");
	}

	public static ItemStack create(MarkerType type, String settlement, String area) {
		ItemStack stack = new ItemStack(type.item);
		String name = type.label + (area.isEmpty() ? "" : ": " + area);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(describe(type)).withStyle(ChatFormatting.GRAY));
		if (!settlement.isEmpty()) {
			lore.add(Component.literal("Placing it again moves its settlement here.").withStyle(ChatFormatting.DARK_GRAY));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, type.id);
		if (!settlement.isEmpty()) {
			tag.putString(SETTLEMENT, settlement);
		}
		if (!area.isEmpty()) {
			tag.putString(AREA, area);
		}
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		return stack;
	}

	/** Turns a dropped block (keeping its banner patterns) back into its marker item. */
	public static ItemStack mark(ItemStack base, MarkerType type, String settlement, String area) {
		ItemStack fresh = create(type, settlement, area);
		ItemStack out = base.copyWithCount(1);
		out.set(DataComponents.CUSTOM_NAME, fresh.get(DataComponents.CUSTOM_NAME));
		out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		out.set(DataComponents.LORE, fresh.get(DataComponents.LORE));
		out.set(DataComponents.CUSTOM_DATA, fresh.get(DataComponents.CUSTOM_DATA));
		return out;
	}

	private static String describe(MarkerType type) {
		return switch (type) {
			case BELL -> "Founds a settlement (10 minutes without fighting).";
			case BANNER16, BANNER48 -> "Names an area of radius " + type.radius + ".";
			case GOLD_BLOCK -> "Keeps its chunk and the 8 around it loaded (inside your claimed area).";
			default -> "No monsters spawn within " + type.radius + " blocks (5 minutes without fighting).";
		};
	}

	public static Info read(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		MarkerType type = MarkerType.byId(tag.getStringOr(TAG, ""));
		if (type == null || !stack.is(type.item) && !(type.banner() && stack.is(net.minecraft.tags.ItemTags.BANNERS))) {
			return null;
		}
		return new Info(type, tag.getStringOr(SETTLEMENT, ""), tag.getStringOr(AREA, ""));
	}
}
