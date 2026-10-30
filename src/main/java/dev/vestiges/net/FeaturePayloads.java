package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The world's feature switches: the server tells each client the state ({@link State}), an operator changes them ({@link Change}). */
public final class FeaturePayloads {
	private FeaturePayloads() {
	}

	/** JSON: {@code systems} (id to on/off), {@code operator}, {@code prompt} (open the welcome page), {@code sleep} (percent). */
	public record State(String json) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Vestiges.id("features"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(8192), State::json,
			State::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** {@code op}: apply (value = JSON of id to on/off), sleep (value = percent), done (the welcome page was closed). */
	public record Change(String op, String value) implements CustomPacketPayload {
		public static final Type<Change> TYPE = new Type<>(Vestiges.id("features_change"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(16), Change::op,
			ByteBufCodecs.stringUtf8(8192), Change::value,
			Change::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Change.TYPE, Change.CODEC);
	}
}
