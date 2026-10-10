package dev.vestiges.grave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * A gravestone: the owner's player head at the spot they died, holding their items. After the agreed number of Minecraft days it "decays":
 * it turns into a skeleton skull and keeps only the most valuable item. {@code created} is the overworld game time.
 */
public record Grave(int id, UUID owner, String ownerName, String dimension, BlockPos pos, long created, boolean warned, boolean decayed,
					List<ItemStack> items) {
	public static final Codec<Grave> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(Grave::id),
		UUIDUtil.CODEC.fieldOf("owner").forGetter(Grave::owner),
		Codec.STRING.optionalFieldOf("ownerName", "").forGetter(Grave::ownerName),
		Codec.STRING.fieldOf("dimension").forGetter(Grave::dimension),
		BlockPos.CODEC.fieldOf("pos").forGetter(Grave::pos),
		Codec.LONG.fieldOf("created").forGetter(Grave::created),
		Codec.BOOL.optionalFieldOf("warned", false).forGetter(Grave::warned),
		Codec.BOOL.optionalFieldOf("decayed", false).forGetter(Grave::decayed),
		ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Grave::items)
	).apply(i, Grave::new));

	public Grave warnedNow() {
		return new Grave(id, owner, ownerName, dimension, pos, created, true, decayed, items);
	}

	public Grave decayTo(List<ItemStack> kept) {
		return new Grave(id, owner, ownerName, dimension, pos, created, true, true, List.copyOf(kept));
	}
}
