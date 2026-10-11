package dev.vestiges.client.mobs;

import com.mojang.math.Transformation;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.mobs.CombatFeedback;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;

/**
 * Damage numbers (approved): every hit you land shows the damage floating up from the target, gold and bigger for crits. Each number is a
 * client-only text display (negative ids, never sent anywhere) that rises, fades and is removed after about a second.
 */
public final class DamageNumbers {
	private static final int LIFE = 24;
	private static final int FADE_FROM = 12;

	private static final class Floating {
		final Display.TextDisplay entity;
		final double rise;
		int age;

		Floating(Display.TextDisplay entity, double rise) {
			this.entity = entity;
			this.rise = rise;
		}
	}

	private static final List<Floating> LIVE = new ArrayList<>();
	private static ClientLevel liveLevel;
	private static int nextId = -1_000_000;

	private DamageNumbers() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CombatFeedback.DamageNumber.TYPE,
			(payload, ctx) -> ctx.client().execute(() -> onHit(payload.entityId(), payload.amount(), payload.crit())));
	}

	private static void onHit(int entityId, float amount, boolean crit) {
		ClientMobs.onHit(entityId);
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || !ClientConfigStore.get().damageNumbers) {
			return;
		}
		Entity target = level.getEntity(entityId);
		if (target == null) {
			return;
		}
		if (level != liveLevel) {
			LIVE.clear();
			liveLevel = level;
		}
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		d.setId(nextId--);
		if (nextId > -1000) {
			nextId = -1_000_000;
		}
		double jx = (level.getRandom().nextDouble() - 0.5) * target.getBbWidth();
		double jz = (level.getRandom().nextDouble() - 0.5) * target.getBbWidth();
		d.setPos(target.getX() + jx, target.getY() + target.getBbHeight() * 0.75 + 0.2, target.getZ() + jz);
		d.setOldPosAndRot();
		d.setText(crit ? Component.literal(format(amount)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			: Component.literal(format(amount)).withStyle(ChatFormatting.WHITE));
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBackgroundColor(0);
		d.setFlags(Display.TextDisplay.FLAG_SHADOW);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		float s = crit ? 1.4f : 1.0f;
		d.setTransformation(new Transformation(null, null, new Vector3f(s, s, s), null));
		level.addEntity(d);
		LIVE.add(new Floating(d, 0.05 + level.getRandom().nextDouble() * 0.02));
	}

	/** Damage shown as a whole number, or with one decimal below 10. */
	static String format(float amount) {
		if (amount >= 10) {
			return String.valueOf(Math.round(amount));
		}
		float r = Math.round(amount * 10) / 10.0f;
		return r == (int) r ? String.valueOf((int) r) : String.valueOf(r);
	}

	/** Rises and fades each tick; removed at the end of its life (or when the world changes). */
	public static void tick(Minecraft mc) {
		if (LIVE.isEmpty()) {
			return;
		}
		if (mc.level == null || mc.level != liveLevel) {
			LIVE.clear();
			return;
		}
		for (Iterator<Floating> it = LIVE.iterator(); it.hasNext(); ) {
			Floating f = it.next();
			f.age++;
			if (f.age >= LIFE || f.entity.isRemoved()) {
				if (!f.entity.isRemoved()) {
					mc.level.removeEntity(f.entity.getId(), Entity.RemovalReason.DISCARDED);
				}
				it.remove();
				continue;
			}
			double speed = f.rise * (1.0 - f.age / (double) LIFE);
			f.entity.setOldPosAndRot();
			f.entity.setPos(f.entity.getX(), f.entity.getY() + speed, f.entity.getZ());
			if (f.age > FADE_FROM) {
				int alpha = (int) (255 * (1.0 - (f.age - FADE_FROM) / (double) (LIFE - FADE_FROM)));
				f.entity.setTextOpacity((byte) Math.max(26, alpha));
			}
		}
	}
}
