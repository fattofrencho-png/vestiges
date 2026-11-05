"""Reads builds made by others: Sponge .schem, .litematic, structure .nbt, mod and datapack archives, and saved worlds.

Nothing here writes to a source, or unpacks an archive to disk. Every reader gives the same result:
{"blocks": [[x, y, z, "minecraft:name[props]"], ...], "dropped": {...}, "notes": [...]}. Blocks are moved so the lowest
is at y=0 and the middle is near x=0, z=0. Air is left out; export works out what air a build needs.
"""
import io
import os
import re
import struct
import zipfile
import zlib
from pathlib import Path

import nbt

MAX_FILE = 256 * 1024 * 1024
MAX_BLOCKS = 500_000
SLOW_BLOCKS = 100_000
MAX_WORLD_CELLS = 16_000_000
EMPTY = {"air", "cave_air", "void_air", "structure_void"}
COMMAND = {"command_block", "chain_command_block", "repeating_command_block"}
STRUCTURE_PATH = re.compile(r"^data/[^/]+/structures?/.+\.nbt$")
BUILD_FILE = re.compile(r"\.(nbt|schem|litematic)$", re.I)


class ImportProblem(ValueError):
    """A file the tool can't use, in words the author can act on."""


def state_text(name, props=None):
    name = str(name)
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def normal_state(text):
    """Tidies "name[b=1,a=2]" so equal states are always written the same way."""
    found = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", str(text).strip())
    if not found:
        raise ImportProblem(f"The file has a damaged block name: {text}")
    props = dict(p.split("=", 1) for p in found.group(2).split(",") if "=" in p) if found.group(2) else {}
    return state_text(found.group(1), props)


def _is_empty(state):
    return state.split("[", 1)[0].split(":")[-1] in EMPTY


def finish(cells, dropped, notes):
    """cells: iterable of (x, y, z, state) with air still in it."""
    blocks = []
    for x, y, z, state in cells:
        if _is_empty(state):
            continue
        blocks.append([x, y, z, state])
        if len(blocks) > MAX_BLOCKS:
            raise ImportProblem(f"This build has more than {MAX_BLOCKS:,} blocks, the limit for this tool. Pick a smaller part.")
    if not blocks:
        raise ImportProblem("This build has no blocks in it, only air.")
    low_y = min(b[1] for b in blocks)
    mid_x = (min(b[0] for b in blocks) + max(b[0] for b in blocks)) // 2
    mid_z = (min(b[2] for b in blocks) + max(b[2] for b in blocks)) // 2
    for block in blocks:
        block[0] -= mid_x
        block[1] -= low_y
        block[2] -= mid_z
    blocks.sort()
    commands = sum(1 for b in blocks if b[3].split("[", 1)[0].split(":")[-1] in COMMAND)
    if commands:
        dropped["command_blocks"] = commands
    if len(blocks) > SLOW_BLOCKS:
        notes.append(f"{len(blocks):,} blocks: the 3D view may be slow.")
    return {"blocks": blocks, "dropped": {k: v for k, v in dropped.items() if v}, "notes": notes}


def _entry_state(entry):
    """A palette entry. 26.3 structures say id/properties, 26.3 worlds can use plain text, and older files say Name/Properties."""
    if isinstance(entry, str):
        return normal_state(entry)
    if "" in entry:  # Minecraft wraps plain text this way when a list also holds compounds
        return normal_state(entry[""])
    return state_text(entry.get("id") or entry.get("Name"), entry.get("properties") or entry.get("Properties"))


def _version_note(root):
    version = root.get("DataVersion") or root.get("MinecraftDataVersion")
    if isinstance(version, int) and version < 4000:
        return ["This build is from an older Minecraft version, so some block names may have changed. They would show as not vanilla."]
    return []


# ---- each format ----
def read_structure(root):
    blocks = root.get("blocks")
    palette = root.get("palette")
    notes = _version_note(root)
    if palette is None and root.get("palettes"):
        palette = root["palettes"][0]
        if len(root["palettes"]) > 1:
            notes.append(f"This file has {len(root['palettes'])} block palettes, and the first one is used.")
    if blocks is None or palette is None:
        raise ImportProblem("This .nbt file isn't a structure.")
    states = [_entry_state(p) for p in palette]

    def cells():
        for block in blocks:
            x, y, z = block["pos"]
            yield x, y, z, states[block["state"]]
    try:
        return finish(cells(), {"block_data": sum(1 for b in blocks if "nbt" in b), "entities": len(root.get("entities") or [])}, notes)
    except (KeyError, IndexError, TypeError, ValueError) as problem:
        if isinstance(problem, ImportProblem):
            raise
        raise ImportProblem("This structure file is damaged.") from problem


