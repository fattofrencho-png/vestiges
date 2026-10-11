package dev.vestiges.mobs;

/** The agreed armor formula (Batch 3): blocked = armor / (armor + 20), at most 85%; toughness counts as half an armor point. */
public final class ArmorFormula {
	public static final float MAX_BLOCKED = 0.85f;

	private ArmorFormula() {
	}

	/** Share of the damage armor blocks, 0 to 0.85. */
	public static float blocked(float armor, float toughness) {
		float effective = Math.max(0, armor + toughness / 2.0f);
		return Math.min(MAX_BLOCKED, effective / (effective + 20.0f));
	}
}
