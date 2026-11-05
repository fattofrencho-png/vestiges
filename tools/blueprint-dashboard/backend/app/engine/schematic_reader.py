import io
import gzip
import math
import nbtlib
from typing import Tuple, List, Dict
from app.engine.voxel_grid import VoxelGrid

# Common legacy block id mappings
LEGACY_BLOCK_MAP: Dict[int, str] = {
    0: "minecraft:air",
    1: "minecraft:stone",
    2: "minecraft:grass_block",
    3: "minecraft:dirt",
    4: "minecraft:cobblestone",
    5: "minecraft:oak_planks",
    7: "minecraft:bedrock",
    8: "minecraft:water",
    9: "minecraft:water",
    10: "minecraft:lava",
    11: "minecraft:lava",
    12: "minecraft:sand",
    13: "minecraft:gravel",
    14: "minecraft:gold_ore",
    15: "minecraft:iron_ore",
    16: "minecraft:coal_ore",
    17: "minecraft:oak_log",
    18: "minecraft:oak_leaves",
    19: "minecraft:sponge",
    20: "minecraft:glass",
    24: "minecraft:sandstone",
    35: "minecraft:white_wool",
    41: "minecraft:gold_block",
    42: "minecraft:iron_block",
    44: "minecraft:stone_slab",
    45: "minecraft:bricks",
    48: "minecraft:mossy_cobblestone",
    49: "minecraft:obsidian",
    50: "minecraft:torch",
    53: "minecraft:oak_stairs",
    54: "minecraft:chest",
    57: "minecraft:diamond_block",
    67: "minecraft:cobblestone_stairs",
    85: "minecraft:oak_fence",
    89: "minecraft:glowstone",
    98: "minecraft:stone_bricks",
    108: "minecraft:brick_stairs",
    109: "minecraft:stone_brick_stairs",
    126: "minecraft:oak_slab",
    133: "minecraft:emerald_block",
    155: "minecraft:quartz_block",
    156: "minecraft:quartz_stairs",
    159: "minecraft:terracotta",
    201: "minecraft:purpur_block",
    202: "minecraft:purpur_pillar",
}

def read_varint(stream: io.BytesIO) -> int:
    result = 0
    shift = 0
    while True:
        b = stream.read(1)
        if not b:
            return -1
        byte = b[0]
        result |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            break
        shift += 7
    return result

def read_schematic(file_bytes: bytes, filename: str = "") -> Tuple[VoxelGrid, str]:
    """
    Parses Sponge .schem, Litematica .litematic, Vanilla Structure .nbt, or Legacy .schematic.
    Returns (VoxelGrid, format_name).
    """
    # Decompress gzip if compressed
    try:
        data = gzip.decompress(file_bytes)
    except Exception:
        data = file_bytes

    nbt_file = nbtlib.File.from_fileobj(io.BytesIO(data))
    root = nbt_file

    # 1. Sponge .schem format
    if "Schematic" in root or ("Width" in root and "Palette" in root):
        schem_tag = root["Schematic"] if "Schematic" in root else root
        w = int(schem_tag["Width"])
        h = int(schem_tag["Height"])
        l = int(schem_tag["Length"])
        
        palette_tag = schem_tag["Palette"]
        # Invert palette: int_id -> block_name
        palette = {int(v): str(k) for k, v in palette_tag.items()}
        
        block_data = bytes(schem_tag["BlockData"])
        stream = io.BytesIO(block_data)
        grid = VoxelGrid(w, h, l)

        for y in range(h):
            for z in range(l):
                for x in range(w):
                    val = read_varint(stream)
                    if val == -1:
                        break
                    block_name = palette.get(val, "minecraft:air")
                    if block_name != "minecraft:air":
                        grid.set_block(x, y, z, block_name)

        grid.recalculate_bounds()
        return grid, "sponge_schem"

    # 2. Vanilla Structure .nbt format
    if "size" in root and "palette" in root and "blocks" in root:
        size = [int(v) for v in root["size"]]
        w, h, l = size[0], size[1], size[2]
        palette_list = [str(item["Name"]) for item in root["palette"]]
        grid = VoxelGrid(w, h, l)

        for b in root["blocks"]:
            pos = [int(p) for p in b["pos"]]
            state_idx = int(b["state"])
            if 0 <= state_idx < len(palette_list):
                b_name = palette_list[state_idx]
                if b_name != "minecraft:air":
                    grid.set_block(pos[0], pos[1], pos[2], b_name)

        grid.recalculate_bounds()
        return grid, "vanilla_nbt"

    # 3. Litematica .litematic format
    if "Regions" in root:
        grid = VoxelGrid()
        for region_name, region in root["Regions"].items():
            size = region["Size"]
            rw = abs(int(size["x"]))
            rh = abs(int(size["y"]))
            rl = abs(int(size["z"]))
            
            palette = [str(item["Name"]) for item in region["BlockStatePalette"]]
            if not palette:
                continue

            bits_per_block = max(2, math.ceil(math.log2(len(palette))))
            blocks_per_long = 64 // bits_per_block
            mask = (1 << bits_per_block) - 1

            long_array = region.get("BlockStates", [])
            total_blocks = rw * rh * rl

            for idx in range(min(total_blocks, len(long_array) * blocks_per_long)):
                long_idx = idx // blocks_per_long
                if long_idx >= len(long_array):
                    break
                bit_offset = (idx % blocks_per_long) * bits_per_block
                val = (int(long_array[long_idx]) >> bit_offset) & mask
                
                # Unpack coordinates (y, z, x)
                y = idx // (rw * rl)
                rem = idx % (rw * rl)
                z = rem // rw
                x = rem % rw

                if val < len(palette):
                    b_name = palette[val]
                    if b_name != "minecraft:air":
                        grid.set_block(x, y, z, b_name)

        grid.recalculate_bounds()
        return grid, "litematica"

    # 4. Legacy .schematic format
    if "Blocks" in root and "Width" in root and "Length" in root:
        w = int(root["Width"])
        h = int(root["Height"])
        l = int(root["Length"])
        blocks = bytes(root["Blocks"])
        grid = VoxelGrid(w, h, l)

        for y in range(h):
            for z in range(l):
                for x in range(w):
                    idx = (y * l + z) * w + x
                    if idx < len(blocks):
                        block_id_num = blocks[idx]
                        b_name = LEGACY_BLOCK_MAP.get(block_id_num, "minecraft:stone" if block_id_num > 0 else "minecraft:air")
                        if b_name != "minecraft:air":
                            grid.set_block(x, y, z, b_name)

        grid.recalculate_bounds()
        return grid, "legacy_schematic"

    raise ValueError("Unrecognized schematic format. Expected Sponge .schem, Litematica, Vanilla NBT, or Legacy schematic.")
