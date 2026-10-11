package dev.vestiges.client.loot;

import dev.vestiges.gear.Rarity;
import dev.vestiges.loot.PersonalLoot;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Small sparkles (M3b, approved): a faint golden one on loot chests you have not opened yet, and the same small sparkle in the rarity colour on
 * Rare-or-better gear and books lying on the ground (Relic alternates brown and white). No beams.
 */
public final class LootSparkles {
	private static final int CHEST_COLOR = 0xFFE7A6;
	private static final float SIZE = 1.0f;
	private static final float ITEM_SIZE = 1.3f;
	private static int timer;

	private LootSparkles() {
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused() || ++timer % 4 != 0) {
			return;
		}
		RandomSource r = level.getRandom();
		UUID me = mc.player.getUUID();
		BlockPos at = mc.player.blockPosition();
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					PersonalLoot.LootSource src = be.getAttached(PersonalLoot.SOURCE);
					if (src != null && !src.opened().contains(me) && r.nextInt(3) == 0) {
						BlockPos p = be.getBlockPos();
						sparkle(level, r, CHEST_COLOR, p.getX() + 0.15 + r.nextDouble() * 0.7, p.getY() + 0.95 + r.nextDouble() * 0.2,
							p.getZ() + 0.15 + r.nextDouble() * 0.7);
					}
				}
			}
		}
		AABB around = mc.player.getBoundingBox().inflate(24);
		for (AbstractMinecartContainer cart : level.getEntitiesOfClass(AbstractMinecartContainer.class, around)) {
			PersonalLoot.LootSource src = cart.getAttached(PersonalLoot.SOURCE);
			if (src != null && !src.opened().contains(me) && r.nextInt(3) == 0) {
				sparkle(level, r, CHEST_COLOR, cart.getX() + (r.nextDouble() - 0.5) * 0.7, cart.getY() + 0.9 + r.nextDouble() * 0.2,
					cart.getZ() + (r.nextDouble() - 0.5) * 0.7);
			}
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) {
			if (r.nextInt(2) != 0 || !Rarity.applies(item.getItem())) {
				continue;
			}
			Rarity.Result rarity = Rarity.of(item.getItem());
			if (rarity.tier().ordinal() < Rarity.Tier.RARE.ordinal()) {
				continue;
			}
			int color = rarity.tier() == Rarity.Tier.RELIC && r.nextBoolean() ? 0xFFFFFF : brighter(rarity.tier().color);
			// Right on top of the bobbing item.
			level.addParticle(new DustParticleOptions(color, ITEM_SIZE), item.getX() + (r.nextDouble() - 0.5) * 0.15,
				item.getY() + 0.5 + r.nextDouble() * 0.15, item.getZ() + (r.nextDouble() - 0.5) * 0.15, 0, 0.015, 0);
		}
	}

	/** Dust takes the light around it, so dark rarity colours (Rare blue, Relic brown) are lifted halfway to white to stay readable. */
	private static int brighter(int rgb) {
		int luma = (299 * (rgb >> 16 & 0xFF) + 587 * (rgb >> 8 & 0xFF) + 114 * (rgb & 0xFF)) / 1000;
		if (luma >= 90) {
			return rgb;
		}
		int red = (rgb >> 16 & 0xFF) + (255 - (rgb >> 16 & 0xFF)) / 2;
		int green = (rgb >> 8 & 0xFF) + (255 - (rgb >> 8 & 0xFF)) / 2;
		int blue = (rgb & 0xFF) + (255 - (rgb & 0xFF)) / 2;
		return red << 16 | green << 8 | blue;
	}

	private static void sparkle(ClientLevel level, RandomSource r, int color, double x, double y, double z) {
		level.addParticle(new DustParticleOptions(color, SIZE), x, y, z, 0, 0.01 + r.nextDouble() * 0.01, 0);
	}
}
