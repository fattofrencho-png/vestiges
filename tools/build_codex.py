#!/usr/bin/env python3
"""build_codex.py - Vestiges Codex entries (M4) from the approved flavour text.

Reads docs/lore/codex-flavour.md (the 414 approved entries and their two lines) and writes one JSON file per entry to
src/main/resources/data/vestiges/vestiges/codex/<section>/<slug>.json, with the game ids each entry covers, its class
(blocks), its icon and how it is found. Every id is checked against the 26.3 game files, so a typo fails here.

Run after changing the flavour file or the tables below:   python tools/build_codex.py
"""

import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FLAVOUR = ROOT / "docs" / "lore" / "codex-flavour.md"
OUT = ROOT / "src" / "main" / "resources" / "data" / "vestiges" / "vestiges" / "codex"
CACHE = ROOT / ".gradle" / "loom-cache" / "minecraftMaven" / "net" / "minecraft"
COMMON_JAR = next(CACHE.glob("minecraft-common-*/26.3/*.jar"))
CLIENT_JAR = next(CACHE.glob("minecraft-clientOnly-*/26.3/*.jar"))

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
          "brown", "green", "red", "black"]
CORALS = ["tube", "brain", "bubble", "fire", "horn"]

# --- Natural blocks: class (Batch 4 classes; 26.3's newest blocks classed here) and members when not just the name ------
C, U, R, VR, UQ = "common", "uncommon", "rare", "very_rare", "unique"
BLOCKS = {
    "Stone": (C, None), "Cobblestone": (C, None), "Mossy Cobblestone": (U, None), "Granite": (C, None), "Diorite": (C, None),
    "Andesite": (C, None), "Deepslate": (C, None), "Cobbled Deepslate": (C, None), "Tuff": (C, None), "Calcite": (C, None),
    "Smooth Basalt": (C, None), "Dripstone Block": (U, None), "Pointed Dripstone": (U, None), "Obsidian": (C, None),
    "Crying Obsidian": (R, None), "Magma Block": (U, None), "Sandstone": (C, None), "Red Sandstone": (U, None), "Sand": (C, None),
    "Red Sand": (U, None), "Gravel": (C, None), "Clay": (C, None), "Mud": (U, None), "Dirt": (C, None), "Coarse Dirt": (C, None),
    "Rooted Dirt": (C, None), "Podzol": (U, None), "Mycelium": (U, None), "Grass Block": (C, None),
    "Terracotta": (U, ["terracotta"] + [f"{c}_terracotta" for c in COLORS]),
    "Suspicious Sand": (UQ, None), "Suspicious Gravel": (UQ, None), "Snow": (C, None), "Snow Block": (C, None),
    "Powder Snow": (U, None), "Ice": (C, None), "Packed Ice": (U, None), "Blue Ice": (R, None),
    "Sulfur": (U, None), "Potent Sulfur": (R, None), "Cinnabar": (U, None), "Sulfur Spike": (U, None),
    "Coal Ore": (C, ["coal_ore", "deepslate_coal_ore"]), "Copper Ore": (C, ["copper_ore", "deepslate_copper_ore"]),
    "Iron Ore": (C, ["iron_ore", "deepslate_iron_ore"]), "Gold Ore": (U, ["gold_ore", "deepslate_gold_ore"]),
    "Redstone Ore": (U, ["redstone_ore", "deepslate_redstone_ore"]), "Lapis Ore": (U, ["lapis_ore", "deepslate_lapis_ore"]),
    "Diamond Ore": (R, ["diamond_ore", "deepslate_diamond_ore"]), "Emerald Ore": (R, ["emerald_ore", "deepslate_emerald_ore"]),
    "Nether Quartz Ore": (U, None), "Nether Gold Ore": (U, None), "Ancient Debris": (VR, None),
    "Amethyst Block": (R, None), "Budding Amethyst": (UQ, None),
    "Amethyst Cluster": (R, ["amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud"]),
    "Glow Lichen": (C, None), "Moss Block": (U, None), "Moss Carpet": (U, None), "Hanging Roots": (U, None),
    "Cave Vines": (U, ["cave_vines", "cave_vines_plant"]), "Big Dripleaf": (U, ["big_dripleaf", "big_dripleaf_stem"]),
    "Small Dripleaf": (U, None), "Spore Blossom": (VR, None), "Azalea": (U, ["azalea", "flowering_azalea"]), "Cobweb": (U, None),
    "Sculk": (R, None), "Sculk Vein": (R, None), "Sculk Sensor": (R, None), "Sculk Catalyst": (VR, None),
    "Sculk Shrieker": (R, None), "Reinforced Deepslate": (UQ, None),
    "Oak": (C, ["oak_log", "oak_wood"]), "Spruce": (C, ["spruce_log", "spruce_wood"]), "Birch": (C, ["birch_log", "birch_wood"]),
    "Jungle": (U, ["jungle_log", "jungle_wood"]), "Acacia": (U, ["acacia_log", "acacia_wood"]),
    "Dark Oak": (U, ["dark_oak_log", "dark_oak_wood"]),
    "Mangrove": (U, ["mangrove_log", "mangrove_wood", "mangrove_roots", "muddy_mangrove_roots"]),
    "Cherry": (U, ["cherry_log", "cherry_wood"]), "Pale Oak": (U, ["pale_oak_log", "pale_oak_wood"]),
    "Poplar": (U, ["poplar_log"]), "Azalea Leaves": (U, ["azalea_leaves", "flowering_azalea_leaves"]), "Bamboo": (U, None),
    "Short Grass": (C, None), "Tall Grass": (C, None), "Fern": (C, None), "Large Fern": (C, None), "Bush": (C, None),
    "Firefly Bush": (U, None), "Leaf Litter": (C, None), "Wildflowers": (C, None), "Short Dry Grass": (C, None),
    "Tall Dry Grass": (C, None), "Dead Bush": (C, None), "Dandelion": (C, None), "Golden Dandelion": (R, None), "Poppy": (C, None),
    "Blue Orchid": (U, None), "Allium": (C, None), "Azure Bluet": (C, None),
    "Tulip": (C, ["red_tulip", "orange_tulip", "white_tulip", "pink_tulip"]), "Oxeye Daisy": (C, None), "Cornflower": (C, None),
    "Lily of the Valley": (C, None), "Wither Rose": (R, None), "Torchflower": (R, None), "Pitcher Plant": (R, None),
    "Sunflower": (C, None), "Lilac": (C, None), "Rose Bush": (C, None), "Peony": (C, None), "Pink Petals": (U, None),
    "Eyeblossom": (R, ["open_eyeblossom", "closed_eyeblossom"]), "Cactus": (U, None), "Cactus Flower": (U, None),
    "Sugar Cane": (C, None), "Lily Pad": (C, None), "Vines": (C, ["vine"]), "Sweet Berry Bush": (U, None), "Cocoa": (U, None),
    "Melon": (U, None), "Pumpkin": (C, None), "Brown Mushroom": (C, None), "Red Mushroom": (C, None),
    "Mushroom Block": (U, ["brown_mushroom_block", "red_mushroom_block", "mushroom_stem"]), "Shelf Mushroom": (U, None),
    "Red Shrub": (U, None), "Pale Moss": (R, ["pale_moss_block", "pale_moss_carpet", "pale_hanging_moss"]),
    "Creaking Heart": (R, None), "Resin Clump": (U, None), "Kelp": (C, ["kelp", "kelp_plant"]),
    "Seagrass": (C, ["seagrass", "tall_seagrass"]), "Sea Pickle": (R, None),
    **{f"{c.title()} Coral": (R, [f"{c}_coral", f"{c}_coral_block", f"{c}_coral_fan", f"{c}_coral_wall_fan"]) for c in CORALS},
    "Dead Coral": (U, [f"dead_{c}_coral{s}" for c in CORALS for s in ["", "_block", "_fan", "_wall_fan"]]),
    "Sponge": (VR, None), "Wet Sponge": (VR, None),
    "Prismarine": (R, ["prismarine", "prismarine_bricks", "dark_prismarine", "prismarine_stairs", "prismarine_slab", "prismarine_wall",
                       "prismarine_brick_stairs", "prismarine_brick_slab", "dark_prismarine_stairs", "dark_prismarine_slab"]),
    "Sea Lantern": (R, None), "Turtle Egg": (R, None), "Frogspawn": (U, None), "Netherrack": (C, None), "Soul Sand": (C, None),
    "Soul Soil": (C, None), "Basalt": (C, None), "Blackstone": (C, None), "Gilded Blackstone": (VR, None), "Glowstone": (U, None),
    "Crimson Nylium": (U, None), "Warped Nylium": (U, None), "Crimson Stem": (U, ["crimson_stem", "crimson_hyphae"]),
    "Warped Stem": (U, ["warped_stem", "warped_hyphae"]), "Nether Wart Block": (U, None), "Warped Wart Block": (U, None),
    "Shroomlight": (R, None), "Crimson Fungus": (U, None), "Warped Fungus": (U, None),
    "Nether Roots": (C, ["crimson_roots", "warped_roots"]), "Nether Sprouts": (C, None),
    "Weeping Vines": (U, ["weeping_vines", "weeping_vines_plant"]), "Twisting Vines": (U, ["twisting_vines", "twisting_vines_plant"]),
    "Nether Wart": (U, None), "Dried Ghast": (R, None), "Bone Block": (U, None), "End Stone": (R, None),
    "Purpur": (R, ["purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab"]), "Chorus Plant": (R, None),
    "Chorus Flower": (VR, None), "Dragon Egg": (UQ, None), "Bee Nest": (U, None),
    "Froglight": (R, ["ochre_froglight", "verdant_froglight", "pearlescent_froglight"]), "Sniffer Egg": (VR, None),
    "Spawner": (UQ, None), "Trial Spawner": (UQ, None), "Vault": (UQ, None),
}
BLOCK_ICONS = {"cave_vines": "glow_berries", "cocoa": "cocoa_beans", "sweet_berry_bush": "sweet_berries", "powder_snow": "powder_snow_bucket",
               "kelp": "kelp", "nether_wart": "nether_wart", "frogspawn": "frog_spawn_egg", "tube_coral": "tube_coral",
               "pitcher_plant": "pitcher_plant", "snow": "snow", "big_dripleaf": "big_dripleaf"}

