"""Trees from presets and sliders, with no AI and no cost.

A "spec" is a small set of numbers (height, trunk, branches, canopy, roots, blocks). The same spec and seed always make
the same tree, so a tree you like can be made again, or varied by changing only the seed. Ported from the author's earlier
prototype (mc-asset-forge), with block states written the way this tool's checks expect: logs with an axis, leaves with a
real distance to the nearest log, stairs with every property.
"""
import copy
import math
import random

import plan_tools

SHAPES = ("round", "cone", "flat", "droop")

DEFAULT = {
    "name": "Oak",
    "seed": 1,
    "height": 16,
    "trunk": {"base_radius": 1.4, "top_radius": 0.6, "height_ratio": 0.7, "lean": 0.1, "lean_dir_deg": 0, "flare": 0.6, "wobble": 0.25},
    "branches": {"count": 5, "start_ratio": 0.55, "length": 6, "length_jitter": 0.3, "upward_angle_deg": 30, "curve_up": 0.25},
    "canopy": {"shape": "round", "radius": 4.5, "thickness": 4, "density": 0.85, "droop_length": 3},
    "roots": {"count": 4, "length": 3, "depth": 1},
    "palette": {
        "trunk": {"minecraft:oak_log": 1.0}, "branch": {"minecraft:oak_log": 1.0}, "leaves": {"minecraft:oak_leaves": 1.0},
        "roots": {"minecraft:oak_log": 0.7, "minecraft:rooted_dirt": 0.3},
        "detail_stairs": "minecraft:oak_stairs",
    },
    "detailing": {"smooth_trunk": True, "amount": 0.8},
}

# Only what differs from DEFAULT. Each wood keeps its own stairs for the smoothing pass.
def _wood(wood, leaves=None, roots=None, stairs=None):
    return {"trunk": {f"minecraft:{wood}_log": 1}, "branch": {f"minecraft:{wood}_log": 1}, "leaves": {f"minecraft:{leaves or wood}_leaves": 1},
            "roots": roots or {f"minecraft:{wood}_log": 0.7, "minecraft:rooted_dirt": 0.3}, "detail_stairs": f"minecraft:{stairs or wood}_stairs"}


