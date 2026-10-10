package dev.vestiges.waystone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/** One placed waystone (a lodestone placed from an earned Waystone item). {@code dimension} is the level id, e.g. "minecraft:overworld". */
public record Waystone(int id, String name, String dimension, BlockPos pos, UUID owner, List<UUID> sharedWith) {
	public static final Codec<Waystone> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(Waystone::id),
		Codec.STRING.fieldOf("name").forGetter(Waystone::name),
		Codec.STRING.fieldOf("dimension").forGetter(Waystone::dimension),
		BlockPos.CODEC.fieldOf("pos").forGetter(Waystone::pos),
		UUIDUtil.CODEC.fieldOf("owner").forGetter(Waystone::owner),
		UUIDUtil.CODEC.listOf().optionalFieldOf("sharedWith", List.of()).forGetter(Waystone::sharedWith)
	).apply(i, Waystone::new));

	/** The owner and every player it was shared with may travel to it. */
	public boolean usableBy(UUID player) {
		return owner.equals(player) || sharedWith.contains(player);
	}

	public Waystone withName(String newName) {
		return new Waystone(id, newName, dimension, pos, owner, sharedWith);
	}

	public Waystone sharedWith(UUID player) {
		if (sharedWith.contains(player)) {
			return this;
		}
		List<UUID> list = new ArrayList<>(sharedWith);
		list.add(player);
		return new Waystone(id, name, dimension, pos, owner, List.copyOf(list));
	}
}
