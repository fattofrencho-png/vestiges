import math
from typing import Dict, Tuple, List

# Standard vanilla Minecraft blocks with representative sRGB values
MINECRAFT_BLOCKS: Dict[str, Tuple[int, int, int]] = {
    "minecraft:stone": (125, 125, 125),
    "minecraft:cobblestone": (130, 130, 130),
    "minecraft:stone_bricks": (120, 120, 120),
    "minecraft:mossy_stone_bricks": (115, 130, 115),
    "minecraft:cracked_stone_bricks": (110, 110, 110),
    "minecraft:granite": (150, 100, 85),
    "minecraft:diorite": (180, 180, 185),
    "minecraft:andesite": (135, 135, 138),
    "minecraft:deepslate": (70, 70, 75),
    "minecraft:deepslate_bricks": (60, 60, 65),
    "minecraft:deepslate_tiles": (50, 50, 55),
    "minecraft:polished_deepslate": (75, 75, 80),
    "minecraft:blackstone": (40, 35, 42),
    "minecraft:polished_blackstone_bricks": (45, 40, 48),
    "minecraft:dirt": (134, 96, 67),
    "minecraft:coarse_dirt": (119, 85, 59),
    "minecraft:grass_block": (92, 140, 50),
    "minecraft:oak_planks": (162, 130, 78),
    "minecraft:spruce_planks": (104, 78, 48),
    "minecraft:birch_planks": (196, 178, 124),
    "minecraft:jungle_planks": (160, 115, 80),
    "minecraft:acacia_planks": (168, 90, 50),
    "minecraft:dark_oak_planks": (66, 43, 20),
    "minecraft:mangrove_planks": (117, 50, 48),
    "minecraft:cherry_planks": (228, 178, 175),
    "minecraft:bamboo_planks": (195, 172, 80),
    "minecraft:crimson_planks": (101, 48, 70),
    "minecraft:warped_planks": (43, 104, 99),
    "minecraft:oak_log": (108, 86, 52),
    "minecraft:spruce_log": (58, 37, 16),
    "minecraft:dark_oak_log": (60, 47, 26),
    "minecraft:oak_leaves": (60, 120, 40),
    "minecraft:spruce_leaves": (45, 85, 45),
    "minecraft:glass": (210, 230, 235),
    "minecraft:white_wool": (235, 235, 235),
    "minecraft:orange_wool": (240, 118, 19),
    "minecraft:magenta_wool": (189, 68, 179),
    "minecraft:light_blue_wool": (58, 175, 217),
    "minecraft:yellow_wool": (248, 197, 39),
    "minecraft:lime_wool": (112, 185, 25),
    "minecraft:pink_wool": (237, 141, 172),
    "minecraft:gray_wool": (62, 68, 71),
    "minecraft:light_gray_wool": (142, 142, 134),
    "minecraft:cyan_wool": (21, 137, 145),
    "minecraft:purple_wool": (121, 42, 172),
    "minecraft:blue_wool": (53, 57, 157),
    "minecraft:brown_wool": (114, 71, 40),
    "minecraft:green_wool": (84, 109, 27),
    "minecraft:red_wool": (160, 39, 34),
    "minecraft:black_wool": (20, 21, 25),
    "minecraft:terracotta": (152, 94, 67),
    "minecraft:white_terracotta": (209, 178, 161),
    "minecraft:orange_terracotta": (161, 83, 37),
    "minecraft:yellow_terracotta": (186, 133, 35),
    "minecraft:brown_terracotta": (77, 51, 35),
    "minecraft:red_terracotta": (143, 61, 46),
    "minecraft:black_terracotta": (37, 22, 16),
    "minecraft:sand": (219, 207, 153),
    "minecraft:sandstone": (216, 203, 155),
    "minecraft:red_sandstone": (181, 97, 31),
    "minecraft:bricks": (150, 97, 83),
    "minecraft:mud_bricks": (137, 104, 78),
    "minecraft:prismarine": (99, 156, 151),
    "minecraft:prismarine_bricks": (99, 171, 158),
    "minecraft:dark_prismarine": (51, 75, 65),
    "minecraft:nether_bricks": (44, 21, 26),
    "minecraft:red_nether_bricks": (69, 7, 9),
    "minecraft:end_stone_bricks": (220, 223, 158),
    "minecraft:purpur_block": (169, 125, 169),
    "minecraft:quartz_block": (235, 229, 222),
    "minecraft:glowstone": (240, 190, 100),
    "minecraft:sea_lantern": (175, 215, 205),
    "minecraft:iron_block": (220, 220, 220),
    "minecraft:gold_block": (245, 205, 50),
    "minecraft:diamond_block": (95, 235, 225),
    "minecraft:emerald_block": (40, 205, 85),
    "minecraft:netherite_block": (65, 60, 65),
    "minecraft:copper_block": (192, 107, 80),
    "minecraft:oxidized_copper": (82, 162, 132),
    "minecraft:lantern": (240, 180, 80),
    "minecraft:water": (64, 96, 215),
}

