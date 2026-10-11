package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Codex screen (M4): the client asks, the server answers with a complete JSON snapshot (also sent on join for the look-at "New" tag). */
public final class CodexPayloads {
	private CodexPayloads() {
	}

	public record Request(String section) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Vestiges.id("codex_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Request::section, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Data(String json) implements CustomPacketPayload {
		public static final Type<Data> TYPE = new Type<>(Vestiges.id("codex_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(1_000_000), Data::json, Data::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Data.TYPE, Data.CODEC);
	}
}
