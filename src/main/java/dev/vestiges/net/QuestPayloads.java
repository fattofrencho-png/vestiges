package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Quests: the client accepts, drops, tracks, hands in, chooses, picks a title, or reports something only it sees (a new waypoint). */
public final class QuestPayloads {
	private QuestPayloads() {
	}

	public record Action(String op, String id, String arg) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Vestiges.id("quest_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Action::op,
			ByteBufCodecs.stringUtf8(64), Action::id,
			ByteBufCodecs.stringUtf8(64), Action::arg,
			Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
	}
}
