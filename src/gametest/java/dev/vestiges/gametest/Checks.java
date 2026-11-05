package dev.vestiges.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * Pass/fail lines for one automatic suite, written to {@code report-<suite>.md} in the test run folder as they happen (a stalled game loses
 * nothing). Also runs a command as an operator at the player and captures what it answered.
 */
final class Checks {
	/** What a command did: {@code crashed} = threw (a bug or a bad argument), {@code refused} = answered with a red failure line. */
	record Result(boolean crashed, boolean refused, int value, String text) {
		boolean ok() {
			return !crashed && !refused;
		}
	}

	private final Path file;
	private int pass;
	private int fail;
	private int notes;

	Checks(String suite) {
		this.file = FabricLoader.getInstance().getGameDir().resolve("report-" + suite + ".md");
		try {
			Files.writeString(file, "# Vestiges auto-check: " + suite + "\n\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	void check(String name, boolean ok, String detail) {
		if (ok) {
			pass++;
		} else {
			fail++;
		}
		line((ok ? "- PASS " : "- **FAIL** ") + name + (detail == null || detail.isEmpty() ? "" : " — " + detail));
	}

	void check(String name, boolean ok) {
		check(name, ok, "");
	}

	/** Something to look at but not a pass or a fail (a command that refused for a reason that may be right). */
	void note(String text) {
		notes++;
		line("- note: " + text);
	}

	void section(String title) {
		line("\n## " + title);
	}

	/** The command must run and not answer with a failure. */
	Result expectOk(TestServerContext server, String cmd) {
		Result r = run(server, cmd);
		check("`" + cmd + "`", r.ok(), r.ok() ? "" : (r.crashed() ? "CRASHED: " : "refused: ") + r.text());
		return r;
	}

	/** The command must not throw; a refusal is noted for a human to judge. */
	Result survey(TestServerContext server, String cmd) {
		Result r = run(server, cmd);
		check("`" + cmd + "` runs", !r.crashed(), r.crashed() ? "CRASHED: " + r.text() : "");
		if (r.refused()) {
			note("`" + cmd + "` refused: " + r.text());
		}
		return r;
	}

	/** The command must be rejected before it runs (a bad argument). */
	void expectRejected(TestServerContext server, String cmd) {
		Result r = run(server, cmd);
		check("`" + cmd + "` is rejected", r.crashed() || r.refused(), r.ok() ? "it ran: " + r.text() : "");
	}

	Result run(TestServerContext server, String cmd) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			StringBuilder out = new StringBuilder();
			boolean[] red = {false};
			CommandSource capture = new CommandSource() {
				@Override
				public void sendSystemMessage(Component message) {
					if (message.getStyle().getColor() != null && message.getStyle().getColor().getValue() == 0xFF5555) {
						red[0] = true;
					}
					out.append(message.getString()).append(" | ");
				}

				@Override
				public boolean acceptsSuccess() {
					return true;
				}

				@Override
				public boolean acceptsFailure() {
					return true;
				}

				@Override
				public boolean shouldInformAdmins() {
					return false;
				}
			};
			CommandSourceStack src = new CommandSourceStack(capture, p.position(), p.getRotationVector(), (ServerLevel) p.level(),
				LevelBasedPermissionSet.OWNER, s, p);
			try {
				int value = s.getCommands().getDispatcher().execute(cmd, src);
				return new Result(false, red[0], value, out.toString());
			} catch (CommandSyntaxException e) {
				return new Result(true, false, 0, e.getMessage() + " " + out);
			} catch (RuntimeException e) {
				return new Result(true, false, 0, e + " " + out);
			}
		});
	}

	void done() {
		line("\n**" + pass + " passed, " + fail + " failed, " + notes + " notes**");
		line("SUITE DONE");
		System.out.println("[autocheck] " + file.getFileName() + ": " + pass + " passed, " + fail + " failed, " + notes + " notes");
	}

	private void line(String text) {
		try {
			Files.writeString(file, text + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
