#!/usr/bin/env python3
"""build_loot.py - Vestiges loot tables (M3b), adapted from RPG Craft's tools/build_loot.py.

Writes, under src/main/resources/data/:
    vestiges/loot_table/loot/bands/band_<N>.json     11 band tables, RPG Craft's 6 pools
    vestiges/loot_table/loot/scaled.json             band router (vestiges:loot_level)
    vestiges/loot_table/loot/bundles/<kit>[/band_N].json  kit bundles + routers
    vestiges/loot_table/entities/monster_drops.json  mob and elite drops (player kills)
    vestiges/vestiges/loot_rules/default.json        gear ladder + rarity odds, read by Java (LootRules)
    minecraft/loot_table/chests/**.json              every vanilla chest table: themed pools without gear + one band
                                                     roll while the `loot` system is on; the untouched vanilla pools
                                                     while it is off

Differences from RPG Craft (approved 2026-10-11):
  - gear and books are built in Java (vestiges:loot_gear): the rarity is rolled from the Batch 3 odds, then an item is
    made to match it; the ladder lives in loot_rules so Java and this script agree;
  - bands are picked by Vestiges' loot level (75% of the opener's level + layer bonus, capped per layer);
  - no elytra, heavy core or nether star in chests; spears join the weapon ladder;
  - every vanilla chest keeps working when the `loot` system is switched off.

Run after changing anything here:   python tools/build_loot.py
"""

import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "data"
LOOT = DATA / "vestiges" / "loot_table" / "loot"
CHESTS = DATA / "minecraft" / "loot_table" / "chests"
RPG_CRAFT_CHESTS = (ROOT.parent.parent / "Minecraft DataPacks" / "RPG Craft" / "datapacks" / "RPG_Loot_Rebalanced" / "data"
                    / "minecraft" / "loot_table" / "chests")
MC_JAR = next((ROOT / ".gradle" / "loom-cache" / "minecraftMaven" / "net" / "minecraft").glob("minecraft-common-*/26.3/*.jar"))

# --- Bands (loot level ranges) -------------------------------------------------------------------------------------
BANDS = {
    1: (1, 4), 2: (5, 9), 3: (10, 14), 4: (15, 19), 5: (20, 29), 6: (30, 39),
    7: (40, 49), 8: (50, 64), 9: (65, 79), 10: (80, 89), 11: (90, 100),
}
NB = len(BANDS)

# Batch 3 rarity odds per item (%): Common, Uncommon, Superior, Rare, Epic, Legendary, Relic. Dungeon rows come in M8.
RARITY_ROWS = [
    (10, [60, 35, 4.9, 0.1, 0, 0, 0]),
    (20, [35, 44, 17, 3.5, 0.4, 0.08, 0.02]),
    (35, [15, 39, 32, 12, 1.7, 0.25, 0.05]),
    (10000, [5, 24, 40, 25, 5.4, 0.5, 0.1]),
]


def t_of(band):
    return (band - 1) / (NB - 1)


def interp(band, v1, v11):
    return v1 + t_of(band) * (v11 - v1)


def scale_count(band, lo1, hi1, lo11, hi11):
    lo = max(1, round(interp(band, lo1, lo11)))
    hi = max(lo, round(interp(band, hi1, hi11)))
    return lo, hi


def piecewise(band, anchors):
    keys = sorted(anchors)
    if band <= keys[0]:
        return anchors[keys[0]]
    if band >= keys[-1]:
        return anchors[keys[-1]]
    for a, b in zip(keys, keys[1:]):
        if a <= band <= b:
            f = (band - a) / (b - a)
            return anchors[a] + f * (anchors[b] - anchors[a])
    return anchors[keys[-1]]


# --- Gear ladder (exported to loot_rules; Java picks the base) ------------------------------------------------------
LADDER = {
    "wooden": (1, 1, 26), "stone": (1, 2, 30), "leather": (1, 1, 30), "chainmail": (2, 3, 22), "copper": (3, 4, 24),
    "golden": (4, 5, 20), "iron": (5, 6, 34), "diamond": (6, 8, 24), "netherite": (7, 10, 16),
}
WEAPON_MATS = ["wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"]
ARMOR_MATS = ["leather", "chainmail", "copper", "golden", "iron", "diamond", "netherite"]
STANDALONE = [
    ("bow", 2, 4, 40), ("crossbow", 3, 4, 30), ("shield", 2, 4, 34), ("turtle_helmet", 5, 7, 12), ("trident", 6, 8, 12),
    ("mace", 6, 8, 9), ("fishing_rod", 1, 2, 16), ("shears", 1, 2, 20), ("flint_and_steel", 1, 3, 18), ("brush", 2, 4, 10),
]


def material_items(mat):
    out = {}
    if mat in WEAPON_MATS:
        out[f"minecraft:{mat}_sword"] = 1.0
        out[f"minecraft:{mat}_spear"] = 0.85
        for s in ["pickaxe", "axe", "shovel", "hoe"]:
            out[f"minecraft:{mat}_{s}"] = 1.0 if s == "pickaxe" else 0.85
    if mat in ARMOR_MATS:
        for s, m in [("helmet", 0.9), ("chestplate", 1.1), ("leggings", 1.0), ("boots", 0.9)]:
            out[f"minecraft:{mat}_{s}"] = m
    return out


