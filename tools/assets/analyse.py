"""The analyser: looks inside every build and PROPOSES what it is. The author decides (see facets.py).

Layer 0  measurements and rules   size, blocks, rooms, doors, roofs, symmetry, materials -> type, species, realm, palette, scale,
                                  complexity, rarity. Free and instant.
Layer 1  fingerprints             exact / shape-only / palette hashes that ignore rotation and mirroring -> duplicates, palette
                                  variants, shape families. Free and instant.
(Layers 2 and 3, a picture model and a vision AI for style and realm, come later and only for what is still unsure.)

Nothing here changes a block or a decided facet. Every proposal carries a confidence and a reason in plain words.
Type names are the folders of Dev/TAXONOMY.md in the Minecraft Inspiration project.
"""
import hashlib
import re
import time
from collections import Counter, deque

import facets
import naming

VERSION = 2  # 2: a package of many builds is never 'sure'
MAX_CELLS = 700_000  # biggest bounding box we flood-fill for rooms

TYPES = ("Trees", "Gardens and plants", "Houses", "Towers", "Gates", "Walls", "Castles and fortresses", "Bridges and roads", "Farms",
         "Water", "Terrain", "Ships and vehicles", "Workshops and shops", "Civic and public buildings", "Streets and plazas",
         "Temples, shrines and graves", "Statues and pixel art", "Other")

# Old Asset Studio kinds, used by the library filter until the folder view replaces them.
OLD_KIND = {"Trees": "tree", "Gardens and plants": "plant", "Houses": "house", "Workshops and shops": "house", "Farms": "house",
            "Towers": "structure", "Gates": "structure", "Walls": "structure", "Castles and fortresses": "structure",
            "Bridges and roads": "structure", "Civic and public buildings": "structure", "Streets and plazas": "structure", "Temples, shrines and graves": "structure",
            "Water": "other", "Terrain": "other", "Ships and vehicles": "other", "Statues and pixel art": "other", "Other": "other"}

# Words in the path, folder or title that hint at a type: (regex on a lowercase word list, type, subtype). First match wins per type.
PATH_HINTS = (
    (r"\b(oak|birch|spruce|acacia|cherry|mangrove|jungle|sapling|trees?)\b", "Trees", ""),
    (r"\b(bush|shrub|flowers?|fern|hedge|topiary|garden|planter|pergola|greenhouse)\b", "Gardens and plants", ""),
    (r"\b(cottage)\b", "Houses", "Cottages"), (r"\b(cabin|hut|shack)\b", "Houses", "Cabins"), (r"\b(farmhouse)\b", "Houses", "Farmhouses"),
    (r"\b(manor|mansion|villa)\b", "Houses", "Manors"), (r"\b(houses?|homes?|townhouses?|tiny)\b", "Houses", ""),
    (r"\b(towers?|lighthouse|watchtower|belfry|pagoda|minaret)\b", "Towers", ""),
    (r"\b(gates?|gatehouse|barbican|portcullis|torii)\b", "Gates", ""),
    (r"\b(walls?|palisade|rampart|battlement)\b", "Walls", ""),
    (r"\b(castles?|fortress|keep|citadel|palace)\b", "Castles and fortresses", ""),
    (r"\b(bridges?|roads?|aqueduct|tunnel)\b", "Bridges and roads", ""),
    (r"\b(farms?|barn|stable|silo|windmill|watermill|orchard|vineyard|crops?|pens?)\b", "Farms", ""),
    (r"\b(ponds?|lake|river|waterfall|fountain|oasis|lagoon|well)\b", "Water", ""),
    (r"\b(hills?|mountain|cliff|rock|arch|boulder|cave|canyon|island|volcano)\b", "Terrain", ""),
    (r"\b(ships?|boat|galleon|longship|submarine|cart|train|airship|balloon)\b", "Ships and vehicles", ""),
    (r"\b(smith|armou?rer|weaponsmith|toolsmith|fletcher|butcher|tannery|mason|carpenter|cartographer|librarian|baker|brewer|alchemist|"
     r"jeweller|potter|tailor|shop|store|market|stall|mill|observatory)\b", "Workshops and shops", ""),
    (r"\b(church|cathedral|town ?hall|inn|tavern|school|library|bank|theatre|courthouse|prison|hospital|guild|museum|monastery|mosque|bathhouse)\b",
     "Civic and public buildings", ""),
    (r"\b(temple|shrine|grave|graveyard|mausoleum|obelisk|monument|ruins?|ruined|tomb|altar|portal)\b", "Temples, shrines and graves", ""),
    (r"\b(meeting point|plaza|square|streets?|alley|lamp ?post)\b", "Streets and plazas", ""),
    (r"\b(statue|sculpture|pixel ?art|logo|head)\b", "Statues and pixel art", ""),
)

