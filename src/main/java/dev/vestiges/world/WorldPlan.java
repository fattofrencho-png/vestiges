package dev.vestiges.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * What a Vestiges world is meant to hold: continents, climate, villages and resource sites. The seed varies the details. The first land is the spawn
 * continent. Read through {@link WorldPlans}, which keeps each world on its own copy.
 */
public record WorldPlan(int safeGap, int islandGap, int edgeGap, int spawnClear, int emeraldMaxPerLand, int emeraldMinTotal, int emeraldRadius, double coldTilt, List<Point> temperature, List<Land> lands, List<String> landOrder, List<Village> villages, List<Resource> resources) {
	public static final Codec<WorldPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("safe_gap").forGetter(WorldPlan::safeGap),
		Codec.INT.fieldOf("island_gap").forGetter(WorldPlan::islandGap),
		Codec.INT.fieldOf("edge_gap").forGetter(WorldPlan::edgeGap),
		Codec.INT.fieldOf("spawn_clear").forGetter(WorldPlan::spawnClear),
		Codec.INT.fieldOf("emerald_max_per_land").forGetter(WorldPlan::emeraldMaxPerLand),
		Codec.INT.fieldOf("emerald_min_total").forGetter(WorldPlan::emeraldMinTotal),
		Codec.INT.fieldOf("emerald_radius").forGetter(WorldPlan::emeraldRadius),
		Codec.DOUBLE.fieldOf("cold_tilt").forGetter(WorldPlan::coldTilt),
		Point.CODEC.listOf().fieldOf("temperature").forGetter(WorldPlan::temperature),
		Land.CODEC.listOf().fieldOf("lands").forGetter(WorldPlan::lands),
		Codec.STRING.listOf().fieldOf("land_order").forGetter(WorldPlan::landOrder),
		Village.CODEC.listOf().fieldOf("villages").forGetter(WorldPlan::villages),
		Resource.CODEC.listOf().fieldOf("resources").forGetter(WorldPlan::resources)
	).apply(i, WorldPlan::new));

	/** Temperature in vanilla's units at a distance along the tilted line, where -z is north. */
	public record Point(double at, double value) {
		static final Codec<Point> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("at").forGetter(Point::at),
			Codec.DOUBLE.fieldOf("value").forGetter(Point::value)
		).apply(i, Point::new));
	}

	/** Rough place and size, and the character of its mountains. Directions and shapes come from the seed. */
	public record Land(String name, int x, int z, int r, double sx, double sz, double core, double widthMin, double widthMax, double lenMin, double lenMax, int mains) {
		static final Codec<Land> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Land::name),
			Codec.INT.fieldOf("x").forGetter(Land::x),
			Codec.INT.fieldOf("z").forGetter(Land::z),
			Codec.INT.fieldOf("r").forGetter(Land::r),
			Codec.DOUBLE.fieldOf("sx").forGetter(Land::sx),
			Codec.DOUBLE.fieldOf("sz").forGetter(Land::sz),
			Codec.DOUBLE.fieldOf("core").forGetter(Land::core),
			Codec.DOUBLE.fieldOf("width_min").forGetter(Land::widthMin),
			Codec.DOUBLE.fieldOf("width_max").forGetter(Land::widthMax),
			Codec.DOUBLE.fieldOf("len_min").forGetter(Land::lenMin),
			Codec.DOUBLE.fieldOf("len_max").forGetter(Land::lenMax),
			Codec.INT.fieldOf("mains").forGetter(Land::mains)
		).apply(i, Land::new));
	}

	/** A pre-placed village: which land it is on, where on that land, and how big. */
	public record Village(String name, String land, String site, String size) {
		static final Codec<Village> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Village::name),
			Codec.STRING.fieldOf("land").forGetter(Village::land),
			Codec.STRING.fieldOf("site").forGetter(Village::site),
			Codec.STRING.fieldOf("size").forGetter(Village::size)
		).apply(i, Village::new));
	}

	/** A resource limited to chosen places. */
	public record Resource(String block, String land, String site) {
		static final Codec<Resource> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("block").forGetter(Resource::block),
			Codec.STRING.fieldOf("land").forGetter(Resource::land),
			Codec.STRING.fieldOf("site").forGetter(Resource::site)
		).apply(i, Resource::new));
	}

	public int landIndex(String name) {
		for (int i = 0; i < lands.size(); i++) {
			if (lands.get(i).name().equals(name)) {
				return i;
			}
		}
		throw new IllegalArgumentException("No land called " + name);
	}
}