def gear_weight(band, start, peak, peak_w):
    if band < start:
        return 0
    full = peak_w * 100
    if band <= peak:
        prog = 0 if peak == start else (band - start) / (peak - start)
        return max(1, int(full * (0.10 + 0.90 * prog)))
    return max(1, int(full * max(0.06, 0.72 ** (band - peak))))


# --- The catalogue (non-gear), from RPG Craft --------------------------------------------------------------------------
class Item:
    __slots__ = ("id", "start", "w", "c1lo", "c1hi", "c11lo", "c11hi", "bmin")

    def __init__(self, id, start=1, w=10, c=(1, 1, 1, 1), bmin=None):
        self.id, self.start, self.w = id, start, w
        self.c1lo, self.c1hi, self.c11lo, self.c11hi = c
        self.bmin = bmin

    def weight(self, band):
        if band < self.start or (self.bmin and band < self.bmin):
            return 0
        d = band - self.start
        ramp = 0.4 if d == 0 else (0.7 if d == 1 else 1.0)
        return max(1, int(self.w * ramp))

    def count(self, band):
        return scale_count(band, self.c1lo, self.c1hi, self.c11lo, self.c11hi)


def C(*a):
    if len(a) == 1:
        return (a[0], a[0], a[0], a[0])
    if len(a) == 2:
        return (a[0], a[1], a[0], a[1])
    return a