def read_schem(root):
    inner = root.get("Schematic", root)
    if "Blocks" in inner and isinstance(inner["Blocks"], (bytes, bytearray)) or "Materials" in inner:
        raise ImportProblem("This is an old .schematic from before Minecraft 1.13, which stores numbered block IDs the tool can't convert. "
                            "Load it in WorldEdit on a newer version, save it as .schem, and import that.")
    holder = inner["Blocks"] if isinstance(inner.get("Blocks"), dict) else inner
    palette = holder.get("Palette")
    data = holder.get("Data") if "Data" in holder else holder.get("BlockData")
    if palette is None or data is None:
        raise ImportProblem("This .schem file has no blocks, or is damaged.")
    width, height, length = inner["Width"] & 0xFFFF, inner["Height"] & 0xFFFF, inner["Length"] & 0xFFFF
    states = {index: normal_state(state) for state, index in palette.items()}

    def cells():
        at, x, y, z = 0, 0, 0, 0
        end = len(data)
        for _ in range(width * height * length):
            value, shift = 0, 0
            while True:
                if at >= end:
                    raise ImportProblem("This .schem file ends too early, or is damaged.")
                byte = data[at]
                at += 1
                value |= (byte & 0x7F) << shift
                if byte < 0x80:
                    break
                shift += 7
            yield x, y, z, states[value]
            x += 1
            if x == width:
                x, z = 0, z + 1
                if z == length:
                    z, y = 0, y + 1
    entities = holder.get("BlockEntities") or inner.get("BlockEntities") or inner.get("TileEntities") or []
    try:
        return finish(cells(), {"block_data": len(entities), "entities": len(inner.get("Entities") or [])}, _version_note(inner))
    except KeyError as problem:
        raise ImportProblem("This .schem file is damaged.") from problem