def rgb_to_hex(rgb: Tuple[int, int, int]) -> str:
    return f"#{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}"

def rgb_to_lab(rgb: Tuple[int, int, int]) -> Tuple[float, float, float]:
    """Convert sRGB (0-255) to CIE L*a*b* for perceptually uniform color distance."""
    # Convert sRGB to linear RGB
    r, g, b = [x / 255.0 for x in rgb]
    r = ((r + 0.055) / 1.055) ** 2.4 if r > 0.04045 else r / 12.92
    g = ((g + 0.055) / 1.055) ** 2.4 if g > 0.04045 else g / 12.92
    b = ((b + 0.055) / 1.055) ** 2.4 if b > 0.04045 else b / 12.92

    # Linear RGB to XYZ (D65 illuminant)
    x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
    y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000
    z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883

    # XYZ to Lab
    fx = x ** (1/3) if x > 0.008856 else (7.787 * x) + (16 / 116)
    fy = y ** (1/3) if y > 0.008856 else (7.787 * y) + (16 / 116)
    fz = z ** (1/3) if z > 0.008856 else (7.787 * z) + (16 / 116)

    L = (116 * fy) - 16
    a = 500 * (fx - fy)
    b_lab = 200 * (fy - fz)
    return (L, a, b_lab)

# Precalculate Lab values for block palette
BLOCK_LAB_CACHE: Dict[str, Tuple[float, float, float]] = {
    block: rgb_to_lab(rgb) for block, rgb in MINECRAFT_BLOCKS.items()
}

def delta_e(lab1: Tuple[float, float, float], lab2: Tuple[float, float, float]) -> float:
    """Euclidean distance in CIE L*a*b* space."""
    return math.sqrt((lab1[0] - lab2[0])**2 + (lab1[1] - lab2[1])**2 + (lab1[2] - lab2[2])**2)

def match_closest_block(rgb: Tuple[int, int, int], allowed_blocks: List[str] = None) -> Tuple[str, str]:
    """Finds closest Minecraft block to the given RGB color. Returns (block_id, hex_color)."""
    target_lab = rgb_to_lab(rgb)
    candidates = allowed_blocks if allowed_blocks else list(MINECRAFT_BLOCKS.keys())
    
    best_block = "minecraft:stone"
    best_dist = float("inf")
    for b in candidates:
        if b not in BLOCK_LAB_CACHE:
            continue
        dist = delta_e(target_lab, BLOCK_LAB_CACHE[b])
        if dist < best_dist:
            best_dist = dist
            best_block = b

    rgb_val = MINECRAFT_BLOCKS.get(best_block, (128, 128, 128))
    return best_block, rgb_to_hex(rgb_val)

