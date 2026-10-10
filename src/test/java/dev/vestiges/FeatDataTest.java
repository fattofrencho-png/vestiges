package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vestiges.data.Feat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every bundled feat file must decode, and the bundled set must match the approved Batch 4 list. */
class FeatDataTest {
	private static final Path DIR = Path.of("src/main/resources/data/vestiges/vestiges/feats");
	private static final Set<String> CHAINS = Set.of("settler", "warden", "explorer", "gatekeeper", "dragonslayer");

	private static Map<String, Feat> loadAll() throws IOException {
		Map<String, Feat> out = new HashMap<>();
		try (Stream<Path> files = Files.list(DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				Feat feat = Feat.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(f + ": " + msg));
				out.put(f.getFileName().toString().replace(".json", ""), feat);
			}
		}
		return out;
	}

	@Test
	void everyFileDecodes() throws IOException {
		Map<String, Feat> feats = loadAll();
		assertEquals(24, feats.size());
		for (Map.Entry<String, Feat> e : feats.entrySet()) {
			assertTrue(CHAINS.contains(e.getValue().chain()), e.getKey() + " has an unknown chain");
			assertFalse(e.getValue().name().isBlank(), e.getKey());
		}
	}

	@Test
	void repeatablesMatchTheDesign() throws IOException {
		Map<String, Feat> feats = loadAll();
		List<String> repeatable = feats.entrySet().stream().filter(e -> e.getValue().repeatable()).map(Map.Entry::getKey).sorted().toList();
		assertEquals(List.of("elite_hunter", "local_landmark", "regular", "trailblazer"), repeatable);
	}

	@Test
	void optionalFieldsDefault() {
		JsonElement json = JsonParser.parseString("{\"chain\":\"warden\",\"name\":\"X\",\"requirement\":\"r\",\"reward\":\"w\"}");
		Feat f = Feat.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(0, f.order());
		assertEquals(0, f.xp());
		assertFalse(f.repeatable());
	}
}
