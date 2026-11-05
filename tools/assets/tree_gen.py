"""Makes a tree from a recipe and a seed. The same recipe and seed always give the same tree.

Coordinates: x east, y up, z south. The trunk centre is at x=0, z=0 and y=0 is the first block above the ground,
so roots go below zero. Output: {"blocks": [[x, y, z, "minecraft:oak_log[axis=y]"], ...]}.
"""
import math
import random
from collections import deque

SIDES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
LEAF_REACH = 6  # vanilla leaves further than this from a log decay


def _rotate_towards(direction, target, amount):
    mixed = [d + (t - d) * amount for d, t in zip(direction, target)]
    size = math.sqrt(sum(m * m for m in mixed)) or 1.0
    return [m / size for m in mixed]


class _Tree:
    def __init__(self, params, seed):
        self.p = params
        self.rng = random.Random(seed)
        self.wood = {}  # (x, y, z) -> "x" | "y" | "z" for logs, "w" for all-bark wood
        self.leaves = {}  # (x, y, z) -> leaf block id
        self.tips = []
        self.centres = {}

    def put_wood(self, pos, kind):
        self.wood[pos] = kind

    def stamp(self, centre, radius, kind):
        """A ball of wood. Thin balls still touch face to face, because line() walks one axis at a time."""
        cx, cy, cz = centre
        reach = int(math.ceil(radius))
        for x in range(-reach, reach + 1):
            for y in range(-reach, reach + 1):
                for z in range(-reach, reach + 1):
                    if x * x + y * y + z * z <= radius * radius + 0.25:
                        self.put_wood((round(cx) + x, round(cy) + y, round(cz) + z), kind)

    def line(self, start, end, radius, kind):
        """Stamps along a path of single axis moves, so each step shares a face with the last."""
        pos = [round(v) for v in start]
        goal = [round(v) for v in end]
        self.stamp(pos, radius, kind)
        while pos != goal:
            axis = max(range(3), key=lambda i: abs(goal[i] - pos[i]))
            pos[axis] += 1 if goal[axis] > pos[axis] else -1
            self.stamp(pos, radius, kind)

    # ---- trunk ----
    def trunk(self):
        p, rng = self.p, self.rng
        height = p["trunk_height"]
        lean = p.get("lean", 0.0)
        lean_angle = rng.uniform(0, math.tau)
        phases = [rng.uniform(0, math.tau) for _ in range(5)]
        lobes = p.get("buttress_lobes", 5)
        flare = p.get("flare", 3.0)
        for y in range(-3, height + 1):
            t = max(y, 0) / height
            drift = lean * height * t ** 1.5
            cx = math.cos(lean_angle) * drift + 1.2 * math.sin(t * 5 + phases[0])
            cz = math.sin(lean_angle) * drift + 1.2 * math.sin(t * 4 + phases[1])
            base = p["trunk_radius"] * (1 - t) ** p.get("taper", 0.6) + p.get("trunk_top_radius", 1.5)
            spread = flare * math.exp(-max(y, 0) / 3.5)
            self.centres[y] = (cx, cz, base + spread * 0.5)
            reach = int(base + spread + 3)
            for x in range(-reach, reach + 1):
                for z in range(-reach, reach + 1):
                    angle = math.atan2(z, x)
                    ridge = 0.5 + 0.5 * math.sin(angle * lobes + phases[2])
                    edge = base + spread * ridge + 0.35 * math.sin(angle * 9 + y * 0.35 + phases[3])
                    if x * x + z * z <= edge * edge:
                        self.put_wood((round(cx) + x, y, round(cz) + z), "y")
        return lean_angle

    def hollow(self):
        depth = self.p.get("hollow", 0)
        if not depth:
            return
        door = self.rng.uniform(0, math.tau)
        for y in range(0, depth):
            cx, cz, radius = self.centres[y]
            inner = max(radius - 2.2, 1.5)
            if y >= depth - 3:
                inner *= (depth - y) / 4  # the roof closes in
            reach = int(radius + 8)
            for x in range(-reach, reach + 1):
                for z in range(-reach, reach + 1):
                    d = math.hypot(x, z)
                    away = abs((math.atan2(z, x) - door + math.pi) % math.tau - math.pi)
                    in_door = y < 4 and away * max(d, 1) < 1.6
                    if d <= inner or in_door:
                        self.wood.pop((round(cx) + x, y, round(cz) + z), None)

    # ---- branches ----
    def branch(self, start, direction, length, radius, level):
        p, rng = self.p, self.rng
        pos = list(start)
        up = p.get("branch_rise", 0.15)
        steps = max(int(length / 2), 1)
        for i in range(steps):
            t = (i + 1) / steps
            direction = _rotate_towards(direction, [
                direction[0] + rng.uniform(-0.6, 0.6),
                direction[1] + up + rng.uniform(-0.25, 0.25),
                direction[2] + rng.uniform(-0.6, 0.6)], 0.35)
            nxt = [c + d * 2 for c, d in zip(pos, direction)]
            r = radius * (1 - t * 0.6)
            self.line(pos, nxt, r if r >= 0.75 else 0, "w")
            pos = nxt
            if level < p.get("branch_levels", 3) and i >= steps * 0.3 and rng.random() < p.get("fork_chance", 0.35):
                side = rng.choice((-1, 1)) * rng.uniform(0.6, 1.1)
                c, s = math.cos(side), math.sin(side)
                fork = [direction[0] * c - direction[2] * s, direction[1] + 0.1, direction[0] * s + direction[2] * c]
                self.branch(pos, fork, length * rng.uniform(0.45, 0.7), max(r * 0.7, 0.5), level + 1)
        self.tips.append(tuple(round(v) for v in pos))
        if level < p.get("branch_levels", 3):
            for side in (-0.7, 0.7):
                c, s = math.cos(side), math.sin(side)
                fork = [direction[0] * c - direction[2] * s, direction[1], direction[0] * s + direction[2] * c]
                self.branch(pos, fork, length * 0.5, max(radius * 0.4, 0.5), level + 1)

    def crown(self):
        p, rng = self.p, self.rng
        height = p["trunk_height"]
        count = p["branches"]
        low, high = p.get("branch_from", 0.5), 0.98
        for i in range(count):
            y = int(height * (low + (high - low) * i / max(count - 1, 1)))
            cx, cz, radius = self.centres[y]
            angle = i * 2.39996 + rng.uniform(-0.35, 0.35)  # the golden angle spreads branches evenly
            direction = [math.cos(angle), p.get("branch_start_rise", 0.35), math.sin(angle)]
            start = (cx, y, cz)
            self.branch(start, direction, p["branch_length"] * rng.uniform(0.75, 1.15), p["branch_radius"], 1)

    # ---- roots ----
    def roots(self):
        p, rng = self.p, self.rng
        count = p.get("roots", 0)
        for i in range(count):
            angle = i * math.tau / count + rng.uniform(-0.25, 0.25)
            reach = p.get("root_length", 10) * rng.uniform(0.7, 1.2)
            pos = [math.cos(angle) * p["trunk_radius"] * 0.7, rng.uniform(1.5, 3.5), math.sin(angle) * p["trunk_radius"] * 0.7]
            steps = int(reach / 1.5)
            for s in range(steps):
                t = (s + 1) / steps
                angle += rng.uniform(-0.3, 0.3)
                nxt = [pos[0] + math.cos(angle) * 1.5, 2.5 * (1 - t) ** 2 - 3 * t ** 3 + 0.2, pos[2] + math.sin(angle) * 1.5]
                r = p.get("root_radius", 1.6) * (1 - t * 0.75)
                self.line(pos, nxt, r if r >= 0.75 else 0, "w")
                pos = nxt

    # ---- leaves ----
    def foliage(self):
        p, rng = self.p, self.rng
        size = p.get("leaf_radius", 5.0)
        flat = p.get("leaf_flatness", 0.55)
        for tip in self.tips:
            # Each tip is a few overlapping lobes, so the crown is lumpy instead of a row of smooth pads.
            for lobe in range(p.get("leaf_lobes", 3)):
                rx = size * rng.uniform(0.65, 1.0)
                ry = rx * flat * rng.uniform(0.85, 1.25)
                spread = 0 if lobe == 0 else size * 0.7
                centre = (tip[0] + rng.uniform(-spread, spread),
                          tip[1] + ry * 0.3 + rng.uniform(-spread, spread) * flat,
                          tip[2] + rng.uniform(-spread, spread))
                reach = int(rx) + 1
                for x in range(-reach, reach + 1):
                    for z in range(-reach, reach + 1):
                        for y in range(-int(ry) - 1, int(ry) + 2):
                            d = (x / rx) ** 2 + (y / ry) ** 2 + (z / rx) ** 2
                            if d > 1 or (d > 0.65 and rng.random() < 0.3):
                                continue
                            pos = (round(centre[0]) + x, round(centre[1]) + y, round(centre[2]) + z)
                            if pos not in self.wood and pos[1] > 1:
                                self.leaves[pos] = self.pick_leaf()

    def pick_leaf(self):
        roll, total = self.rng.random(), 0.0
        for block, share in self.p["leaves"]:
            total += share
            if roll <= total:
                return block
        return self.p["leaves"][0][0]

    def leaf_distances(self):
        """Vanilla's rule: a leaf's distance is the shortest walk to a log, through leaves, face to face."""
        seen = {}
        queue = deque()
        for pos in self.leaves:
            if any((pos[0] + a, pos[1] + b, pos[2] + c) in self.wood for a, b, c in SIDES):
                seen[pos] = 1
                queue.append(pos)
        while queue:
            pos = queue.popleft()
            if seen[pos] >= LEAF_REACH:
                continue
            for a, b, c in SIDES:
                nxt = (pos[0] + a, pos[1] + b, pos[2] + c)
                if nxt in self.leaves and nxt not in seen:
                    seen[nxt] = seen[pos] + 1
                    queue.append(nxt)
        self.leaves = {pos: block for pos, block in self.leaves.items() if pos in seen}
        return seen

    # ---- small details ----
    def vines(self, taken):
        p, rng = self.p, self.rng
        chance = p.get("vine_chance", 0.0)
        out = {}
        faces = (((0, 0, -1), "south"), ((0, 0, 1), "north"), ((1, 0, 0), "west"), ((-1, 0, 0), "east"))
        for pos in sorted(self.leaves):
            for (a, _, c), face in faces:
                start = (pos[0] + a, pos[1], pos[2] + c)
                if start in taken or start in out or rng.random() > chance:
                    continue
                for drop in range(rng.randint(2, p.get("vine_length", 8))):
                    spot = (start[0], start[1] - drop, start[2])
                    if spot[1] < 1 or spot in taken or spot in out:
                        break
                    out[spot] = f"minecraft:vine[east={str(face == 'east').lower()},north={str(face == 'north').lower()},south={str(face == 'south').lower()},up=false,west={str(face == 'west').lower()}]"
        return out

    def moss(self, taken):
        chance = self.p.get("moss_chance", 0.0)
        out = {}
        for pos in sorted(self.wood):
            above = (pos[0], pos[1] + 1, pos[2])
            if above[1] >= 0 and above not in taken and self.rng.random() < chance:
                out[above] = "minecraft:moss_carpet"
        return out


