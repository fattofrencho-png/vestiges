package dev.vestiges.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client settings, saved as {@code config/vestiges/client.json} and changed from the hub's Settings tab. Pure data (lives in the main source
 * set so it is unit-tested); only the client reads or writes it.
 */
public final class ClientConfig {
	public int schema = 1;
	/** Master switch for the look-at HUD. */
	public boolean lookAtEnabled = true;
	/** Each look-at line by provider id; a provider the file does not mention defaults to on. */
	public Map<String, Boolean> lookAtLines = new LinkedHashMap<>();

	public ClientConfig normalize() {
		if (lookAtLines == null) {
			lookAtLines = new LinkedHashMap<>();
		}
		return this;
	}

	public boolean lineOn(String providerId) {
		Boolean v = lookAtLines.get(providerId);
		return v == null || v;
	}

	public void setLine(String providerId, boolean on) {
		lookAtLines.put(providerId, on);
	}
}
