"""One-click edits on a block list. Each takes blocks and returns new ones: same input, same result, no AI."""
import random
import re
from collections import deque

import plan_tools

SIDES = plan_tools.SIDES
TURN = {"north": "east", "east": "south", "south": "west", "west": "north"}
MIRROR_X = {"east": "west", "west": "east"}
MIRROR_Z = {"north": "south", "south": "north"}
FACING_PROPS = ("facing", "horizontal_facing")
LEFT_RIGHT = {"left": "right", "right": "left"}
STAIR_SHAPE = {"inner_left": "inner_right", "inner_right": "inner_left", "outer_left": "outer_right", "outer_right": "outer_left"}
RAIL_TURN = {"north_south": "east_west", "east_west": "north_south", "ascending_north": "ascending_east", "ascending_east": "ascending_south",
             "ascending_south": "ascending_west", "ascending_west": "ascending_north", "north_east": "south_east", "south_east": "south_west",
             "south_west": "north_west", "north_west": "north_east"}
RAIL_MIRROR_X = {"ascending_east": "ascending_west", "ascending_west": "ascending_east", "north_east": "north_west", "north_west": "north_east",
                 "south_east": "south_west", "south_west": "south_east"}
RAIL_MIRROR_Z = {"ascending_north": "ascending_south", "ascending_south": "ascending_north", "north_east": "south_east", "south_east": "north_east",
                 "north_west": "south_west", "south_west": "north_west"}


class EditProblem(ValueError):
    pass


def split(state):
    found = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", state)
    props = dict(p.split("=", 1) for p in found.group(2).split(",")) if found.group(2) else {}
    return found.group(1), props


def join(name, props):
    return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")


def _short(state):
    return state.split("[", 1)[0].replace("minecraft:", "")


def _full(name):
    return name if ":" in name else "minecraft:" + name


def _map_facing(props, mapping):
    """Facing-style values, including combined ones such as "north_up" on crafters."""
    for key in FACING_PROPS + ("orientation",):
        if key in props:
            parts = props[key].split("_")
            props[key] = "_".join(mapping.get(p, p) for p in parts)


def _map_sides(props, mapping):
    """Properties named after a direction, as on walls, fences, panes and vines."""
    moved = {}
    for key in list(props):
        if key in TURN:
            moved[mapping.get(key, key)] = props.pop(key)
    props.update(moved)


def turn_state(state, turns):
    name, props = split(state)
    for _ in range(turns % 4):
        _map_facing(props, TURN)
        _map_sides(props, TURN)
        if "axis" in props:
            props["axis"] = {"x": "z", "z": "x"}.get(props["axis"], props["axis"])
        if "rotation" in props:
            props["rotation"] = str((int(props["rotation"]) + 4) % 16)
        if "shape" in props and props["shape"] in RAIL_TURN:
            props["shape"] = RAIL_TURN[props["shape"]]
    return join(name, props)


def mirror_state(state, axis):
    name, props = split(state)
    mapping = MIRROR_X if axis == "x" else MIRROR_Z
    _map_facing(props, mapping)
    _map_sides(props, mapping)
    if "rotation" in props:
        value = int(props["rotation"])
        props["rotation"] = str((16 - value) % 16 if axis == "x" else (8 - value) % 16)
    if "shape" in props:
        if props["shape"] in STAIR_SHAPE:
            props["shape"] = STAIR_SHAPE[props["shape"]]
        else:
            rails = RAIL_MIRROR_X if axis == "x" else RAIL_MIRROR_Z
            props["shape"] = rails.get(props["shape"], props["shape"])
    if "hinge" in props:
        props["hinge"] = LEFT_RIGHT[props["hinge"]]
    if "type" in props and props["type"] in LEFT_RIGHT:
        props["type"] = LEFT_RIGHT[props["type"]]
    return join(name, props)