PRESETS = {
    "oak": {"name": "Classic oak", "height": 16, "trunk": {"base_radius": 1.3, "height_ratio": 0.65},
            "branches": {"count": 5, "start_ratio": 0.6, "length": 5, "upward_angle_deg": 35}, "canopy": {"shape": "round", "radius": 4, "thickness": 4}},
    "birch": {"name": "Birch", "height": 14, "trunk": {"base_radius": 0.8, "top_radius": 0.5, "height_ratio": 0.75, "flare": 0.2, "lean": 0.05},
              "branches": {"count": 4, "start_ratio": 0.6, "length": 4, "upward_angle_deg": 45}, "canopy": {"shape": "round", "radius": 3, "thickness": 4, "density": 0.8},
              "roots": {"count": 2, "length": 1}, "palette": _wood("birch")},
    "dark_oak": {"name": "Dark oak", "height": 14, "trunk": {"base_radius": 2.0, "top_radius": 1.0, "height_ratio": 0.55, "flare": 0.7},
                 "branches": {"count": 6, "start_ratio": 0.5, "length": 5, "upward_angle_deg": 25}, "canopy": {"shape": "flat", "radius": 5.5, "thickness": 4, "density": 0.9},
                 "roots": {"count": 5, "length": 3}, "palette": _wood("dark_oak")},
    "spruce": {"name": "Tall spruce", "height": 24, "trunk": {"base_radius": 1.0, "top_radius": 0.5, "height_ratio": 0.95, "lean": 0.02, "flare": 0.4},
               "branches": {"count": 0, "start_ratio": 0.3}, "canopy": {"shape": "cone", "radius": 5, "density": 0.9}, "roots": {"count": 3, "length": 2},
               "palette": _wood("spruce", roots={"minecraft:spruce_log": 0.6, "minecraft:podzol": 0.4})},
    "jungle": {"name": "Jungle", "height": 30, "trunk": {"base_radius": 1.6, "top_radius": 0.8, "height_ratio": 0.8, "flare": 1.0},
               "branches": {"count": 6, "start_ratio": 0.7, "length": 6, "upward_angle_deg": 30}, "canopy": {"shape": "round", "radius": 6, "thickness": 5, "density": 0.85},
               "roots": {"count": 5, "length": 3}, "palette": _wood("jungle")},
    "acacia": {"name": "Acacia", "height": 12, "trunk": {"base_radius": 1.0, "top_radius": 0.8, "height_ratio": 0.7, "lean": 0.35, "lean_dir_deg": 30},
               "branches": {"count": 3, "start_ratio": 0.6, "length": 5, "upward_angle_deg": 20}, "canopy": {"shape": "flat", "radius": 4.5, "thickness": 2, "density": 0.9},
               "roots": {"count": 2, "length": 2}, "palette": _wood("acacia")},
    "cherry": {"name": "Cherry blossom", "height": 13, "trunk": {"base_radius": 1.1, "top_radius": 0.5, "height_ratio": 0.7, "lean": 0.2, "lean_dir_deg": 120},
               "branches": {"count": 6, "start_ratio": 0.5, "length": 6, "upward_angle_deg": 30}, "canopy": {"shape": "round", "radius": 3.5, "thickness": 3, "density": 0.8},
               "palette": _wood("cherry")},
    "willow": {"name": "Weeping willow", "height": 15, "trunk": {"base_radius": 1.6, "top_radius": 0.8, "height_ratio": 0.6, "lean": 0.12, "lean_dir_deg": 200},
               "branches": {"count": 6, "start_ratio": 0.55, "length": 6, "upward_angle_deg": 20, "curve_up": 0.1},
               "canopy": {"shape": "droop", "radius": 4, "thickness": 3, "density": 0.75, "droop_length": 6},
               "palette": {"trunk": {"minecraft:mangrove_log": 0.7, "minecraft:dark_oak_log": 0.3}, "branch": {"minecraft:mangrove_log": 1},
                           "leaves": {"minecraft:azalea_leaves": 0.6, "minecraft:mangrove_leaves": 0.4},
                           "roots": {"minecraft:mangrove_roots": 0.6, "minecraft:muddy_mangrove_roots": 0.4}, "detail_stairs": "minecraft:mangrove_stairs"}},
    "petrified": {"name": "Petrified oak", "height": 14, "trunk": {"base_radius": 1.8, "top_radius": 0.8, "height_ratio": 0.75, "lean": 0.18, "lean_dir_deg": 60, "wobble": 0.5},
                  "branches": {"count": 4, "start_ratio": 0.55, "length": 5, "upward_angle_deg": 25}, "canopy": {"shape": "flat", "radius": 3.5, "thickness": 3, "density": 0.55},
                  "roots": {"count": 5, "length": 4, "depth": 2}, "detailing": {"amount": 1.0},
                  "palette": {"trunk": {"minecraft:stone": 0.4, "minecraft:cobblestone": 0.3, "minecraft:mossy_cobblestone": 0.2, "minecraft:andesite": 0.1},
                              "branch": {"minecraft:stone": 0.6, "minecraft:cobblestone": 0.4}, "leaves": {"minecraft:moss_block": 0.5, "minecraft:azalea_leaves": 0.5},
                              "roots": {"minecraft:mossy_cobblestone": 0.5, "minecraft:cobblestone": 0.5}, "detail_stairs": "minecraft:mossy_cobblestone_stairs"}},
}

