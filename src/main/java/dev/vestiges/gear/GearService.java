package dev.vestiges.gear;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.level.LevelService;
import dev.vestiges.tree.PlayerStats;
import dev.vestiges.tree.StatEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * What the player wears, beyond vanilla: trim stats (material sets the stat per piece, set bonuses; approved in Batch 3) and the gear ladder's
 * armor penalty. Checked every 10 ticks; when the armor or the level changes, the stats are rebuilt and merged into the player's stats.
 */
public final class GearService {
	/** One trim material's stat per armor piece. */
	public record TrimStat(String stat, double value, String text) {
	}

	public static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	public static final Map<String, TrimStat> TRIMS = Map.ofEntries(
		Map.entry("iron", new TrimStat("armor", 1, "+1 armor")),
		Map.entry("netherite", new TrimStat("toughness", 1, "+1 armor toughness")),
		Map.entry("diamond", new TrimStat("max_health", 3, "+3 max health")),
		Map.entry("amethyst", new TrimStat("damage", 3, "+3% damage")),
		Map.entry("quartz", new TrimStat("attack_speed", 2, "+2% attack speed")),
		Map.entry("redstone", new TrimStat("movement_speed", 2, "+2% movement speed")),
		Map.entry("resin", new TrimStat("regen_speed", 5, "+5% natural regeneration")),
		Map.entry("gold", new TrimStat("mining_speed", 4, "+4% mining speed")),
		Map.entry("copper", new TrimStat("placing_speed", 5, "+5% block placing speed")),
		Map.entry("lapis", new TrimStat("enchant_cost", -4, "-4% enchanting cost")),
		Map.entry("emerald", new TrimStat("trade_discount", 2, "villager trades 2% cheaper")));
	/** Trim stats whose system comes later (shown greyed in tooltips). */
	public static final Map<String, String> TRIM_LATER = Map.of("placing_speed", "settlements (M5)", "trade_discount", "villagers (M6)");

	private static final Identifier LADDER_ARMOR = Vestiges.id("ladder_armor");
	private static final Identifier LADDER_TOUGHNESS = Vestiges.id("ladder_toughness");
	private static final Map<UUID, PlayerStats> STATS = new HashMap<>();
	private static final Map<UUID, Integer> SIGNATURES = new HashMap<>();

	private GearService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GearService::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			STATS.remove(handler.player.getUUID());
			SIGNATURES.remove(handler.player.getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SIGNATURES.remove(newPlayer.getUUID()));
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.GEAR);
	}

	/** Server: the config switch. Client: what the server told us. */
	public static boolean on(Player player) {
		return player instanceof ServerPlayer ? enabled() : GearLadder.clientOn.test(player);
	}

	public static PlayerStats stats(ServerPlayer player) {
		return STATS.getOrDefault(player.getUUID(), PlayerStats.NONE);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		boolean on = enabled();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int sig = signature(p, on);
			if (!Objects.equals(SIGNATURES.put(p.getUUID(), sig), sig)) {
				rebuild(p, on);
			}
		}
	}

	private static int signature(ServerPlayer p, boolean on) {
		int h = Objects.hash(on, LevelService.enabled() ? LevelService.get(p).level() : 0);
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			h = 31 * h + Objects.hash(BuiltInRegistries.ITEM.getKey(s.getItem()), s.get(DataComponents.TRIM));
		}
		return h;
	}

	private static void rebuild(ServerPlayer p, boolean on) {
		List<ItemStack> armor = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR) {
			armor.add(p.getItemBySlot(slot));
		}
		STATS.put(p.getUUID(), on ? trimStats(armor) : PlayerStats.NONE);
		// Ladder: armor above your level gives half its armor and toughness.
		double armorLoss = 0;
		double toughnessLoss = 0;
		if (on) {
			for (EquipmentSlot slot : ARMOR) {
				ItemStack s = p.getItemBySlot(slot);
				if (!GearLadder.tooHigh(p, s)) {
					continue;
				}
				double[] sums = new double[2];
				s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attr, mod) -> {
					if (mod.operation() == AttributeModifier.Operation.ADD_VALUE && attr.value() == Attributes.ARMOR.value()) {
						sums[0] += mod.amount();
					} else if (mod.operation() == AttributeModifier.Operation.ADD_VALUE && attr.value() == Attributes.ARMOR_TOUGHNESS.value()) {
						sums[1] += mod.amount();
					}
				});
				armorLoss += sums[0] / 2;
				toughnessLoss += sums[1] / 2;
			}
		}
		StatEngine.setModifier(p, Attributes.ARMOR, LADDER_ARMOR, -armorLoss, AttributeModifier.Operation.ADD_VALUE);
		StatEngine.setModifier(p, Attributes.ARMOR_TOUGHNESS, LADDER_TOUGHNESS, -toughnessLoss, AttributeModifier.Operation.ADD_VALUE);
		StatEngine.invalidate(p);
		StatEngine.apply(p);
	}

	/** Trim stats from four armor pieces: each piece adds its material's stat; 4 of one material = +25% on it; 4 of one pattern = +2 max
	 * health and +2% movement speed. Pure (the client uses it too, for its own cost previews). */
	public static PlayerStats trimStats(Iterable<ItemStack> armor) {
		Map<String, Integer> materials = new HashMap<>();
		Map<String, Integer> patterns = new HashMap<>();
		for (ItemStack s : armor) {
			ArmorTrim trim = s.isEmpty() ? null : s.get(DataComponents.TRIM);
			if (trim == null) {
				continue;
			}
			trim.material().unwrapKey().ifPresent(k -> materials.merge(k.identifier().getPath(), 1, Integer::sum));
			trim.pattern().unwrapKey().ifPresent(k -> patterns.merge(k.identifier().getPath(), 1, Integer::sum));
		}
		if (materials.isEmpty()) {
			return PlayerStats.NONE;
		}
		PlayerStats out = new PlayerStats();
		for (Map.Entry<String, Integer> m : materials.entrySet()) {
			TrimStat t = TRIMS.get(m.getKey());
			if (t != null) {
				out.addStat(t.stat(), t.value() * m.getValue() * (m.getValue() >= 4 ? 1.25 : 1.0));
			}
		}
		if (patterns.values().stream().anyMatch(n -> n >= 4)) {
			out.addStat("max_health", 2);
			out.addStat("movement_speed", 2);
		}
		return out;
	}
}
