package dev.vestiges.gametest;

import dev.vestiges.config.VestigesSystem;
import dev.vestiges.gear.BrokenGear;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * S12: the reworked Mending ({@code BrokenGear}). Gear with Mending that would break stays at 1 durability, broken and useless, with the enchant
 * gone; gear without Mending breaks as in vanilla; a repaired item is normal again and can take Mending once more; XP orbs no longer repair;
 * with the Gear switch off everything is vanilla. All of it runs on the real server with a survival player (creative skips durability).
 */
final class MendingSuite {
	private MendingSuite() {
	}

	/** What one "hit the item hard" run showed. */
	private record Hit(boolean broke, int damage, int max, boolean brokenFlag, boolean mendingLeft, float speed, boolean dropsOk, InteractionResult use,
		int modifiers, int modifiersFresh) {
	}

	private static Holder<Enchantment> mending(MinecraftServer s) {
		return s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
	}

	private static ItemStack nearlyBroken(MinecraftServer s, net.minecraft.world.item.Item item, boolean withMending) {
		ItemStack stack = new ItemStack(item);
		if (withMending) {
			stack.enchant(mending(s), 1);
		}
		stack.setDamageValue(stack.getMaxDamage() - 2);
		return stack;
	}

	private static int modifiers(ItemStack stack, EquipmentSlot slot) {
		int[] n = {0};
		stack.forEachModifier(slot, (attribute, modifier) -> n[0]++);
		return n[0];
	}

	/** Hits {@code stack} for {@code amount} durability as a survival player would (the server path that breaks tools), then looks at it. */
	private static Hit hit(MinecraftServer s, ItemStack stack, int amount, EquipmentSlot slot) {
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		boolean[] broke = {false};
		int fresh = modifiers(new ItemStack(stack.getItem()), slot);
		stack.hurtAndBreak(amount, s.overworld(), p, item -> broke[0] = true);
		return new Hit(broke[0], stack.getDamageValue(), stack.getMaxDamage(), BrokenGear.isBroken(stack), BrokenGear.hasMending(stack),
			stack.getDestroySpeed(Blocks.STONE.defaultBlockState()), stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
			stack.use(s.overworld(), p, InteractionHand.MAIN_HAND), modifiers(stack, slot), fresh);
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		var wanted = T.switches();
		server.runCommand("gamemode survival @p");
		server.runCommand("vestiges level set @p 5");
		ctx.waitTicks(5);

		c.section("A Mending tool that would break is held at 1 durability, broken");
		Hit h = server.computeOnServer(s -> hit(s, nearlyBroken(s, Items.DIAMOND_PICKAXE, true), 5, EquipmentSlot.MAINHAND));
		c.check("the tool did not break", !h.broke, "broke=" + h.broke);
		c.check("it sits at 1 durability", h.damage == h.max - 1, h.damage + " of " + h.max);
		c.check("it is flagged broken", h.brokenFlag);
		c.check("Mending is used up (the enchant is gone)", !h.mendingLeft);
		c.check("a broken pickaxe mines at hand speed", h.speed == 1.0F, String.valueOf(h.speed));
		c.check("a broken pickaxe harvests nothing (wrong tool for drops)", !h.dropsOk);
		c.check("a broken item can't be used", h.use == InteractionResult.FAIL, String.valueOf(h.use));

		c.section("Broken armor gives no stats");
		Hit a = server.computeOnServer(s -> hit(s, nearlyBroken(s, Items.DIAMOND_CHESTPLATE, true), 5, EquipmentSlot.CHEST));
		c.check("the chestplate did not break and is broken", !a.broke && a.brokenFlag && a.damage == a.max - 1, "broke=" + a.broke + " flag=" + a.brokenFlag);
		c.check("a fresh chestplate has armor modifiers", a.modifiersFresh > 0, String.valueOf(a.modifiersFresh));
		c.check("the broken chestplate has none", a.modifiers == 0, String.valueOf(a.modifiers));

		c.section("A broken item stays broken when hit again, and repairing ends it");
		boolean[] steps = server.computeOnServer(s -> {
			ItemStack stack = nearlyBroken(s, Items.DIAMOND_PICKAXE, true);
			hit(s, stack, 5, EquipmentSlot.MAINHAND);
			Hit again = hit(s, stack, 5, EquipmentSlot.MAINHAND);
			boolean stillBroken = !again.broke && again.brokenFlag && again.damage == again.max - 1;
			stack.setDamageValue(0);
			boolean repaired = !BrokenGear.isBroken(stack) && stack.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F;
			// A repaired item can take Mending again and be saved once more.
			stack.enchant(mending(s), 1);
			stack.setDamageValue(stack.getMaxDamage() - 2);
			Hit second = hit(s, stack, 5, EquipmentSlot.MAINHAND);
			return new boolean[] {stillBroken, repaired, !second.broke && second.brokenFlag};
		});
		c.check("hitting a broken item again does not break it", steps[0]);
		c.check("a repaired item is normal again (not broken, mines fast)", steps[1]);
		c.check("a repaired item given Mending again is saved a second time", steps[2]);

		c.section("Without Mending, gear breaks as in vanilla");
		Hit v = server.computeOnServer(s -> hit(s, nearlyBroken(s, Items.DIAMOND_PICKAXE, false), 5, EquipmentSlot.MAINHAND));
		c.check("a plain tool breaks", v.broke, "broke=" + v.broke);
		c.check("and is never flagged broken", !v.brokenFlag);

		c.section("XP orbs no longer repair");
		server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
			stack.enchant(mending(s), 1);
			stack.setDamageValue(100);
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), stack);
			return 0;
		});
		server.runCommand("execute as @p at @s run summon minecraft:experience_orb ~ ~ ~ {Value:30s}");
		ctx.waitTicks(40);
		// Vanilla XP no longer changes the player's XP in this mod (the bar mirrors the Vestiges level), so "picked up" is shown by the orb being gone.
		int[] after = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			int orbs = s.overworld().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(8.0)).size();
			return new int[] {orbs, p.getInventory().getItem(p.getInventory().getSelectedSlot()).getDamageValue()};
		});
		c.check("the orb was picked up (so the repair path really ran)", after[0] == 0, after[0] + " orbs left near the player");
		c.check("the Mending pickaxe was not repaired by it", after[1] == 100, "damage " + after[1] + " (was 100)");

		c.section("Gear switch off: vanilla");
		server.runCommand("vestiges config system gear false");
		ctx.waitTicks(10);
		Hit off = server.computeOnServer(s -> hit(s, nearlyBroken(s, Items.DIAMOND_PICKAXE, true), 5, EquipmentSlot.MAINHAND));
		c.check("with Gear off a Mending tool breaks like vanilla", off.broke && !off.brokenFlag, "broke=" + off.broke + " flag=" + off.brokenFlag);
		T.restore(server, wanted);
		ctx.waitTicks(10);
		Hit on = server.computeOnServer(s -> hit(s, nearlyBroken(s, Items.DIAMOND_PICKAXE, true), 5, EquipmentSlot.MAINHAND));
		c.check("with Gear back on Mending holds again", !on.broke && on.brokenFlag);

		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
	}
}
