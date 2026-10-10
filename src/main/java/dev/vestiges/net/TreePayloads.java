package dev.vestiges.net;

import dev.vestiges.Vestiges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Passive tree messages. The tree content lives in the server's datapack, so the server sends it on join and after {@code /reload}
 * ({@link Defs}, one wheel or the ascendancy list per message). The player's own tree comes as a {@link State} snapshot after every change;
 * the client only sends {@link Action}s, and the server checks every rule again.
 */
public final class TreePayloads {
	private TreePayloads() {
	}

	/** {@code kind}: "clear" (forget all), "wheel" (one wheel as JSON) or "ascendancies" (JSON array). */
	public record Defs(String kind, String json) implements CustomPacketPayload {
		public static final Type<Defs> TYPE = new Type<>(Vestiges.id("tree_defs"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Defs> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Defs::kind,
			ByteBufCodecs.stringUtf8(900_000), Defs::json,
			Defs::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player's tree, points and checklists as JSON. */
	public record State(String json) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Vestiges.id("tree_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(200_000), State::json,
			State::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * What the player wants to do. {@code op}: request, start, take, refund, switch, asc_choose, asc_second, asc_take, asc_refund, utility.
	 * {@code wheel} is "fight" or "craft"; {@code node} a node id (or an ascendancy id); {@code value} a start or choice index.
	 */
	public record Action(String op, String wheel, String node, int value) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Vestiges.id("tree_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Action::op,
			ByteBufCodecs.stringUtf8(32), Action::wheel,
			ByteBufCodecs.stringUtf8(128), Action::node,
			ByteBufCodecs.VAR_INT, Action::value,
			Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Defs.TYPE, Defs.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
	}
}
