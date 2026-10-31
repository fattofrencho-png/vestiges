package dev.vestiges.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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

	/** The first thing a full screen draws: light screens cover the world, dark ones dim it so text stays readable. */
	public static void paintBackground(GuiGraphicsExtractor g, int width, int height) {
		if (light()) {
			g.fill(0, 0, width, height, background());
		} else {
			g.fill(0, 0, width, height, 0x90101418);
		}
	}

	public static int panel() {
		return light() ? 0xF0C6C6C6 : 0xE0101418;
	}

	/** A card or a small window on a screen. */
	public static int card() {
		return light() ? 0xFFD6D6D6 : 0xC0101418;
	}

	public static int cardLine() {
		return light() ? 0x60000000 : 0x60FFFFFF;
	}

	/** A window over everything else, such as a question. */
	public static int popup() {
		return light() ? 0xFFD6D6D6 : 0xF0181818;
	}

	/** The row under the pointer, and the picked row. */
	public static int hoverRow() {
		return light() ? 0x12000000 : 0x12FFFFFF;
	}

	public static int pickedRow() {
		return light() ? 0x24000000 : 0x24FFFFFF;
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

	public static int good() {
		return light() ? 0xFF2E7D32 : 0xFF8FD18F;
	}

	public static int bad() {
		return light() ? 0xFFA83232 : 0xFFD18F8F;
	}

	public static int error() {
		return light() ? 0xFFB02020 : 0xFFFF7070;
	}

	/** Help marks and other small hints you can hover. */
	public static int info() {
		return light() ? 0xFF146C80 : 0xFF6FD3E0;
	}

	/** Lore and flavour lines. */
	public static int flavour() {
		return light() ? 0xFF4A4238 : 0xFFE0D8C8;
	}

	/** The empty part of a progress bar. */
	public static int barTrack() {
		return light() ? 0xFF8E8E8E : 0xFF2A2F33;
	}

	public static boolean shadow() {
		return !light();
	}

	/** Players don't know our milestone numbers: "dungeons (M8)" reads "dungeons". */
	public static String noMilestone(String text) {
		return text.replaceAll(" ?\\(M\\d+\\)", "");
	}
}
