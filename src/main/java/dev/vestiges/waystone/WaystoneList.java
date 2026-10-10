package dev.vestiges.waystone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/** Every waystone in the world, stored as a persistent attachment on the overworld. Immutable: changes make a new list. */
public record WaystoneList(int nextId, List<Waystone> waystones) {
	public static final WaystoneList EMPTY = new WaystoneList(1, List.of());

	public static final Codec<WaystoneList> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("nextId", 1).forGetter(WaystoneList::nextId),
		Waystone.CODEC.listOf().optionalFieldOf("waystones", List.of()).forGetter(WaystoneList::waystones)
	).apply(i, WaystoneList::new));

	public Waystone byId(int id) {
		for (Waystone w : waystones) {
			if (w.id() == id) {
				return w;
			}
		}
		return null;
	}

	public Waystone at(String dimension, net.minecraft.core.BlockPos pos) {
		for (Waystone w : waystones) {
			if (w.dimension().equals(dimension) && w.pos().equals(pos)) {
				return w;
			}
		}
		return null;
	}

	public WaystoneList add(Waystone w) {
		List<Waystone> list = new ArrayList<>(waystones);
		list.add(w);
		return new WaystoneList(Math.max(nextId, w.id() + 1), List.copyOf(list));
	}

	public WaystoneList remove(int id) {
		List<Waystone> list = new ArrayList<>(waystones);
		list.removeIf(w -> w.id() == id);
		return new WaystoneList(nextId, List.copyOf(list));
	}

	public WaystoneList replace(Waystone w) {
		List<Waystone> list = new ArrayList<>(waystones);
		list.replaceAll(o -> o.id() == w.id() ? w : o);
		return new WaystoneList(nextId, List.copyOf(list));
	}
}
