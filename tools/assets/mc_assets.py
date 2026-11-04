"""Reads real block looks from the Minecraft client jar on this machine.

Nothing from the jar is ever copied into the repo; the preview asks for it while running.
bake() turns a block state into plain quads that the browser can draw.
"""
import json
import math
import re
import zipfile
from functools import lru_cache
from pathlib import Path

JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"

# Corners of each face: top-left, top-right, bottom-right, bottom-left, as (x, y, z) with 0=from, 1=to.
CORNERS = {
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
}
NORMALS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
FLUIDS = {"water": {"texture": "block/water_still", "tint": "#3f76e4"}, "lava": {"texture": "block/lava_still", "tint": "#ffffff"}}
SEE_THROUGH = re.compile(r"glass|ice$|slime|honey|spawner|_leaves$")
FIXED_TINTS ={"spruce_leaves": "#619961", "birch_leaves": "#80a755"}
FOLIAGE_TINTED = re.compile(r"^(oak|jungle|acacia|dark_oak|mangrove)_leaves$|^vine$")


def _default_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "down": (x0, 16 - z1, x1, 16 - z0),
        "up": (x0, z0, x1, z1),
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
    }[face]


def _turn(point, axis, degrees, origin=(0.5, 0.5, 0.5), rescale=False):
    """Right-handed turn of a point about an axis through origin."""
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    x, y, z = (p - o for p, o in zip(point, origin))
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    if rescale:
        grow = 1 / max(abs(c), 1e-6)
        if axis != "x":
            x *= grow
        if axis != "y":
            y *= grow
        if axis != "z":
            z *= grow
    return (x + origin[0], y + origin[1], z + origin[2])


def _turn_dir(name, x_degrees, y_degrees):
    v = _turn(NORMALS[name], "x", -x_degrees, (0, 0, 0))
    v = _turn(v, "y", -y_degrees, (0, 0, 0))
    best = max(NORMALS, key=lambda d: sum(a * b for a, b in zip(NORMALS[d], v)))
    return best


def parse_state(state):
    found = re.fullmatch(r"(?:minecraft:)?([^\[]+)(?:\[(.*)\])?", state)
    props = dict(p.split("=") for p in found.group(2).split(",")) if found.group(2) else {}
    return found.group(1), props


def _when_matches(rule, props):
    if "OR" in rule:
        return any(_when_matches(r, props) for r in rule["OR"])
    if "AND" in rule:
        return all(_when_matches(r, props) for r in rule["AND"])
    return all(props.get(k) in str(v).split("|") for k, v in rule.items())


