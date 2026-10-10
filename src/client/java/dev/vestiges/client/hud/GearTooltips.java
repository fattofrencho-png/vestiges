package dev.vestiges.client.hud;

import dev.vestiges.gear.GearLadder;
import dev.vestiges.gear.GearService;
import dev.vestiges.gear.Rarity;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * Item tooltips for gear (approved for M1c): the name in its rarity colour with a tier line under it (Relic in deep brown with a white outline, plus a star per
 * VI enchant), a red line when the gear is above your level, and what its trim material gives.
 */
public final class GearTooltips {
	private GearTooltips() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Player player = Minecraft.getInstance().player;
			if (lines.isEmpty() || player == null || !GearService.on(player)) {
				return;
			}
			int at = 1;
			if (Rarity.applies(stack)) {
				Rarity.Result r = Rarity.of(stack);
				boolean relic = r.tier() == Rarity.Tier.RELIC;
				String stars = r.stars() > 0 ? " " + "★".repeat(r.stars()) : "";
				lines.set(0, styled(lines.get(0).copy(), r.tier(), relic));
				lines.add(at++, styled(Component.literal(r.tier().label + stars), r.tier(), relic));
			}
			int need = GearLadder.levelFor(stack);
			if (need > 1 && GearLadder.tooHigh(player, stack)) {
				lines.add(at++, Component.literal("Level " + need + " gear (you are level " + GearLadder.level(player) + "): half strength")
					.withStyle(ChatFormatting.RED));
			}
			ArmorTrim trim = stack.get(DataComponents.TRIM);
			if (trim != null) {
				trim.material().unwrapKey().map(k -> GearService.TRIMS.get(k.identifier().getPath())).ifPresent(t -> {
					String later = GearService.TRIM_LATER.get(t.stat());
					lines.add(Component.literal("Trim: " + t.text() + " per piece, +25% with 4 of this material")
						.withStyle(later == null ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
					if (later != null) {
						lines.add(Component.literal("Not active yet: comes with " + later + ".").withStyle(ChatFormatting.DARK_GRAY));
					}
				});
				lines.add(Component.literal("4 pieces with the same pattern: +2 max health, +2% movement speed").withStyle(ChatFormatting.DARK_AQUA));
			}
		});
	}

	/** Marks Relic lines; the tooltip text renderer draws them deep brown with a thin white outline. */
	private static final String RELIC_MARK = "vestiges:relic";

	private static MutableComponent styled(MutableComponent line, Rarity.Tier tier, boolean relic) {
		return line.withStyle(s -> relic ? s.withColor(tier.color).withInsertion(RELIC_MARK) : s.withColor(tier.color));
	}

	/** True when a tooltip line carries the Relic mark. */
	public static boolean isRelic(FormattedCharSequence text) {
		boolean[] found = {false};
		text.accept((index, style, codePoint) -> {
			if (RELIC_MARK.equals(style.getInsertion())) {
				found[0] = true;
				return false;
			}
			return true;
		});
		return found[0];
	}
}
