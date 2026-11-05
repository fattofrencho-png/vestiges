import io
import gzip
import time
import math
import nbtlib
from typing import Dict, List
from app.engine.voxel_grid import VoxelGrid

# Reverse mapping for legacy IDs
REVERSE_LEGACY_MAP: Dict[str, int] = {
    "minecraft:air": 0,
    "minecraft:stone": 1,
    "minecraft:grass_block": 2,
    "minecraft:dirt": 3,
    "minecraft:cobblestone": 4,
    "minecraft:oak_planks": 5,
    "minecraft:bedrock": 7,
    "minecraft:water": 8,
    "minecraft:sand": 12,
    "minecraft:gravel": 13,
    "minecraft:oak_log": 17,
    "minecraft:oak_leaves": 18,
    "minecraft:glass": 20,
    "minecraft:sandstone": 24,
    "minecraft:white_wool": 35,
    "minecraft:gold_block": 41,
    "minecraft:iron_block": 42,
    "minecraft:bricks": 45,
    "minecraft:mossy_cobblestone": 48,
    "minecraft:obsidian": 49,
    "minecraft:torch": 50,
    "minecraft:diamond_block": 57,
    "minecraft:glowstone": 89,
    "minecraft:stone_bricks": 98,
    "minecraft:emerald_block": 133,
    "minecraft:quartz_block": 155,
    "minecraft:terracotta": 159,
    "minecraft:purpur_block": 201,
}

def write_varint(stream: io.BytesIO, value: int):
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            stream.write(bytes([byte | 0x80]))
        else:
            stream.write(bytes([byte]))
            break

def export_sponge_schem(grid: VoxelGrid) -> bytes:
    """Exports VoxelGrid to Sponge / WorldEdit .schem format."""
    grid.recalculate_bounds()
    w, h, l = max(grid.width, 1), max(grid.height, 1), max(grid.length, 1)

    palette: Dict[str, int] = {"minecraft:air": 0}
    next_id = 1
    stream = io.BytesIO()

    for y in range(h):
        for z in range(l):
            for x in range(w):
                b_name = grid.get_block(x, y, z)
                if b_name not in palette:
                    palette[b_name] = next_id
                    next_id += 1
                write_varint(stream, palette[b_name])

    # Convert bytes to signed int8 list for nbtlib ByteArray
    byte_list = [x if x < 128 else x - 256 for x in stream.getvalue()]
    palette_compound = {k: nbtlib.Int(v) for k, v in palette.items()}

    schem_compound = nbtlib.Compound({
        "Width": nbtlib.Short(w),
        "Height": nbtlib.Short(h),
        "Length": nbtlib.Length(l) if hasattr(nbtlib, "Length") else nbtlib.Short(l),
        "PaletteMax": nbtlib.Int(len(palette)),
        "Palette": nbtlib.Compound(palette_compound),
        "BlockData": nbtlib.ByteArray(byte_list),
        "Version": nbtlib.Int(2),
        "DataVersion": nbtlib.Int(3955)
    })

    root = nbtlib.Compound({"Schematic": schem_compound})
    out_buf = io.BytesIO()
    nbt_file = nbtlib.File(root)
    nbt_file.save(out_buf, gzipped=True)
    return out_buf.getvalue()

def export_vanilla_nbt(grid: VoxelGrid) -> bytes:
    """Exports VoxelGrid to standard Minecraft Structure .nbt format."""
    grid.recalculate_bounds()
    w, h, l = max(grid.width, 1), max(grid.height, 1), max(grid.length, 1)

    palette_list = ["minecraft:air"]
    palette_map = {"minecraft:air": 0}
    blocks_list = []

    for (x, y, z), b_name in grid.voxels.items():
        if b_name not in palette_map:
            palette_map[b_name] = len(palette_list)
            palette_list.append(b_name)
        blocks_list.append(nbtlib.Compound({
            "pos": nbtlib.List[nbtlib.Int]([nbtlib.Int(x), nbtlib.Int(y), nbtlib.Int(z)]),
            "state": nbtlib.Int(palette_map[b_name])
        }))

    palette_compound_list = [
        nbtlib.Compound({"Name": nbtlib.String(name)}) for name in palette_list
    ]

    root = nbtlib.Compound({
        "size": nbtlib.List[nbtlib.Int]([nbtlib.Int(w), nbtlib.Int(h), nbtlib.Int(l)]),
        "entities": nbtlib.List[nbtlib.Compound]([]),
        "blocks": nbtlib.List[nbtlib.Compound](blocks_list),
        "palette": nbtlib.List[nbtlib.Compound](palette_compound_list),
        "DataVersion": nbtlib.Int(3955)
    })

    out_buf = io.BytesIO()
    nbt_file = nbtlib.File(root)
    nbt_file.save(out_buf, gzipped=True)
    return out_buf.getvalue()

