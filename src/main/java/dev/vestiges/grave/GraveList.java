package dev.vestiges.grave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Every grave in the world, stored as a persistent attachment on the overworld. Immutable: changes make a new list. */
public record GraveList(int nextId, List<Grave> graves) {
	public static final GraveList EMPTY = new GraveList(1, List.of());

	public static final Codec<GraveList> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("nextId", 1).forGetter(GraveList::nextId),
		Grave.CODEC.listOf().optionalFieldOf("graves", List.of()).forGetter(GraveList::graves)
	).apply(i, GraveList::new));

	public Grave at(String dimension, BlockPos pos) {
		for (Grave g : graves) {
			if (g.dimension().equals(dimension) && g.pos().equals(pos)) {
				return g;
			}
		}
		return null;
	}

	public GraveList add(Grave g) {
		List<Grave> list = new ArrayList<>(graves);
		list.add(g);
		return new GraveList(Math.max(nextId, g.id() + 1), List.copyOf(list));
	}

	public GraveList remove(int id) {
		List<Grave> list = new ArrayList<>(graves);
		list.removeIf(g -> g.id() == id);
		return new GraveList(nextId, List.copyOf(list));
	}

	public GraveList replace(Grave g) {
		List<Grave> list = new ArrayList<>(graves);
		list.replaceAll(o -> o.id() == g.id() ? g : o);
		return new GraveList(nextId, List.copyOf(list));
	}
}
