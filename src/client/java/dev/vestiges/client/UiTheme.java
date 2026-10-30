package dev.vestiges.client;

/** The colours our screens draw their panels and text with, for the light and dark UI setting. */
public final class UiTheme {
	private UiTheme() {
	}

	public static boolean light() {
		return "light".equals(ClientConfigStore.get().theme);
	}

	/** Light screens paint their whole background in vanilla's window grey. */
	public static int background() {
		return 0xFFC6C6C6;
	}

	public static int panel() {
		return light() ? 0xF0C6C6C6 : 0xE0101418;
	}

	public static int text() {
		return light() ? 0xFF303030 : 0xFFFFFFFF;
	}

	public static int muted() {
		return light() ? 0xFF5E5E5E : 0xFFAAAAAA;
	}

	public static int faint() {
		return light() ? 0xFF6E6E6E : 0xFF888888;
	}

	public static int head() {
		return light() ? 0xFF7A5A12 : 0xFFE6C36A;
	}

	public static int line() {
		return light() ? 0x55000000 : 0x55FFFFFF;
	}

	public static boolean shadow() {
		return !light();
	}
}
