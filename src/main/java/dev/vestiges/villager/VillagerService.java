package dev.vestiges.villager;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.data.VillagerLoreDef;
import dev.vestiges.world.WorldAtlas;
import dev.vestiges.world.WorldPlans;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

/**
 * Villagers (slice 1: identity). Every villager gets a name and a background the first time it loads, saved on it and shown to everyone near it.
 * Villagers that already exist in an older world get theirs the same way. Behind the Villagers switch: off, villagers stay as vanilla made them.
 */
public final class VillagerService {
	public static final AttachmentType<VillagerProfile> PROFILE = AttachmentRegistry.create(Vestiges.id("villager"),
		b -> b.persistent(VillagerProfile.CODEC).syncWith(VillagerProfile.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private VillagerService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.VILLAGERS);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && enabled() && villager.getAttached(PROFILE) == null) {
				assign(villager, level);
			}
		});
	}

	/** The lore lists in use, or null before the data has loaded. */
	public static VillagerLoreDef lore() {
		return VestigesData.VILLAGER_LORE.all().values().stream().findFirst().orElse(null);
	}

	private static void assign(Villager villager, ServerLevel level) {
		VillagerLoreDef lore = lore();
		if (lore == null) {
			return;
		}
		long seed = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
		String continent = level.dimension() == Level.OVERWORLD ? WorldAtlas.continentAt(level.getServer(), villager.getX(), villager.getZ()) : null;
		villager.setAttached(PROFILE, VillagerProfile.generate(lore, seed, continent, farLand(continent, seed)));
	}

	/** Another continent than the given one, picked by the seed; null in a world without continents. */
	static String farLand(String continent, long seed) {
		if (continent == null) {
			return null;
		}
		List<String> others = new ArrayList<>();
		for (var land : WorldPlans.current().lands()) {
			if (!land.name().equals(continent)) {
				others.add(land.name());
			}
		}
		return others.isEmpty() ? null : others.get((int) Math.floorMod(seed >> 7, (long) others.size()));
	}
}
