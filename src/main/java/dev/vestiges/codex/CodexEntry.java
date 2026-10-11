package dev.vestiges.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * One Codex entry (M4), from {@code data/<ns>/vestiges/codex/<section>/<name>.json} (written by tools/build_codex.py from the approved flavour
 * text). {@code members} are the game ids it covers (blocks, entity types, biomes, structures or items); {@code detect} names how a feature
 * without a structure id is spotted (geode, fossil, monster room, desert well); {@code later} names the milestone that brings an entry that
 * cannot be found yet.
 */
public record CodexEntry(String section, String name, String cls, List<String> members, String icon, String line1, String line2,
						 boolean boss, boolean rare, String detect, String later) {
	public static final String BLOCKS = "blocks";
	public static final String MOBS = "mobs";
	public static final String BIOMES = "biomes";
	public static final String STRUCTURES = "structures";
	public static final String ITEMS = "items";

	public static final Codec<CodexEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("section").forGetter(CodexEntry::section),
		Codec.STRING.fieldOf("name").forGetter(CodexEntry::name),
		Codec.STRING.optionalFieldOf("class", "").forGetter(CodexEntry::cls),
		Codec.STRING.listOf().optionalFieldOf("members", List.of()).forGetter(CodexEntry::members),
		Codec.STRING.optionalFieldOf("icon", "minecraft:book").forGetter(CodexEntry::icon),
		Codec.STRING.optionalFieldOf("line1", "").forGetter(CodexEntry::line1),
		Codec.STRING.optionalFieldOf("line2", "").forGetter(CodexEntry::line2),
		Codec.BOOL.optionalFieldOf("boss", false).forGetter(CodexEntry::boss),
		Codec.BOOL.optionalFieldOf("rare", false).forGetter(CodexEntry::rare),
		Codec.STRING.optionalFieldOf("detect", "").forGetter(CodexEntry::detect),
		Codec.STRING.optionalFieldOf("later", "").forGetter(CodexEntry::later)
	).apply(i, CodexEntry::new));

	public boolean isPlace() {
		return BIOMES.equals(section) || STRUCTURES.equals(section);
	}
}