LOG = re.compile(r"(?:_log|_wood|_stem|_hyphae)$")
LEAVES = re.compile(r"_leaves$")
MUSHROOM = re.compile(r"mushroom_block$|mushroom_stem$")
WART = re.compile(r"wart_block$|shroomlight$")
GLASS = re.compile(r"glass(_pane)?$|tinted_glass$")
DOOR = re.compile(r"_door$")
BED = re.compile(r"_bed$")
WORK = re.compile(r"furnace|smoker|crafting_table|smithing_table|anvil|brewing_stand|enchanting_table|bookshelf|lectern|loom|cartography_table|"
                  r"fletching_table|grindstone|stonecutter|composter|cauldron|^bell$|barrel|chest$")
CROP = re.compile(r"^(wheat|carrots|potatoes|beetroots|melon_stem|pumpkin_stem|sweet_berry_bush|farmland|hay_block|melon|pumpkin)$")
DETAIL = re.compile(r"_stairs$|_slab$|_fence$|_fence_gate$|_wall$|_trapdoor$|_door$|_pane$|_button$|_pressure_plate$|_sign$|_carpet$|torch|lantern|"
                    r"candle|_bars$|^chain$|flower|fern|vine|lichen|ladder|banner|_head$|_skull$|flower_pot|campfire|rail|lever|tripwire|cobweb|"
                    r"bamboo$|sapling|grass$|dead_bush|petals|azalea|rose|tulip|orchid|allium|dandelion|poppy|sunflower|lilac|peony|cornflower|"
                    r"lily|mushroom$|^snow$|pointed_dripstone|amethyst_cluster|sculk_vein|^glow_lichen")
STAIRSLAB = re.compile(r"_stairs$|_slab$")
FLORA = re.compile(r"flower|fern|grass$|tulip|orchid|allium|dandelion|poppy|sunflower|lilac|peony|cornflower|rose|petals|azalea|bush|vine|lily|moss_carpet|moss_block|^short_grass|^tall_grass|spore")
NETHER = re.compile(r"netherrack|nether_brick|crimson|warped|blackstone|basalt|soul_|glowstone|magma|shroomlight|nether_wart|gilded|ancient_debris|^lava$")
END = re.compile(r"end_stone|purpur|end_rod|chorus|end_portal")
SEA = re.compile(r"prismarine|sea_lantern|kelp|seagrass|coral|sea_pickle|^water$|conduit")
PALETTES = (("Desert", re.compile(r"sandstone|^sand$|cactus|dead_bush|terracotta")), ("Snowy", re.compile(r"snow|ice|powder_snow")),
            ("Taiga", re.compile(r"^spruce_|^stripped_spruce_|podzol|coarse_dirt")), ("Savanna", re.compile(r"^acacia_|^stripped_acacia_")),
            ("Jungle", re.compile(r"^jungle_|^stripped_jungle_|bamboo|mossy_cobblestone|cocoa|melon")),
            ("Cherry grove", re.compile(r"^cherry_|^stripped_cherry_|pink_petals")), ("Swamp", re.compile(r"^mangrove_|^stripped_mangrove_|mud|lily_pad|slime")),
            ("Dark forest", re.compile(r"^dark_oak_|^stripped_dark_oak_")), ("Pale garden", re.compile(r"^pale_oak_|^stripped_pale_oak_|pale_moss|resin")),
            ("Plains", re.compile(r"^oak_|^stripped_oak_|^birch_|cobblestone|^stone_brick|^grass_block|^dirt")))
SPECIES_NAME = {"dark_oak": "dark oak", "pale_oak": "pale oak"}


