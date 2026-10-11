package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Look-at card (M4): the client asks about the entity it looks at (growth, breeding, owner); the server answers with an "entity_info" notice. */
public final class LookAtPayloads {
	private LookAtPayloads() {
	}

	public record EntityInfo(int id) implements CustomPacketPayload {
		public static final Type<EntityInfo> TYPE = new Type<>(Vestiges.id("entity_info"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EntityInfo> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EntityInfo::id, EntityInfo::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(EntityInfo.TYPE, EntityInfo.CODEC);
	}
}