def export_litematica(grid: VoxelGrid, name: str = "Structure") -> bytes:
    """Exports VoxelGrid to Litematica .litematic format."""
    grid.recalculate_bounds()
    w, h, l = max(grid.width, 1), max(grid.height, 1), max(grid.length, 1)

    palette_list = ["minecraft:air"]
    palette_map = {"minecraft:air": 0}

    # Collect palette
    for b_name in grid.voxels.values():
        if b_name not in palette_map:
            palette_map[b_name] = len(palette_list)
            palette_list.append(b_name)

    bits_per_block = max(2, math.ceil(math.log2(max(len(palette_list), 2))))
    blocks_per_long = 64 // bits_per_block
    total_blocks = w * h * l
    num_longs = math.ceil(total_blocks / blocks_per_long)
    long_array = [0] * num_longs

    for y in range(h):
        for z in range(l):
            for x in range(w):
                idx = (y * l + z) * w + x
                long_idx = idx // blocks_per_long
                bit_offset = (idx % blocks_per_long) * bits_per_block
                b_name = grid.get_block(x, y, z)
                palette_idx = palette_map.get(b_name, 0)
                long_array[long_idx] |= (palette_idx << bit_offset)

    palette_tag = [nbtlib.Compound({"Name": nbtlib.String(n)}) for n in palette_list]

    signed_longs = [x if x < (1 << 63) else x - (1 << 64) for x in long_array]

    region_tag = nbtlib.Compound({
        "Position": nbtlib.Compound({"x": nbtlib.Int(0), "y": nbtlib.Int(0), "z": nbtlib.Int(0)}),
        "Size": nbtlib.Compound({"x": nbtlib.Int(w), "y": nbtlib.Int(h), "z": nbtlib.Int(l)}),
        "BlockStatePalette": nbtlib.List[nbtlib.Compound](palette_tag),
        "BlockStates": nbtlib.LongArray(signed_longs),
        "Entities": nbtlib.List[nbtlib.Compound]([]),
        "TileEntities": nbtlib.List[nbtlib.Compound]([]),
        "PendingBlockTicks": nbtlib.List[nbtlib.Compound]([])
    })

    now_ms = int(time.time() * 1000)
    root = nbtlib.Compound({
        "MinecraftDataVersion": nbtlib.Int(3955),
        "Version": nbtlib.Int(6),
        "Metadata": nbtlib.Compound({
            "Author": nbtlib.String("Blueprint Studio"),
            "Description": nbtlib.String("Exported from Blueprint Studio"),
            "Name": nbtlib.String(name),
            "RegionCount": nbtlib.Int(1),
            "TimeCreated": nbtlib.Long(now_ms),
            "TimeModified": nbtlib.Long(now_ms),
            "TotalBlocks": nbtlib.Int(len(grid.voxels)),
            "TotalVolume": nbtlib.Int(total_blocks),
            "EnclosingSize": nbtlib.Compound({"x": nbtlib.Int(w), "y": nbtlib.Int(h), "z": nbtlib.Int(l)})
        }),
        "Regions": nbtlib.Compound({name: region_tag})
    })

    out_buf = io.BytesIO()
    nbt_file = nbtlib.File(root)
    nbt_file.save(out_buf, gzipped=True)
    return out_buf.getvalue()

def export_legacy_schematic(grid: VoxelGrid) -> bytes:
    """Exports VoxelGrid to legacy .schematic format."""
    grid.recalculate_bounds()
    w, h, l = max(grid.width, 1), max(grid.height, 1), max(grid.length, 1)

    total = w * h * l
    block_bytes = bytearray(total)
    data_bytes = bytearray(total)

    for y in range(h):
        for z in range(l):
            for x in range(w):
                idx = (y * l + z) * w + x
                b_name = grid.get_block(x, y, z)
                clean_name = b_name.split("[")[0].strip()
                legacy_id = REVERSE_LEGACY_MAP.get(clean_name, 1 if b_name != "minecraft:air" else 0)
                block_bytes[idx] = legacy_id & 0xFF

    signed_block_list = [x if x < 128 else x - 256 for x in block_bytes]
    signed_data_list = [x if x < 128 else x - 256 for x in data_bytes]

    root = nbtlib.Compound({
        "Width": nbtlib.Short(w),
        "Height": nbtlib.Short(h),
        "Length": nbtlib.Short(l),
        "Materials": nbtlib.String("Alpha"),
        "Blocks": nbtlib.ByteArray(signed_block_list),
        "Data": nbtlib.ByteArray(signed_data_list),
        "Entities": nbtlib.List[nbtlib.Compound]([]),
        "TileEntities": nbtlib.List[nbtlib.Compound]([])
    })

    out_buf = io.BytesIO()
    nbt_file = nbtlib.File(root)
    nbt_file.save(out_buf, gzipped=True)
    return out_buf.getvalue()

def serialize_schematic(grid: VoxelGrid, format_type: str, name: str = "Structure") -> bytes:
    format_lower = format_type.lower()
    if "schem" in format_lower and "legacy" not in format_lower:
        return export_sponge_schem(grid)
    elif "litematic" in format_lower:
        return export_litematica(grid, name)
    elif "nbt" in format_lower:
        return export_vanilla_nbt(grid)
    elif "legacy" in format_lower or "schematic" in format_lower:
        return export_legacy_schematic(grid)
    else:
        return export_sponge_schem(grid)