def _name(state):
    return state.split("[", 1)[0].replace("minecraft:", "", 1)


def _words(*parts):
    return " ".join(naming.slug(p).replace("-", " ") for p in parts if p)


# ---------------------------------------------------------------- Layer 0: measurements


def measure(blocks):
    """blocks: [[x, y, z, state], ...] (no air). Returns plain numbers; nothing here guesses what the build is."""
    if not blocks:
        return {"blocks": 0}
    pos = {}
    names = Counter()
    for x, y, z, state in blocks:
        n = _name(state)
        if n in ("barrier", "structure_void", "light", "jigsaw"):
            continue
        pos[(x, y, z)] = n
        names[n] += 1
    if not pos:
        return {"blocks": 0}
    xs, ys, zs = zip(*pos)
    x0, y0, z0 = min(xs), min(ys), min(zs)
    w, h, d = max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1
    total = len(pos)

    def share(pattern):
        return sum(c for n, c in names.items() if pattern.search(n)) / total

    def count(pattern):
        return sum(c for n, c in names.items() if pattern.search(n))

    logs, leaves = count(LOG), count(LEAVES)
    leafless = [p for p, n in pos.items() if not LEAVES.search(n)]
    floating = sum(1 for p in leafless if p[1] > y0 and (p[0], p[1] - 1, p[2]) not in pos) / max(len(leafless), 1)
    footprint = Counter(p[1] for p in pos)  # blocks per layer
    layer_cells = [len({(p[0], p[2]) for p in pos if p[1] == y}) for y in range(y0, y0 + h)]
    top = [p for p in pos if p[1] >= y0 + (2 * h) // 3]
    roof = sum(1 for p in top if STAIRSLAB.search(pos[p])) / max(len(top), 1) if h >= 6 else 0.0
    dominant = names.most_common(1)[0]
    out = {
        "blocks": total, "w": w, "h": h, "d": d, "distinct": len(names),
        "logs": logs, "leaves": leaves, "log_share": round(logs / total, 3), "leaf_share": round(leaves / total, 3),
        "glass": count(GLASS), "doors": count(DOOR) // 2, "beds": count(BED) // 2, "workstations": count(WORK), "crops": count(CROP),
        "detail": round(share(DETAIL), 3), "flora": round(share(FLORA), 3), "water": round(share(re.compile(r"^water$")), 3),
        "nether": round(share(NETHER), 3), "end": round(share(END), 3), "sea": round(share(SEA), 3),
        "floating": round(floating, 3), "roof": round(roof, 3), "fill": round(total / (w * h * d), 3),
        "widest_layer": max(layer_cells), "base_layer": layer_cells[0], "top_layer": layer_cells[-1],
        "top_blocks": [[n, c] for n, c in names.most_common(6)], "dominant": dominant[0],
    }
    out["symmetry"] = round(max(_mirror(pos, x0, w, 0), _mirror(pos, z0, d, 2)), 3)
    out["enclosed"] = _enclosed(pos, x0, y0, z0, w, h, d)
    out["roofed"] = _roofed(pos, x0, y0, z0, w, h, d)
    out["parts"] = _parts(pos) if max(w, d) >= 40 else 1
    woods = {n: c for n, c in names.items() if LOG.search(n) or MUSHROOM.search(n)}
    out["log_name"] = max(woods, key=woods.get) if woods else ""
    return out


def _mirror(pos, low, size, axis):
    """Share of blocks whose mirror image across the build's middle is also a block (same material ignored)."""
    if size < 3:
        return 0.0
    hit = 0
    for p in pos:
        q = list(p)
        q[axis] = 2 * low + size - 1 - p[axis]
        if tuple(q) in pos:
            hit += 1
    return hit / len(pos)


def _enclosed(pos, x0, y0, z0, w, h, d):
    """Number of empty cells that can't be reached from outside the build: its rooms. None when the box is too big to check."""
    if (w + 2) * (h + 2) * (d + 2) > MAX_CELLS:
        return None
    lo = (x0 - 1, y0 - 1, z0 - 1)
    hi = (x0 + w, y0 + h, z0 + d)
    seen = {lo}
    queue = deque([lo])
    while queue:
        x, y, z = queue.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in seen or not (lo[0] <= n[0] <= hi[0] and lo[1] <= n[1] <= hi[1] and lo[2] <= n[2] <= hi[2]) or n in pos:
                continue
            seen.add(n)
            queue.append(n)
    return w * h * d - len(pos) - (len(seen) - _margin_cells(w, h, d))


def _margin_cells(w, h, d):
    return (w + 2) * (h + 2) * (d + 2) - w * h * d


def _roofed(pos, x0, y0, z0, w, h, d, reach=12):
    """Empty cells that have a floor close below, a roof above and walls on at least 3 of 4 sides within `reach` blocks.
    Unlike _enclosed this forgives open windows, gaps and doorways, so houses with an open door still count as having rooms."""
    from bisect import bisect_right
    if w * h * d > MAX_CELLS:
        return None
    by_x, by_z, by_y = {}, {}, {}
    for x, y, z in pos:
        by_x.setdefault((y, z), []).append(x)
        by_z.setdefault((x, y), []).append(z)
        by_y.setdefault((x, z), []).append(y)
    for lines in (by_x, by_z, by_y):
        for line in lines.values():
            line.sort()

    def has_after(line, v, limit=None):
        i = bisect_right(line, v)
        return i < len(line) and (limit is None or line[i] - v <= limit)

    def has_before(line, v, limit=None):
        i = bisect_right(line, v - 1)
        return i > 0 and (limit is None or v - line[i - 1] <= limit)

    count = 0
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            column = by_y.get((x, z))
            if not column:
                continue
            for y in range(y0 + 1, y0 + h):
                if (x, y, z) in pos or not has_after(column, y) or not has_before(column, y, 3):
                    continue
                sides = (has_after(by_x.get((y, z), ()), x, reach) + has_before(by_x.get((y, z), ()), x, reach)
                         + has_after(by_z.get((x, y), ()), z, reach) + has_before(by_z.get((x, y), ()), z, reach))
                if sides >= 3:
                    count += 1
    return count


def _parts(pos, minimum=20):
    """How many separate builds are in the file (face or corner touching blocks make one). Used to spot packages."""
    left = set(pos)
    parts = 0
    while left:
        start = left.pop()
        stack, size = [start], 1
        while stack:
            x, y, z = stack.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        n = (x + dx, y + dy, z + dz)
                        if n in left:
                            left.discard(n)
                            stack.append(n)
                            size += 1
        if size >= minimum:
            parts += 1
    return parts


# ---------------------------------------------------------------- Layer 1: fingerprints


def _orientations(cells):
    """The 8 ways to turn a build around the vertical axis (4 turns, with and without a mirror). cells: [(x, y, z, tag)]."""
    for mirror in (False, True):
        for turn in range(4):
            out = []
            for x, y, z, tag in cells:
                if mirror:
                    x = -x
                for _ in range(turn):
                    x, z = z, -x
                out.append((x, y, z, tag))
            yield out


def _digest(cells):
    low = (min(c[0] for c in cells), min(c[1] for c in cells), min(c[2] for c in cells))
    shifted = sorted((x - low[0], y - low[1], z - low[2], tag) for x, y, z, tag in cells)
    return hashlib.sha1(repr(shifted).encode()).hexdigest()[:16]


def fingerprints(blocks):
    """exact: same blocks in any of the 8 turns/mirrors. shape: same occupied cells, any materials. palette: same set of materials."""
    cells = [(x, y, z, _name(s)) for x, y, z, s in blocks if _name(s) not in ("barrier", "structure_void", "light", "jigsaw")]
    if not cells:
        return {}
    exact = min(_digest(c) for c in _orientations(cells))
    shape = min(_digest([(x, y, z, 0) for x, y, z, _ in c]) for c in _orientations(cells))
    palette = hashlib.sha1(",".join(sorted({t for *_, t in cells})).encode()).hexdigest()[:16]
    return {"exact": exact, "shape": shape, "palette": palette}


# ---------------------------------------------------------------- rules -> proposals


def difficulty(m):
    """1 (Starter) to 5 (Master): how hard to build by hand. Same rules as the video catalog."""
    pts = next(i for i, lim in enumerate((150, 500, 1000, 2000, 10 ** 9)) if m["blocks"] < lim)
    pts += next(i for i, lim in enumerate((8, 14, 22, 10 ** 9)) if m["distinct"] < lim)
    pts += 2 if m["detail"] > 0.45 else 1 if m["detail"] > 0.25 else 0
    pts += 1 if m["h"] >= 20 else 0
    pts += 1 if m["floating"] > 0.10 else 0
    return 1 if pts <= 1 else 2 if pts <= 3 else 3 if pts <= 5 else 4 if pts <= 7 else 5


DIFFICULTY_WORDS = ("", "Starter", "Easy", "Medium", "Hard", "Master")
COMPLEXITY = {1: "Simple", 2: "Simple", 3: "Standard", 4: "Detailed", 5: "Masterpiece"}
RARITY_STEPS = ((4, "Common"), (8, "Uncommon"), (10, "Rare"), (12, "Epic"), (10 ** 9, "Legendary"))


def rarity_points(m, design=0):
    """The video catalog's points (size, difficulty, variety, design) without 'newest blocks', 'features' and 'setting', which need
    context we don't have per asset. `design` comes from how scarce the kind is, added by cluster_pass()."""
    size = sum(1 for e in (300, 800, 1500, 2500) if m["blocks"] >= e)
    diff = max(0, difficulty(m) - 1)
    variety = sum(1 for e in (10, 20, 30) if m["distinct"] >= e)
    why = f"size {size} ({m['blocks']:,} blocks), difficulty {diff}, variety {variety} ({m['distinct']} blocks), scarcity {design}"
    return size + diff + variety + design, why


def rarity_name(points):
    return next(name for limit, name in RARITY_STEPS if points <= limit)


def scale_of(m):
    biggest = max(m["w"], m["h"], m["d"])
    return "Micro" if biggest < 8 else "Small" if biggest < 16 else "Medium" if biggest < 32 else "Large" if biggest < 64 else "Mega"


def type_scores(m, hints):
    """Evidence for each type, 0..1, with the reasons kept. hints: {type: (subtype, word)} from the path and title."""
    scores, why = Counter(), {}

    def add(t, amount, reason):
        scores[t] += amount
        why.setdefault(t, []).append(reason)

    n = m["blocks"]
    longest, shortest = max(m["w"], m["d"]), min(m["w"], m["d"])
    treeish = m["logs"] > 0 and m["leaves"] > 0 and m["log_share"] + m["leaf_share"] >= 0.6
    mush = any(MUSHROOM.search(b) for b, _ in m["top_blocks"])
    if treeish:
        add("Trees", 0.8, f"{m['logs']} log blocks and {m['leaves']} leaves make up {round(100 * (m['log_share'] + m['leaf_share']))}% of it")
    elif mush:
        add("Trees", 0.7, "giant mushroom blocks")
    elif m["logs"] and not m["leaves"] and m["log_share"] > 0.8 and n < 120:
        add("Trees", 0.6, "only logs, small: a dead tree or a stump")
    if n <= 150 and not m["logs"] and (m["flora"] > 0.3 or m["leaf_share"] > 0.3):
        add("Gardens and plants", 0.7, f"small, and {round(100 * max(m['flora'], m['leaf_share']))}% plants or leaves")
    rooms = max(m.get("roofed") or 0, m.get("enclosed") or 0)
    building = rooms >= 20 and (m["doors"] or m["glass"] or m["beds"] or m["workstations"])
    if building:
        add("Houses", 0.4, f"{rooms} cells of roofed, walled space" + (f", {m['doors']} doors" if m["doors"] else "") + (f", {m['beds']} beds" if m["beds"] else ""))
        if m["beds"]:
            add("Houses", 0.15, "has beds")
        if m["workstations"] >= 2:
            add("Workshops and shops", 0.2, f"{m['workstations']} workstations")
        if longest <= 20 and m["h"] >= 2 * longest and m["h"] >= 14:
            add("Towers", 0.5, f"tall and narrow: {m['h']} high on a {longest}-wide base")
            scores["Houses"] -= 0.25
        if longest >= 40 and m["h"] >= 12 and rooms > 800:
            add("Castles and fortresses", 0.3, f"large ({m['w']}x{m['d']}) with {rooms} cells of rooms")
    elif m["h"] >= 14 and longest <= 14 and m["h"] >= 2 * longest and not treeish:
        add("Towers", 0.45, f"tall and narrow ({m['h']} high on a {longest}-wide base)")
    if longest >= 6 and shortest <= 4 and longest >= 3 * shortest and not treeish and m["h"] <= longest and not m["crops"]:
        add("Walls", 0.35, f"long and thin ({m['w']}x{m['d']})")
        if m["floating"] > 0.08 and m["h"] <= 8:
            add("Bridges and roads", 0.2, "long, thin, with parts hanging over nothing")
    if m["crops"] >= 8 and m["crops"] / n > 0.15:
        add("Farms", 0.7, f"{m['crops']} crop, farmland or hay blocks")
    if m["water"] > 0.3 and not treeish:
        add("Water", 0.6, f"{round(100 * m['water'])}% water")
    if n > 600 and rooms < 20 and not treeish and m["fill"] > 0.2 and m["doors"] == 0 and m["h"] <= 40:
        add("Terrain", 0.3, "big, solid, no doors or rooms")
    for t, (sub, word, weight) in hints.items():
        add(t, weight, f"the {'name' if weight >= 0.5 else 'folder'} says '{word}'")
    if m.get("parts", 1) >= 4:
        scores.clear()
        why.clear()
        add("Other", 0.9, f"{m['parts']} separate builds in one file (a package): split it before use")
    if not scores:
        add("Other", 0.2, "nothing in it points to a known type")
    return scores, why


def hints_from(path, title):
    """Words that hint at a type. The file name and title count more (0.7) than the folders above it (0.2): a pack's folders
    (for example 'houses') hold libraries, farms and temples too. Only the last three parts of the path are read."""
    parts = [p for p in str(path).replace("\\", "/").split("/") if p][-3:]
    near = _words(parts[-1] if parts else "", title)
    far = _words(*parts[:-1])
    found = {}
    for words, weight in ((near, 0.7), (far, 0.2)):
        for pattern, kind, sub in PATH_HINTS:
            match = re.search(pattern, words)
            if match and kind not in found:
                found[kind] = (sub, match.group(0), weight)
    return found


def species_of(m):
    """Tree species from the most common log, as a folder name. Returns (species, subtype)."""
    log = m.get("log_name", "")
    if not log:
        return "", ""
    if MUSHROOM.search(log):
        return "giant mushroom", "Giant mushrooms"
    base = re.sub(r"^stripped_", "", log)
    base = LOG.sub("", base)
    if base in ("crimson", "warped"):
        return f"{base} fungus", "Fungus trees"
    name = SPECIES_NAME.get(base, base.replace("_", " "))
    return name, name


def realm_of(m):
    if m["nether"] >= 0.3:
        return "Nether", min(0.9, 0.5 + m["nether"]), f"{round(100 * m['nether'])}% nether blocks"
    if m["end"] >= 0.3:
        return "End", min(0.9, 0.5 + m["end"]), f"{round(100 * m['end'])}% end blocks"
    return "Overworld", 0.6, "no nether or end blocks (Elven, Wizard and other realms need the picture layer)"


def palette_of(m, names):
    """Biome-style palette from the materials; None when nothing stands out."""
    total = sum(names.values()) or 1
    best = None
    for label, pattern in PALETTES[:-1] + PALETTES[-1:]:
        share = sum(c for n, c in names.items() if pattern.search(n)) / total
        if share >= 0.25 and (best is None or share > best[1]):
            best = (label, share)
    return best


def analyse_blocks(blocks, path="", title=""):
    """Everything the analyser can say about one build. Returns {'measures', 'prints', 'proposals': [(facet, value, source, conf, why)]}."""
    m = measure(blocks)
    if not m.get("blocks"):
        return {"measures": m, "prints": {}, "proposals": []}
    names = Counter(_name(b[3]) for b in blocks)
    hints = hints_from(path, title)
    scores, why = type_scores(m, hints)
    ranked = scores.most_common()
    best, top = ranked[0]
    second = ranked[1][1] if len(ranked) > 1 else 0.0
    confidence = max(0.2, min(0.95, top - 0.5 * second))
    if m.get("parts", 1) >= 4:
        confidence = 0.5  # it is certainly a package, but what to do with it (split, archive) is the author's call
    out = []
    out.append(("type", best, "rule", confidence, "; ".join(why[best])))
    if best == "Trees":
        species, sub = species_of(m)
        if species:
            out.append(("species", species, "rule", 0.9, f"most common wood is {m['log_name']}"))
        if m["leaves"] == 0 and m["logs"]:
            sub = "Stumps and logs" if m["h"] <= 4 else "Dead"
        if sub:
            out.append(("subtype", sub, "rule", 0.8 if sub == species else 0.55, "from the wood and shape"))
    elif hints.get(best, ("", "", 0))[0]:
        out.append(("subtype", hints[best][0], "rule", 0.5, f"the name says '{hints[best][1]}'"))
    realm, rconf, rwhy = realm_of(m)
    out.append(("realm", realm, "rule", rconf, rwhy))
    palette = palette_of(m, names)
    if palette:
        out.append(("palette", palette[0], "rule", round(min(0.85, 0.3 + palette[1]), 2), f"{round(100 * palette[1])}% of the blocks fit a {palette[0].lower()} palette"))
    if m["sea"] >= 0.5 and best != "Water":
        out.append(("setting", "Underwater", "rule", 0.6, f"{round(100 * m['sea'])}% sea blocks"))
    elif m["floating"] >= 0.4 and best != "Trees":
        out.append(("setting", "Floating", "rule", 0.4, f"{round(100 * m['floating'])}% of the blocks hang over nothing"))
    out.append(("scale", scale_of(m), "rule", 1.0, f"{m['w']}x{m['h']}x{m['d']}, {m['blocks']:,} blocks"))
    stars = difficulty(m)
    out.append(("complexity", COMPLEXITY[stars], "rule", 0.7, f"difficulty {DIFFICULTY_WORDS[stars]} ({stars} of 5)"))
    points, rwhy = rarity_points(m)
    out.append(("rarity", rarity_name(points), "rule", 0.5, f"{points} points: {rwhy}"))
    m["difficulty"] = stars
    m["rarity_points"] = points
    return {"measures": m, "prints": fingerprints(blocks), "proposals": out}


# ---------------------------------------------------------------- on a project


def apply(project, result, now=None):
    """Store the analysis on the project and offer the proposals. Returns how many proposals were stored."""
    project["analysis"] = {"version": VERSION, "at": now or time.time(), "measures": result["measures"], "prints": result["prints"]}
    stored = 0
    for facet, value, source, confidence, why in result["proposals"]:
        stored += facets.propose(project, facet, value, source, confidence, why, VERSION)
    if "review" not in project:
        facets.set_review(project, "inbox")
    return stored


def analyse_project(project, blocks):
    """Run the analyser on a loaded project and its latest blocks. The caller saves the project."""
    source = project.get("source") or {}
    result = analyse_blocks(blocks, naming.source_path(source), project.get("title", ""))
    return apply(project, result)


def needs_analysis(project):
    return bool(project.get("versions")) and (project.get("analysis") or {}).get("version") != VERSION


def families(rows):
    """Group index rows by fingerprint. rows: dicts with id, fp_exact, fp_shape, fp_palette.
    Returns {'duplicates': [[ids]], 'shapes': [[ids]], 'palettes': [[ids]]}: groups of two or more."""
    groups = {"exact": {}, "shape": {}, "palette": {}}
    for row in rows:
        for key in groups:
            value = row.get("fp_" + key)
            if value:
                groups[key].setdefault(value, []).append(row["id"])
    return {"duplicates": [g for g in groups["exact"].values() if len(g) > 1],
            "shapes": [g for g in groups["shape"].values() if len(g) > 1],
            "palettes": [g for g in groups["palette"].values() if len(g) > 1]}


def scarcity_points(count):
    """The video catalog's 'design' rule for a library build: the only one of its kind = 2, one of three or fewer = 1."""
    return 2 if count <= 1 else 1 if count <= 3 else 0
