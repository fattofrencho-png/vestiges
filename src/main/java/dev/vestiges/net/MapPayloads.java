package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Map and waystone messages. Same pattern as the hub: one client-to-server "action" and one server-to-client "event", each a name plus JSON.
 * Actions: pin, unpin, share, waystone_travel, waystone_rename, waystone_share. Events: shared, waystone_terminal, waystones, message.
 */
public final class MapPayloads {
	private MapPayloads() {
	}

	public record Action(String action, String json) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Vestiges.id("map_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Action::action,
			ByteBufCodecs.stringUtf8(32000), Action::json,
			Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Event(String event, String json) implements CustomPacketPayload {
		public static final Type<Event> TYPE = new Type<>(Vestiges.id("map_event"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Event::event,
			ByteBufCodecs.stringUtf8(500000), Event::json,
			Event::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
	}
}
