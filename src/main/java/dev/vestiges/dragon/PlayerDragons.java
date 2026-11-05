package dev.vestiges.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The dragons one player has been rewarded for, by number. It decides the ender chest pages and the dragon feats. Kept on death. */
public record PlayerDragons(List<Integer> done) {
	public static final PlayerDragons EMPTY = new PlayerDragons(List.of());

	public static final Codec<PlayerDragons> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.listOf().optionalFieldOf("done", List.of()).forGetter(PlayerDragons::done)
	).apply(i, PlayerDragons::new));

	public boolean has(int number) {
		return done.contains(number);
	}

	public int count() {
		return done.size();
	}

	/** The highest dragon number rewarded, or 0. */
	public int best() {
		return done.isEmpty() ? 0 : Collections.max(done);
	}

	public PlayerDragons with(int number) {
		if (has(number)) {
			return this;
		}
		List<Integer> next = new ArrayList<>(done);
		next.add(number);
		Collections.sort(next);
		return new PlayerDragons(List.copyOf(next));
	}
}
