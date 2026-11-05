"""Writes assets as files the game and other tools can read: vanilla structures (.nbt) and Sponge schematics (.schem)."""
import gzip
import re
import struct

TAG_BYTE, TAG_SHORT, TAG_INT, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY = 1, 2, 3, 7, 8, 9, 10, 11


class Short(int):
    pass


class LongArray(tuple):
    pass


def _string(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(value):
    if isinstance(value, Short):
        return TAG_SHORT, struct.pack(">h", value)
    if isinstance(value, int):
        return TAG_INT, struct.pack(">i", value)
    if isinstance(value, bytes):
        return TAG_BYTE_ARRAY, struct.pack(">i", len(value)) + value
    if isinstance(value, LongArray):
        return 12, struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
    if isinstance(value, tuple):  # a tuple of ints is an int array
        return TAG_INT_ARRAY, struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
    if isinstance(value, str):
        return TAG_STRING, _string(value)
    if isinstance(value, list):
        if not value:
            return TAG_LIST, struct.pack(">bi", 0, 0)
        kind = _payload(value[0])[0]
        return TAG_LIST, struct.pack(">bi", kind, len(value)) + b"".join(_payload(v)[1] for v in value)
    if isinstance(value, dict):
        out = bytearray()
        for key, item in value.items():
            kind, body = _payload(item)
            out += struct.pack(">b", kind) + _string(key) + body
        return TAG_COMPOUND, bytes(out) + b"\0"
    raise TypeError(type(value))


def write_nbt(value):
    """Gzipped NBT with an empty root name, as the game saves structures."""
    kind, body = _payload(value)
    return gzip.compress(struct.pack(">b", kind) + _string("") + body, mtime=0)


def read_nbt(data):
    """Reads back what write_nbt writes. Used by the tests."""
    raw = gzip.decompress(data)
    at = 0

    def take(fmt):
        nonlocal at
        size = struct.calcsize(fmt)
        out = struct.unpack_from(fmt, raw, at)[0]
        at += size
        return out

    def text():
        nonlocal at
        size = take(">H")
        out = raw[at:at + size].decode("utf-8")
        at += size
        return out

    def body(kind):
        nonlocal at
        if kind == TAG_BYTE:
            return take(">b")
        if kind == TAG_SHORT:
            return take(">h")
        if kind == TAG_INT:
            return take(">i")
        if kind == TAG_BYTE_ARRAY:
            size = take(">i")
            out = raw[at:at + size]
            at += size
            return out
        if kind == TAG_INT_ARRAY:
            return tuple(take(">i") for _ in range(take(">i")))
        if kind == TAG_STRING:
            return text()
        if kind == TAG_LIST:
            inner = take(">b")
            return [body(inner) for _ in range(take(">i"))]
        if kind == TAG_COMPOUND:
            out = {}
            while True:
                tag = take(">b")
                if tag == 0:
                    return out
                key = text()
                out[key] = body(tag)
        raise ValueError(kind)

    root = take(">b")
    text()
    return body(root)


def _palette_entry(state):
    found = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", state)
    entry = {"id": found.group(1)}  # 26.3 says "id" and "properties"; older versions said "Name" and "Properties"
    if found.group(2):
        entry["properties"] = dict(pair.split("=") for pair in found.group(2).split(","))
    return entry


def inside_air(blocks):
    """Empty cells enclosed by the build: with a block above them and blocks on both sides, in both directions.
    Placing air only there clears terrain from inside a house, without cutting a box out of the land around it."""
    top, row, column = {}, {}, {}
    filled = set()
    for x, y, z, _ in blocks:
        filled.add((x, y, z))
        top[(x, z)] = max(top.get((x, z), y), y)
        low, high = row.get((y, z), (x, x))
        row[(y, z)] = (min(low, x), max(high, x))
        low, high = column.get((x, y), (z, z))
        column[(x, y)] = (min(low, z), max(high, z))
    out = []
    for (y, z), (low, high) in row.items():
        for x in range(low + 1, high):
            if (x, y, z) in filled or top.get((x, z), y) <= y:
                continue
            near, far = column.get((x, y), (z, z))
            if near < z < far:
                out.append([x, y, z, "minecraft:air"])
    return out


def to_structure(tree, data_version, with_air=False):
    xs, ys, zs = zip(*((b[0], b[1], b[2]) for b in tree["blocks"]))
    low = (min(xs), min(ys), min(zs))
    size = [max(xs) - low[0] + 1, max(ys) - low[1] + 1, max(zs) - low[2] + 1]
    palette, lookup, blocks = [], {}, []
    cells = list(tree["blocks"]) + (inside_air(tree["blocks"]) if with_air else [])
    for x, y, z, state in cells:
        if state not in lookup:
            lookup[state] = len(palette)
            palette.append(_palette_entry(state))
        blocks.append({"pos": [x - low[0], y - low[1], z - low[2]], "state": lookup[state]})
    structure = {"DataVersion": data_version, "size": size, "entities": [], "palette": palette, "blocks": blocks}
    placement = {"ground_y": -low[1], "trunk_x": -low[0], "trunk_z": -low[2], "size": size}
    return structure, placement


def with_ground_offset(placement, project):
    """Adds the author's ground offset (Review: Lower / Raise) to a build's placement.

    ground_y is how many layers lie below the ground in the file; ground_offset is the blocks the author sank (-) or raised (+) the build
    in Review; lower_by is what a placer should use: the blocks to put the file's lowest layer below the ground surface.
    """
    offset = int(project.get("ground_offset", 0) or 0)
    return {**placement, "ground_offset": offset, "lower_by": placement["ground_y"] - offset}


def _varint(value):
    out = bytearray()
    while value >= 0x80:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def to_schem(tree, data_version):
    """Sponge schematic version 3. Pasting places the trunk where the player stands, with roots below the ground."""
    xs, ys, zs = zip(*((b[0], b[1], b[2]) for b in tree["blocks"]))
    low = (min(xs), min(ys), min(zs))
    width, height, length = max(xs) - low[0] + 1, max(ys) - low[1] + 1, max(zs) - low[2] + 1
    palette = {"minecraft:air": 0}
    cells = [0] * (width * height * length)
    for x, y, z, state in tree["blocks"]:
        index = palette.setdefault(state, len(palette))
        cells[(x - low[0]) + (z - low[2]) * width + (y - low[1]) * width * length] = index
    data = b"".join(_varint(c) for c in cells)
    return {"Schematic": {
        "Version": 3,
        "DataVersion": data_version,
        "Width": Short(width),
        "Height": Short(height),
        "Length": Short(length),
        "Offset": (low[0], low[1], low[2]),
        "Blocks": {"Palette": palette, "Data": data, "BlockEntities": []},
    }}