CATALOGUE = {
    "ore": [
        Item("iron_ingot", 1, 70, C(2, 5, 32, 64)), Item("copper_ingot", 1, 55, C(3, 8, 32, 96)),
        Item("coal", 1, 55, C(4, 10, 24, 64)), Item("redstone", 1, 45, C(3, 8, 32, 96)),
        Item("lapis_lazuli", 1, 45, C(2, 6, 24, 64)), Item("gold_ingot", 2, 55, C(1, 3, 24, 64)),
        Item("quartz", 3, 45, C(4, 10, 32, 96)), Item("iron_nugget", 1, 20, C(4, 12, 12, 32)),
        Item("gold_nugget", 2, 18, C(4, 12, 12, 32)), Item("raw_iron", 1, 40, C(2, 5, 16, 48)),
        Item("raw_copper", 1, 35, C(3, 8, 16, 48)), Item("raw_gold", 2, 30, C(1, 4, 12, 32)),
        Item("amethyst_shard", 4, 30, C(2, 5, 12, 40)), Item("netherite_scrap", 7, 12, C(1, 1, 2, 4)),
        Item("iron_block", 6, 8, C(1, 2), bmin=6), Item("gold_block", 6, 6, C(1, 2), bmin=6),
        Item("copper_block", 5, 8, C(1, 3), bmin=5), Item("coal_block", 5, 8, C(1, 3), bmin=5),
        Item("redstone_block", 6, 7, C(1, 3), bmin=6), Item("lapis_block", 6, 6, C(1, 2), bmin=6),
    ],
    "gem": [
        Item("diamond", 6, 40, C(1, 2, 4, 12)), Item("emerald", 1, 70, C(1, 3, 12, 48)),
        Item("emerald_block", 6, 12, C(1, 2), bmin=6), Item("diamond_block", 8, 6, C(1, 1), bmin=8),
    ],
    "food": [
        Item("bread", 1, 40, C(2, 4, 8, 24)), Item("apple", 1, 26, C(2, 4, 6, 16)),
        Item("baked_potato", 1, 26, C(3, 6, 8, 24)), Item("carrot", 1, 22, C(3, 6, 8, 24)),
        Item("cooked_cod", 1, 20, C(2, 4, 6, 16)), Item("cooked_chicken", 1, 20, C(2, 4, 6, 16)),
        Item("cooked_beef", 3, 32, C(2, 5, 8, 24)), Item("cooked_porkchop", 3, 28, C(2, 5, 8, 24)),
        Item("cooked_salmon", 3, 22, C(2, 4, 6, 20)), Item("pumpkin_pie", 3, 16, C(1, 3, 4, 12)),
        Item("golden_carrot", 5, 22, C(2, 4, 8, 16)), Item("golden_apple", 6, 9, C(1, 2, 2, 5)),
    ],
    "brewing": [
        Item("nether_wart", 3, 40, C(3, 8, 12, 48)), Item("blaze_powder", 3, 30, C(2, 5, 8, 24)),
        Item("glowstone_dust", 3, 30, C(3, 8, 12, 48)), Item("gunpowder", 2, 26, C(3, 8, 12, 48)),
        Item("spider_eye", 1, 20, C(2, 6, 8, 24)), Item("fermented_spider_eye", 3, 16, C(1, 3, 4, 12)),
        Item("magma_cream", 4, 18, C(2, 5, 8, 24)), Item("ghast_tear", 5, 10, C(1, 2, 3, 8)),
        Item("rabbit_foot", 3, 10, C(1, 2, 2, 6)), Item("phantom_membrane", 5, 12, C(1, 3, 4, 12)),
        Item("pufferfish", 2, 14, C(1, 3, 4, 12)), Item("sugar", 1, 16, C(3, 8, 12, 32)),
        Item("glistering_melon_slice", 4, 12, C(2, 5, 8, 24)), Item("glass_bottle", 1, 22, C(3, 8, 12, 32)),
        Item("dragon_breath", 9, 6, C(1, 2, 2, 6)),
    ],
    "redstone": [
        Item("redstone", 1, 45, C(4, 12, 16, 64)), Item("redstone_torch", 1, 30, C(2, 6, 8, 24)),
        Item("repeater", 1, 30, C(1, 4, 6, 16)), Item("comparator", 2, 24, C(1, 3, 4, 12)),
        Item("lever", 1, 22, C(2, 5, 6, 16)), Item("tripwire_hook", 2, 16, C(1, 4, 4, 12)),
        Item("piston", 2, 24, C(1, 4, 6, 16)), Item("sticky_piston", 3, 18, C(1, 3, 4, 12)),
        Item("observer", 3, 18, C(1, 3, 4, 12)), Item("hopper", 2, 18, C(1, 2, 3, 10)),
        Item("dropper", 2, 16, C(1, 3, 4, 12)), Item("dispenser", 3, 14, C(1, 2, 3, 10)),
        Item("target", 2, 14, C(1, 3, 4, 12)), Item("daylight_detector", 2, 12, C(1, 2, 2, 8)),
        Item("note_block", 1, 14, C(1, 4, 4, 12)), Item("redstone_lamp", 2, 16, C(1, 4, 4, 16)),
        Item("rail", 1, 20, C(4, 12, 12, 48)), Item("powered_rail", 2, 14, C(2, 6, 8, 24)),
        Item("detector_rail", 2, 12, C(1, 4, 4, 16)), Item("activator_rail", 3, 10, C(1, 4, 4, 16)),
    ],
    "seed": [
        Item("wheat_seeds", 1, 40, C(3, 8, 12, 32)), Item("beetroot_seeds", 1, 30, C(2, 6, 8, 24)),
        Item("melon_seeds", 1, 24, C(1, 4, 4, 16)), Item("pumpkin_seeds", 1, 24, C(1, 4, 4, 16)),
        Item("torchflower_seeds", 4, 8, C(1, 2, 2, 6)), Item("pitcher_pod", 4, 8, C(1, 2, 2, 6)),
        Item("bone_meal", 1, 36, C(4, 10, 16, 48)), Item("oak_sapling", 1, 18, C(2, 5, 6, 16)),
        Item("spruce_sapling", 1, 18, C(2, 5, 6, 16)), Item("birch_sapling", 1, 18, C(2, 5, 6, 16)),
        Item("jungle_sapling", 1, 14, C(1, 4, 4, 12)), Item("acacia_sapling", 1, 14, C(1, 4, 4, 12)),
        Item("dark_oak_sapling", 1, 14, C(1, 4, 4, 12)), Item("cherry_sapling", 2, 12, C(1, 3, 3, 10)),
        Item("mangrove_propagule", 2, 12, C(1, 3, 3, 10)), Item("sugar_cane", 1, 24, C(2, 6, 8, 24)),
        Item("cocoa_beans", 2, 16, C(2, 6, 8, 24)), Item("sweet_berries", 1, 16, C(2, 6, 8, 24)),
        Item("glow_berries", 3, 14, C(2, 6, 8, 24)), Item("kelp", 1, 12, C(3, 8, 8, 24)),
    ],
    "construction": [
        Item("white_terracotta", 1, 18, C(6, 16, 48, 192)), Item("orange_terracotta", 1, 12, C(6, 16, 48, 160)),
        Item("light_blue_terracotta", 1, 12, C(6, 16, 48, 160)), Item("yellow_terracotta", 1, 12, C(6, 16, 48, 160)),
        Item("brown_terracotta", 1, 10, C(6, 16, 48, 160)), Item("terracotta", 1, 20, C(8, 24, 64, 224)),
        Item("sandstone", 1, 20, C(8, 24, 64, 224)), Item("red_sandstone", 1, 14, C(8, 24, 48, 192)),
        Item("mud", 1, 16, C(8, 24, 48, 192)), Item("packed_mud", 2, 14, C(6, 16, 32, 128)),
        Item("prismarine", 4, 16, C(6, 16, 48, 160)), Item("dark_prismarine", 5, 12, C(4, 12, 32, 128)),
        Item("quartz_block", 3, 18, C(6, 16, 48, 192)), Item("purpur_block", 7, 12, C(6, 16, 32, 128)),
        Item("purpur_pillar", 7, 8, C(4, 12, 24, 96)), Item("calcite", 2, 12, C(6, 16, 32, 128)),
        Item("dripstone_block", 2, 10, C(6, 16, 32, 128)), Item("tuff", 2, 12, C(8, 24, 48, 160)),
        Item("glass", 1, 20, C(8, 24, 64, 224)), Item("tinted_glass", 4, 8, C(2, 6, 12, 48)),
        Item("white_wool", 1, 16, C(6, 16, 32, 128)), Item("light_gray_wool", 1, 10, C(6, 16, 32, 128)),
        Item("brown_wool", 1, 8, C(6, 16, 32, 128)), Item("moss_block", 2, 10, C(4, 12, 24, 96)),
        Item("sea_lantern", 4, 8, C(2, 6, 12, 48)), Item("obsidian", 3, 12, C(2, 6, 16, 64)),
        Item("sponge", 8, 4, C(1, 3, 2, 8), bmin=8),
    ],
    "decoration": [
        Item("ladder", 1, 24, C(4, 12, 12, 48)), Item("lantern", 1, 22, C(2, 6, 8, 24)),
        Item("soul_lantern", 3, 12, C(1, 4, 4, 16)), Item("white_candle", 1, 12, C(2, 5, 6, 16)),
        Item("orange_candle", 1, 8, C(2, 5, 6, 16)), Item("flower_pot", 1, 14, C(1, 4, 3, 10)),
        Item("painting", 1, 14, C(1, 3, 2, 6)), Item("item_frame", 1, 16, C(1, 4, 4, 12)),
        Item("white_banner", 1, 10, C(1, 2, 2, 6)), Item("iron_chain", 1, 16, C(2, 6, 8, 24)),
        Item("bell", 2, 8, C(1, 1, 1, 3)), Item("white_glazed_terracotta", 2, 10, C(2, 6, 8, 24)),
        Item("blue_glazed_terracotta", 2, 8, C(2, 6, 8, 24)), Item("pointed_dripstone", 2, 8, C(2, 6, 6, 16)),
        Item("poppy", 1, 10, C(2, 6, 4, 12)), Item("dandelion", 1, 10, C(2, 6, 4, 12)),
        Item("moss_carpet", 2, 8, C(2, 6, 8, 24)), Item("white_carpet", 1, 10, C(2, 6, 8, 24)),
        Item("scaffolding", 1, 12, C(4, 12, 12, 48)), Item("glass_pane", 1, 14, C(4, 12, 16, 64)),
        Item("iron_bars", 1, 12, C(4, 12, 12, 48)), Item("cobweb", 2, 8, C(1, 4, 4, 12)),
        Item("end_rod", 7, 10, C(2, 6, 6, 16)), Item("shroomlight", 4, 8, C(1, 4, 4, 12)),
        Item("armor_stand", 1, 10, C(1, 2, 2, 4)),
    ],
    "decoration_special": [
        Item("decorated_pot", 2, 14, C(1, 2, 2, 6)), Item("amethyst_cluster", 4, 12, C(1, 3, 4, 12)),
        Item("ochre_froglight", 6, 8, C(1, 3, 2, 8)), Item("verdant_froglight", 6, 8, C(1, 3, 2, 8)),
        Item("pearlescent_froglight", 6, 6, C(1, 3, 2, 8)), Item("glow_item_frame", 3, 10, C(1, 3, 3, 10)),
        Item("skeleton_skull", 4, 5, C(1, 1, 1, 2)), Item("zombie_head", 4, 5, C(1, 1, 1, 2)),
        Item("creeper_head", 5, 4, C(1, 1, 1, 2)), Item("oak_hanging_sign", 1, 10, C(1, 3, 2, 6)),
        Item("copper_bulb", 4, 8, C(1, 3, 3, 10)), Item("sculk", 7, 8, C(2, 6, 6, 16)),
        Item("sculk_vein", 7, 6, C(2, 6, 6, 16)), Item("flower_banner_pattern", 3, 4, C(1, 1)),
        Item("mojang_banner_pattern", 6, 2, C(1, 1)),
    ] + [Item(f"{s}_pottery_sherd", 1, 6, C(1, 1)) for s in [
        "angler", "archer", "arms_up", "blade", "brewer", "burn", "danger", "explorer", "flow", "friend", "guster", "heart",
        "heartbreak", "howl", "miner", "mourner", "plenty", "prize", "scrape", "sheaf", "shelter", "skull", "snort"]],
    "utility": [
        Item("torch", 1, 40, C(1, 1, 8, 16)), Item("arrow", 1, 30, C(4, 12, 16, 64)), Item("map", 1, 20, C(1, 1, 1, 2)),
        Item("compass", 1, 18, C(1, 1)), Item("clock", 1, 14, C(1, 1)), Item("lead", 1, 20, C(1, 2, 1, 3)),
        Item("name_tag", 1, 14, C(1, 1, 1, 2)), Item("saddle", 2, 14, C(1, 1)), Item("spyglass", 3, 8, C(1, 1)),
        Item("oak_boat", 1, 12, C(1, 1)), Item("ender_pearl", 3, 16, C(1, 3, 4, 12)),
        Item("fire_charge", 3, 10, C(1, 4, 4, 12)), Item("water_bucket", 1, 10, C(1, 1)), Item("bundle", 1, 12, C(1, 1)),
    ],
    # No heavy core (trial vaults) and no nether star (the Wither) in chests.
    "collectible": [
        Item("music_disc_cat", 2, 6, C(1, 1)), Item("music_disc_13", 2, 6, C(1, 1)),
        Item("music_disc_blocks", 3, 5, C(1, 1)), Item("music_disc_wait", 3, 5, C(1, 1)),
        Item("music_disc_mall", 4, 4, C(1, 1)), Item("music_disc_ward", 5, 3, C(1, 1)),
        Item("music_disc_5", 6, 3, C(1, 1)), Item("music_disc_otherside", 6, 3, C(1, 1)),
        Item("music_disc_pigstep", 7, 3, C(1, 1)), Item("music_disc_precipice", 8, 2, C(1, 1)),
        Item("music_disc_relic", 8, 2, C(1, 1)), Item("music_disc_creator", 9, 2, C(1, 1)),
        Item("goat_horn", 2, 8, C(1, 1)), Item("heart_of_the_sea", 5, 4, C(1, 1)),
        Item("nautilus_shell", 3, 10, C(1, 2, 2, 5)), Item("totem_of_undying", 6, 6, C(1, 1)),
        Item("ominous_bottle", 4, 8, C(1, 2, 2, 4)), Item("name_tag", 2, 8, C(1, 1)), Item("saddle", 2, 8, C(1, 1)),
        Item("netherite_upgrade_smithing_template", 6, 4, C(1, 1), bmin=6),
        Item("sentry_armor_trim_smithing_template", 3, 3, C(1, 1)),
        Item("vex_armor_trim_smithing_template", 5, 3, C(1, 1)),
        Item("silence_armor_trim_smithing_template", 7, 2, C(1, 1)),
        Item("flow_armor_trim_smithing_template", 7, 2, C(1, 1)),
        Item("enchanted_golden_apple", 7, 2, C(1, 1), bmin=7), Item("experience_bottle", 3, 20, C(2, 6, 8, 24)),
    ],
}