# What the sliders may do, so no setting can make a tree too big to build: (path, low, high)
LIMITS = {
    ("height",): (4, 60), ("seed",): (0, 9999999),
    ("trunk", "base_radius"): (0.4, 4), ("trunk", "top_radius"): (0.3, 3), ("trunk", "height_ratio"): (0.3, 1), ("trunk", "lean"): (0, 0.8),
    ("trunk", "lean_dir_deg"): (0, 360), ("trunk", "flare"): (0, 2), ("trunk", "wobble"): (0, 1),
    ("branches", "count"): (0, 12), ("branches", "start_ratio"): (0.1, 0.95), ("branches", "length"): (1, 14), ("branches", "length_jitter"): (0, 0.8),
    ("branches", "upward_angle_deg"): (0, 80), ("branches", "curve_up"): (0, 1),
    ("canopy", "radius"): (1.5, 12), ("canopy", "thickness"): (1, 10), ("canopy", "density"): (0.2, 1), ("canopy", "droop_length"): (1, 12),
    ("roots", "count"): (0, 8), ("roots", "length"): (1, 8), ("roots", "depth"): (0, 4),
    ("detailing", "amount"): (0, 1),
}
INT_FIELDS = ("height", "seed", "count", "depth", "thickness", "droop_length", "lean_dir_deg")
AXIS_BLOCKS = ("_log", "_stem", "_wood", "_hyphae", "basalt", "_pillar", "bone_block", "hay_block")
DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


class PresetProblem(ValueError):
    pass


def _merge(base, over):
    out = copy.deepcopy(base)
    for key, value in (over or {}).items():
        if isinstance(value, dict) and isinstance(out.get(key), dict) and key != "palette":
            out[key] = _merge(out[key], value)
        elif key == "palette" and isinstance(value, dict):
            out["palette"].update(copy.deepcopy(value))
        else:
            out[key] = copy.deepcopy(value)
    return out


def make_spec(preset="oak", changes=None):
    """A full spec: the defaults, then the preset, then the changes, with every number kept inside its limits."""
    if preset not in PRESETS:
        raise PresetProblem("Unknown preset")
    spec = _merge(_merge(DEFAULT, PRESETS[preset]), changes or {})
    for path, (low, high) in LIMITS.items():
        holder = spec
        for part in path[:-1]:
            holder = holder[part]
        value = holder.get(path[-1])
        try:
            value = float(value)
        except (TypeError, ValueError) as problem:
            raise PresetProblem(f"{'.'.join(path)} must be a number") from problem
        value = min(max(value, low), high)
        holder[path[-1]] = int(round(value)) if path[-1] in INT_FIELDS else value
    if spec["canopy"]["shape"] not in SHAPES:
        raise PresetProblem("canopy shape must be round, cone, flat or droop")
    spec["trunk"]["height_ratio"] = float(spec["trunk"]["height_ratio"])
    return spec


def listing():
    return [{"id": key, "name": value["name"], "spec": make_spec(key)} for key, value in PRESETS.items()]


# ---------------------------------------------------------------- the generator
class _Grid:
    def __init__(self):
        self.blocks, self.parts = {}, {}

    def set(self, pos, state, part, overwrite=True):
        pos = (int(pos[0]), int(pos[1]), int(pos[2]))
        if overwrite or pos not in self.blocks:
            self.blocks[pos] = state
            self.parts[pos] = part


def _pick(rng, weights, axis=None):
    items = [(b, float(w)) for b, w in weights.items() if float(w) > 0]
    total = sum(w for _, w in items)
    roll, acc, chosen = rng.random() * total, 0.0, items[-1][0]
    for block, weight in items:
        acc += weight
        if roll <= acc:
            chosen = block
            break
    if axis and chosen.endswith(AXIS_BLOCKS):
        return f"{chosen}[axis={axis}]"
    return chosen


def _disc(grid, cx, y, cz, r, block, part):
    r = max(r, 0.5)
    for x in range(math.floor(cx - r), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r), math.ceil(cz + r) + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.25:
                grid.set((x, y, z), block(), part)


def _trunk(grid, spec, rng):
    t = spec["trunk"]
    height = max(2, round(spec["height"] * t["height_ratio"]))
    angle = math.radians(t["lean_dir_deg"])
    dx, dz = math.cos(angle) * t["lean"], math.sin(angle) * t["lean"]
    path = []
    for y in range(height):
        f = y / max(1, height - 1)
        r = t["base_radius"] + (t["top_radius"] - t["base_radius"]) * f
        r *= 1 + t["flare"] * (1 - f) ** 6
        r *= 1 + t["wobble"] * (rng.random() - 0.5)
        cx, cz = dx * y * f, dz * y * f
        path.append((cx, y, cz, r))
        _disc(grid, cx, y, cz, r, lambda: _pick(rng, spec["palette"]["trunk"], "y"), "trunk")
    return path


