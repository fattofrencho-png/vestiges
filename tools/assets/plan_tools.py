"""Turns an AI's build plan into blocks. The plan is plain data: the AI never writes code that runs here.

A plan is {"seed": int, "ops": [{"tool": name, "args_json": "<json>"}, ...]}. Ops run in order, and later ops
overwrite earlier ones, so "carve" can open up what was built. x east, y up, z south; y=0 is the first block
above the ground.
"""
import json
import math
import random
import re
from collections import deque

import tree_gen

MAX_OPS = 300
MAX_BLOCKS = 80000
MAX_XZ = 128
MIN_Y, MAX_Y = -16, 192
SIDES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
AXIS_BLOCK = re.compile(r"_(log|wood|stem|hyphae)$")
LOG_BLOCK = re.compile(r"(^|_)(log|wood|stem|hyphae)$")

TOOL_HELP = """\
Every "blocks" value is a block name such as "oak_log", or a weighted mix such as [["oak_leaves", 0.7], ["moss_block", 0.3]].
- organic_tree {"at":[x,z],"trunk_height":int,"trunk_radius":number,"branches":int,"branch_length":number,"leaf_radius":number,"log":"oak_log","wood":"oak_wood","leaves":[["oak_leaves",1]], optional "lean":0-0.4,"hollow":int,"roots":int,"vine_chance":0-0.03,"moss_chance":0-0.3,"seed":int}: a natural branching tree with roots. It grows far past its trunk height and branch length, so use it only for big trees; build small and normal trees from the other tools.
- column {"x":int,"z":int,"from_y":int,"to_y":int,"radius":number,"blocks":...}: a vertical pillar.
- line {"from":[x,y,z],"to":[x,y,z],"radius":number,"blocks":...}: a branch or root. Logs turn along the line.
- disc {"center":[x,y,z],"radius":number,"blocks":...}: a flat circle.
- blob {"center":[x,y,z],"radii":[rx,ry,rz],"blocks":...,"density":0-1}: a rounded mass. A density under 1 removes blocks near the edge, so it looks natural.
- scatter {"on":["block names"],"blocks":...,"chance":0-1,"from_y":int,"to_y":int}: places blocks on top of matching blocks, such as moss_carpet on logs.
- hanging {"from":["block names"],"blocks":...,"chance":0-1,"min_length":int,"max_length":int}: hangs blocks. "vine" attaches to the side of the block; anything else hangs below it. "leaves" and "logs" match every kind.
- set_block {"pos":[x,y,z],"block":"name"}: one block.
- carve {"center":[x,y,z],"radii":[rx,ry,rz]}: removes a rounded space.
"""


def leaf_distances(logs, leaves):
    """Vanilla's rule: a leaf's distance is the shortest walk to a log, through leaves, face to face, up to 6."""
    distance = {}
    queue = deque()
    for p in leaves:
        if any((p[0] + a, p[1] + b, p[2] + c) in logs for a, b, c in SIDES):
            distance[p] = 1
            queue.append(p)
    while queue:
        p = queue.popleft()
        if distance[p] >= 6:
            continue
        for a, b, c in SIDES:
            n = (p[0] + a, p[1] + b, p[2] + c)
            if n in leaves and n not in distance:
                distance[n] = distance[p] + 1
                queue.append(n)
    return distance


class PlanError(ValueError):
    """Something wrong with a plan, in words the AI can act on when it tries again."""


def _number(args, key, default=None, low=None, high=None):
    value = args.get(key, default)
    if value is None or isinstance(value, bool) or not isinstance(value, (int, float)):
        raise PlanError(f'"{key}" must be a number')
    if (low is not None and value < low) or (high is not None and value > high):
        raise PlanError(f'"{key}" must be between {low} and {high}')
    return value


def _point(args, key):
    value = args.get(key)
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, (int, float)) for v in value):
        raise PlanError(f'"{key}" must be [x, y, z]')
    return value


