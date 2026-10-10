package dev.vestiges.waystone;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Reads and writes the world's waystone list, kept as a persistent attachment on the overworld (saved with the world). */
public final class WaystoneStore {
	public static final AttachmentType<WaystoneList> WAYSTONES = AttachmentRegistry.create(Vestiges.id("waystones"),
		builder -> builder.persistent(WaystoneList.CODEC));

	private WaystoneStore() {
	}

	public static void init() {
		// Touching the class registers the attachment.
	}

	public static WaystoneList get(MinecraftServer server) {
		WaystoneList list = server.overworld().getAttached(WAYSTONES);
		return list != null ? list : WaystoneList.EMPTY;
	}

	public static void set(MinecraftServer server, WaystoneList list) {
		ServerLevel overworld = server.overworld();
		overworld.setAttached(WAYSTONES, list);
	}
}