def _axis(dx, dy, dz):
    return max((abs(dx), "x"), (abs(dy), "y"), (abs(dz), "z"))[1]


def _branches(grid, spec, rng, path):
    b = spec["branches"]
    top = path[-1]
    tips = [(top[0], top[1] + 1, top[2])]
    count = int(b["count"])
    if count <= 0:
        return tips
    start = int(len(path) * b["start_ratio"])
    base = rng.random() * math.tau
    for i in range(count):
        az = base + i * (math.tau / count) * (1 + 0.15 * (rng.random() - 0.5)) + i * 2.4
        index = start + int((len(path) - 1 - start) * (i + rng.random()) / count)
        cx, cy, cz, _ = path[min(index, len(path) - 1)]
        length = b["length"] * (1 + b["length_jitter"] * (rng.random() * 2 - 1))
        lift = math.radians(b["upward_angle_deg"])
        x, y, z = cx, float(cy), cz
        steps = max(2, int(length * 1.5))
        for s in range(steps):
            e = lift + b["curve_up"] * (s / steps) * (math.pi / 2 - lift)
            step = length / steps
            dx, dz, dy = math.cos(az) * math.cos(e) * step, math.sin(az) * math.cos(e) * step, math.sin(e) * step
            x, y, z = x + dx, y + dy, z + dz
            grid.set((round(x), round(y), round(z)), _pick(rng, spec["palette"]["branch"], _axis(dx, dy, dz)), "branch", overwrite=False)
        tips.append((x, y, z))
    return tips


def _leaf(grid, spec, rng, pos):
    grid.set(pos, _pick(rng, spec["palette"]["leaves"]), "leaves", overwrite=False)


def _canopy(grid, spec, rng, tips):
    c = spec["canopy"]
    if c["shape"] == "cone":
        return _cone(grid, spec, rng, tips)
    for tx, ty, tz in tips:
        radius = c["radius"] * (0.75 + 0.35 * rng.random())
        half = c["thickness"] / 2 * (0.8 + 0.3 * rng.random())
        if c["shape"] == "flat":
            half *= 0.6
        for y in range(math.floor(ty - half), math.ceil(ty + half) + 1):
            rel = y - ty
            if c["shape"] == "flat":
                here = radius * math.sqrt(max(0.0, 1 - (rel / half) ** 2)) if rel <= 0 else radius * (1 - rel / (half + 1))
            else:
                here = radius * math.sqrt(max(0.0, 1 - (rel / (half + 0.5)) ** 2))
            for x in range(math.floor(tx - here), math.ceil(tx + here) + 1):
                for z in range(math.floor(tz - here), math.ceil(tz + here) + 1):
                    d = math.hypot(x - tx, z - tz)
                    if d > here:
                        continue
                    edge = d / max(here, 0.01)
                    if rng.random() < c["density"] * (1.0 if edge < 0.7 else 1.25 - edge * 0.6):
                        _leaf(grid, spec, rng, (x, y, z))
        if c["shape"] == "droop":
            _droop(grid, spec, rng, tx, ty, tz, radius, half)


def _cone(grid, spec, rng, tips):
    c = spec["canopy"]
    tx, ty, tz = tips[0]
    top = round(ty) + 1
    bottom = round(spec["height"] * spec["branches"]["start_ratio"] * 0.8)
    span = max(1, top - bottom)
    for y in range(bottom, top + 1):
        f = (y - bottom) / span
        r = c["radius"] * (1 - f) ** 1.1
        if (top - y) % 3 == 2:
            r *= 0.6
        r = max(r, 1.5)
        if y == top:
            _leaf(grid, spec, rng, (round(tx), y + 1, round(tz)))
        for x in range(math.floor(tx - r), math.ceil(tx + r) + 1):
            for z in range(math.floor(tz - r), math.ceil(tz + r) + 1):
                d = math.hypot(x - tx, z - tz)
                if d <= r and rng.random() < c["density"] * (1.0 if d < r * 0.75 else 0.8):
                    _leaf(grid, spec, rng, (x, y, z))


