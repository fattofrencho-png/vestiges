package dev.vestiges.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.gear.Rarity;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Vestiges' own loot-table pieces (M3b), used by the JSON tables from tools/build_loot.py:
 * <ul>
 * <li>{@code vestiges:loot_level} {min, max}: the loot level (see {@link LootLevels}) is in range;</li>
 * <li>{@code vestiges:loot_enabled} {value}: whether the {@code loot} system is on (chests fall back to their vanilla pools when it is off);</li>
 * <li>{@code vestiges:mob_rank} {rank}: the dying mob's rank (1 Champion, 2 Elite, 3 Warlord);</li>
 * <li>{@code vestiges:loot_gear} {kind: gear|book, min_rarity, rarity}: replaces the item with found gear or a book (see {@link LootGear}).</li>
 * </ul>
 */
public final class LootTypes {
	private LootTypes() {
	}

	public record LootLevel(Optional<Integer> min, Optional<Integer> max) implements LootItemCondition {
		public static final MapCodec<LootLevel> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("min").forGetter(LootLevel::min),
			Codec.INT.optionalFieldOf("max").forGetter(LootLevel::max)
		).apply(i, LootLevel::new));

		@Override
		public boolean test(LootContext ctx) {
			int level = LootLevels.of(ctx);
			return min.map(m -> level >= m).orElse(true) && max.map(m -> level <= m).orElse(true);
		}

		@Override
		public MapCodec<LootLevel> codec() {
			return CODEC;
		}
	}

	public record LootEnabled(boolean value) implements LootItemCondition {
		public static final MapCodec<LootEnabled> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.fieldOf("value").forGetter(LootEnabled::value)
		).apply(i, LootEnabled::new));

		@Override
		public boolean test(LootContext ctx) {
			return ConfigStore.server().enabled(VestigesSystem.LOOT) == value;
		}

		@Override
		public MapCodec<LootEnabled> codec() {
			return CODEC;
		}
	}

	public record MobRank(int rank) implements LootItemCondition {
		public static final MapCodec<MobRank> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("rank").forGetter(MobRank::rank)
		).apply(i, MobRank::new));

		@Override
		public boolean test(LootContext ctx) {
			MobData d = MobService.data(ctx.getOptional(LootContextParams.THIS_ENTITY));
			return d != null && d.rank() == rank;
		}

		@Override
		public MapCodec<MobRank> codec() {
			return CODEC;
		}
	}

	/** A drop chance plus the killer's Bestiary bonus for this mob (+1% at 50 kills, +2% at 250). */
	public record DropChance(float chance) implements LootItemCondition {
		public static final MapCodec<DropChance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.FLOAT.fieldOf("chance").forGetter(DropChance::chance)
		).apply(i, DropChance::new));

		@Override
		public boolean test(LootContext ctx) {
			double bonus = 0;
			if (ctx.getOptional(LootContextParams.LAST_DAMAGE_PLAYER) instanceof net.minecraft.server.level.ServerPlayer killer) {
				bonus = dev.vestiges.codex.CodexService.dropBonus(killer, ctx.getOptional(LootContextParams.THIS_ENTITY));
			}
			return ctx.getRandom().nextFloat() < chance + bonus;
		}

		@Override
		public MapCodec<DropChance> codec() {
			return CODEC;
		}
	}

	public record Gear(String kind, Optional<String> minRarity, Optional<String> rarity) implements LootItemFunction {
		public static final MapCodec<Gear> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Gear::kind),
			Codec.STRING.optionalFieldOf("min_rarity").forGetter(Gear::minRarity),
			Codec.STRING.optionalFieldOf("rarity").forGetter(Gear::rarity)
		).apply(i, Gear::new));

		@Override
		public ItemStack apply(ItemStack stack, LootContext ctx) {
			return LootGear.build(ctx.getLevel(), ctx.getRandom(), LootLevels.of(ctx), "book".equals(kind), tier(minRarity), tier(rarity));
		}

		@Override
		public MapCodec<Gear> codec() {
			return CODEC;
		}
	}

	static Rarity.Tier tier(Optional<String> name) {
		return name.map(n -> Rarity.Tier.valueOf(n.toUpperCase(Locale.ROOT))).orElse(null);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Vestiges.id("loot_level"), LootLevel.CODEC);
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Vestiges.id("loot_enabled"), LootEnabled.CODEC);
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Vestiges.id("mob_rank"), MobRank.CODEC);
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Vestiges.id("drop_chance"), DropChance.CODEC);
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Vestiges.id("loot_gear"), Gear.CODEC);
	}
}