def _packed(values, bits, count, spanning):
    """Yields numbers packed into 64-bit values."""
    mask = (1 << bits) - 1
    if spanning:  # litematic: a number may cross into the next value
        for i in range(count):
            start = i * bits
            word = start >> 6
            offset = start & 63
            number = (values[word] & 0xFFFFFFFFFFFFFFFF) >> offset
            if offset + bits > 64:
                number |= (values[word + 1] & 0xFFFFFFFFFFFFFFFF) << (64 - offset)
            yield number & mask
    else:  # worlds: numbers never cross
        per_word = 64 // bits
        for i in range(count):
            yield ((values[i // per_word] & 0xFFFFFFFFFFFFFFFF) >> ((i % per_word) * bits)) & mask


def read_litematic(root):
    regions = root.get("Regions")
    if not regions:
        raise ImportProblem("This .litematic file has no regions.")
    dropped = {"block_data": 0, "entities": 0}

    def cells():
        for region in regions.values():
            palette = [_entry_state(p) for p in region["BlockStatePalette"]]
            size, pos = region["Size"], region["Position"]
            sx, sy, sz = abs(size["x"]), abs(size["y"]), abs(size["z"])
            # A negative size means the region reaches back from its position.
            ox = pos["x"] + (size["x"] + 1 if size["x"] < 0 else 0)
            oy = pos["y"] + (size["y"] + 1 if size["y"] < 0 else 0)
            oz = pos["z"] + (size["z"] + 1 if size["z"] < 0 else 0)
            dropped["block_data"] += len(region.get("TileEntities") or [])
            dropped["entities"] += len(region.get("Entities") or [])
            bits = max(2, (len(palette) - 1).bit_length())
            values = region["BlockStates"]
            if len(values) * 64 < bits * sx * sy * sz:
                raise ImportProblem("This .litematic file is damaged.")
            index = 0
            for number in _packed(values, bits, sx * sy * sz, True):
                x = index % sx
                z = (index // sx) % sz
                y = index // (sx * sz)
                if number >= len(palette):
                    raise ImportProblem("This .litematic file is damaged.")
                yield ox + x, oy + y, oz + z, palette[number]
                index += 1
    try:
        return finish(cells(), dropped, _version_note(root))
    except (KeyError, IndexError, TypeError) as problem:
        raise ImportProblem("This .litematic file is damaged.") from problem


def read_build(data):
    """Works out the format from what's inside the file, not its name."""
    try:
        root = nbt.read(data)
    except (nbt.NbtError, OSError, EOFError, zlib.error, struct.error) as problem:
        raise ImportProblem(f"This file can't be read as a Minecraft build: {problem}") from problem
    if "Regions" in root:
        return read_litematic(root)
    if "Schematic" in root or "Palette" in root or "Materials" in root or ("Width" in root and "Length" in root):
        return read_schem(root)
    if "blocks" in root and ("palette" in root or "palettes" in root):
        return read_structure(root)
    raise ImportProblem("This file is Minecraft data, but not a build the tool knows.")


# ---- files and archives ----
def _start_of(source):
    if isinstance(source, Path):
        with open(source, "rb") as handle:
            return handle.read(2)
    return source[:2]


def _size_of(source):
    return source.stat().st_size if isinstance(source, Path) else len(source)


def _zip(source):
    return zipfile.ZipFile(source if isinstance(source, Path) else io.BytesIO(source))


def scan(name, data):
    """What can be imported from an uploaded file, given as bytes or a path: a list of {"id", "label"}. Archives can hold many."""
    if _size_of(data) > MAX_FILE:
        raise ImportProblem("This file is over the 256 MB limit.")
    if _start_of(data) != b"PK":
        if not _size_of(data):
            raise ImportProblem("This file is empty.")
        return [{"id": "", "label": name}]
    try:
        archive = _zip(data)
    except zipfile.BadZipFile as problem:
        raise ImportProblem("This archive is damaged.") from problem
    items = []
    for info in archive.infolist():
        if info.is_dir() or info.file_size > MAX_FILE:
            continue
        path = info.filename.replace("\\", "/")
        if STRUCTURE_PATH.match(path) or (BUILD_FILE.search(path) and not path.startswith("data/")):
            items.append({"id": info.filename, "label": _label(path)})
    if not items:
        raise ImportProblem("No structures found in this archive. A mod or datapack keeps them in data/<name>/structure/.")
    return sorted(items, key=lambda i: i["label"])


def _label(path):
    found = re.match(r"^data/([^/]+)/structures?/(.+)\.nbt$", path)
    return f"{found.group(1)}: {found.group(2)}" if found else path


def read_item(data, item):
    if not item:
        return read_build(data.read_bytes() if isinstance(data, Path) else data)
    try:
        archive = _zip(data)
        info = archive.getinfo(item)
    except (zipfile.BadZipFile, KeyError) as problem:
        raise ImportProblem("That structure isn't in this archive.") from problem
    if info.file_size > MAX_FILE:
        raise ImportProblem("That structure is over the 256 MB limit.")
    return read_build(archive.read(info))


# ---- saved worlds ----
DIMENSIONS = {
    "overworld": ("dimensions/minecraft/overworld/region", "region"),
    "the_nether": ("dimensions/minecraft/the_nether/region", "DIM-1/region"),
    "the_end": ("dimensions/minecraft/the_end/region", "DIM1/region"),
}


def find_saves(extra_roots=()):
    """Saved worlds on this PC: [{"name", "path"}]."""
    appdata = Path(os.environ.get("APPDATA", ""))
    roots = [(".minecraft", appdata / ".minecraft/saves")]
    instances = appdata / "ATLauncher/instances"
    if instances.is_dir():
        roots += [(i.name, i / "saves") for i in sorted(instances.iterdir()) if i.is_dir()]
    roots += [(label, Path(path)) for label, path in extra_roots]
    out = []
    for label, root in roots:
        if root.is_dir():
            for world in sorted(root.iterdir()):
                if (world / "level.dat").exists():
                    out.append({"name": f"{label}: {world.name}", "path": str(world)})
    return out


def region_folder(world, dimension):
    if dimension not in DIMENSIONS:
        raise ImportProblem("Unknown dimension.")
    base = Path(str(world).strip().strip('"'))
    if not (base / "level.dat").exists():
        raise ImportProblem("That folder isn't a saved world: it has no level.dat.")
    for relative in DIMENSIONS[dimension]:
        folder = base / relative
        if folder.is_dir():
            return folder
    raise ImportProblem("This world has no saved chunks in that dimension.")


def _read_chunk(handle, folder, region_x, region_z, chunk_x, chunk_z):
    """One chunk from an open region file, or None if it was never generated."""
    handle.seek(4 * ((chunk_x & 31) + (chunk_z & 31) * 32))
    entry = handle.read(4)
    offset = int.from_bytes(entry[:3], "big") * 4096
    if not offset:
        return None
    handle.seek(offset)
    length = int.from_bytes(handle.read(4), "big")
    kind = handle.read(1)[0]
    if kind & 0x80:  # very large chunks live in their own file
        path = folder / f"c.{chunk_x}.{chunk_z}.mcc"
        raw = path.read_bytes() if path.exists() else None
        if raw is None:
            return None
        kind &= 0x7F
    else:
        raw = handle.read(length - 1)
    if kind == 1 or kind == 2:
        return nbt.read(raw)
    if kind == 3:
        return nbt.parse(raw)
    raise ImportProblem("This world uses a chunk compression the tool can't read yet. It can be changed in server.properties.")


def read_world(world, dimension, first, second):
    """Copies a box between two corners. Region files are opened read-only."""
    folder = region_folder(world, dimension)
    try:
        low = [min(int(a), int(b)) for a, b in zip(first, second)]
        high = [max(int(a), int(b)) for a, b in zip(first, second)]
    except (TypeError, ValueError) as problem:
        raise ImportProblem("Enter numbers for both corners.") from problem
    if (high[0] - low[0] + 1) * (high[1] - low[1] + 1) * (high[2] - low[2] + 1) > MAX_WORLD_CELLS:
        raise ImportProblem("That box is too big. Keep it under 16 million cells, about 250 × 250 × 250.")
    dropped = {"block_data": 0, "entities": 0}
    notes = []
    missing = [0]

    def cells():
        handles = {}
        try:
            for chunk_x in range(low[0] >> 4, (high[0] >> 4) + 1):
                for chunk_z in range(low[2] >> 4, (high[2] >> 4) + 1):
                    key = (chunk_x >> 5, chunk_z >> 5)
                    if key not in handles:
                        path = folder / f"r.{key[0]}.{key[1]}.mca"
                        handles[key] = open(path, "rb") if path.exists() else None
                    chunk = _read_chunk(handles[key], folder, *key, chunk_x, chunk_z) if handles[key] else None
                    if chunk is None:
                        missing[0] += 1
                        continue
                    if "Level" in chunk:
                        raise ImportProblem("This world was saved before Minecraft 1.18, and the tool can't read that format. Open it once in a newer version first.")
                    for entity in chunk.get("block_entities") or []:
                        if low[0] <= entity.get("x", low[0] - 1) <= high[0] and low[1] <= entity.get("y", 0) <= high[1] and low[2] <= entity.get("z", 0) <= high[2]:
                            dropped["block_data"] += 1
                    for section in chunk.get("sections") or []:
                        base_y = section["Y"] * 16
                        if base_y + 15 < low[1] or base_y > high[1]:
                            continue
                        store = section.get("block_states") or {}
                        palette = [_entry_state(p) for p in store.get("palette") or []]
                        if not palette or (len(palette) == 1 and _is_empty(palette[0])):
                            continue
                        if len(palette) == 1 or "data" not in store:
                            numbers = [0] * 4096
                        else:
                            numbers = _packed(store["data"], max(4, (len(palette) - 1).bit_length()), 4096, False)
                        for index, number in enumerate(numbers):
                            x = chunk_x * 16 + (index & 15)
                            z = chunk_z * 16 + ((index >> 4) & 15)
                            y = base_y + (index >> 8)
                            if low[0] <= x <= high[0] and low[1] <= y <= high[1] and low[2] <= z <= high[2]:
                                yield x, y, z, palette[number]
        finally:
            for handle in handles.values():
                if handle:
                    handle.close()
    try:
        result = finish(cells(), dropped, notes)
    except (KeyError, IndexError, TypeError, nbt.NbtError, OSError, zlib.error) as problem:
        raise ImportProblem(f"This world's files couldn't be read: {problem}") from problem
    if missing[0]:
        result["notes"].append(f"{missing[0]} chunks in that box were never generated, so they are empty.")
    result["notes"].append("If the world is open in the game, recent changes may not be saved yet.")
    return result