KIT_BUNDLES = {
    "redstone_kit": {"name": "Redstone Kit", "cat": "redstone", "w": {1: 90, 3: 80, 5: 30, 7: 5, 11: 1}, "picks": (3, 6)},
    "seed_kit": {"name": "Seed & Sapling Kit", "cat": "seed", "w": {1: 90, 3: 70, 5: 20, 7: 2, 11: 1}, "picks": (3, 6)},
    "brewing_kit": {"name": "Brewing Kit", "cat": "brewing", "w": {1: 1, 3: 60, 5: 55, 8: 10, 11: 2}, "picks": (3, 6)},
    "explorer_kit": {"name": "Explorer Kit", "cat": "utility", "w": {1: 55, 4: 55, 6: 40, 9: 8, 11: 3}, "picks": (3, 5)},
    "decoration_kit": {"name": "Decoration Kit", "cat": "decoration_special", "w": {1: 10, 3: 45, 6: 40, 9: 10, 11: 4},
                       "picks": (3, 6)},
    "arrows_kit": {"name": "Special Arrows Kit", "cat": "_arrows", "w": {1: 1, 3: 12, 6: 14, 9: 10, 11: 8}, "picks": (2, 4)},
}
ARROWS_KIT_ITEMS = [
    Item("tipped_arrow", 3, 10, C(4, 12, 12, 32)), Item("spectral_arrow", 3, 8, C(4, 12, 12, 32)),
    Item("fire_charge", 3, 8, C(2, 6, 6, 16)), Item("wind_charge", 5, 8, C(2, 6, 6, 16)),
]
POTION_EFFECTS = {
    "minecraft:swiftness": 1, "minecraft:healing": 1, "minecraft:night_vision": 1, "minecraft:water_breathing": 2,
    "minecraft:fire_resistance": 3, "minecraft:strength": 3, "minecraft:leaping": 2, "minecraft:regeneration": 4,
    "minecraft:slow_falling": 5, "minecraft:turtle_master": 6, "minecraft:strong_healing": 6,
    "minecraft:strong_regeneration": 8, "minecraft:long_strength": 7, "minecraft:long_fire_resistance": 6,
}
TIPPED_EFFECTS = {
    "minecraft:poison": 3, "minecraft:weakness": 3, "minecraft:slowness": 3, "minecraft:harming": 5, "minecraft:healing": 5,
    "minecraft:strength": 6, "minecraft:regeneration": 7, "minecraft:swiftness": 4, "minecraft:leaping": 4,
}