def get_block_color(block_id: str) -> str:
    """Returns hex color representation for a given block id."""
    clean_id = block_id.split("[")[0].strip()
    if clean_id in MINECRAFT_BLOCKS:
        return rgb_to_hex(MINECRAFT_BLOCKS[clean_id])
    # Fallbacks for unknown variants
    if "deepslate" in clean_id:
        return "#46464b"
    if "stone" in clean_id or "andesite" in clean_id:
        return "#7d7d7d"
    if "wood" in clean_id or "plank" in clean_id or "log" in clean_id:
        return "#a2824e"
    if "brick" in clean_id:
        return "#966153"
    if "glass" in clean_id:
        return "#d2e6eb"
    if "sand" in clean_id:
        return "#d8cb9b"
    return "#888888"

# Theme swapping palettes
THEME_PALETTES = {
    "gothic_deepslate": {
        "minecraft:stone": "minecraft:deepslate",
        "minecraft:cobblestone": "minecraft:cobbled_deepslate",
        "minecraft:stone_bricks": "minecraft:deepslate_bricks",
        "minecraft:oak_planks": "minecraft:spruce_planks",
        "minecraft:oak_log": "minecraft:dark_oak_log",
        "minecraft:glass": "minecraft:tinted_glass",
        "minecraft:dirt": "minecraft:mud",
        "minecraft:grass_block": "minecraft:moss_block",
        "minecraft:lantern": "minecraft:soul_lantern",
        "minecraft:torch": "minecraft:soul_torch"
    },
    "ruined_nether": {
        "minecraft:stone": "minecraft:blackstone",
        "minecraft:cobblestone": "minecraft:gilded_blackstone",
        "minecraft:stone_bricks": "minecraft:nether_bricks",
        "minecraft:oak_planks": "minecraft:crimson_planks",
        "minecraft:oak_log": "minecraft:crimson_stem",
        "minecraft:glass": "minecraft:red_stained_glass",
        "minecraft:dirt": "minecraft:soul_sand",
        "minecraft:grass_block": "minecraft:soul_soil",
        "minecraft:lantern": "minecraft:soul_lantern",
        "minecraft:torch": "minecraft:soul_torch"
    },
    "desert_sandstone": {
        "minecraft:stone": "minecraft:sandstone",
        "minecraft:cobblestone": "minecraft:cut_sandstone",
        "minecraft:stone_bricks": "minecraft:chiseled_sandstone",
        "minecraft:oak_planks": "minecraft:acacia_planks",
        "minecraft:oak_log": "minecraft:acacia_log",
        "minecraft:glass": "minecraft:orange_stained_glass",
        "minecraft:dirt": "minecraft:sand",
        "minecraft:grass_block": "minecraft:terracotta",
        "minecraft:lantern": "minecraft:lantern",
    },
    "overgrown_ruins": {
        "minecraft:stone": "minecraft:mossy_cobblestone",
        "minecraft:cobblestone": "minecraft:mossy_cobblestone",
        "minecraft:stone_bricks": "minecraft:mossy_stone_bricks",
        "minecraft:oak_planks": "minecraft:oak_planks",
        "minecraft:oak_log": "minecraft:oak_log",
        "minecraft:dirt": "minecraft:rooted_dirt",
        "minecraft:grass_block": "minecraft:moss_block",
    },
    "modern_quartz": {
        "minecraft:stone": "minecraft:quartz_block",
        "minecraft:cobblestone": "minecraft:smooth_quartz",
        "minecraft:stone_bricks": "minecraft:quartz_bricks",
        "minecraft:oak_planks": "minecraft:birch_planks",
        "minecraft:oak_log": "minecraft:stripped_birch_log",
        "minecraft:glass": "minecraft:white_stained_glass",
        "minecraft:dirt": "minecraft:white_concrete",
        "minecraft:grass_block": "minecraft:cyan_terracotta",
        "minecraft:lantern": "minecraft:sea_lantern",
    }
}
