package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.AABB;

/**
 * Every running dungeon of the world, saved on the overworld. A run owns one plot of the dungeon world, a party, and its rooms; each member
 * remembers where to be sent back to. A plot whose run ended is cleared first and only then handed out again (lowest first). Immutable.
 */
public record DungeonRuns(List<Run> runs, List<Integer> freePlots, List<Dirty> clearing, int nextPlot, int nextRun) {
	public static final DungeonRuns EMPTY = new DungeonRuns(List.of(), List.of(), List.of(), 0, 1);

	/** A plot that still has blocks to clear: the stretch of the strip (west to east, in blocks) the run built on. */
	public record Dirty(int plot, int fromX, int toX) {
		public static final Codec<Dirty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(Dirty::plot),
			Codec.INT.fieldOf("from_x").forGetter(Dirty::fromX),
			Codec.INT.fieldOf("to_x").forGetter(Dirty::toX)
		).apply(i, Dirty::new));
	}

	/** One player of a run: lives left, whether they are out for good, and where the portal sent them from. */
	public record Member(UUID id, String name, int lives, boolean out, String returnDim, BlockPos returnPos) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Member::name),
			Codec.INT.fieldOf("lives").forGetter(Member::lives),
			Codec.BOOL.optionalFieldOf("out", false).forGetter(Member::out),
			Codec.STRING.fieldOf("return_dim").forGetter(Member::returnDim),
			BlockPos.CODEC.fieldOf("return_pos").forGetter(Member::returnPos)
		).apply(i, Member::new));

		public Member withOut() {
			return new Member(id, name, lives, true, returnDim, returnPos);
		}

		public Member withLives(int left) {
			return new Member(id, name, left, out, returnDim, returnPos);
		}
	}

	/**
	 * One room of a run: where it stands, how far it has got ({@link #IDLE}, {@link #ACTIVE} while its monsters live, {@link #CLEARED}), and its
	 * markers: where monsters may spawn, where the cache goes and the boss spot. {@code corridorAfter} is the length of the corridor to the next room.
	 */
	public record Room(int index, String role, String template, String shape, int x, int z, int size, int height, int corridorAfter, int state,
					   boolean built, List<BlockPos> spawns, List<BlockPos> caches, List<BlockPos> boss) {
		public static final int IDLE = 0;
		public static final int ACTIVE = 1;
		public static final int CLEARED = 2;

		public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("index").forGetter(Room::index),
			Codec.STRING.fieldOf("role").forGetter(Room::role),
			Codec.STRING.fieldOf("template").forGetter(Room::template),
			Codec.STRING.optionalFieldOf("shape", "arena").forGetter(Room::shape),
			Codec.INT.fieldOf("x").forGetter(Room::x),
			Codec.INT.fieldOf("z").forGetter(Room::z),
			Codec.INT.fieldOf("size").forGetter(Room::size),
			Codec.INT.fieldOf("height").forGetter(Room::height),
			Codec.INT.optionalFieldOf("corridor_after", 0).forGetter(Room::corridorAfter),
			Codec.INT.optionalFieldOf("state", IDLE).forGetter(Room::state),
			Codec.BOOL.optionalFieldOf("built", false).forGetter(Room::built),
			BlockPos.CODEC.listOf().optionalFieldOf("spawns", List.of()).forGetter(Room::spawns),
			BlockPos.CODEC.listOf().optionalFieldOf("caches", List.of()).forGetter(Room::caches),
			BlockPos.CODEC.listOf().optionalFieldOf("boss", List.of()).forGetter(Room::boss)
		).apply(i, Room::new));

		/** The middle line of the room, where the doors are. */
		public int centerZ() {
			return z + size / 2;
		}

		/** The wall column of the west (entrance) door and of the east (exit) door. */
		public int westWallX() {
			return x - 1;
		}

		public int eastWallX() {
			return x + size;
		}

		/** Monsters live in fighting rooms: combat, lieutenant and boss. */
		public boolean fighting() {
			return role.equals("combat") || role.equals("lieutenant") || role.equals("boss");
		}

		/** Inside the room (floor to ceiling) by this many blocks from its walls. */
		public boolean inside(double px, double py, double pz, double margin) {
			return px >= x + margin && px < x + size - margin && pz >= z + margin && pz < z + size - margin && py >= DungeonPlots.FLOOR_Y - 5
				&& py <= DungeonPlots.FLOOR_Y + height + 1;
		}

		/** The room's air and walls, for finding what lives in it. */
		public AABB box() {
			return new AABB(x - 1, DungeonPlots.FLOOR_Y - 5, z - 1, x + size + 1, DungeonPlots.FLOOR_Y + height + 2, z + size + 1);
		}

		public Room withState(int next) {
			return new Room(index, role, template, shape, x, z, size, height, corridorAfter, next, built, spawns, caches, boss);
		}

		public Room withMarkers(List<BlockPos> newSpawns, List<BlockPos> newCaches, List<BlockPos> newBoss) {
			return new Room(index, role, template, shape, x, z, size, height, corridorAfter, state, true, List.copyOf(newSpawns), List.copyOf(newCaches), List.copyOf(newBoss));
		}
	}

	/** A run: its plot, theme and tier, the party, and its rooms. */
	public record Run(int id, int plot, String theme, int tier, long started, List<Member> party, List<Room> rooms) {
		public static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Run::id),
			Codec.INT.fieldOf("plot").forGetter(Run::plot),
			Codec.STRING.fieldOf("theme").forGetter(Run::theme),
			Codec.INT.fieldOf("tier").forGetter(Run::tier),
			Codec.LONG.optionalFieldOf("started", 0L).forGetter(Run::started),
			Member.CODEC.listOf().optionalFieldOf("party", List.of()).forGetter(Run::party),
			Room.CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(Run::rooms)
		).apply(i, Run::new));

		public Member member(UUID player) {
			for (Member m : party) {
				if (m.id().equals(player)) {
					return m;
				}
			}
			return null;
		}

		public Run withMember(Member updated) {
			List<Member> next = new ArrayList<>();
			for (Member m : party) {
				next.add(m.id().equals(updated.id()) ? updated : m);
			}
			return new Run(id, plot, theme, tier, started, List.copyOf(next), rooms);
		}

		public Run withRoom(Room updated) {
			List<Room> next = new ArrayList<>();
			for (Room r : rooms) {
				next.add(r.index() == updated.index() ? updated : r);
			}
			return new Run(id, plot, theme, tier, started, party, List.copyOf(next));
		}

		/** Players who have not left or run out of lives. */
		public boolean anyoneIn() {
			return party.stream().anyMatch(m -> !m.out());
		}
	}

	public static final Codec<DungeonRuns> CODEC = RecordCodecBuilder.create(i -> i.group(
		Run.CODEC.listOf().optionalFieldOf("runs", List.of()).forGetter(DungeonRuns::runs),
		Codec.INT.listOf().optionalFieldOf("free_plots", List.of()).forGetter(DungeonRuns::freePlots),
		Dirty.CODEC.listOf().optionalFieldOf("clearing", List.of()).forGetter(DungeonRuns::clearing),
		Codec.INT.optionalFieldOf("next_plot", 0).forGetter(DungeonRuns::nextPlot),
		Codec.INT.optionalFieldOf("next_run", 1).forGetter(DungeonRuns::nextRun)
	).apply(i, DungeonRuns::new));

	/** The plot the next run takes: the lowest cleared one, or a new one. */
	public int peekPlot() {
		return freePlots.isEmpty() ? nextPlot : freePlots.stream().min(Integer::compare).orElse(nextPlot);
	}

	/** Adds a run, taking its plot out of the free list. */
	public DungeonRuns with(Run run) {
		List<Integer> free = new ArrayList<>(freePlots);
		free.remove(Integer.valueOf(run.plot()));
		List<Run> next = new ArrayList<>(runs);
		next.add(run);
		return new DungeonRuns(List.copyOf(next), List.copyOf(free), clearing, Math.max(nextPlot, run.plot() + 1), Math.max(nextRun, run.id() + 1));
	}

	public DungeonRuns replace(Run run) {
		List<Run> next = new ArrayList<>();
		for (Run r : runs) {
			next.add(r.id() == run.id() ? run : r);
		}
		return new DungeonRuns(List.copyOf(next), freePlots, clearing, nextPlot, nextRun);
	}

	/** Removes a finished run. Its plot is cleared (the stretch its rooms stood on) before it is handed out again. */
	public DungeonRuns end(int runId) {
		List<Run> next = new ArrayList<>();
		List<Dirty> clear = new ArrayList<>(clearing);
		for (Run r : runs) {
			if (r.id() == runId) {
				int from = DungeonPlots.originX(r.plot());
				int to = from + 40;
				boolean any = false;
				for (Room room : r.rooms()) {
					if (room.built()) {
						from = any ? Math.min(from, room.x() - 2) : room.x() - 2;
						to = any ? Math.max(to, room.x() + room.size() + 2) : room.x() + room.size() + 2;
						any = true;
					}
				}
				clear.add(new Dirty(r.plot(), from, to));
			} else {
				next.add(r);
			}
		}
		return new DungeonRuns(List.copyOf(next), freePlots, List.copyOf(clear), nextPlot, nextRun);
	}

	/** A plot has been cleared: it can be used again. */
	public DungeonRuns cleared(int plot) {
		List<Dirty> clear = new ArrayList<>(clearing);
		clear.removeIf(d -> d.plot() == plot);
		List<Integer> free = new ArrayList<>(freePlots);
		if (!free.contains(plot)) {
			free.add(plot);
		}
		return new DungeonRuns(runs, List.copyOf(free), List.copyOf(clear), nextPlot, nextRun);
	}

	public boolean isClearing(int plot) {
		return clearing.stream().anyMatch(d -> d.plot() == plot);
	}

	public Run byPlayer(UUID player) {
		for (Run r : runs) {
			Member m = r.member(player);
			if (m != null && !m.out()) {
				return r;
			}
		}
		return null;
	}

	public Run byPlot(int plot) {
		for (Run r : runs) {
			if (r.plot() == plot) {
				return r;
			}
		}
		return null;
	}

	public Run byId(int id) {
		for (Run r : runs) {
			if (r.id() == id) {
				return r;
			}
		}
		return null;
	}
}
