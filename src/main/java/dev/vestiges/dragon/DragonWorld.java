package dev.vestiges.dragon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * What a world knows about its dragons, saved on the overworld: {@code kills} is the world's kill count (goes up only on a real kill), {@code active}
 * the number of the dragon being fought (0 = none), and {@code hurt} the players who hurt it or its crystals (they are rewarded when it dies, even if
 * they log out and in during the fight).
 */
public record DragonWorld(int kills, int active, List<UUID> hurt) {
	public static final DragonWorld EMPTY = new DragonWorld(0, 0, List.of());

	public static final Codec<DragonWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("kills", 0).forGetter(DragonWorld::kills),
		Codec.INT.optionalFieldOf("active", 0).forGetter(DragonWorld::active),
		UUIDUtil.CODEC.listOf().optionalFieldOf("hurt", List.of()).forGetter(DragonWorld::hurt)
	).apply(i, DragonWorld::new));

	public DragonWorld withKills(int n) {
		return new DragonWorld(Math.max(0, n), active, hurt);
	}

	/** Starts a fight with dragon {@code n} (nobody has hurt it yet). */
	public DragonWorld fighting(int n) {
		return new DragonWorld(kills, n, List.of());
	}

	/** The fight is over (won, lost or reset): nobody is credited any more. */
	public DragonWorld idle() {
		return new DragonWorld(kills, 0, List.of());
	}

	public DragonWorld withHurt(UUID player) {
		if (hurt.contains(player)) {
			return this;
		}
		List<UUID> next = new ArrayList<>(hurt);
		next.add(player);
		return new DragonWorld(kills, active, List.copyOf(next));
	}
}