class Jar:
    def __init__(self, path=JAR):
        if not Path(path).exists():
            raise SystemExit(f"Minecraft client jar not found: {path}\nRun 'dev build' once so Gradle downloads it.")
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def data_version(self):
        return json.loads(self.zip.read("version.json"))["world_version"]

    def block_ids(self):
        prefix, out = "assets/minecraft/blockstates/", set()
        for name in self.names:
            if name.startswith(prefix) and name.endswith(".json"):
                out.add("minecraft:" + name[len(prefix):-5])
        return out

    def texture(self, path):
        """path like block/oak_log. Returns png bytes, only from the textures folder."""
        name = f"assets/minecraft/textures/{path}.png"
        return self.zip.read(name) if name in self.names else None

    def _json(self, name):
        return json.loads(self.zip.read(name))

    @lru_cache(maxsize=None)
    def model(self, name):
        """Resolves a model's parents into one {textures, elements}."""
        name = name.replace("minecraft:", "")
        data = self._json(f"assets/minecraft/models/{name}.json")
        merged = {"textures": {}, "elements": None}
        if "parent" in data:
            parent = self.model(data["parent"])
            merged = {"textures": dict(parent["textures"]), "elements": parent["elements"]}
        merged["textures"].update(data.get("textures", {}))
        if "elements" in data:
            merged["elements"] = data["elements"]
        return merged

    def parts(self, block, props):
        """The models a block state uses, as blockstate entries."""
        data = self._json(f"assets/minecraft/blockstates/{block}.json")
        if "variants" in data:
            for key, entry in data["variants"].items():
                wanted = dict(p.split("=") for p in key.split(",")) if key else {}
                if all(props.get(k) == v for k, v in wanted.items()):
                    return [entry[0] if isinstance(entry, list) else entry]
            raise KeyError(f"{block} has no model for {props}")
        out = []
        for part in data["multipart"]:
            if "when" not in part or _when_matches(part["when"], props):
                entry = part["apply"]
                out.append(entry[0] if isinstance(entry, list) else entry)
        return out

    def default_state(self, block):
        """The properties a plain block name needs before the game's model files can draw it."""
        data = self._json(f"assets/minecraft/blockstates/{block}.json")
        if "variants" in data:
            first = next(iter(data["variants"]))
            return dict(p.split("=") for p in first.split(",")) if first else {}
        return {}

    def is_full_cube(self, block):
        """A plain opaque cube, which hides the faces of blocks touching it."""
        if SEE_THROUGH.search(block):
            return False
        data = self._json(f"assets/minecraft/blockstates/{block}.json")
        if "variants" not in data:
            return False
        entry = next(iter(data["variants"].values()))
        entry = entry[0] if isinstance(entry, list) else entry
        elements = self.model(entry["model"])["elements"] or []
        return len(elements) == 1 and elements[0]["from"] == [0, 0, 0] and elements[0]["to"] == [16, 16, 16]

    def property_names(self, block):
        """Which properties a block's look depends on."""
        data = self._json(f"assets/minecraft/blockstates/{block}.json")
        names = set()
        for key in data.get("variants", {}):
            names.update(p.split("=")[0] for p in key.split(",") if p)

        def walk(rule):
            for key, value in rule.items():
                if key in ("OR", "AND"):
                    for inner in value:
                        walk(inner)
                else:
                    names.add(key)
        for part in data.get("multipart", []):
            walk(part.get("when", {}))
        return names

    def known_ids(self):
        """Every block and item name the game has, without the namespace."""
        if getattr(self, "_known", None) is None:
            self._known = set()
            for name in self.names:
                for prefix in ("assets/minecraft/blockstates/", "assets/minecraft/items/"):
                    if name.startswith(prefix) and name.endswith(".json"):
                        self._known.add(name[len(prefix):-5])
        return self._known

    def bake_model(self, data):
        """Quads for a model file's content. Its parent and textures may come from the game. Returns (baked, missing textures).

        A blockstate file (variants or multipart) is shown through its first model. Textures the game doesn't have are
        swapped for a plain placeholder and listed, so the shape can still be judged.
        """
        if "variants" in data or "multipart" in data:
            entry = next(iter(data["variants"].values())) if "variants" in data else data["multipart"][0]["apply"]
            entry = entry[0] if isinstance(entry, list) else entry
            data = {"parent": entry["model"]}
        merged = {"textures": {}, "elements": None}
        if data.get("parent"):
            parent = self.model(str(data["parent"]))
            merged = {"textures": dict(parent["textures"]), "elements": parent["elements"]}
        merged["textures"].update(data.get("textures", {}))
        if data.get("elements"):
            merged["elements"] = data["elements"]
        quads = []
        for element in merged["elements"] or []:
            quads.extend(self._bake_element(element, merged["textures"], 0, 0))
        missing = []
        for quad in quads:
            if quad["tex"].startswith("#") or self.texture(quad["tex"]) is None:
                if quad["tex"] not in missing:
                    missing.append(quad["tex"])
                quad["tex"] = "block/magenta_concrete"
        if not quads:
            return {"quads": [], "kind": "invisible", "tint": None, "marker": "code"}, missing
        return {"quads": quads, "kind": "other", "tint": None}, missing

    def bake(self, state):
        block, props = parse_state(state)
        if ":" in block or f"assets/minecraft/blockstates/{block}.json" not in self.names:
            return {"quads": [], "kind": "invisible", "tint": None, "marker": "unknown"}
        if block in FLUIDS:
            return self._bake_fluid(block)
        quads = []
        try:
            entries = self.parts(block, props)
        except KeyError:  # a state from another version: any look of this block is better than none
            entries = self.parts(block, self.default_state(block))
        for entry in entries:
            model = self.model(entry["model"])
            for element in model["elements"] or []:
                quads.extend(self._bake_element(element, model["textures"], entry.get("x", 0), entry.get("y", 0)))
        kind = "other"
        if not quads:
            # barriers, light blocks, and blocks the game draws in code (chests, signs)
            return {"quads": [], "kind": "invisible", "tint": None, "marker": "barrier" if block == "barrier" else "code"}
        if block.endswith("_leaves"):
            kind = "leaves"
        elif self.is_full_cube(block):
            kind = "solid"
        tint =FIXED_TINTS.get(block) or ("foliage" if FOLIAGE_TINTED.match(block) else None)
        return {"quads": quads, "kind": kind, "tint": tint}

    def _bake_fluid(self, block):
        element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": FLUIDS[block]["texture"], "cullface": d, "tintindex": 0} for d in CORNERS}}
        return {"quads": self._bake_element(element, {}, 0, 0), "kind": "fluid", "tint": FLUIDS[block]["tint"]}

    def _bake_element(self, element, textures, turn_x, turn_y):
        f, t = element["from"], element["to"]
        rotation = element.get("rotation")
        out = []
        for face, info in element.get("faces", {}).items():
            tex = info["texture"]
            for _ in range(10):
                if isinstance(tex, dict):  # 26.3 can wrap a texture, such as {"sprite": ..., "force_translucent": true}
                    tex = tex.get("sprite", "")
                if tex.startswith("#"):
                    tex = textures.get(tex[1:], tex)
            tex = tex.replace("minecraft:", "")
            corners = []
            for corner in CORNERS[face]:
                p = tuple((t[i] if corner[i] else f[i]) / 16 for i in range(3))
                if rotation:
                    origin = tuple(v / 16 for v in rotation["origin"])
                    p = _turn(p, rotation["axis"], -rotation["angle"], origin, rotation.get("rescale", False))
                p = _turn(p, "x", -turn_x)
                p = _turn(p, "y", -turn_y)
                corners.extend(round(v, 5) for v in p)
            u0, v0, u1, v1 = info.get("uv") or _default_uv(face, f, t)
            uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            spin = (info.get("rotation", 0) // 90) % 4
            uv = uv[-spin:] + uv[:-spin] if spin else uv
            normal = _turn_dir(face, turn_x, turn_y)
            cull = info.get("cullface")
            out.append({
                "v": corners,
                "uv": [round(c / 16, 5) for pair in uv for c in pair],
                "tex": tex,
                "n": list(NORMALS[normal]),
                "cull": _turn_dir(cull, turn_x, turn_y) if cull else None,
                "tinted": info.get("tintindex", -1) >= 0,
            })
        return out