def _droop(grid, spec, rng, tx, ty, tz, radius, half):
    n = int(math.tau * radius * 0.8)
    for i in range(n):
        a = i / n * math.tau
        x, z = round(tx + math.cos(a) * radius * 0.9), round(tz + math.sin(a) * radius * 0.9)
        if rng.random() < 0.35:
            continue
        for k in range(int(spec["canopy"]["droop_length"] * (0.3 + rng.random()))):
            _leaf(grid, spec, rng, (x, round(ty - half * 0.3) - k, z))


def _roots(grid, spec, rng, path):
    r = spec["roots"]
    count = int(r["count"])
    if count <= 0:
        return
    cx, _, cz, base = path[0]
    start = rng.random() * math.tau
    for i in range(count):
        az = start + i * math.tau / count + 0.4 * (rng.random() - 0.5)
        length = r["length"] * (0.7 + 0.6 * rng.random())
        steps = max(2, int((base + length) * 2))
        for s in range(steps):
            f = s / steps
            dist = base * 0.6 + f * length
            x, z = cx + math.cos(az) * dist, cz + math.sin(az) * dist
            y = 0 if f < 0.4 else -round((f - 0.4) / 0.6 * r["depth"])
            axis = "x" if abs(math.cos(az)) > abs(math.sin(az)) else "z"
            grid.set((round(x), y, round(z)), _pick(rng, spec["palette"]["roots"], axis), "roots", overwrite=False)


def _stairs(grid, spec, rng):
    """Smooths ledges and overhangs of the trunk with stairs. Stair direction is a fixed rule from the neighbours."""
    stairs = spec["palette"].get("detail_stairs")
    if not stairs or not spec["detailing"].get("smooth_trunk", True):
        return
    amount = spec["detailing"]["amount"]
    wood = {p for p, part in grid.parts.items() if part in ("trunk", "roots")}
    changes = {}

    def side(x, y, z, dy):
        hits = [name for name, (dx, dz) in DIRS.items() if (x + dx, y + dy, z + dz) in wood and (x + dx, y, z + dz) in wood]
        return hits[0] if len(hits) == 1 else None
    for x, y, z in sorted(wood):
        if (x, y + 1, z) not in grid.blocks:
            facing = side(x, y, z, 1)
            if facing and rng.random() < amount:
                changes[(x, y, z)] = f"{stairs}[facing={facing},half=bottom,shape=straight,waterlogged=false]"
                continue
        if y > 0 and (x, y - 1, z) not in grid.blocks:
            facing = side(x, y, z, -1)
            if facing and rng.random() < amount:
                changes[(x, y, z)] = f"{stairs}[facing={facing},half=top,shape=straight,waterlogged=false]"
    for pos, state in changes.items():
        grid.blocks[pos] = state


def _finish(grid):
    """Block states the game and this tool's checks expect: leaves get their real distance to a log."""
    logs = {p for p, s in grid.blocks.items() if s.split("[")[0].endswith(("_log", "_wood", "_stem", "_hyphae"))}
    leaves = {p for p, s in grid.blocks.items() if s.split("[")[0].endswith("_leaves")}
    distance = plan_tools.leaf_distances(logs, leaves)
    out = []
    for (x, y, z), state in grid.blocks.items():
        block = state.split("[")[0]
        if block.endswith("_leaves"):
            near = distance.get((x, y, z))
            state = f"{block}[distance={near},persistent=false,waterlogged=false]" if near else f"{block}[distance=7,persistent=true,waterlogged=false]"
        out.append([x, y, z, state])
    return sorted(out)


def generate(spec):
    """The blocks of a tree: [[x, y, z, "minecraft:oak_log[axis=y]"], ...]. Same spec, same tree."""
    rng = random.Random(spec["seed"])
    grid = _Grid()
    path = _trunk(grid, spec, rng)
    tips = _branches(grid, spec, rng, path)
    _canopy(grid, spec, rng, tips)
    _roots(grid, spec, rng, path)
    _stairs(grid, spec, rng)
    return _finish(grid)
