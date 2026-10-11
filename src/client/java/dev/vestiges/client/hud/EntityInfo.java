package dev.vestiges.client.hud;

import com.google.gson.JsonObject;
import dev.vestiges.net.LookAtPayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;

/**
 * What only the server knows about the entity you look at (M4): a baby's time to grow up, an adult's breeding cooldown, a pet's owner. Asked at
 * most twice a second while you look at it; answers arrive as "entity_info" notices.
 */
public final class EntityInfo {
	private record Answer(JsonObject json, long at) {
	}

	private static final Map<Integer, Answer> ANSWERS = new HashMap<>();
	private static final Map<Integer, Long> ASKED = new HashMap<>();

	private EntityInfo() {
	}

	/** The latest answer for this entity (asking again if it is old), or null while waiting. */
	public static JsonObject get(Entity e) {
		long now = System.currentTimeMillis();
		Long asked = ASKED.get(e.getId());
		if ((asked == null || now - asked > 500) && ClientPlayNetworking.canSend(LookAtPayloads.EntityInfo.TYPE)) {
			ASKED.put(e.getId(), now);
			ClientPlayNetworking.send(new LookAtPayloads.EntityInfo(e.getId()));
			if (ASKED.size() > 64) {
				ASKED.values().removeIf(t -> now - t > 10_000);
				ANSWERS.values().removeIf(a -> now - a.at() > 10_000);
			}
		}
		Answer a = ANSWERS.get(e.getId());
		return a == null ? null : a.json();
	}

	/** The age in an answer, moved on by the time since it arrived (babies count up to 0, cooldowns count down to 0). */
	public static int age(Entity e) {
		Answer a = ANSWERS.get(e.getId());
		if (a == null || !a.json().has("age")) {
			return 0;
		}
		int age = a.json().get("age").getAsInt();
		int passed = (int) ((System.currentTimeMillis() - a.at()) / 50);
		return age < 0 ? Math.min(0, age + passed) : Math.max(0, age - passed);
	}

	public static void onAnswer(JsonObject json) {
		ANSWERS.put(json.get("id").getAsInt(), new Answer(json, System.currentTimeMillis()));
	}

	public static void reset() {
		ANSWERS.clear();
		ASKED.clear();
	}
}
