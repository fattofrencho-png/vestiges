package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import dev.vestiges.data.VestigesData;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What happens inside a run's rooms. Rooms are built a little ahead of the party (two rooms), so starting a run is quick. A fighting room stays
 * quiet until someone walks in; then its doors close, its monsters spawn, and the doors open again when the last one is dead. Lieutenant and Boss
 * rooms are the same with one named monster. The reward room needs nothing: stepping in is enough.
 */
final class DungeonRunner {
	static final String TAG = "vestiges_dungeon";
	/** How many rooms are built beyond the one being fought. */
	static final int AHEAD = 2;

	private DungeonRunner() {
	}

	static String roomTag(int runId, int room) {
		return "vdr" + runId + "r" + room;
	}

	static Random rng(DungeonRuns.Run run, int salt) {
		return new Random(run.id() * 7919L + salt * 104729L + run.plot());
	}

	static DungeonTheme theme(String id) {
		DungeonTheme t = VestigesData.DUNGEON_THEMES.get(Vestiges.id(id));
		return t == null ? new DungeonTheme(id, "white", "") : t;
	}

	/** The room templates of a theme for a role. */
	static List<RoomLayout.Template> pool(String theme, String role) {
		List<RoomLayout.Template> out = new ArrayList<>();
		VestigesData.DUNGEON_ROOMS.all().forEach((id, room) -> {
			if (room.theme().equals(theme) && room.role().equals(role)) {
				out.add(new RoomLayout.Template(id.getPath(), role, room.shape(), room.size()));
			}
		});
		return out;
	}

	/** Plans the rooms of a run on a plot, or null when the theme lacks rooms for a role. */
	static List<DungeonRuns.Room> plan(String theme, int plot, long seed) {
		List<DungeonRuns.Room> rooms = RoomLayout.plan(plot, role -> pool(theme, role), new Random(seed));
		if (rooms == null) {
			return null;
		}
		List<DungeonRuns.Room> out = new ArrayList<>(rooms);
		out.set(0, out.get(0).withState(DungeonRuns.Room.CLEARED));
		return List.copyOf(out);
	}

	/** Builds every room up to {@code upTo} that is not built yet, with the corridors leading to them. */
	static DungeonRuns.Run ensureBuilt(ServerLevel level, DungeonRuns.Run run, int upTo) {
		DungeonTheme theme = theme(run.theme());
		DungeonRuns.Run r = run;
		for (int i = 0; i <= Math.min(upTo, r.rooms().size() - 1); i++) {
			DungeonRuns.Room room = r.rooms().get(i);
			if (room.built()) {
				continue;
			}
			DungeonRuns.Room built = RoomBuilder.build(level, room, theme, rng(r, i));
			r = r.withRoom(built);
			if (i > 0) {
				DungeonRuns.Room prev = r.rooms().get(i - 1);
				RoomBuilder.corridor(level, prev, built, theme, rng(r, 100 + i));
				if (prev.fighting() && prev.state() != DungeonRuns.Room.CLEARED) {
					RoomBuilder.gate(level, prev.eastWallX(), prev.centerZ(), true, theme, rng(r, 200 + i));
				}
			}
		}
		return r;
	}

	/** One look at a run: who walked into which room, and whose monsters are all dead. Returns the run, changed when something happened. */
	static DungeonRuns.Run progress(MinecraftServer server, ServerLevel level, DungeonRuns.Run run) {
		List<ServerPlayer> inside = new ArrayList<>();
		for (DungeonRuns.Member m : run.party()) {
			ServerPlayer p = m.out() ? null : server.getPlayerList().getPlayer(m.id());
			if (p != null && p.level() == level) {
				inside.add(p);
			}
		}
		if (inside.isEmpty()) {
			return run;
		}
		DungeonRuns.Run r = run;
		for (int i = 1; i < r.rooms().size(); i++) {
			DungeonRuns.Room room = r.rooms().get(i);
			if (!room.built()) {
				break;
			}
			if (room.state() == DungeonRuns.Room.IDLE && inside.stream().anyMatch(p -> room.inside(p.getX(), p.getY(), p.getZ(), 2.0))) {
				r = room.fighting() ? activate(level, r, room, inside) : reached(r, room, inside);
			} else if (room.state() == DungeonRuns.Room.ACTIVE && alive(level, r, room) == 0) {
				r = cleared(level, r, room, inside);
			}
		}
		return r;
	}

	private static void tell(List<ServerPlayer> players, String text, ChatFormatting color) {
		for (ServerPlayer p : players) {
			p.sendOverlayMessage(Component.literal(text).withStyle(color));
		}
	}

	/** The party enters a fighting room: the entrance gate drops, the monsters appear, the rooms ahead get built. */
	private static DungeonRuns.Run activate(ServerLevel level, DungeonRuns.Run run, DungeonRuns.Room room, List<ServerPlayer> players) {
		DungeonTheme theme = theme(run.theme());
		RoomBuilder.gate(level, room.westWallX(), room.centerZ(), true, theme, rng(run, 300 + room.index()));
		int spawned = spawnMobs(level, run, room, theme, players.size());
		if (!room.role().equals("combat")) {
			nameCard(players, theme, room);
		}
		Vestiges.LOGGER.info("Vestiges: dungeon run {} room {} ({}) active with {} monsters", run.id(), room.index(), room.role(), spawned);
		DungeonRuns.Run r = run.withRoom(room.withState(DungeonRuns.Room.ACTIVE));
		tell(players, "The doors close!", ChatFormatting.RED);
		return ensureBuilt(level, r, room.index() + AHEAD);
	}

