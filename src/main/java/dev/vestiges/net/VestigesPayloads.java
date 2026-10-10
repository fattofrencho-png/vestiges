package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client and server talk through two small messages, as in Hearthbook: the client only asks, and the server always answers with a complete,
 * fresh JSON snapshot, so a screen can never drift from what is really saved. Later systems add their own sections to the same snapshot.
 */
public final class VestigesPayloads {
	private VestigesPayloads() {
	}

	/** Client asks for a snapshot. {@code section} names what the open screen needs ("hub" for now). */
	public record HubRequest(String section) implements CustomPacketPayload {
		public static final Type<HubRequest> TYPE = new Type<>(Vestiges.id("hub_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HubRequest> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), HubRequest::section,
			HubRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server's answer, as JSON. */
	public record HubData(String json) implements CustomPacketPayload {
		public static final Type<HubData> TYPE = new Type<>(Vestiges.id("hub_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HubData> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(1_000_000), HubData::json,
			HubData::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Small server-to-client notices for the HUD: "xp", "levelup", "graves", "grave", "grave_removed". */
	public record Notice(String kind, String json) implements CustomPacketPayload {
		public static final Type<Notice> TYPE = new Type<>(Vestiges.id("notice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Notice::kind,
			ByteBufCodecs.stringUtf8(64000), Notice::json,
			Notice::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(HubRequest.TYPE, HubRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HubData.TYPE, HubData.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Notice.TYPE, Notice.CODEC);
	}
}