# --- Items ---------------------------------------------------------------------------------------------------------------
ITEMS = {
    "Anvil": ["anvil", "chipped_anvil", "damaged_anvil"], "Bundle": ["bundle"] + [f"{c}_bundle" for c in COLORS],
    "Dragon's Breath": ["dragon_breath"], "Eye of Ender": ["ender_eye"], "Harness": [f"{c}_harness" for c in COLORS],
    "Map": ["map", "filled_map"], "Music Disc": "music_disc_", "Pottery Sherd": "_pottery_sherd",
    "Smithing Template": "_smithing_template", "Spear": "_spear",
}
RARE_ITEMS = {"Beacon", "Conduit", "Elytra", "Enchanted Golden Apple", "Heart of the Sea", "Heavy Core", "Mace", "Nether Star",
              "Totem of Undying", "Trident", "Recovery Compass", "Music Disc", "Smithing Template", "Ominous Trial Key"}

# --- Mobs ----------------------------------------------------------------------------------------------------------------
BOSSES = {"Ender Dragon", "Wither", "Warden", "Elder Guardian"}
MOB_ICONS = {"ender_dragon": "dragon_head", "wither": "wither_skeleton_skull", "player": "player_head"}

# --- Biomes (icon per biome) ---------------------------------------------------------------------------------------------
BIOME_IDS = {"The End": "the_end"}
BIOME_ICONS = {
    "badlands": "red_sand", "eroded_badlands": "red_sand", "wooded_badlands": "terracotta", "bamboo_jungle": "bamboo",
    "basalt_deltas": "basalt", "beach": "sand", "snowy_beach": "snow_block", "birch_forest": "birch_sapling",
    "old_growth_birch_forest": "birch_log", "cherry_grove": "cherry_sapling", "crimson_forest": "crimson_fungus",
    "warped_forest": "warped_fungus", "dappled_forest": "poplar_sapling", "dark_forest": "dark_oak_sapling", "deep_dark": "sculk",
    "desert": "cactus", "dripstone_caves": "pointed_dripstone", "flower_forest": "allium", "forest": "oak_sapling",
    "frozen_peaks": "packed_ice", "jagged_peaks": "snow_block", "stony_peaks": "stone", "frozen_river": "ice", "grove": "snowball",
    "ice_spikes": "packed_ice", "jungle": "jungle_sapling", "sparse_jungle": "jungle_log", "lush_caves": "moss_block",
    "mangrove_swamp": "mangrove_propagule", "meadow": "cornflower", "mushroom_fields": "red_mushroom_block",
    "nether_wastes": "netherrack", "pale_garden": "pale_oak_sapling", "plains": "short_grass", "sunflower_plains": "sunflower",
    "river": "water_bucket", "savanna": "acacia_sapling", "savanna_plateau": "acacia_log", "windswept_savanna": "acacia_leaves",
    "snowy_plains": "snow_block", "snowy_slopes": "powder_snow_bucket", "snowy_taiga": "spruce_leaves",
    "soul_sand_valley": "soul_sand", "stony_shore": "gravel", "sulfur_caves": "sulfur", "swamp": "lily_pad", "taiga": "spruce_sapling",
    "old_growth_pine_taiga": "podzol", "old_growth_spruce_taiga": "spruce_log", "the_end": "end_stone",
    "windswept_forest": "spruce_sapling", "windswept_gravelly_hills": "gravel", "windswept_hills": "emerald_ore",
    "ocean": "kelp", "deep_ocean": "kelp", "cold_ocean": "cod", "deep_cold_ocean": "cod", "frozen_ocean": "ice",
    "deep_frozen_ocean": "blue_ice", "lukewarm_ocean": "seagrass", "deep_lukewarm_ocean": "seagrass", "warm_ocean": "brain_coral",
}

