package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory messages. The client asks ({@link Action}): select a rack slot, pick the active offhand slot, Bank deposit and withdraw, open the ender
 * chest, rename a page. The server shows other players' elytra slot ({@link Elytra}) so their wings render.
 */
public final class InventoryPayloads {
	private InventoryPayloads() {
	}

	/** {@code op}: select (a = kind, b = rack position), offhand_active (a = 1 for the second offhand slot), bank_deposit, bank_withdraw (b = count), ender_open, page_rename (text). */
	public record Action(String op, int a, int b, String text) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Vestiges.id("inventory_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Action::op,
			ByteBufCodecs.VAR_INT, Action::a,
			ByteBufCodecs.VAR_INT, Action::b,
			ByteBufCodecs.stringUtf8(64), Action::text,
			Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The elytra in a player's elytra slot (empty when none), for rendering their wings. */
	public record Elytra(int entityId, ItemStack stack) implements CustomPacketPayload {
		public static final Type<Elytra> TYPE = new Type<>(Vestiges.id("elytra_slot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Elytra> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Elytra::entityId,
			ItemStack.OPTIONAL_STREAM_CODEC, Elytra::stack,
			Elytra::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Elytra.TYPE, Elytra.CODEC);
	}
}
