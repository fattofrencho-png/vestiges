package dev.vestiges.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** Saved settlement data (M5), kept on the overworld: every settlement and every placed marker. Immutable records; changes make new ones. */
public final class SettlementData {
	private SettlementData() {
	}

	public static final String SETUP = "setup";
	public static final String ACTIVE = "active";
	public static final String INACTIVE = "inactive";
	/** "No Bell placed" (the Bell was broken; the settlement waits to be set up again). */
	public static final long NO_POS = Long.MIN_VALUE;

	/** The renown scores from the last count, the level, and the bookkeeping for level-downs and building XP. */
	public record Stats(int size, int variety, int homes, int people, int deeds, int level, int bestLevel, int belowTicks, int bestPoints) {
		public static final Stats START = new Stats(0, 0, 0, 0, 0, 1, 1, 0, 0);
		public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("size", 0).forGetter(Stats::size),
			Codec.INT.optionalFieldOf("variety", 0).forGetter(Stats::variety),
			Codec.INT.optionalFieldOf("homes", 0).forGetter(Stats::homes),
			Codec.INT.optionalFieldOf("people", 0).forGetter(Stats::people),
			Codec.INT.optionalFieldOf("deeds", 0).forGetter(Stats::deeds),
			Codec.INT.optionalFieldOf("level", 1).forGetter(Stats::level),
			Codec.INT.optionalFieldOf("best_level", 1).forGetter(Stats::bestLevel),
			Codec.INT.optionalFieldOf("below_ticks", 0).forGetter(Stats::belowTicks),
			Codec.INT.optionalFieldOf("best_points", 0).forGetter(Stats::bestPoints)
		).apply(i, Stats::new));

		public int total() {
			return size + variety + homes + people + deeds;
		}
	}

	/** Block and biome Codex progress shared by the settlement's members (highest count wins). */
	public record Shared(Map<String, Integer> counts, Map<String, List<Long>> spots) {
		public static final Shared EMPTY = new Shared(Map.of(), Map.of());
		public static final Codec<Shared> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(Shared::counts),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("spots", Map.of()).forGetter(Shared::spots)
		).apply(i, Shared::new));
	}

	public record Settlement(String id, String name, UUID owner, List<UUID> members, String dimension, long bell, String state, int setupLeft,
							 Stats stats, Shared shared) {
		public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Settlement::id),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Settlement::name),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Settlement::owner),
			UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Settlement::members),
			Codec.STRING.fieldOf("dimension").forGetter(Settlement::dimension),
			Codec.LONG.optionalFieldOf("bell", NO_POS).forGetter(Settlement::bell),
			Codec.STRING.optionalFieldOf("state", SETUP).forGetter(Settlement::state),
			Codec.INT.optionalFieldOf("setup_left", 0).forGetter(Settlement::setupLeft),
			Stats.CODEC.optionalFieldOf("stats", Stats.START).forGetter(Settlement::stats),
			Shared.CODEC.optionalFieldOf("shared", Shared.EMPTY).forGetter(Settlement::shared)
		).apply(i, Settlement::new));

		public boolean hasBell() {
			return bell != NO_POS;
		}

		public BlockPos bellPos() {
			return BlockPos.of(bell);
		}

		public int radius() {
			return Renown.radius(stats.level());
		}

		/** Setting up or active: protected, spawn-proof once active. */
		public boolean standing() {
			return hasBell() && !INACTIVE.equals(state);
		}

		public boolean active() {
			return hasBell() && ACTIVE.equals(state);
		}

		public boolean member(UUID player) {
			return owner.equals(player) || members.contains(player);
		}

		public String displayName() {
			return name.isEmpty() ? "Unnamed settlement" : name;
		}

		public Settlement withState(String s, int left) {
			return new Settlement(id, name, owner, members, dimension, bell, s, left, stats, shared);
		}

		public Settlement withBell(long pos, String dim) {
			return new Settlement(id, name, owner, members, dim, pos, pos == NO_POS ? INACTIVE : SETUP, MarkerType.BELL.setupTicks, stats, shared);
		}

		public Settlement withName(String n) {
			return new Settlement(id, n, owner, members, dimension, bell, state, setupLeft, stats, shared);
		}

		public Settlement withMembers(List<UUID> m) {
			return new Settlement(id, name, owner, List.copyOf(m), dimension, bell, state, setupLeft, stats, shared);
		}

		public Settlement withStats(Stats s) {
			return new Settlement(id, name, owner, members, dimension, bell, state, setupLeft, s, shared);
		}

		public Settlement withShared(Shared s) {
			return new Settlement(id, name, owner, members, dimension, bell, state, setupLeft, stats, s);
		}
	}

	/** A placed Banner, Ward Lantern or Gold Block. */
	public record Marker(int id, String type, UUID owner, String dimension, long pos, String name, int setupLeft) {
		public static final Codec<Marker> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Marker::id),
			Codec.STRING.fieldOf("type").forGetter(Marker::type),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Marker::owner),
			Codec.STRING.fieldOf("dimension").forGetter(Marker::dimension),
			Codec.LONG.fieldOf("pos").forGetter(Marker::pos),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Marker::name),
			Codec.INT.optionalFieldOf("setup_left", 0).forGetter(Marker::setupLeft)
		).apply(i, Marker::new));

		public MarkerType kind() {
			return MarkerType.byId(type);
		}

		public BlockPos blockPos() {
			return BlockPos.of(pos);
		}

		public boolean active() {
			return setupLeft <= 0;
		}

		public Marker withSetupLeft(int left) {
			return new Marker(id, type, owner, dimension, pos, name, left);
		}

		public Marker withName(String n) {
			return new Marker(id, type, owner, dimension, pos, n, setupLeft);
		}
	}

	public record World(List<Settlement> settlements, List<Marker> markers, int nextId) {
		public static final World EMPTY = new World(List.of(), List.of(), 1);
		public static final Codec<World> CODEC = RecordCodecBuilder.create(i -> i.group(
			Settlement.CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(World::settlements),
			Marker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(World::markers),
			Codec.INT.optionalFieldOf("next_id", 1).forGetter(World::nextId)
		).apply(i, World::new));

		public Settlement settlement(String id) {
			for (Settlement s : settlements) {
				if (s.id().equals(id)) {
					return s;
				}
			}
			return null;
		}

		public Marker marker(int id) {
			for (Marker m : markers) {
				if (m.id() == id) {
					return m;
				}
			}
			return null;
		}

		public World put(Settlement s) {
			List<Settlement> list = new ArrayList<>(settlements);
			list.removeIf(x -> x.id().equals(s.id()));
			list.add(s);
			return new World(List.copyOf(list), markers, nextId);
		}

		public World update(String id, UnaryOperator<Settlement> change) {
			Settlement s = settlement(id);
			return s == null ? this : put(change.apply(s));
		}

		public World add(Marker m) {
			List<Marker> list = new ArrayList<>(markers);
			list.add(m);
			return new World(settlements, List.copyOf(list), Math.max(nextId, m.id() + 1));
		}

		public World putMarker(Marker m) {
			List<Marker> list = new ArrayList<>(markers);
			list.removeIf(x -> x.id() == m.id());
			list.add(m);
			return new World(settlements, List.copyOf(list), nextId);
		}

		public World removeMarker(int id) {
			List<Marker> list = new ArrayList<>(markers);
			list.removeIf(x -> x.id() == id);
			return new World(settlements, List.copyOf(list), nextId);
		}
	}
}