POOL_N = {"equipment": 1, "ore": 2, "construction": 2, "decoration": 1, "utility": 1, "special": 1}
POOL_P = {"equipment": (0.40, 0.95), "ore": (0.18, 0.75), "construction": (0.12, 0.80), "decoration": (0.12, 0.65),
          "utility": (0.30, 0.70), "special": (0.45, 0.60)}
BOOK_SHARE = 0.25

# --- Mob drops (approved: player kills only) --------------------------------------------------------------------------
EMERALD_CHANCE = 0.167  # 1 kill in 6 (user, 2026-10-11)
INGOT_CHANCE = 0.20  # 1 kill in 5 (user, 2026-10-11)
INGOTS = [  # (min level, max level, [(item, weight, count lo, count hi)])
    (1, 15, [("copper_ingot", 55, 1, 2), ("gold_nugget", 45, 2, 5)]),
    (16, 35, [("copper_ingot", 20, 1, 3), ("gold_nugget", 25, 2, 6), ("iron_ingot", 30, 1, 2), ("gold_ingot", 25, 1, 1)]),
    (36, None, [("iron_ingot", 40, 1, 2), ("gold_ingot", 35, 1, 2), ("gold_nugget", 15, 3, 8), ("diamond", 10, 1, 1)]),
]
ELITES = {  # rank: (emerald lo, hi, gear chance, book chance, book rarity)
    1: (2, 4, 0.10, 0.0, None),
    2: (4, 8, 0.25, 0.10, None),
    3: (10, 20, 1.0, 0.05, "relic"),
}


