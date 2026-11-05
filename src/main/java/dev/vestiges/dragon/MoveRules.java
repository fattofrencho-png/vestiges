package dev.vestiges.dragon;

import java.util.List;

/**
 * The numbers and shapes of the signature moves of dragons 2 to 4, as pure maths (unit-tested). All of them are drafts for the balance pass.
 * <ul>
 * <li>Fire ring (Belaz): when the dragon perches, a ring of fire burns around it for 7 seconds.</li>
 * <li>Wing shove (Morst): a warning sound, one second later everyone near is thrown sideways off its flight line.</li>
 * <li>Crystal regrow (Dhars): 60 seconds after a pillar crystal is destroyed, the last 3 destroyed grow back (sparks first), twice per fight at most.</li>
 * </ul>
 */
public final class MoveRules {
	// ---- fire ring ---------------------------------------------------------------------------------------------------
	/** The ring is the band between these two distances from the perched dragon, and 4 blocks up or down. */
	public static final double RING_INNER = 4.0;
	public static final double RING_OUTER = 8.0;
	public static final double RING_HEIGHT = 4.0;
	public static final int RING_TICKS = 140;
	public static final int RING_HIT_EVERY = 10;
	/** Damage of one burn before the dragon's damage multiplier, and how long it sets you alight. */
	public static final float RING_DAMAGE = 2.0f;
	public static final int RING_BURN_SECONDS = 3;

	// ---- wing shove --------------------------------------------------------------------------------------------------
	/** A player this close to the flying dragon starts the warning. */
	public static final double SHOVE_NEAR = 14.0;
	/** Everyone this close when the warning ends is thrown. */
	public static final double SHOVE_REACH = 12.0;
	public static final int SHOVE_WARN_TICKS = 20;
	public static final int SHOVE_COOLDOWN_TICKS = 300;
	public static final double SHOVE_SPEED = 1.4;
	public static final double SHOVE_LIFT = 0.35;

	// ---- crystal regrow ----------------------------------------------------------------------------------------------
	public static final int REGROW_DELAY_TICKS = 60 * 20;
	public static final int REGROW_WARN_TICKS = 3 * 20;
	public static final int REGROW_COUNT = 3;
	public static final int REGROW_WAVES = 2;

	private MoveRules() {
	}

	/** Is a player at this offset from the perched dragon standing in the ring of fire? */
	public static boolean inRing(double dx, double dy, double dz) {
		double d = Math.sqrt(dx * dx + dz * dz);
		return d >= RING_INNER && d <= RING_OUTER && Math.abs(dy) <= RING_HEIGHT;
	}

	/**
	 * The push direction of the wing shove: a unit vector at right angles to the dragon's heading, pointing to the side the player is on (a player
	 * exactly on the line goes to the left). Returns {0, 0} for a dragon that does not move.
	 */
	public static double[] sideways(double headingX, double headingZ, double relX, double relZ) {
		double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
		if (len < 1e-6) {
			return new double[] {0, 0};
		}
		double sx = -headingZ / len;
		double sz = headingX / len;
		boolean left = sx * relX + sz * relZ >= 0;
		return left ? new double[] {sx, sz} : new double[] {-sx, -sz};
	}

	/** The last {@code n} of a list (the most recent ones), oldest first. */
	public static <T> List<T> last(List<T> items, int n) {
		return items.subList(Math.max(0, items.size() - Math.max(0, n)), items.size());
	}

	/** May another regrow wave be planned: fewer than the allowed waves have run. */
	public static boolean wavesLeft(int wavesRun) {
		return wavesRun < REGROW_WAVES;
	}
}
