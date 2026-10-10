package dev.vestiges;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rescaled enchantments (I-VI) replace vanilla's files, and a broken one would stop worlds from loading. Each file is decoded here with
 * the game's own codec, which also checks every condition against what the effect may use.
 */
class EnchantDataTest {
	private static final Path DIR = Path.of("src/main/resources/resourcepacks/enchant_levels/data/minecraft/enchantment");

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void everyRescaledEnchantmentDecodes() throws IOException {
		HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
		RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
		List<Path> files;
		try (Stream<Path> s = Files.list(DIR)) {
			files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
		assertEquals(41, files.size(), "every vanilla enchantment except the two curses");
		for (Path f : files) {
			JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			Enchantment e = Enchantment.DIRECT_CODEC.parse(ops, json).getOrThrow(msg -> new IllegalStateException(f.getFileName() + ": " + msg));
			assertEquals(4, e.getMaxLevel(), f.getFileName() + ": the table, loot and trades must stop at IV");
			// Like vanilla, Quick Charge's top level never comes from the table (its min cost is above its max cost).
			if (!f.getFileName().toString().equals("quick_charge.json")) {
				assertTrue(e.getMinCost(4) <= e.getMaxCost(4), f.getFileName() + ": IV must be reachable at the table");
			}
		}
	}
}
