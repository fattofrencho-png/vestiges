package dev.vestiges.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Items inside world-level data (graves). Fabric saves world attachments without the server's registries, so an enchanted item could not be
 * written ("Can't access registry minecraft:enchantment") and the whole overworld data failed to save with it (settlements, waystones,
 * graves). This codec writes and reads items with the running server's registries instead.
 */
public final class SavedItems {
	private static volatile HolderLookup.Provider registries;

	public static final Codec<ItemStack> CODEC = Codec.PASSTHROUGH.comapFlatMap(SavedItems::decode, SavedItems::encode);

	private SavedItems() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> registries = server.registryAccess());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> registries = null);
	}

	private static DynamicOps<Tag> ops() {
		HolderLookup.Provider r = registries;
		return r != null ? r.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
	}

	private static DataResult<ItemStack> decode(Dynamic<?> value) {
		Tag tag = value.convert(NbtOps.INSTANCE).getValue();
		return ItemStack.OPTIONAL_CODEC.parse(ops(), tag);
	}

	private static Dynamic<?> encode(ItemStack stack) {
		Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(ops(), stack).result().orElseGet(CompoundTag::new);
		return new Dynamic<>(NbtOps.INSTANCE, tag);
	}
}