class Builder:
    def __init__(self, jar, seed):
        self.jar = jar
        self.known = jar.block_ids()
        self.rng = random.Random(seed)
        self.blocks = {}  # (x, y, z) -> "name" or "name[props]", without the namespace

    def name(self, text):
        if not isinstance(text, str):
            raise PlanError(f"A block name must be text, not {text!r}")
        clean = text.replace("minecraft:", "").strip()
        base = clean.split("[", 1)[0]
        if "minecraft:" + base not in self.known:
            raise PlanError(f'"{base}" is not a block in this version of Minecraft')
        if not re.fullmatch(r"[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?", clean):
            raise PlanError(f'"{text}" is not written like a block')
        return clean

    def picker(self, spec):
        if isinstance(spec, str):
            block = self.name(spec)
            return lambda: block
        if not isinstance(spec, list) or not spec:
            raise PlanError('"blocks" must be a block name or a list of [name, weight]')
        names, weights = [], []
        for entry in spec:
            if not isinstance(entry, list) or len(entry) != 2 or not isinstance(entry[1], (int, float)) or entry[1] <= 0:
                raise PlanError('Each mix entry must be ["block", positive weight]')
            names.append(self.name(entry[0]))
            weights.append(entry[1])
        return lambda: self.rng.choices(names, weights)[0]

    def put(self, pos, block):
        x, y, z = (round(v) for v in pos)
        if abs(x) > MAX_XZ or abs(z) > MAX_XZ or not MIN_Y <= y <= MAX_Y:
            raise PlanError(f"Block at {x},{y},{z} is outside the allowed space (x and z up to {MAX_XZ}, y {MIN_Y} to {MAX_Y})")
        self.blocks[(x, y, z)] = block
        if len(self.blocks) > MAX_BLOCKS:
            raise PlanError(f"The plan makes more than {MAX_BLOCKS} blocks")

    def ball(self, centre, radius, pick, axis=None):
        reach = math.ceil(radius)
        cx, cy, cz = (round(v) for v in centre)
        for x in range(-reach, reach + 1):
            for y in range(-reach, reach + 1):
                for z in range(-reach, reach + 1):
                    if x * x + y * y + z * z <= radius * radius + 0.25:
                        self.put((cx + x, cy + y, cz + z), self.oriented(pick(), axis))

    @staticmethod
    def oriented(block, axis):
        if axis and "[" not in block and AXIS_BLOCK.search(block):
            return f"{block}[axis={axis}]"
        return block

    # ---- tools ----
    def column(self, args):
        x, z = _number(args, "x"), _number(args, "z")
        low, high = sorted((int(_number(args, "from_y")), int(_number(args, "to_y"))))
        radius = _number(args, "radius", 0, 0, 24)
        pick = self.picker(args.get("blocks"))
        reach = math.ceil(radius)
        for y in range(low, high + 1):
            for a in range(-reach, reach + 1):
                for b in range(-reach, reach + 1):
                    if a * a + b * b <= radius * radius + 0.25:
                        self.put((x + a, y, z + b), self.oriented(pick(), "y"))

    def line(self, args):
        pos = [round(v) for v in _point(args, "from")]
        goal = [round(v) for v in _point(args, "to")]
        radius = _number(args, "radius", 0, 0, 12)
        pick = self.picker(args.get("blocks"))
        names = "xyz"
        self.ball(pos, radius, pick, names[max(range(3), key=lambda i: abs(goal[i] - pos[i]))])
        while pos != goal:  # one axis at a time, so every step shares a face with the last
            axis = max(range(3), key=lambda i: abs(goal[i] - pos[i]))
            pos[axis] += 1 if goal[axis] > pos[axis] else -1
            self.ball(pos, radius, pick, names[axis])

    def disc(self, args):
        cx, cy, cz = _point(args, "center")
        radius = _number(args, "radius", None, 0, 64)
        pick = self.picker(args.get("blocks"))
        reach = math.ceil(radius)
        for a in range(-reach, reach + 1):
            for b in range(-reach, reach + 1):
                if a * a + b * b <= radius * radius + 0.25:
                    self.put((cx + a, cy, cz + b), pick())

    def _rounded(self, args):
        cx, cy, cz = _point(args, "center")
        rx, ry, rz = _point(args, "radii")
        if min(rx, ry, rz) <= 0 or max(rx, ry, rz) > 64:
            raise PlanError('"radii" must be above 0 and at most 64')
        for x in range(-math.ceil(rx), math.ceil(rx) + 1):
            for y in range(-math.ceil(ry), math.ceil(ry) + 1):
                for z in range(-math.ceil(rz), math.ceil(rz) + 1):
                    d = (x / rx) ** 2 + (y / ry) ** 2 + (z / rz) ** 2
                    if d <= 1:
                        yield (round(cx) + x, round(cy) + y, round(cz) + z), d

    def blob(self, args):
        pick = self.picker(args.get("blocks"))
        density = _number(args, "density", 1, 0, 1)
        for pos, d in self._rounded(args):
            if d <= 0.6 or self.rng.random() <= density:
                self.put(pos, pick())

    def carve(self, args):
        for pos, _ in self._rounded(args):
            self.blocks.pop(pos, None)

    def _matching(self, names):
        if not isinstance(names, list) or not names:
            raise PlanError('Give a list of block names, or ["leaves"] or ["logs"]')
        wanted = {n for n in names if n not in ("leaves", "logs")}
        for n in wanted:
            self.name(n)
        wanted = {n.replace("minecraft:", "") for n in wanted}

        def matches(block):
            base = block.split("[", 1)[0]
            return (base in wanted or ("leaves" in names and base.endswith("_leaves"))
                    or ("logs" in names and bool(LOG_BLOCK.search(base))))
        return matches

    def scatter(self, args):
        matches = self._matching(args.get("on"))
        pick = self.picker(args.get("blocks"))
        chance = _number(args, "chance", None, 0, 1)
        low, high = args.get("from_y", MIN_Y), args.get("to_y", MAX_Y)
        for (x, y, z), block in sorted(self.blocks.items()):
            above = (x, y + 1, z)
            if low <= above[1] <= high and matches(block) and above not in self.blocks and self.rng.random() < chance:
                self.put(above, pick())

    def hanging(self, args):
        matches = self._matching(args.get("from"))
        pick = self.picker(args.get("blocks", "vine"))
        chance = _number(args, "chance", None, 0, 1)
        low = int(_number(args, "min_length", 1, 1, 48))
        high = int(_number(args, "max_length", 6, low, 48))
        sides = (((0, 0, -1), "south"), ((0, 0, 1), "north"), ((1, 0, 0), "west"), ((-1, 0, 0), "east"))
        for (x, y, z), block in sorted(self.blocks.items()):
            if not matches(block) or self.rng.random() >= chance:
                continue
            choice = pick()
            if choice.split("[", 1)[0] == "vine":
                (a, _, c), face = self.rng.choice(sides)
                start = (x + a, y, z + c)
                flags = ",".join(f"{d}={'true' if d == face else 'false'}" for d in ("east", "north", "south"))
                state = f"vine[{flags},up=false,west={'true' if face == 'west' else 'false'}]"
            else:
                start, state = (x, y - 1, z), choice
            for drop in range(self.rng.randint(low, high)):
                spot = (start[0], start[1] - drop, start[2])
                if spot[1] < 0 or spot in self.blocks:
                    break
                self.put(spot, state)

    def set_block(self, args):
        self.put(_point(args, "pos"), self.name(args.get("block")))

    def organic_tree(self, args):
        at = args.get("at", [0, 0])
        if not isinstance(at, list) or len(at) != 2:
            raise PlanError('"at" must be [x, z]')
        leaves = args.get("leaves") or [["oak_leaves", 1]]
        if isinstance(leaves, str):
            leaves = [[leaves, 1]]
        total = 0
        for entry in leaves:
            self.name(entry[0])
            total += entry[1]
        params = {
            "log": self.name(args.get("log", "oak_log")),
            "wood": self.name(args.get("wood", "oak_wood")),
            "leaves": [[self.name(e[0]), e[1] / total] for e in leaves],
            "trunk_height": int(_number(args, "trunk_height", 24, 3, 150)),
            "trunk_radius": _number(args, "trunk_radius", 3, 0, 20),
            "trunk_top_radius": _number(args, "trunk_top_radius", 1.0, 0, 8),
            "taper": 0.7,
            "flare": _number(args, "flare", min(args.get("trunk_radius", 3), 4), 0, 12),
            "buttress_lobes": 5,
            "branches": int(_number(args, "branches", 10, 1, 40)),
            "branch_from": _number(args, "branch_from", 0.45, 0.1, 0.95),
            "branch_length": _number(args, "branch_length", 12, 2, 60),
            "branch_radius": _number(args, "branch_radius", 1.5, 0.5, 6),
            "branch_rise": _number(args, "branch_rise", 0.14, -0.2, 0.8),
            "branch_levels": int(_number(args, "branch_levels", 3, 1, 4)),
            "fork_chance": _number(args, "fork_chance", 0.28, 0, 0.5),
            "leaf_radius": _number(args, "leaf_radius", 4.5, 1, 10),
            "leaf_flatness": _number(args, "leaf_flatness", 0.72, 0.3, 1.2),
            "leaf_lobes": int(_number(args, "leaf_lobes", 3, 1, 6)),
            "lean": _number(args, "lean", 0, 0, 0.5),
            "hollow": int(_number(args, "hollow", 0, 0, 40)),
            "roots": int(_number(args, "roots", 6, 0, 16)),
            "root_length": _number(args, "root_length", 10, 2, 40),
            "root_radius": _number(args, "root_radius", 1.5, 0.5, 5),
            "vine_chance": _number(args, "vine_chance", 0.008, 0, 0.1),
            "vine_length": 8,
            "moss_chance": _number(args, "moss_chance", 0.1, 0, 1),
        }
        if params["hollow"] >= params["trunk_height"]:
            raise PlanError('"hollow" must be lower than "trunk_height"')
        made = tree_gen.generate(params, int(_number(args, "seed", self.rng.randrange(1 << 30))))
        for x, y, z, state in made["blocks"]:
            self.put((x + at[0], y, z + at[1]), state.replace("minecraft:", ""))

    TOOLS = ("organic_tree", "column", "line", "disc", "blob", "scatter", "hanging", "set_block", "carve")

    def finish(self):
        """Works out what depends on neighbours: leaf distance, wall and fence joins, and missing properties."""
        solid = {}
        out = {}
        for pos, block in self.blocks.items():
            base = block.split("[", 1)[0]
            if base == "air":
                continue
            solid[pos] = base
            out[pos] = block
        logs = {p for p, b in solid.items() if LOG_BLOCK.search(b)}
        leaves = {p for p, b in solid.items() if b.endswith("_leaves")}

        distance = leaf_distances(logs, leaves)

        result = []
        for pos, block in out.items():
            base = solid[pos]
            if pos in leaves:
                if pos in distance:  # near real wood: behaves like any tree
                    state = f"{base}[distance={distance[pos]},persistent=false,waterlogged=false]"
                else:  # no wood in reach, for example a trunk of walls: permanent, or it would decay
                    state = f"{base}[distance=7,persistent=true,waterlogged=false]"
            elif base.endswith("_wall"):
                state = self._wall(base, pos, solid)
            elif base.endswith("_fence"):
                state = self._fence(base, pos, solid)
            elif "[" in block:
                state = block
            else:
                props = self.jar.default_state(base)
                state = base + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")
            result.append([pos[0], pos[1], pos[2], "minecraft:" + state])
        result.sort()
        return {"blocks": result}

    def _joins(self, base, pos, solid):
        for name, (a, c) in (("east", (1, 0)), ("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0))):
            other = solid.get((pos[0] + a, pos[1], pos[2] + c))
            yield name, other is not None and self._connects(base, other)

    def _connects(self, base, other):
        if other.endswith(("_wall", "_fence", "_fence_gate")):
            return base.endswith("_wall") == other.endswith("_wall") or other.endswith("_fence_gate")
        return other.endswith("_leaves") or self.jar.is_full_cube(other)

    def _wall(self, base, pos, solid):
        joins = dict(self._joins(base, pos, solid))
        count = sum(joins.values())
        straight = count == 2 and (joins["east"] == joins["west"])
        above = solid.get((pos[0], pos[1] + 1, pos[2]))
        up = "false" if straight and not (above and above.endswith("_wall")) else "true"
        sides = ",".join(f"{k}={'low' if joins[k] else 'none'}" for k in ("east", "north", "south"))
        return f"{base}[{sides},up={up},waterlogged=false,west={'low' if joins['west'] else 'none'}]"

    def _fence(self, base, pos, solid):
        joins = dict(self._joins(base, pos, solid))
        return (f"{base}[east={str(joins['east']).lower()},north={str(joins['north']).lower()},"
                f"south={str(joins['south']).lower()},waterlogged=false,west={str(joins['west']).lower()}]")


def run_plan(plan, jar):
    """Returns {"blocks": [...]} or raises PlanError."""
    if not isinstance(plan, dict) or not isinstance(plan.get("ops"), list) or not plan["ops"]:
        raise PlanError('A plan needs a non-empty "ops" list')
    if len(plan["ops"]) > MAX_OPS:
        raise PlanError(f"A plan can have at most {MAX_OPS} ops")
    builder = Builder(jar, plan.get("seed", 1))
    for number, op in enumerate(plan["ops"], 1):
        tool = op.get("tool") if isinstance(op, dict) else None
        if tool not in Builder.TOOLS:
            raise PlanError(f'Op {number}: "{tool}" is not a tool')
        raw = op.get("args_json", "{}")
        try:
            args = json.loads(raw) if isinstance(raw, str) else raw
        except json.JSONDecodeError as problem:
            raise PlanError(f"Op {number} ({tool}): args_json is not valid JSON: {problem}") from problem
        if not isinstance(args, dict):
            raise PlanError(f"Op {number} ({tool}): args_json must be an object")
        try:
            getattr(builder, tool)(args)
        except PlanError as problem:
            raise PlanError(f"Op {number} ({tool}): {problem}") from problem
        except (TypeError, KeyError, IndexError, ValueError) as problem:
            raise PlanError(f"Op {number} ({tool}): bad arguments ({problem})") from problem
    tree = builder.finish()
    if not tree["blocks"]:
        raise PlanError("The plan made no blocks")
    return tree