def rolls(pool, band):
    return {"type": "minecraft:binomial", "n": POOL_N[pool], "p": round(interp(band, *POOL_P[pool]), 3)}


def count_fn(lo, hi):
    if lo == hi:
        return {"function": "minecraft:set_count", "count": lo}
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def item_entry(item_id, weight, functions=None):
    e = {"type": "minecraft:item", "name": item_id if ":" in item_id else f"minecraft:{item_id}", "weight": max(1, int(weight))}
    if functions:
        e["functions"] = functions
    return e


def level_cond(lo, hi):
    c = {"type": "vestiges:loot_level"}
    if lo is not None:
        c["min"] = lo
    if hi is not None:
        c["max"] = hi
    return c


def band_cond(band):
    lo, hi = BANDS[band]
    return level_cond(None if band == 1 else lo, None if band == NB else hi)


LOOT_ON = {"type": "vestiges:loot_enabled", "value": True}
LOOT_OFF = {"type": "vestiges:loot_enabled", "value": False}


def gear_fn(kind, min_rarity=None, rarity=None):
    f = {"function": "vestiges:loot_gear", "kind": kind}
    if min_rarity:
        f["min_rarity"] = min_rarity
    if rarity:
        f["rarity"] = rarity
    return f


# --- Band tables -------------------------------------------------------------------------------------------------------
def equipment_pool(band):
    return {"rolls": rolls("equipment", band), "entries": [
        item_entry("iron_sword", round(100 * (1 - BOOK_SHARE)), [gear_fn("gear")]),
        item_entry("book", round(100 * BOOK_SHARE), [gear_fn("book")]),
    ]}


def loose_pool(name, cats, band):
    entries = [item_entry(it.id, it.weight(band), [count_fn(*it.count(band))])
               for cat in cats for it in CATALOGUE[cat] if it.weight(band) >= 1]
    return {"rolls": rolls(name, band), "entries": entries or [item_entry("gravel", 1)]}


def potion_entries(potion_id, table, band, total):
    opts = [e for e, b in table.items() if band >= b]
    w = max(1, total // max(1, len(opts)))
    return [{"type": "minecraft:item", "name": f"minecraft:{potion_id}", "weight": w, "functions": [
        {"function": "minecraft:set_components", "components": {"minecraft:potion_contents": {"potion": eff}}}]}
        for eff in opts]


def utility_pool(band):
    entries = [item_entry(it.id, it.weight(band), [count_fn(*it.count(band))]) for it in CATALOGUE["utility"] if it.weight(band)]
    entries += [item_entry(it.id, max(1, int(it.weight(band) * 0.6)), [count_fn(*it.count(band))])
                for it in CATALOGUE["food"] if it.weight(band)]
    entries += potion_entries("potion", POTION_EFFECTS, band, 24)
    if band >= 4:
        entries += potion_entries("splash_potion", POTION_EFFECTS, band, 10)
    return {"rolls": rolls("utility", band), "entries": entries}


def special_pool(band):
    entries = []
    for bid, spec in KIT_BUNDLES.items():
        w = piecewise(band, spec["w"])
        if w >= 1:
            entries.append({"type": "minecraft:loot_table", "value": f"vestiges:loot/bundles/{bid}", "weight": max(1, int(w))})
    ramp = piecewise(band, {1: 0.15, 5: 0.9, 8: 2.6, 11: 3.2})
    for it in CATALOGUE["collectible"]:
        w = it.weight(band)
        if w:
            entries.append(item_entry(it.id, max(1, int(w * ramp)), [count_fn(*it.count(band))] if it.count(band) != (1, 1) else None))
    return {"rolls": rolls("special", band), "entries": entries}


def band_table(band):
    return {"type": "minecraft:chest", "pools": [
        equipment_pool(band), loose_pool("ore", ["ore", "gem"], band), loose_pool("construction", ["construction"], band),
        loose_pool("decoration", ["decoration"], band), utility_pool(band), special_pool(band)]}


def bundle_table(spec, band):
    pool = ARROWS_KIT_ITEMS if spec["cat"] == "_arrows" else CATALOGUE[spec["cat"]]
    avail = [it for it in pool if it.weight(band) >= 1]
    lo, hi = spec["picks"]
    chance = round(min(0.95, ((lo + hi) / 2) / max(1, len(avail))), 3)
    contents = []
    for it in avail:
        fns = []
        if it.count(band) != (1, 1):
            fns.append(count_fn(*it.count(band)))
        if it.id == "tipped_arrow":
            opts = [x for x, b in TIPPED_EFFECTS.items() if band >= b] or ["minecraft:poison"]
            fns.append({"function": "minecraft:set_components",
                        "components": {"minecraft:potion_contents": {"potion": opts[min(len(opts) - 1, band // 2)]}}})
        e = {"type": "minecraft:item", "name": f"minecraft:{it.id}",
             "conditions": [{"type": "minecraft:random_chance", "chance": chance}]}
        if fns:
            e["functions"] = fns
        contents.append(e)
    if not contents:
        contents = [{"type": "minecraft:item", "name": "minecraft:stick"}]
    return {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:item", "name": "minecraft:bundle", "functions": [
            {"function": "minecraft:set_name", "name": {"text": spec["name"], "italic": False}},
            {"function": "minecraft:set_contents", "component": "minecraft:bundle_contents", "entries": contents}]}]}]}


def router(prefix):
    return {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "conditions": [band_cond(b)], "entries": [{"type": "minecraft:loot_table", "value": f"{prefix}/band_{b}"}]}
        for b in BANDS]}


