package dev.vestiges.codex;

/**
 * The agreed Codex numbers (Batch 3 and M4), pure so they are unit-tested.
 * <ul>
 * <li>Blocks: Discovered (1) → Familiar (2) → Mastered (3); Familiar / Mastered need Common 64/256 · Uncommon 48/192 · Rare 32/128 · Very rare
 * 16/64 blocks (fewer with the Codex gathering nodes); Unique blocks only get their entry.</li>
 * <li>Bestiary: Discovered (1), then 10 / 50 / 250 kills or breedings (2 / 3 / 4 = Mastered); bosses 1 / 3 / 10.</li>
 * <li>Items: Discovered (1), Mastered (3) after obtaining 16 (rare items 3). Places: Discovered (1), Mastered (3) after 32 chunks of a biome
 * or 3 of a structure.</li>
 * </ul>
 */
public final class CodexRules {
	public static final int BIOME_CHUNKS = 32;
	public static final int STRUCTURES_FOUND = 3;

	private CodexRules() {
	}

	public static boolean unique(CodexEntry e) {
		return CodexEntry.BLOCKS.equals(e.section()) && "unique".equals(e.cls());
	}

	public static int maxStage(CodexEntry e) {
		if (!e.later().isEmpty()) {
			return 1;
		}
		if (CodexEntry.MOBS.equals(e.section())) {
			return 4;
		}
		return unique(e) ? 1 : 3;
	}

	/** Blocks needed for Familiar and Mastered; {@code gathering} = the Codex gathering stat in % (at most 60% off). */
	public static int[] blockNeeds(String cls, double gathering) {
		int[] base = switch (cls) {
			case "uncommon" -> new int[] {48, 192};
			case "rare" -> new int[] {32, 128};
			case "very_rare" -> new int[] {16, 64};
			default -> new int[] {64, 256};
		};
		double f = 1 - Math.max(0, Math.min(60, gathering)) / 100.0;
		return new int[] {Math.max(1, (int) Math.round(base[0] * f)), Math.max(1, (int) Math.round(base[1] * f))};
	}

	private static int[] mobNeeds(CodexEntry e) {
		return e.boss() ? new int[] {1, 3, 10} : new int[] {10, 50, 250};
	}

	/** The stage a count reaches (blocks broken, kills and breedings, items obtained, places found). */
	public static int stageFor(CodexEntry e, int count, double gathering) {
		if (count <= 0) {
			return 0;
		}
		switch (e.section()) {
			case CodexEntry.BLOCKS -> {
				if (unique(e)) {
					return 1;
				}
				int[] n = blockNeeds(e.cls(), gathering);
				return count >= n[1] ? 3 : count >= n[0] ? 2 : 1;
			}
			case CodexEntry.MOBS -> {
				int[] n = mobNeeds(e);
				return count >= n[2] ? 4 : count >= n[1] ? 3 : count >= n[0] ? 2 : 1;
			}
			case CodexEntry.ITEMS -> {
				return count >= (e.rare() ? 3 : 16) ? 3 : 1;
			}
			case CodexEntry.BIOMES -> {
				return count >= BIOME_CHUNKS ? 3 : 1;
			}
			default -> {
				return !e.later().isEmpty() ? 1 : count >= STRUCTURES_FOUND ? 3 : 1;
			}
		}
	}

	/** What the progress bar counts toward next, or 0 when there is nothing left. */
	public static int nextNeed(CodexEntry e, int stage, double gathering) {
		if (stage >= maxStage(e)) {
			return 0;
		}
		switch (e.section()) {
			case CodexEntry.BLOCKS -> {
				int[] n = blockNeeds(e.cls(), gathering);
				return stage <= 1 ? n[0] : n[1];
			}
			case CodexEntry.MOBS -> {
				int[] n = mobNeeds(e);
				return stage <= 1 ? n[0] : stage == 2 ? n[1] : n[2];
			}
			case CodexEntry.ITEMS -> {
				return e.rare() ? 3 : 16;
			}
			case CodexEntry.BIOMES -> {
				return BIOME_CHUNKS;
			}
			default -> {
				return STRUCTURES_FOUND;
			}
		}
	}

	/** XP for reaching a stage (approved). Biomes and structures keep their discovery XP from the level system. */
	public static long xpFor(CodexEntry e, int stage) {
		switch (e.section()) {
			case CodexEntry.BLOCKS -> {
				int base = switch (e.cls()) {
					case "uncommon" -> 20;
					case "rare" -> 40;
					case "very_rare" -> 80;
					case "unique" -> 150;
					default -> 10;
				};
				return stage == 1 ? base : stage == 2 ? base * 2L : stage == 3 ? base * 5L : 0;
			}
			case CodexEntry.MOBS -> {
				return stage == 2 ? 25 : stage == 3 ? 75 : stage == 4 ? 200 : 0;
			}
			case CodexEntry.ITEMS -> {
				return stage == 1 ? 15 : stage == 3 ? 50 : 0;
			}
			default -> {
				if (stage == 3) {
					return 100;
				}
				// Features (geodes, fossils...) have no structure id, so the level system gives them nothing: they pay like a structure.
				return stage == 1 && !e.detect().isEmpty() ? 150 : 0;
			}
		}
	}

	/** Bonus Craft points from discovered entries: 100 / 250 / 500, or 80 / 200 / 400 with the Lorekeeper keystone. */
	public static int bonusPoints(int discovered, boolean lorekeeper) {
		int[] at = lorekeeper ? new int[] {80, 200, 400} : new int[] {100, 250, 500};
		int p = 0;
		for (int a : at) {
			if (discovered >= a) {
				p++;
			}
		}
		return p;
	}

	public static int nextPointAt(int discovered, boolean lorekeeper) {
		int[] at = lorekeeper ? new int[] {80, 200, 400} : new int[] {100, 250, 500};
		for (int a : at) {
			if (discovered < a) {
				return a;
			}
		}
		return 0;
	}

	public static String label(CodexEntry e, int stage) {
		if (stage <= 0) {
			return "Not found yet";
		}
		if (CodexEntry.MOBS.equals(e.section())) {
			int[] n = mobNeeds(e);
			return switch (stage) {
				case 1 -> "Discovered";
				case 2 -> n[0] + " kills";
				case 3 -> n[1] + " kills";
				default -> "Mastered";
			};
		}
		if (unique(e)) {
			return "Recorded";
		}
		return stage == 1 ? "Discovered" : stage == 2 ? "Familiar" : "Mastered";
	}

	/** Bestiary damage bonus in %: +2 / +4 / +6 at 10 / 50 / 250. */
	public static int bestiaryDamage(int stage) {
		return stage >= 4 ? 6 : stage == 3 ? 4 : stage == 2 ? 2 : 0;
	}

	/** Bestiary drop bonus (added to the emerald and ingot chances): +1% at 50, +2% at 250. */
	public static double bestiaryDrops(int stage) {
		return stage >= 4 ? 0.02 : stage == 3 ? 0.01 : 0;
	}

	public static boolean line1Visible(CodexEntry e, int stage, boolean hostile) {
		return CodexEntry.MOBS.equals(e.section()) && hostile ? stage >= 2 : stage >= 1;
	}

	public static boolean line2Visible(CodexEntry e, int stage) {
		return stage >= maxStage(e) && stage > 0;
	}
}