# --- Structures: game structure ids, or how a feature is spotted, or the milestone that brings it -------------------------
STRUCTURES = {
    "Abandoned Camp": ("campfire", {"prefix": "abandoned_camp_"}),
    "Amethyst Geode": ("amethyst_cluster", {"detect": "geode"}),
    "Ancient City": ("sculk_shrieker", ["ancient_city"]),
    "Bastion Remnant": ("gilded_blackstone", ["bastion_remnant"]),
    "Buried Treasure": ("heart_of_the_sea", ["buried_treasure"]),
    "Desert Pyramid": ("chiseled_sandstone", ["desert_pyramid"]),
    "Desert Well": ("sandstone_slab", {"detect": "desert_well"}),
    "Dungeon Portal (the Gate)": ("chiseled_tuff", {"later": "dungeons (M8)"}),
    "Fossil": ("bone_block", {"detect": "fossil"}),
    "Igloo": ("snow_block", ["igloo"]),
    "Jungle Temple": ("mossy_cobblestone", ["jungle_pyramid"]),
    "Mineshaft": ("rail", ["mineshaft", "mineshaft_mesa"]),
    "Monster Room": ("spawner", {"detect": "monster_room"}),
    "Nether Fossil": ("bone_block", ["nether_fossil"]),
    "Nether Fortress": ("nether_bricks", ["fortress"]),
    "Ocean Monument": ("prismarine_bricks", ["monument"]),
    "Ocean Ruins": ("suspicious_gravel", ["ocean_ruin_cold", "ocean_ruin_warm"]),
    "Pillager Outpost": ("crossbow", ["pillager_outpost"]),
    "Ruined Portal": ("crying_obsidian", {"prefix": "ruined_portal"}),
    "Ruined Shrine": ("mossy_stone_bricks", {"later": "world events (M9)"}),
    "Shipwreck": ("oak_boat", ["shipwreck", "shipwreck_beached"]),
    "Stronghold": ("end_portal_frame", ["stronghold"]),
    "Swamp Hut": ("cauldron", ["swamp_hut"]),
    "The Vestige": ("end_stone_bricks", {"later": "the End (M9)"}),
    "Trail Ruins": ("brush", ["trail_ruins"]),
    "Trial Chambers": ("trial_key", ["trial_chambers"]),
    "Village (Desert)": ("bell", ["village_desert"]),
    "Village (Plains)": ("bell", ["village_plains"]),
    "Village (Savanna)": ("bell", ["village_savanna"]),
    "Village (Snowy)": ("bell", ["village_snowy"]),
    "Village (Taiga)": ("bell", ["village_taiga"]),
    "Woodland Mansion": ("dark_oak_planks", ["mansion"]),
}


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower().replace("'s", "")).strip("_")