# --- Mob drops ---------------------------------------------------------------------------------------------------------
def monster_drops():
    kill = {"type": "minecraft:killed_by_player"}
    pools = [
        {"rolls": 1, "conditions": [kill, {"type": "vestiges:drop_chance", "chance": EMERALD_CHANCE}, level_cond(None, 24)],
         "entries": [item_entry("emerald", 1, [count_fn(1, 2)])]},
        {"rolls": 1, "conditions": [kill, {"type": "vestiges:drop_chance", "chance": EMERALD_CHANCE}, level_cond(25, None)],
         "entries": [item_entry("emerald", 1, [count_fn(1, 3)])]},
    ]
    for lo, hi, items in INGOTS:
        pools.append({"rolls": 1, "conditions": [kill, {"type": "vestiges:drop_chance", "chance": INGOT_CHANCE},
                                                level_cond(None if lo == 1 else lo, hi)],
                      "entries": [item_entry(i, w, [count_fn(a, b)]) for i, w, a, b in items]})
    for rank, (elo, ehi, gear, book, book_rarity) in ELITES.items():
        rank_c = {"type": "vestiges:mob_rank", "rank": rank}
        pools.append({"rolls": 1, "conditions": [kill, rank_c], "entries": [item_entry("emerald", 1, [count_fn(elo, ehi)])]})
        gear_conds = [kill, rank_c] + ([{"type": "minecraft:random_chance", "chance": gear}] if gear < 1 else [])
        pools.append({"rolls": 1, "conditions": gear_conds,
                      "entries": [item_entry("iron_sword", 1, [gear_fn("gear", min_rarity="uncommon")])]})
        if book > 0:
            pools.append({"rolls": 1, "conditions": [kill, rank_c, {"type": "minecraft:random_chance", "chance": book}],
                          "entries": [item_entry("book", 1, [gear_fn("book", min_rarity="uncommon", rarity=book_rarity)])]})
    return {"type": "minecraft:entity", "pools": pools}


# --- Chest overrides ---------------------------------------------------------------------------------------------------
SKIP_CHESTS = {"jungle_temple_dispenser", "spawn_bonus_chest"}  # dispensers and the starter chest stay vanilla
GEAR_SUFFIX = ("_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_helmet", "_chestplate", "_leggings", "_boots", "_spear")
GEAR_ITEMS = {"bow", "crossbow", "shield", "trident", "mace", "elytra", "turtle_helmet"}
ENCHANT_FNS = {"minecraft:enchant_randomly", "minecraft:enchant_with_levels", "minecraft:set_enchantments"}


def mods_of(entry):
    m = entry.get("modifier", entry.get("functions", []))
    return m if isinstance(m, list) else [m]


def is_gear_entry(entry):
    if entry.get("type") != "minecraft:item":
        return False
    path = entry.get("name", "").split(":")[-1]
    if path.endswith(GEAR_SUFFIX) or path in GEAR_ITEMS:
        return True
    return any((m.get("type") or m.get("function")) in ENCHANT_FNS for m in mods_of(entry))


def with_condition(pool, cond):
    pool = dict(pool)
    old = pool.pop("condition", None)
    olds = pool.pop("conditions", None)
    terms = [cond] + ([old] if old else []) + (olds or [])
    pool["condition"] = terms[0] if len(terms) == 1 else {"type": "minecraft:all_of", "terms": terms}
    return pool


def themed_pools(pools):
    out = []
    for p in pools:
        if any("scaled" in str(e.get("value", "")) for e in p.get("entries", [])):
            continue
        entries = [e for e in p.get("entries", []) if not is_gear_entry(e)]
        if not any(e.get("type") != "minecraft:empty" for e in entries):
            continue
        q = dict(p)
        q["entries"] = entries
        out.append(q)
    return out


def chest_overrides():
    z = zipfile.ZipFile(MC_JAR)
    prefix = "data/minecraft/loot_table/chests/"
    written = []
    for name in sorted(n for n in z.namelist() if n.startswith(prefix) and n.endswith(".json")):
        rel = name[len(prefix):]
        stem = rel[:-5]
        if stem in SKIP_CHESTS or "/reward" in stem:
            continue
        vanilla = json.load(z.open(name))
        ours = RPG_CRAFT_CHESTS / rel
        theme_src = json.load(open(ours, encoding="utf-8"))["pools"] if ours.exists() else vanilla["pools"]
        out = {k: v for k, v in vanilla.items() if k != "pools"}
        out["pools"] = ([with_condition(p, LOOT_OFF) for p in vanilla["pools"]]
                        + [with_condition(p, LOOT_ON) for p in themed_pools(theme_src)]
                        + [{"rolls": 1, "condition": LOOT_ON,
                            "entries": [{"type": "minecraft:loot_table", "value": "vestiges:loot/scaled"}]}])
        written.append((CHESTS / rel, out))
    return written