	/** A room with nothing to fight (the reward room) counts as done once someone is in it. */
	private static DungeonRuns.Run reached(DungeonRuns.Run run, DungeonRuns.Room room, List<ServerPlayer> players) {
		tell(players, "You made it to the end.", ChatFormatting.GOLD);
		return run.withRoom(room.withState(DungeonRuns.Room.CLEARED));
	}

	/** The last monster is dead: both doors open again. */
	private static DungeonRuns.Run cleared(ServerLevel level, DungeonRuns.Run run, DungeonRuns.Room room, List<ServerPlayer> players) {
		DungeonTheme theme = theme(run.theme());
		RoomBuilder.gate(level, room.westWallX(), room.centerZ(), false, theme, rng(run, 400 + room.index()));
		if (room.index() + 1 < run.rooms().size() && run.rooms().get(room.index() + 1).built()) {
			RoomBuilder.gate(level, room.eastWallX(), room.centerZ(), false, theme, rng(run, 500 + room.index()));
		}
		tell(players, "The way on is open.", ChatFormatting.GREEN);
		return run.withRoom(room.withState(DungeonRuns.Room.CLEARED));
	}

	/** Monsters of a room that are still alive. */
	static int alive(ServerLevel level, DungeonRuns.Run run, DungeonRuns.Room room) {
		String tag = roomTag(run.id(), room.index());
		return level.getEntities((Entity) null, room.box(), e -> e.isAlive() && e.entityTags().contains(tag)).size();
	}

	/** Where the members of a boss pack stand around the boss spot. */
	private static final int[][] PACK = {{0, 0}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
	private static final Identifier PARTY_HEALTH = Vestiges.id("dungeon_party");

	/** The name card: the boss's name in big letters when the party walks into its room. */
	private static void nameCard(List<ServerPlayer> players, DungeonTheme theme, DungeonRuns.Room room) {
		boolean boss = room.role().equals("boss");
		DungeonTheme.Boss def = boss ? theme.boss() : theme.lieutenant();
		for (ServerPlayer p : players) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(theme.name() + (boss ? " Boss" : " Lieutenant")).withStyle(ChatFormatting.GRAY)));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(def.name()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
		}
	}

	/**
	 * Spawns a room's monsters. A fighting room holds 6 to 10 of the theme's monsters at the room's spawn points. The Lieutenant and Boss rooms hold
	 * the named boss (a pack for the Shellguard) at the boss spot. Every monster gets the tier's level (12 + 3 a tier) and +50% health for each
	 * player beyond the first; bosses carry their rank (Lieutenant x6, Boss x10 health) and their name.
	 */
	static int spawnMobs(ServerLevel level, DungeonRuns.Run run, DungeonRuns.Room room, DungeonTheme theme, int party) {
		Random random = rng(run, 600 + room.index());
		int made = 0;
		boolean bossRoom = room.role().equals("lieutenant") || room.role().equals("boss");
		if (bossRoom) {
			boolean boss = room.role().equals("boss");
			DungeonTheme.Boss def = boss ? theme.boss() : theme.lieutenant();
			BlockPos centre = room.boss().isEmpty() ? room.spawns().get(0) : room.boss().get(0);
			for (int i = 0; i < Math.max(1, def.count()); i++) {
				int[] o = PACK[i % PACK.length];
				if (summon(level, def.mob(), centre.offset(o[0], 0, o[1]), run, room, boss ? MobData.BOSS : MobData.LIEUTENANT, party, def.name()) != null) {
					made++;
				}
			}
			return made;
		}
		List<BlockPos> spots = new ArrayList<>(room.spawns());
		Collections.shuffle(spots, random);
		int count = DungeonRules.MIN_MONSTERS + random.nextInt(DungeonRules.MAX_MONSTERS - DungeonRules.MIN_MONSTERS + 1);
		for (int i = 0; i < count && !spots.isEmpty(); i++) {
			String id = theme.mobs().get(random.nextInt(theme.mobs().size()));
			if (summon(level, id, spots.get(i % spots.size()), run, room, MobData.NORMAL, party, null) != null) {
				made++;
			}
		}
		return made;
	}

	private static Entity summon(ServerLevel level, String id, BlockPos at, DungeonRuns.Run run, DungeonRuns.Room room, int rank, int party, String name) {
		Identifier key = Identifier.tryParse(id);
		EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
		if (type == null) {
			return null;
		}
		Entity e = type.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		if (e == null) {
			return null;
		}
		e.addTag(TAG);
		e.addTag(roomTag(run.id(), room.index()));
		if (e instanceof Mob mob) {
			mob.setPersistenceRequired();
			// With Mob levels switched off the monsters stay vanilla (the party still toughens them).
			if (MobService.enabled()) {
				MobService.apply(mob, new MobData(DungeonRules.mobLevel(run.tier()), rank, List.of(), 1.0f, false));
			}
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			double extra = DungeonRules.partyHealth(party) - 1.0;
			if (health != null && extra > 0) {
				health.addOrReplacePermanentModifier(new AttributeModifier(PARTY_HEALTH, extra, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			mob.setHealth(mob.getMaxHealth());
			if (name != null) {
				mob.setCustomName(Component.literal(name));
				mob.setCustomNameVisible(true);
			}
		}
		return e;
	}
}
