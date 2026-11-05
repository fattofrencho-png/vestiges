package dev.vestiges.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.data.VillagerLoreDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Who a villager is: a name and a background, made once when it first loads and never changed (history grows later, once recruiting exists).
 * {@code hook} is empty for most villagers; about 1 in 50 gets a rare lore hook.
 */
public record VillagerProfile(String first, String byname, String birthplace, String family, String turning, String wants, List<String> traits, String hook) {
	public static final Codec<VillagerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("first").forGetter(VillagerProfile::first),
		Codec.STRING.fieldOf("byname").forGetter(VillagerProfile::byname),
		Codec.STRING.fieldOf("birthplace").forGetter(VillagerProfile::birthplace),
		Codec.STRING.fieldOf("family").forGetter(VillagerProfile::family),
		Codec.STRING.fieldOf("turning").forGetter(VillagerProfile::turning),
		Codec.STRING.fieldOf("wants").forGetter(VillagerProfile::wants),
		Codec.STRING.listOf().fieldOf("traits").forGetter(VillagerProfile::traits),
		Codec.STRING.optionalFieldOf("hook", "").forGetter(VillagerProfile::hook)
	).apply(i, VillagerProfile::new));
	public static final StreamCodec<io.netty.buffer.ByteBuf, VillagerProfile> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final int HOOK_ONE_IN = 50;

	public String name() {
		return byname.isEmpty() ? first : first + " " + byname;
	}

	/** Two short lines for the look-at HUD and the Overview. */
	public List<String> overview() {
		List<String> lines = new ArrayList<>();
		lines.add("Born in " + birthplace + ", of " + family + ".");
		lines.add("Once " + turning + ". Now wants " + wants + ".");
		return lines;
	}

	/**
	 * Makes a profile from the lore lists. The same seed always gives the same villager. {@code continent} is the continent the villager lives on and
	 * {@code otherContinent} one far away; both are null in a world without continents, which then only has "a village near here".
	 */
	public static VillagerProfile generate(VillagerLoreDef lore, long seed, String continent, String otherContinent) {
		Random r = new Random(seed);
		String first = pick(lore.firstNames(), r);
		VillagerLoreDef.Turning turning = lore.turningPoints().get(r.nextInt(lore.turningPoints().size()));
		String byname = !turning.byname().isEmpty() ? turning.byname() : pick(lore.bynames(), r);
		int roll = r.nextInt(10);
		String birthplace;
		if (continent == null || roll < 6) {
			birthplace = lore.birthplaceNear();
		} else if (roll < 9 || otherContinent == null) {
			birthplace = lore.birthplaceRegion().replace("{continent}", continent);
		} else {
			birthplace = lore.birthplaceFar().replace("{continent}", otherContinent);
		}
		List<String> traits = new ArrayList<>();
		int count = Math.min(2, lore.traits().size());
		while (traits.size() < count) {
			String t = pick(lore.traits(), r);
			if (!traits.contains(t)) {
				traits.add(t);
			}
		}
		String hook = !lore.hooks().isEmpty() && r.nextInt(HOOK_ONE_IN) == 0 ? pick(lore.hooks(), r) : "";
		return new VillagerProfile(first, byname, birthplace, pick(lore.familyTrades(), r), turning.text(), pick(lore.wants(), r), List.copyOf(traits), hook);
	}

	private static String pick(List<String> list, Random r) {
		return list.get(r.nextInt(list.size()));
	}
}