def generate(params, seed):
    tree = _Tree(params, seed)
    tree.trunk()
    tree.crown()
    tree.roots()
    tree.hollow()
    tree.foliage()
    distances = tree.leaf_distances()

    log = params["log"]
    wood = params["wood"]
    blocks = {}
    for pos, kind in tree.wood.items():
        # A log's rings would show wherever its end is open to the sky, so those blocks are all-bark wood.
        open_end = kind == "y" and (pos[0], pos[1] + 1, pos[2]) not in tree.wood
        blocks[pos] = f"minecraft:{wood}[axis=y]" if kind == "w" or open_end else f"minecraft:{log}[axis={kind}]"
    for pos, block in tree.leaves.items():
        blocks[pos] = f"minecraft:{block}[distance={distances[pos]},persistent=false,waterlogged=false]"
    blocks.update(tree.moss(blocks))
    blocks.update(tree.vines(blocks))
    return {"blocks": sorted([x, y, z, b] for (x, y, z), b in blocks.items())}


def vanilla_oak():
    """A plain vanilla-sized oak, shown next to big trees for scale."""
    blocks = {}
    for y in range(3, 7):
        radius = 2 if y < 5 else 1
        for x in range(-radius, radius + 1):
            for z in range(-radius, radius + 1):
                if radius == 2 and abs(x) == 2 and abs(z) == 2 and y == 4:
                    continue
                if radius == 1 and abs(x) == 1 and abs(z) == 1 and y == 6:
                    continue
                blocks[(x, y, z)] = "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]"
    for y in range(0, 6):
        blocks[(0, y, 0)] = "minecraft:oak_log[axis=y]"
    return {"blocks": sorted([x, y, z, b] for (x, y, z), b in blocks.items())}
