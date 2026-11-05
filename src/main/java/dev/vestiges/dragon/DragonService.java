package dev.vestiges.dragon;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first dragons (Act I, dragons 1 to 6). The world's kill count and the active fight are saved on the overworld, what each player has been
 * rewarded for on the player. The whole system sits behind the Dragons switch: off, the End is vanilla and nothing here runs.
 */
public final class DragonService {
	public static final AttachmentType<DragonWorld> WORLD = AttachmentRegistry.create(Vestiges.id("dragons"),
		builder -> builder.persistent(DragonWorld.CODEC));
	public static final AttachmentType<PlayerDragons> PLAYER = AttachmentRegistry.create(Vestiges.id("dragons_player"),
		builder -> builder.persistent(PlayerDragons.CODEC).copyOnDeath());

	private DragonService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.DRAGONS);
	}

	public static void register() {
		// Touched so the attachment types exist before any world or player data loads.
		java.util.Objects.requireNonNull(WORLD);
		java.util.Objects.requireNonNull(PLAYER);
	}

	// ---- saved state -------------------------------------------------------------------------------------------------

	public static DragonWorld world(MinecraftServer server) {
		DragonWorld w = server.overworld().getAttached(WORLD);
		return w == null ? DragonWorld.EMPTY : w;
	}

	public static void save(MinecraftServer server, DragonWorld world) {
		server.overworld().setAttached(WORLD, world);
	}

	public static PlayerDragons player(ServerPlayer p) {
		PlayerDragons d = p.getAttached(PLAYER);
		return d == null ? PlayerDragons.EMPTY : d;
	}

	public static void setPlayer(ServerPlayer p, PlayerDragons d) {
		p.setAttached(PLAYER, d);
	}

	/** Ender chest pages this player has unlocked: 1 to start, one more for every dragon they were rewarded for. */
	public static int pages(ServerPlayer p, int total) {
		return DragonRules.pages(player(p).count(), total);
	}

	// ---- the dragons -------------------------------------------------------------------------------------------------

	/** Every dragon of the datapacks, in order of number. */
	public static List<DragonDef> defs() {
		return VestigesData.DRAGONS.all().values().stream().sorted(Comparator.comparingInt(DragonDef::number)).toList();
	}

	/** The dragon with this number, or null (after dragon 6 summoning gives a plain respawn). */
	public static DragonDef def(int number) {
		for (DragonDef d : VestigesData.DRAGONS.all().values()) {
			if (d.number() == number) {
				return d;
			}
		}
		return null;
	}

	public static Identifier id(DragonDef def) {
		for (var e : VestigesData.DRAGONS.all().entrySet()) {
			if (e.getValue() == def) {
				return e.getKey();
			}
		}
		return null;
	}
}