def _recentred(blocks):
    """Keeps x and z near the middle, and the lowest block where it was."""
    if not blocks:
        raise EditProblem("That would remove every block.")
    mid_x = (min(b[0] for b in blocks) + max(b[0] for b in blocks)) // 2
    mid_z = (min(b[2] for b in blocks) + max(b[2] for b in blocks)) // 2
    return sorted([b[0] - mid_x, b[1], b[2] - mid_z, b[3]] for b in blocks)


def rotate(blocks, turns):
    turns = int(turns) % 4
    out = []
    for x, y, z, state in blocks:
        for _ in range(turns):
            x, z = -z, x
        out.append([x, y, z, turn_state(state, turns)])
    return _recentred(out)


def mirror(blocks, axis):
    if axis not in ("x", "z"):
        raise EditProblem("Mirror needs x or z.")
    return _recentred([[-x if axis == "x" else x, y, -z if axis == "z" else z, mirror_state(s, axis)] for x, y, z, s in blocks])


def _new_state(state, target, jar):
    """Keeps the properties both blocks have, such as a stair's facing, and fills the rest with defaults."""
    old_name, old = split(state)
    short = target.replace("minecraft:", "")
    if not target.startswith("minecraft:") or "minecraft:" + short not in jar.block_ids():
        raise EditProblem(f'"{short}" is not a vanilla block.')
    props = dict(jar.default_state(short))
    allowed = jar.property_names(short)
    same_shape = old_name.rsplit("_", 1)[-1] == short.rsplit("_", 1)[-1]  # stairs to stairs: every property still applies
    for key, value in old.items():
        if same_shape or key in allowed:
            props[key] = value
    if short.endswith("_leaves"):
        props = {"distance": old.get("distance", "7"), "persistent": old.get("persistent", "true"), "waterlogged": old.get("waterlogged", "false")}
    return join(target, props)


def swap(blocks, jar, source, target="", share=1.0, seed=1):
    """Replaces a block with another, or removes it if no target is given. share below 1 changes only some of them."""
    source = _full(source)
    rng = random.Random(seed)
    target = _full(target) if target else ""
    out = []
    changed = 0
    for x, y, z, state in blocks:
        if state.split("[", 1)[0] == source and (share >= 1 or rng.random() < share):
            changed += 1
            if target:
                out.append([x, y, z, _new_state(state, target, jar)])
        else:
            out.append([x, y, z, state])
    if not changed:
        raise EditProblem(f"No {_short(source).replace('_', ' ')} to change.")
    if not out:
        raise EditProblem("That would remove every block.")
    return out


def swap_family(blocks, jar, source, target):
    """Swaps a whole wood family, so oak stairs become spruce stairs and stripped oak logs become stripped spruce logs."""
    pattern = re.compile(rf"^(minecraft:(?:stripped_|potted_)?){re.escape(source)}(?=_)")
    known = jar.block_ids()
    out = []
    changed = 0
    for x, y, z, state in blocks:
        name = state.split("[", 1)[0]
        replacement = pattern.sub(rf"\g<1>{target}", name)
        if replacement != name and replacement in known:
            changed += 1
            state = _new_state(state, replacement, jar)
        out.append([x, y, z, state])
    if not changed:
        raise EditProblem(f"No {source.replace('_', ' ')} blocks to change.")
    return out


def trim(blocks, low=None, high=None):
    """Keeps what's inside a box. Any side left as null isn't limited."""
    low = low or [None, None, None]
    high = high or [None, None, None]

    def inside(block):
        return all((low[i] is None or block[i] >= low[i]) and (high[i] is None or block[i] <= high[i]) for i in range(3))
    return _recentred([b for b in blocks if inside(b)])


def set_ground(blocks, height):
    """This height becomes ground level. What's below it goes under the ground when placed."""
    return [[x, y - int(height), z, s] for x, y, z, s in blocks]


