package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The dungeon key: one item, {@code vestiges:dungeon_key}, with its theme and tier stored as item data (so there is no new item per theme). It
 * shows as "Overgrown Key, Tier 1". It looks like the vanilla ominous trial key until the author hands in art. The frame kit is the vanilla blocks
 * the First Key feat hands out with the first key.
 */
public final class DungeonKeys {
	private static final String TAG = "vestiges_dungeon_key";
	public static final ResourceKey<Item> ID = ResourceKey.create(Registries.ITEM, Vestiges.id("dungeon_key"));
	private static Item item;

	private DungeonKeys() {
	}

	/** What a key says about itself. */
	public record Data(String theme, int tier) {
	}

	public static void register() {
		item = Registry.register(BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(ID).stacksTo(16)));
	}

	public static Item item() {
		return item;
	}

	public static ItemStack create(String theme, int tier) {
		int t = Math.max(1, tier);
		ItemStack stack = new ItemStack(item, 1);
		String name = themeName(theme);
		ChatFormatting color = colorOf(theme);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name + " Key, Tier " + t).withStyle(s -> s.withItalic(false).withColor(color)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Use it on a dungeon portal frame.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
			Component.literal("Everyone within 8 blocks goes in together. One use.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))));
		CompoundTag inner = new CompoundTag();
		inner.putString("theme", theme);
		inner.putInt("tier", t);
		CompoundTag tag = new CompoundTag();
		tag.put(TAG, inner);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		return stack;
	}

	/** The theme and tier of a key, or null when the stack is not a dungeon key. */
	public static Data read(ItemStack stack) {
		if (stack.isEmpty() || item == null || !stack.is(item)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag inner = data.copyTag().getCompound(TAG).orElse(null);
		if (inner == null) {
			return null;
		}
		String theme = inner.getStringOr("theme", "");
		int tier = inner.getIntOr("tier", 0);
		return theme.isEmpty() || tier < 1 ? null : new Data(theme, tier);
	}

	/** The blocks of the smallest frame: 6 chiseled tuff for the sides and 4 copper blocks for the top and bottom. */
	public static List<ItemStack> frameKit() {
		return List.of(new ItemStack(Items.CHISELED_TUFF, PortalFrame.KIT_TUFF), new ItemStack(Items.COPPER_BLOCK.weathering().unaffected(), PortalFrame.KIT_COPPER));
	}

	public static String themeName(String theme) {
		DungeonTheme t = VestigesData.DUNGEON_THEMES.get(Vestiges.id(theme));
		if (t != null) {
			return t.name();
		}
		return theme.isEmpty() ? theme : Character.toUpperCase(theme.charAt(0)) + theme.substring(1);
	}

	private static ChatFormatting colorOf(String theme) {
		DungeonTheme t = VestigesData.DUNGEON_THEMES.get(Vestiges.id(theme));
		if (t == null) {
			return ChatFormatting.WHITE;
		}
		try {
			return ChatFormatting.valueOf(t.color().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ChatFormatting.WHITE;
		}
	}
}