def parse_flavour():
    sections = {}
    current = None
    for line in FLAVOUR.read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            current = line[3:].split(" (")[0]
            sections[current] = []
        elif current and line.startswith("| ") and not line.startswith("| Entry") and not line.startswith("|---"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) >= 3:
                sections[current].append((cells[0], cells[1], cells[2]))
    return sections


def game_ids():
    zc, zl = zipfile.ZipFile(COMMON_JAR), zipfile.ZipFile(CLIENT_JAR)
    names = zc.namelist() + zl.namelist()
    lang_zip = zl if "assets/minecraft/lang/en_us.json" in zl.namelist() else zc
    lang = json.load(lang_zip.open("assets/minecraft/lang/en_us.json"))
    blocks = {k.split(".")[2] for k in lang if k.startswith("block.minecraft.") and k.count(".") == 2}
    items = {n.split("/")[-1][:-5] for n in names if n.startswith("assets/minecraft/items/") and n.endswith(".json")}
    entities = {k.split(".")[2] for k in lang if k.startswith("entity.minecraft.") and k.count(".") == 2}
    biomes = {n.split("/")[-1][:-5] for n in names if n.startswith("data/minecraft/worldgen/biome/") and n.endswith(".json")}
    structures = {n.split("/")[-1][:-5] for n in names if n.startswith("data/minecraft/worldgen/structure/") and n.endswith(".json")}
    return blocks, items, entities, biomes, structures


