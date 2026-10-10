package dev.vestiges.client.mixin;

import dev.vestiges.gear.EnchantScale;
import dev.vestiges.gear.GearService;
import dev.vestiges.server.Emeralds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The enchant tooltip shows the emerald price instead of "N Enchantment Levels" (levels are no longer spent). */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"), index = 1)
	private List<Component> vestiges$emeraldPrice(List<Component> lines) {
		Player player = Minecraft.getInstance().player;
		if (player == null || player.hasInfiniteMaterials() || !GearService.on(player)) {
			return lines;
		}
		int[] costs = ((EnchantmentScreen) (Object) this).getMenu().costs;
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			if (line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("container.enchant.level.") && !t.getKey().endsWith("requirement")) {
				// "one" is slot 1; "many" carries the slot number (2 or 3) as its argument.
				int slot = t.getKey().endsWith("one") ? 0 : t.getArgs().length > 0 && t.getArgs()[0] instanceof Integer n ? n - 1 : -1;
				if (slot >= 0 && slot < costs.length && costs[slot] > 0) {
					int price = EnchantScale.tableEmeralds(player, costs[slot]);
					boolean enough = Emeralds.count(player) >= price;
					out.add(Component.literal(price + (price == 1 ? " Emerald" : " Emeralds")).withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.RED));
					continue;
				}
			}
			out.add(line);
		}
		return out;
	}
}