# --- loot_rules for Java -----------------------------------------------------------------------------------------------
def loot_rules():
    return {
        "bands": [[lo, hi] for lo, hi in BANDS.values()],
        "rarity": [{"max_level": m, "odds": odds} for m, odds in RARITY_ROWS],
        "ladder": [{"start": s, "peak": p, "weight": w, "items": material_items(mat)} for mat, (s, p, w) in LADDER.items()]
        + [{"start": s, "peak": p, "weight": w, "items": {f"minecraft:{iid}": 1.0}} for iid, s, p, w in STANDALONE],
    }


# --- 26.3 schema + output ----------------------------------------------------------------------------------------------
def fix_schema(obj):
    """26.3 loot JSON: 'conditions' -> 'condition' (all_of when several), 'functions' -> 'modifier', 'function' -> 'type'."""
    if isinstance(obj, dict):
        if "conditions" in obj:
            conds = obj.pop("conditions")
            if len(conds) == 1:
                obj["condition"] = conds[0]
            elif conds:
                obj["condition"] = {"type": "minecraft:all_of", "terms": conds}
        if "functions" in obj:
            obj["modifier"] = obj.pop("functions")
        if "function" in obj and "type" not in obj and isinstance(obj["function"], str):
            obj["type"] = obj.pop("function")
        for v in obj.values():
            fix_schema(v)
    elif isinstance(obj, list):
        for v in obj:
            fix_schema(v)
    return obj


def write_json(path, data):
    fix_schema(data)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


# --- Balance report ----------------------------------------------------------------------------------------------------
def mean_rolls(r):
    return r["n"] * r["p"] if isinstance(r, dict) else r


def mean_count(entry):
    for m in mods_of(entry):
        if (m.get("type") or m.get("function")) == "minecraft:set_count":
            c = m["count"]
            return (c["min"] + c["max"]) / 2 if isinstance(c, dict) else c
    return 1


def expected(table, item):
    total = 0.0
    for p in table["pools"]:
        ents = p["entries"]
        wsum = sum(e.get("weight", 1) for e in ents)
        for e in ents:
            if e.get("name") == item:
                total += mean_rolls(p["rolls"]) * e.get("weight", 1) / wsum * mean_count(e)
    return total


def report():
    print("\nBalance per chest (the band roll only; the structure's own items come on top):")
    print("band  levels   gear  books  emeralds  diamonds  netherite  golden_apple  totem   Rare+ per chest")
    for b, (lo, hi) in BANDS.items():
        t = fix_schema(band_table(b))
        eq = mean_rolls(t["pools"][0]["rolls"])
        em = expected(t, "minecraft:emerald") + 9 * expected(t, "minecraft:emerald_block")
        di = expected(t, "minecraft:diamond") + 9 * expected(t, "minecraft:diamond_block")
        row = next(o for m, o in RARITY_ROWS if (lo + hi) / 2 <= m)
        rare_plus = sum(row[3:]) / 100
        print(f"{b:>4}  {lo:>3}-{hi:<4} {eq * (1 - BOOK_SHARE):5.2f}  {eq * BOOK_SHARE:5.2f}  {em:8.2f}  {di:8.2f}  "
              f"{expected(t, 'minecraft:netherite_scrap'):9.2f}  {expected(t, 'minecraft:golden_apple'):12.2f}  "
              f"{expected(t, 'minecraft:totem_of_undying'):5.3f}   {eq * rare_plus:5.2f}")
    print("\nMob drops per kill (player kills):")
    for lvl in (5, 20, 40):
        em = EMERALD_CHANCE * (1.5 if lvl < 25 else 2.0)
        print(f"  level {lvl:>2}: {em:.2f} emeralds, {INGOT_CHANCE:.2f} ingot rolls")
    print("  elites add: Champion 3 emeralds + 10% gear · Elite 6 + 25% gear + 10% book · Warlord 15 + gear + 5% VI book")


def main():
    if LOOT.is_dir():
        shutil.rmtree(LOOT)
    if CHESTS.is_dir():
        shutil.rmtree(CHESTS)
    n = 0
    for b in BANDS:
        write_json(LOOT / "bands" / f"band_{b}.json", band_table(b))
        n += 1
    write_json(LOOT / "scaled.json", router("vestiges:loot/bands"))
    n += 1
    for bid, spec in KIT_BUNDLES.items():
        for b in BANDS:
            write_json(LOOT / "bundles" / bid / f"band_{b}.json", bundle_table(spec, b))
            n += 1
        write_json(LOOT / "bundles" / f"{bid}.json", router(f"vestiges:loot/bundles/{bid}"))
        n += 1
    write_json(DATA / "vestiges" / "loot_table" / "entities" / "monster_drops.json", monster_drops())
    n += 1
    for path, data in chest_overrides():
        write_json(path, data)
        n += 1
    write_json(DATA / "vestiges" / "vestiges" / "loot_rules" / "default.json", loot_rules())
    n += 1
    print(f"Wrote {n} files.")
    if "--quiet" not in sys.argv:
        report()


if __name__ == "__main__":
    main()
