package dev.vestiges.gametest;

import dev.vestiges.client.camera.IsoCamera;
import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshots of the dungeon camera test (VESTIGES_SUITE=camshots): a small walled room is built, the player stands outside the camera zone
 * (normal view), then inside it (dungeon view), and walks with W to show the screen-relative movement.
 */
public final class CameraShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"camshots".equals(System.getenv("VESTIGES_SUITE"))) {
			return;
		}
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(null)));
		ctx.waitForScreen(CreateWorldScreen.class);
		ctx.runOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().setSeed("vestiges"));
		ctx.clickScreenButton("selectWorld.create");
		ctx.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 3600);
		TestServerContext server = new TestServerContextImpl(ctx.computeOnClient(Minecraft::getSingleplayerServer));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("gamemode creative @p");
		server.runCommand("gamerule spawn_monsters false");
		ctx.waitTicks(20);

		int cx = 220;
		int cz = 220;
		BlockPos room = T.pad(ctx, server, cx, cz);
		int y = room.getY();
		buildRoom(server, cx, y, cz);
		IsoCamera.clearZone();
		IsoCamera.setZone(cx - 6, y - 3, cz - 6, cx + 6, y + 40, cz + 6);

		// Outside the zone: the normal view, looking at the room from its west door.
		BlockPos outside = T.pad(ctx, server, cx - 16, cz);
		server.runCommand("execute in minecraft:overworld run tp @p " + (cx - 16) + " " + outside.getY() + " " + cz + " -90 10");
		ctx.waitTicks(30);
		report(ctx, "outside");
		ctx.takeScreenshot("cam_1_normal_view_outside");

		// Inside the zone: the dungeon view takes over.
		server.runCommand("execute in minecraft:overworld run tp @p " + cx + " " + y + " " + cz);
		ctx.waitTicks(5);
		ctx.takeScreenshot("cam_2_blending_in");
		ctx.waitTicks(40);
		report(ctx, "inside");
		ctx.takeScreenshot("cam_3_dungeon_view");

		// W walks up the screen: toward -x and +z (camera yaw 45), and the character turns that way.
		Vec3 before = pos(ctx);
		ctx.getInput().holdKeyFor(o -> o.keyUp, 20);
		ctx.waitTicks(3);
		Vec3 after = pos(ctx);
		report(ctx, "after W");
		System.out.println("[camshots] W moved by dx=" + (after.x - before.x) + " dz=" + (after.z - before.z));
		ctx.takeScreenshot("cam_4_after_walking_w");

		// D walks to the right of the screen.
		before = pos(ctx);
		ctx.getInput().holdKeyFor(o -> o.keyRight, 15);
		ctx.waitTicks(3);
		after = pos(ctx);
		System.out.println("[camshots] D moved by dx=" + (after.x - before.x) + " dz=" + (after.z - before.z));
		ctx.takeScreenshot("cam_5_after_walking_d");

		// Back out through the door: the normal view returns.
		server.runCommand("execute in minecraft:overworld run tp @p " + (cx - 16) + " " + outside.getY() + " " + cz + " -90 10");
		ctx.waitTicks(40);
		report(ctx, "back outside");
		ctx.takeScreenshot("cam_6_back_to_normal");

		IsoCamera.clearZone();
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("autocheck-finished.txt"), "done");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		System.out.println("[camshots] finished");
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static void report(ClientGameTestContext ctx, String label) {
		ctx.runOnClient(mc -> System.out.println("[camshots] " + label + ": blend " + IsoCamera.eased() + ", steering " + IsoCamera.steering()
			+ ", player " + mc.player.position() + ", yaw " + mc.player.getYRot() + ", camera detached " + mc.gameRenderer.mainCamera().isDetached()));
	}

	/** A square room of stone-brick walls (3 high, open top) with a doorway on the west side, a few pillars and torches. */
	private static void buildRoom(TestServerContext server, int cx, int y, int cz) {
		String in = "execute in minecraft:overworld run ";
		int r = 6;
		server.runCommand(in + "fill " + (cx - r) + " " + y + " " + (cz - r) + " " + (cx + r) + " " + (y + 2) + " " + (cz - r) + " minecraft:stone_bricks");
		server.runCommand(in + "fill " + (cx - r) + " " + y + " " + (cz + r) + " " + (cx + r) + " " + (y + 2) + " " + (cz + r) + " minecraft:stone_bricks");
		server.runCommand(in + "fill " + (cx - r) + " " + y + " " + (cz - r) + " " + (cx - r) + " " + (y + 2) + " " + (cz + r) + " minecraft:stone_bricks");
		server.runCommand(in + "fill " + (cx + r) + " " + y + " " + (cz - r) + " " + (cx + r) + " " + (y + 2) + " " + (cz + r) + " minecraft:stone_bricks");
		server.runCommand(in + "fill " + (cx - r) + " " + y + " " + (cz - 1) + " " + (cx - r) + " " + (y + 1) + " " + (cz + 1) + " minecraft:air");
		server.runCommand(in + "fill " + (cx - 3) + " " + y + " " + (cz - 3) + " " + (cx - 3) + " " + (y + 3) + " " + (cz - 3) + " minecraft:polished_andesite");
		server.runCommand(in + "fill " + (cx + 3) + " " + y + " " + (cz + 3) + " " + (cx + 3) + " " + (y + 3) + " " + (cz + 3) + " minecraft:polished_andesite");
		server.runCommand(in + "fill " + (cx + 3) + " " + y + " " + (cz - 3) + " " + (cx + 3) + " " + (y + 3) + " " + (cz - 3) + " minecraft:polished_andesite");
		server.runCommand(in + "setblock " + (cx - 3) + " " + (y + 4) + " " + (cz - 3) + " minecraft:lantern");
		server.runCommand(in + "setblock " + (cx + 1) + " " + y + " " + (cz + 2) + " minecraft:chest");
		server.runCommand(in + "summon minecraft:armor_stand " + (cx + 2) + " " + y + " " + (cz - 1));
	}
}