def main():
    blocks, items, entities, biomes, structures = game_ids()
    flavour = parse_flavour()
    errors = []
    out = []

    def need(kind, ids, pool, name):
        missing = [i for i in ids if i not in pool]
        if missing:
            errors.append(f"{kind} '{name}': unknown ids {missing}")

    def icon_for(candidates, name):
        for c in candidates:
            if c in items:
                return f"minecraft:{c}"
        errors.append(f"no icon for '{name}' (tried {candidates})")
        return "minecraft:barrier"

    for name, l1, l2 in flavour["Natural blocks"]:
        if name not in BLOCKS:
            errors.append(f"block '{name}' has no class")
            continue
        cls, members = BLOCKS[name]
        base = members or [slug(name)]
        need("block", base, blocks, name)
        shaped = [f"{m}_{s}" for m in base for s in ("stairs", "slab", "wall") if f"{m}_{s}" in blocks and f"{m}_{s}" not in base]
        ids = base + (shaped if members is None else [])
        out.append(("blocks", name, {"class": cls, "members": [f"minecraft:{i}" for i in ids],
                                    "icon": icon_for([BLOCK_ICONS.get(base[0], base[0])] + base, name), "line1": l1, "line2": l2}))
    for name, l1, l2 in flavour["Mobs"]:
        eid = slug(name)
        need("mob", [eid], entities, name)
        out.append(("mobs", name, {"members": [f"minecraft:{eid}"], "boss": name in BOSSES,
                                  "icon": icon_for([MOB_ICONS.get(eid, f"{eid}_spawn_egg"), "spawner"], name), "line1": l1, "line2": l2}))
    for name, l1, l2 in flavour["Biomes"]:
        bid = BIOME_IDS.get(name, slug(name))
        need("biome", [bid], biomes, name)
        out.append(("biomes", name, {"members": [f"minecraft:{bid}"], "icon": icon_for([BIOME_ICONS.get(bid, "grass_block")], name),
                                    "line1": l1, "line2": l2}))
    for name, l1, l2 in flavour["Structures"]:
        if name not in STRUCTURES:
            errors.append(f"structure '{name}' is not mapped")
            continue
        icon, how = STRUCTURES[name]
        entry = {"icon": icon_for([icon], name), "line1": l1, "line2": l2, "members": []}
        if isinstance(how, list):
            need("structure", how, structures, name)
            entry["members"] = [f"minecraft:{s}" for s in how]
        elif "prefix" in how:
            entry["members"] = [f"minecraft:{s}" for s in sorted(structures) if s.startswith(how["prefix"])]
            if not entry["members"]:
                errors.append(f"structure '{name}': nothing starts with {how['prefix']}")
        elif "detect" in how:
            entry["detect"] = how["detect"]
        else:
            entry["later"] = how["later"]
        out.append(("structures", name, entry))
    for name, l1, l2 in flavour["Items"]:
        spec = ITEMS.get(name, [slug(name)])
        if isinstance(spec, str):
            ids = sorted(i for i in items if (i.startswith(spec) if spec.endswith("_") else i.endswith(spec)))
        else:
            ids = spec
        need("item", ids, items, name)
        out.append(("items", name, {"members": [f"minecraft:{i}" for i in ids], "rare": name in RARE_ITEMS,
                                   "icon": icon_for(ids, name), "line1": l1, "line2": l2}))

    if errors:
        print("\n".join(errors))
        sys.exit(1)
    if OUT.is_dir():
        shutil.rmtree(OUT)
    seen = set()
    for section, name, data in out:
        key = f"{section}/{slug(name)}"
        if key in seen:
            print(f"duplicate entry {key}")
            sys.exit(1)
        seen.add(key)
        data = {"section": section, "name": name, **data}
        path = OUT / section / f"{slug(name)}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    counts = {}
    for section, _, _ in out:
        counts[section] = counts.get(section, 0) + 1
    print(f"Wrote {len(out)} Codex entries: {counts}")


if __name__ == "__main__":
    main()