def fix_leaves(blocks):
    """Leaves near real wood fall naturally when the tree is cut. Leaves with no wood in reach stay permanent."""
    logs, leaves = set(), set()
    for x, y, z, state in blocks:
        name = state.split("[", 1)[0]
        if name.endswith("_leaves"):
            leaves.add((x, y, z))
        elif plan_tools.LOG_BLOCK.search(name.replace("minecraft:", "")):
            logs.add((x, y, z))
    distance = plan_tools.leaf_distances(logs, leaves)
    out = []
    for x, y, z, state in blocks:
        if (x, y, z) in leaves:
            name, props = split(state)
            if (x, y, z) in distance:
                props.update(distance=str(distance[(x, y, z)]), persistent="false")
            else:
                props.update(distance="7", persistent="true")
            props.setdefault("waterlogged", "false")
            state = join(name, props)
        out.append([x, y, z, state])
    return out


def remove_floating(blocks):
    """Removes anything not joined, face to face, to a block at or below ground level."""
    cells = {(b[0], b[1], b[2]) for b in blocks}
    bottom = min(max(min(b[1] for b in blocks), 0), 0) if min(b[1] for b in blocks) <= 0 else min(b[1] for b in blocks)
    seen = {c for c in cells if c[1] <= bottom}
    queue = deque(seen)
    while queue:
        p = queue.popleft()
        for a, b, c in SIDES:
            n = (p[0] + a, p[1] + b, p[2] + c)
            if n in cells and n not in seen:
                seen.add(n)
                queue.append(n)
    return [b for b in blocks if (b[0], b[1], b[2]) in seen]


def thin_leaves(blocks, share, seed=1):
    """Removes a share of the leaves, for a lighter crown."""
    if not 0 < share < 1:
        raise EditProblem("Choose a share between 0 and 1.")
    rng = random.Random(seed)
    return fix_leaves([b for b in blocks if not (b[3].split("[", 1)[0].endswith("_leaves") and rng.random() < share)])


def apply(blocks, jar, edit):
    """edit: {"op": name, ...}. Returns new blocks."""
    op = edit.get("op")
    try:
        if op == "swap":
            return swap(blocks, jar, edit["source"], edit.get("target", ""), float(edit.get("share", 1)), int(edit.get("seed", 1)))
        if op == "family":
            return swap_family(blocks, jar, edit["source"], edit["target"])
        if op == "rotate":
            return rotate(blocks, edit["turns"])
        if op == "mirror":
            return mirror(blocks, edit["axis"])
        if op == "trim":
            return trim(blocks, edit.get("low"), edit.get("high"))
        if op == "ground":
            return set_ground(blocks, edit["height"])
        if op == "fix_leaves":
            return fix_leaves(blocks)
        if op == "remove_floating":
            return remove_floating(blocks)
        if op == "thin_leaves":
            return thin_leaves(blocks, float(edit["share"]), int(edit.get("seed", 1)))
    except (KeyError, TypeError) as problem:
        raise EditProblem(f"That edit is missing something: {problem}") from problem
    raise EditProblem(f'Unknown edit "{op}".')


WORDS = {
    "swap": lambda e: (f"Swapped {_short(e['source'])} to {_short(e['target'])}" if e.get("target") else f"Removed {_short(e['source'])}")
    + (f" ({round(float(e['share']) * 100)}%)" if float(e.get("share", 1)) < 1 else ""),
    "family": lambda e: f"Swapped {e['source']} wood to {e['target']}",
    "rotate": lambda e: f"Turned {int(e['turns']) * 90}°",
    "mirror": lambda e: f"Mirrored on {e['axis']}",
    "trim": lambda e: "Trimmed",
    "ground": lambda e: f"Ground moved to height {e['height']}",
    "fix_leaves": lambda e: "Fixed leaves",
    "remove_floating": lambda e: "Removed floating bits",
    "thin_leaves": lambda e: f"Thinned leaves by {round(float(e['share']) * 100)}%",
}


def describe(edit):
    return WORDS[edit["op"]](edit)
