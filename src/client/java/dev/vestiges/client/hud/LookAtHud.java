package dev.vestiges.client.hud;

import dev.vestiges.Vestiges;
import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.config.ClientConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Vestiges' own look-at HUD, written from scratch (no Jade code). A small panel at the top centre names what you are looking at, then shows
 * one line per enabled provider. The "New" tag appears for a few seconds the first time you look at something this session; the Codex
 * (M4) will make "first time" permanent.
 */
public final class LookAtHud implements HudElement {
	private static final long NEW_TAG_MS = 4000;
	private static final int PAD = 4;
	private final Map<String, Long> firstSeen = new HashMap<>();

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClientConfig config = ClientConfigStore.get();
		Minecraft mc = Minecraft.getInstance();
		if (!config.lookAtEnabled || mc.level == null || mc.player == null || mc.gui.screen() != null) {
			return;
		}
		LookAtTarget target = target(mc);
		if (target == null) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		for (LookAtProvider p : LookAtProviders.all()) {
			if (config.lineOn(p.id())) {
				try {
					p.addLines(target, lines);
				} catch (RuntimeException e) {
					Vestiges.LOGGER.debug("Vestiges look-at provider {} failed", p.id(), e);
				}
			}
		}

		Font font = mc.font;
		dev.vestiges.mobs.MobData mob = target.isEntity() ? dev.vestiges.client.mobs.ClientMobs.data(target.entity()) : null;
		boolean elite = mob != null && mob.elite();
		Component banner = elite ? dev.vestiges.client.mobs.ClientMobs.banner(mob) : null;
		Component title = title(target, mob);
		boolean showNew = config.lineOn(LookAtProviders.NEW_TAG) && isNew(target.id().toString());
		Component newTag = Component.literal("  NEW").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
		int width = font.width(title) + (showNew ? font.width(newTag) : 0);
		for (Component c : lines) {
			width = Math.max(width, font.width(c));
		}
		if (elite) {
			width = Math.max(width + 12, font.width(banner));
		}
		int lineH = font.lineHeight + 2;
		int extra = elite ? lineH + 5 : 0;
		int height = lineH * (1 + lines.size()) - 2 + extra;
		int x = (g.guiWidth() - width) / 2 - PAD;
		int y = 4;
		int border = elite ? 0xFF000000 | dev.vestiges.client.mobs.ClientMobs.rankColor(mob.rank()) : 0x60FFFFFF;
		g.fill(x, y, x + width + PAD * 2, y + height + PAD * 2, elite ? 0xD0101418 : 0xB0101418);
		g.outline(x, y, width + PAD * 2, height + PAD * 2, border);
		if (elite) {
			g.outline(x + 1, y + 1, width + PAD * 2 - 2, height + PAD * 2 - 2, (border & 0x00FFFFFF) | 0x50000000);
		}
		int tx = x + PAD;
		int ty = y + PAD;
		if (elite) {
			g.text(font, banner, x + PAD + (width - font.width(banner)) / 2, ty, 0xFFFFFFFF);
			ty += lineH;
		}
		g.text(font, title, tx, ty, 0xFFFFFFFF);
		if (showNew) {
			g.text(font, newTag, tx + font.width(title), ty, 0xFFFFFF55);
		}
		if (elite && target.entity() instanceof net.minecraft.world.entity.LivingEntity living) {
			int barY = ty + lineH;
			float f = Math.max(0, Math.min(1, living.getHealth() / Math.max(1, living.getMaxHealth())));
			g.fill(tx, barY, tx + width, barY + 3, 0xFF3A1010);
			g.fill(tx, barY, tx + Math.round(width * f), barY + 3, 0xFFD03030);
			ty += 5;
		}
		for (Component c : lines) {
			ty += lineH;
			g.text(font, c, tx, ty, 0xFFBBBBBB);
		}
	}

	private boolean isNew(String id) {
		Boolean codex = dev.vestiges.client.codex.ClientCodex.isNew(net.minecraft.resources.Identifier.parse(id));
		if (codex != null) {
			return codex;
		}
		long now = System.currentTimeMillis();
		Long t = firstSeen.putIfAbsent(id, now);
		return t == null || now - t < NEW_TAG_MS;
	}

	private static Component title(LookAtTarget t, dev.vestiges.mobs.MobData mob) {
		if (mob != null) {
			return Component.empty().append(dev.vestiges.client.mobs.ClientMobs.name(t.entity(), mob)).append("  ")
				.append(dev.vestiges.client.mobs.ClientMobs.levelTag(mob));
		}
		if (t.isEntity()) {
			return t.entity().getDisplayName();
		}
		return t.state().getBlock().getName();
	}

	private static LookAtTarget target(Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		if (hit instanceof EntityHitResult eh) {
			Entity e = eh.getEntity();
			return new LookAtTarget(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), null, null, e);
		}
		if (hit instanceof BlockHitResult bh) {
			BlockPos pos = bh.getBlockPos();
			BlockState state = mc.level.getBlockState(pos);
			if (state.isAir()) {
				return null;
			}
			return new LookAtTarget(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state, pos, null);
		}
		return null;
	}
}
