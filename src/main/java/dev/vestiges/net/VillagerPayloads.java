package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Villagers: the server opens (or refreshes) the villager screen with a JSON view; the client buys or hears a quest offer. */
public final class VillagerPayloads {
	private VillagerPayloads() {
	}

	/** Server to client: everything the screen shows about one villager, as JSON. */
	public record View(String json) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(Vestiges.id("villager_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64000), View::json,
			View::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: {@code buy} (id = villager, arg = offer number, "n:all" for as many as possible) or {@code quest} (id = villager, arg = quest id). */
	public record Act(String op, String id, String arg) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(Vestiges.id("villager_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(16), Act::op,
			ByteBufCodecs.stringUtf8(64), Act::id,
			ByteBufCodecs.stringUtf8(64), Act::arg,
			Act::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
	}
}
