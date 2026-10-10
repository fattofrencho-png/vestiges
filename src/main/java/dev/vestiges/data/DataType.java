package dev.vestiges.data;

import com.mojang.serialization.Codec;
import dev.vestiges.Vestiges;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * One kind of Vestiges content read from datapacks: every {@code data/<namespace>/vestiges/<name>/*.json} file becomes one entry, decoded by the
 * codec. Reloaded with {@code /reload}, so packs can add, change or remove content without code. Entries are kept sorted by id.
 */
public final class DataType<T> extends SimpleJsonResourceReloadListener<T> {
	private final String name;
	private volatile Map<Identifier, T> entries = Map.of();

	public DataType(String name, Codec<T> codec) {
		super(codec, FileToIdConverter.json(Vestiges.MOD_ID + "/" + name));
		this.name = name;
	}

	@Override
	protected void apply(Map<Identifier, T> loaded, ResourceManager manager, ProfilerFiller profiler) {
		entries = Collections.unmodifiableMap(new TreeMap<>(loaded));
		Vestiges.LOGGER.info("Vestiges: loaded {} {}", entries.size(), name);
	}

	public String name() {
		return name;
	}

	public Map<Identifier, T> all() {
		return entries;
	}

	public T get(Identifier id) {
		return entries.get(id);
	}
}
