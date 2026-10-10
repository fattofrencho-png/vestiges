package dev.vestiges.data;

import dev.vestiges.Vestiges;
import java.util.List;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

/** Every datapack content type Vestiges reads. New systems add their type here (tree, jobs, dungeon themes, elite powers, lore...). */
public final class VestigesData {
	public static final DataType<Feat> FEATS = new DataType<>("feats", Feat.CODEC);

	public static final List<DataType<?>> ALL = List.of(FEATS);

	private VestigesData() {
	}

	public static void register() {
		ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
		for (DataType<?> type : ALL) {
			loader.registerReloadListener(Vestiges.id(type.name()), type);
		}
	}
}
