package dev.vestiges.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * Every running dungeon of the world, saved on the overworld. A run owns one plot of the dungeon world and a party; each member remembers where
 * to be sent back to. Plots that ended are handed out again (lowest first). Immutable.
 */
public record DungeonRuns(List<Run> runs, List<Integer> freePlots, int nextPlot, int nextRun) {
	public static final DungeonRuns EMPTY = new DungeonRuns(List.of(), List.of(), 0, 1);

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

	/** A run: its plot, theme and tier, the party, and how far it has got. */
	public record Run(int id, int plot, String theme, int tier, long started, List<Member> party) {
		public static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Run::id),
			Codec.INT.fieldOf("plot").forGetter(Run::plot),
			Codec.STRING.fieldOf("theme").forGetter(Run::theme),
			Codec.INT.fieldOf("tier").forGetter(Run::tier),
			Codec.LONG.optionalFieldOf("started", 0L).forGetter(Run::started),
			Member.CODEC.listOf().optionalFieldOf("party", List.of()).forGetter(Run::party)
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
			return new Run(id, plot, theme, tier, started, List.copyOf(next));
		}

		/** Players who have not left or run out of lives. */
		public boolean anyoneIn() {
			return party.stream().anyMatch(m -> !m.out());
		}
	}

	public static final Codec<DungeonRuns> CODEC = RecordCodecBuilder.create(i -> i.group(
		Run.CODEC.listOf().optionalFieldOf("runs", List.of()).forGetter(DungeonRuns::runs),
		Codec.INT.listOf().optionalFieldOf("free_plots", List.of()).forGetter(DungeonRuns::freePlots),
		Codec.INT.optionalFieldOf("next_plot", 0).forGetter(DungeonRuns::nextPlot),
		Codec.INT.optionalFieldOf("next_run", 1).forGetter(DungeonRuns::nextRun)
	).apply(i, DungeonRuns::new));

	/** The plot the next run takes: the lowest freed one, or a new one. */
	public int peekPlot() {
		return freePlots.isEmpty() ? nextPlot : freePlots.stream().min(Integer::compare).orElse(nextPlot);
	}

	/** Adds a run, taking its plot out of the free list. */
	public DungeonRuns with(Run run) {
		List<Integer> free = new ArrayList<>(freePlots);
		free.remove(Integer.valueOf(run.plot()));
		List<Run> next = new ArrayList<>(runs);
		next.add(run);
		return new DungeonRuns(List.copyOf(next), List.copyOf(free), Math.max(nextPlot, run.plot() + 1), Math.max(nextRun, run.id() + 1));
	}

	public DungeonRuns replace(Run run) {
		List<Run> next = new ArrayList<>();
		for (Run r : runs) {
			next.add(r.id() == run.id() ? run : r);
		}
		return new DungeonRuns(List.copyOf(next), freePlots, nextPlot, nextRun);
	}

	/** Removes a finished run and frees its plot. */
	public DungeonRuns end(int runId) {
		List<Run> next = new ArrayList<>();
		List<Integer> free = new ArrayList<>(freePlots);
		for (Run r : runs) {
			if (r.id() == runId) {
				free.add(r.plot());
			} else {
				next.add(r);
			}
		}
		return new DungeonRuns(List.copyOf(next), List.copyOf(free), nextPlot, nextRun);
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

	public Run byId(int id) {
		for (Run r : runs) {
			if (r.id() == id) {
				return r;
			}
		}
		return null;
	}
}
