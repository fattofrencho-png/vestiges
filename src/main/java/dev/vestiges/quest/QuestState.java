package dev.vestiges.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's quests: the ones running, how often each was finished and on which day, the tracked one, standing with each group, and the titles
 * earned. Standing is kept by group name, so regions and villages only add names later. Kept on death. Immutable.
 */
public record QuestState(Map<String, Active> active, Map<String, Integer> finished, Map<String, Long> finishedDay, String tracked,
						 Map<String, Integer> standing, Set<String> titles, String title) {
	public static final QuestState EMPTY = new QuestState(Map.of(), Map.of(), Map.of(), "", Map.of(), Set.of(), "");

	/**
	 * A running quest: its step, the count on it and the stat value it started from, who gave it (a villager's id, or ""), the subject picked for
	 * each step, and which time this is (1 = the first).
	 */
	public record Active(int step, int count, long base, String giver, List<String> picks, int comeback) {
		public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("step", 0).forGetter(Active::step),
			Codec.INT.optionalFieldOf("count", 0).forGetter(Active::count),
			Codec.LONG.optionalFieldOf("base", 0L).forGetter(Active::base),
			Codec.STRING.optionalFieldOf("giver", "").forGetter(Active::giver),
			Codec.STRING.listOf().optionalFieldOf("picks", List.of()).forGetter(Active::picks),
			Codec.INT.optionalFieldOf("comeback", 1).forGetter(Active::comeback)
		).apply(i, Active::new));

		public Active counted(int n) {
			return new Active(step, n, base, giver, picks, comeback);
		}

		public Active next(long newBase) {
			return new Active(step + 1, 0, newBase, giver, picks, comeback);
		}
	}

	public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Active.CODEC).optionalFieldOf("active", Map.of()).forGetter(QuestState::active),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("finished", Map.of()).forGetter(QuestState::finished),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("finished_day", Map.of()).forGetter(QuestState::finishedDay),
		Codec.STRING.optionalFieldOf("tracked", "").forGetter(QuestState::tracked),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("standing", Map.of()).forGetter(QuestState::standing),
		Codec.STRING.listOf().xmap(l -> (Set<String>) new HashSet<>(l), s -> new ArrayList<>(s)).optionalFieldOf("titles", Set.of()).forGetter(QuestState::titles),
		Codec.STRING.optionalFieldOf("title", "").forGetter(QuestState::title)
	).apply(i, QuestState::new));

	public boolean done(String id) {
		return finished.getOrDefault(id, 0) > 0;
	}

	public QuestState withActive(String id, Active a) {
		Map<String, Active> m = new HashMap<>(active);
		m.put(id, a);
		return new QuestState(Map.copyOf(m), finished, finishedDay, tracked.isEmpty() ? id : tracked, standing, titles, title);
	}

	public QuestState without(String id) {
		Map<String, Active> m = new HashMap<>(active);
		m.remove(id);
		return new QuestState(Map.copyOf(m), finished, finishedDay, tracked.equals(id) ? m.keySet().stream().sorted().findFirst().orElse("") : tracked, standing, titles, title);
	}

	public QuestState finish(String id, long day) {
		Map<String, Integer> f = new HashMap<>(finished);
		f.merge(id, 1, Integer::sum);
		Map<String, Long> d = new HashMap<>(finishedDay);
		d.put(id, day);
		QuestState s = without(id);
		return new QuestState(s.active, Map.copyOf(f), Map.copyOf(d), s.tracked, standing, titles, title);
	}

	/** Forgets one quest entirely (admin). */
	public QuestState reset(String id) {
		Map<String, Integer> f = new HashMap<>(finished);
		f.remove(id);
		Map<String, Long> d = new HashMap<>(finishedDay);
		d.remove(id);
		QuestState s = without(id);
		return new QuestState(s.active, Map.copyOf(f), Map.copyOf(d), s.tracked, standing, titles, title);
	}

	public QuestState tracking(String id) {
		return new QuestState(active, finished, finishedDay, id, standing, titles, title);
	}

	public QuestState withStanding(Map<String, Integer> s) {
		return new QuestState(active, finished, finishedDay, tracked, Map.copyOf(s), titles, title);
	}

	public QuestState earned(String t) {
		Set<String> s = new HashSet<>(titles);
		s.add(t);
		return new QuestState(active, finished, finishedDay, tracked, standing, Set.copyOf(s), title);
	}

	public QuestState wearing(String t) {
		return new QuestState(active, finished, finishedDay, tracked, standing, titles, t);
	}
}
