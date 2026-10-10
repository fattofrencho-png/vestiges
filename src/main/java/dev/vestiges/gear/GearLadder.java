package dev.vestiges.gear;

import dev.vestiges.level.LevelService;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The gear ladder (RPG Craft's, approved for M1c): the level each gear tier is meant for. Gear above your level still works, at half its
 * armor, damage or mining speed. Runs on both sides; the client gets its level and the server's switch through {@link #clientLevel}.
 */
public final class GearLadder {
	/** Client: the local player's level, or MAX_VALUE when the server has the ladder off (set by ClientTree). */
	public static volatile ToIntFunction<Player> clientLevel = p -> Integer.MAX_VALUE;
	/** Client: whether the server's gear system is on (set by ClientTree). */
	public static volatile Predicate<Player> clientOn = p -> false;

	private GearLadder() {
	}

	/** Armor, weapons and tools a player wears or holds (not horse armor, ingots or food that share a material name). */
	public static boolean isGear(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)
			|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
			|| stack.is(ItemTags.HOES) || stack.is(ItemTags.SPEARS)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return switch (path) {
			case "bow", "crossbow", "shield", "trident", "mace", "elytra", "turtle_helmet" -> true;
			default -> false;
		};
	}

	/** The level a piece of gear is meant for: 1 wood, stone, leather · 5 chainmail, bow, shield · 10 copper, crossbow · 15 gold · 20 iron,
	 * turtle helmet · 30 diamond, trident, mace · 40 netherite, elytra. Spears follow their material. 0 for anything that isn't gear. */
	public static int levelFor(ItemStack stack) {
		if (!isGear(stack)) {
			return 0;
		}
		return levelForPath(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
	}

	public static int levelForPath(String path) {
		if (path.startsWith("netherite_")) {
			return 40;
		}
		if (path.startsWith("diamond_")) {
			return 30;
		}
		if (path.startsWith("iron_")) {
			return 20;
		}
		if (path.startsWith("golden_")) {
			return 15;
		}
		if (path.startsWith("copper_")) {
			return 10;
		}
		if (path.startsWith("chainmail_")) {
			return 5;
		}
		return switch (path) {
			case "bow", "shield" -> 5;
			case "crossbow" -> 10;
			case "turtle_helmet" -> 20;
			case "trident", "mace" -> 30;
			case "elytra" -> 40;
			default -> 1;
		};
	}

	/** True when the ladder is on for this player and the item is meant for a higher level than theirs. */
	public static boolean tooHigh(Player player, ItemStack stack) {
		int need = levelFor(stack);
		if (need <= 1 || !on(player)) {
			return false;
		}
		return level(player) < need;
	}

	public static boolean on(Player player) {
		if (player instanceof ServerPlayer) {
			return GearService.enabled() && LevelService.enabled();
		}
		return player.level().isClientSide() && clientOn.test(player);
	}

	public static int level(Player player) {
		if (player instanceof ServerPlayer sp) {
			return LevelService.get(sp).level();
		}
		return clientLevel.applyAsInt(player);
	}
}
