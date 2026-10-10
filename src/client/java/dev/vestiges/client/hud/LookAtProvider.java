package dev.vestiges.client.hud;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One line (or a few) of the look-at HUD. Every system plugs its own provider in (mob level, worker status, crop growth, Codex progress...), and
 * the player can switch each one off in the hub's Settings tab. Providers must be cheap: they run every frame while the HUD is shown.
 */
public interface LookAtProvider {
	/** Stable id, used as the settings key. */
	String id();

	/** Shown next to the toggle in Settings. */
	String label();

	/** Adds this provider's lines for what the player is looking at. Add nothing when it does not apply. */
	void addLines(LookAtTarget target, List<Component> out);
}
