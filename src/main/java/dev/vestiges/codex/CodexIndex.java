package dev.vestiges.codex;

import dev.vestiges.data.VestigesData;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Fast lookups from game ids to Codex entries, rebuilt whenever the Codex data reloads. */
public final class CodexIndex {
	private static Map<Identifier, CodexEntry> source;
	private static CodexIndex current;

	public final Map<String, CodexEntry> entries;
	private final Map<String, Map<String, String>> bySection;
	private final Map<String, String> byDetect;

	private CodexIndex(Map<Identifier, CodexEntry> data) {
		Map<String, CodexEntry> all = new LinkedHashMap<>();
		Map<String, Map<String, String>> sections = new HashMap<>();
		Map<String, String> detect = new HashMap<>();
		data.forEach((id, e) -> {
			String key = id.getPath();
			all.put(key, e);
			Map<String, String> m = sections.computeIfAbsent(e.section(), s -> new HashMap<>());
			for (String member : e.members()) {
				m.put(member, key);
			}
			if (!e.detect().isEmpty()) {
				detect.put(e.detect(), key);
			}
		});
		this.entries = Collections.unmodifiableMap(all);
		this.bySection = sections;
		this.byDetect = detect;
	}

	public static synchronized CodexIndex get() {
		Map<Identifier, CodexEntry> data = VestigesData.CODEX.all();
		if (current == null || data != source) {
			source = data;
			current = new CodexIndex(data);
		}
		return current;
	}

	/** The entry id ("blocks/stone") covering a game id in a section, or null. */
	public String find(String section, Identifier gameId) {
		Map<String, String> m = bySection.get(section);
		return m == null ? null : m.get(gameId.toString());
	}

	public String detect(String kind) {
		return byDetect.get(kind);
	}

	public CodexEntry entry(String id) {
		return entries.get(id);
	}

	/** Block and entity ids → entry id, for the client's look-at "New" tag. */
	public Map<String, String> lookAtIndex() {
		Map<String, String> out = new HashMap<>();
		for (String section : new String[] {CodexEntry.BLOCKS, CodexEntry.MOBS}) {
			bySection.getOrDefault(section, Map.of()).forEach(out::put);
		}
		return out;
	}
}
