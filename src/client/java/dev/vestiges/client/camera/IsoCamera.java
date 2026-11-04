package dev.vestiges.client.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Test of the dungeon camera (M8): inside one box in the world the view switches to a fixed, tilted third-person view like Minecraft
 * Dungeons, and eases back when you leave. Client only; the box lives in {@code config/vestiges/camera_zone.json}.
 */
public final class IsoCamera {
	public static final float YAW = 45.0f;
	public static final float PITCH = 50.0f;
	public static final float DISTANCE = 14.0f;
	public static final float FOV = 45.0f;
	private static final float BLEND_SECONDS = 0.7f;
	/** Degrees the character may turn toward its walking direction in one tick. */
	private static final float TURN_PER_TICK = 70.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AABB zone;
	private static boolean forced;
	private static float blend;
	private static long lastNanos;

	private IsoCamera() {
	}

	private record Saved(double x1, double y1, double z1, double x2, double y2, double z2) {
	}

	private static Path file() {
		return ConfigStore.dir().resolve("camera_zone.json");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try {
			Saved s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Saved.class);
			zone = s == null ? null : new AABB(s.x1, s.y1, s.z1, s.x2, s.y2, s.z2);
		} catch (IOException | JsonParseException e) {
			Vestiges.LOGGER.error("Vestiges: could not read {}", f, e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			if (zone == null) {
				Files.deleteIfExists(file());
			} else {
				Files.writeString(file(), GSON.toJson(new Saved(zone.minX, zone.minY, zone.minZ, zone.maxX, zone.maxY, zone.maxZ)), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			Vestiges.LOGGER.error("Vestiges: could not save {}", file(), e);
		}
	}

	/** The zone covers whole blocks from one corner to the other, both included. */
	public static void setZone(int x1, int y1, int z1, int x2, int y2, int z2) {
		zone = new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1);
		save();
	}

	public static void clearZone() {
		zone = null;
		save();
	}

	public static AABB zone() {
		return zone;
	}

	public static void setForced(boolean on) {
		forced = on;
	}

	public static boolean forced() {
		return forced;
	}

	/** True while the character is in the zone (or the view is forced on for testing). */
	public static boolean steering() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		return forced || (zone != null && zone.contains(mc.player.position()));
	}

	/** Moves the blend toward on or off; called once per frame by the camera. Returns the eased blend, 0 (normal view) to 1 (full view). */
	public static float tickBlend() {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		float step = dt / BLEND_SECONDS;
		blend = steering() ? Math.min(1.0f, blend + step) : Math.max(0.0f, blend - step);
		return blend * blend * (3.0f - 2.0f * blend);
	}

	public static float eased() {
		return blend * blend * (3.0f - 2.0f * blend);
	}

	/** Where the camera sits for a character at {@code pivot}: up and behind, looking down at it. */
	public static Vec3 position(Vec3 pivot) {
		float yaw = YAW * Mth.DEG_TO_RAD;
		float pitch = PITCH * Mth.DEG_TO_RAD;
		double cos = Mth.cos(pitch);
		Vec3 forward = new Vec3(-Mth.sin(yaw) * cos, -Mth.sin(pitch), Mth.cos(yaw) * cos);
		return pivot.subtract(forward.scale(DISTANCE));
	}

	/**
	 * Turns WASD into screen directions: W walks up the screen whatever way the character faces. The character turns toward its walking
	 * direction. Returns {left, forward} impulses relative to the character's new facing, or null when there is no input.
	 */
	public static float[] steer(net.minecraft.client.player.LocalPlayer player, float left, float forward) {
		if (left == 0 && forward == 0) {
			return null;
		}
		float yaw = YAW * Mth.DEG_TO_RAD;
		double dx = forward * -Mth.sin(yaw) + left * Mth.cos(yaw);
		double dz = forward * Mth.cos(yaw) + left * Mth.sin(yaw);
		float target = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float current = player.getYRot();
		float turned = current + Mth.clamp(Mth.wrapDegrees(target - current), -TURN_PER_TICK, TURN_PER_TICK);
		player.setYRot(turned);
		player.setYHeadRot(turned);
		player.setYBodyRot(turned);
		float r = turned * Mth.DEG_TO_RAD;
		float fwd = (float) (dx * -Mth.sin(r) + dz * Mth.cos(r));
		float lft = (float) (dx * Mth.cos(r) + dz * Mth.sin(r));
		return new float[] {lft, fwd};
	}
}
