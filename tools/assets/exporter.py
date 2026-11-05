"""The two exports of organised assets. Asset Studio is the master; these are copies for the two consumers.

Video export   exports/video-catalog.json and .csv: every accepted build with its folder, realm, style..., permanent model number
               (#315-00), rights and a video_ready flag. The Minecraft Inspiration project reads it; it is never written into that project.
World export   exports/worldgen.json: accepted, vanilla-only builds with rights noted, each with what it could replace, biomes, weight,
               size and the ground/trunk offset. The builds themselves go to the mod through Send to mod (which refuses non-vanilla blocks
               and keeps credits); this file is the list that tells the mod what to do with them.

Rights gate both: a build is only ready when its rights are noted (own, open licence or permission), see review.rights_of().
Everything marked "to confirm" below needs the author or the mod side to agree before it is relied on.
"""
import csv
import json
import random
import time
from pathlib import Path

import analyse
import dashboard
import facets
import library
import projects
import review

SCHEMA = 1
POOL_SEED = 2026  # the same shuffle the video catalog used, so numbers stay compatible
RIGHTS_OK = dashboard.RIGHTS_OK

# What each tree species replaces in vanilla world generation. The ids are to confirm against Minecraft 26.3 and the mod's design.
TREE_VANILLA = {"oak": "minecraft:oak", "birch": "minecraft:birch", "spruce": "minecraft:spruce", "dark oak": "minecraft:dark_oak", "acacia": "minecraft:acacia",
                "jungle": "minecraft:jungle_tree", "mangrove": "minecraft:mangrove", "cherry": "minecraft:cherry", "pale oak": "minecraft:pale_oak"}
TREE_BIOMES = {"oak": ["forest", "plains"], "birch": ["birch_forest", "forest"], "spruce": ["taiga", "snowy_taiga"], "dark oak": ["dark_forest"], "acacia": ["savanna"],
               "jungle": ["jungle"], "mangrove": ["mangrove_swamp"], "cherry": ["cherry_grove"], "pale oak": ["pale_garden"]}
PALETTE_BIOMES = {"Plains": ["plains", "forest", "meadow"], "Desert": ["desert"], "Snowy": ["snowy_plains", "snowy_taiga"], "Taiga": ["taiga"], "Savanna": ["savanna"],
                  "Jungle": ["jungle"], "Swamp": ["swamp", "mangrove_swamp"], "Cherry grove": ["cherry_grove"], "Dark forest": ["dark_forest"], "Pale garden": ["pale_garden"]}
WEIGHT = {"Common": 10, "Uncommon": 6, "Rare": 3, "Epic": 2, "Legendary": 1}  # a suggestion: rarer builds appear less often


def folder():
    return Path(projects.HOME) / "exports"


def numbers_file():
    return Path(projects.HOME) / "card_numbers.json"


# ---------------------------------------------------------------- permanent model numbers


def load_numbers():
    try:
        data = json.loads(numbers_file().read_text(encoding="utf-8"))
        return {str(k): int(v) for k, v in data.items()}
    except (OSError, ValueError, AttributeError):
        return {}


def save_numbers(numbers):
    numbers_file().parent.mkdir(parents=True, exist_ok=True)
    temp = numbers_file().with_suffix(".tmp")
    temp.write_text(json.dumps(numbers, indent=0, sort_keys=True), encoding="utf-8")
    temp.replace(numbers_file())


def import_numbers(path):
    """Adopt the numbers the video catalog already gave (a card_numbers.json: {asset id: number}). Existing numbers are kept and a number
    is never given twice. Returns (adopted, skipped)."""
    incoming = json.loads(Path(path).read_text(encoding="utf-8"))
    numbers = load_numbers()
    used = set(numbers.values())
    adopted = skipped = 0
    for asset_id, number in sorted(incoming.items()):
        number = int(number)
        if asset_id in numbers or number in used or number < 1:
            skipped += 1
            continue
        numbers[asset_id] = number
        used.add(number)
        adopted += 1
    save_numbers(numbers)
    return adopted, skipped


def assign_numbers(ids):
    """Gives every id a permanent model number (three digits from the shuffled pool, then four digits). Existing numbers never change."""
    numbers = load_numbers()
    used = set(numbers.values())
    pool = list(range(1, 1000))
    random.Random(POOL_SEED).shuffle(pool)
    free = [n for n in pool if n not in used]
    extra = max([n for n in used if n >= 1000] + [999]) + 1
    changed = False
    for asset_id in sorted(ids):
        if asset_id in numbers:
            continue
        if free:
            numbers[asset_id] = free.pop(0)
        else:
            numbers[asset_id] = extra
            extra += 1
        changed = True
    if changed:
        save_numbers(numbers)
    return numbers


def label(model_no, variant_no=0):
    width = 3 if model_no < 1000 else 4
    return f"#{model_no:0{width}d}-{variant_no:02d}"


# ---------------------------------------------------------------- selection


def _accepted_builds(rows):
    return [r for r in rows if r.get("mode") != "file" and r.get("versions", 0) > 0 and r.get("analysed") and r.get("review") == "accepted"]


def ready_ids(rows, world=False):
    ok = [r for r in _accepted_builds(rows) if r.get("rights") in RIGHTS_OK and not review.is_package(r)]
    if world:
        ok = [r for r in ok if not r.get("not_vanilla")]
    return [r["id"] for r in sorted(ok, key=lambda r: (r.get("f_type", ""), r.get("f_species", ""), r["id"]))]


def _keepers(rows):
    """For each group of exact twins among the accepted builds: the id to keep (most blocks, then oldest)."""
    groups = {}
    for r in rows:
        if r.get("fp_exact"):
            groups.setdefault(r["fp_exact"], []).append(r)
    out = {}
    for group in groups.values():
        keeper = max(group, key=lambda r: (r.get("blocks", 0), -r.get("created", 0)))["id"]
        for r in group:
            out[r["id"]] = keeper
    return out


# ---------------------------------------------------------------- video export


def video_entries(rows):
    accepted = _accepted_builds(rows)
    numbers = assign_numbers([r["id"] for r in accepted])
    keepers = _keepers(accepted)
    entries = []
    for row in sorted(accepted, key=lambda r: (numbers[r["id"]], r["id"])):
        project = projects.load(row["id"])
        measures = (project.get("analysis") or {}).get("measures") or {}
        credit = project.get("credit") or {}
        model = numbers[row["id"]]
        keeper = keepers.get(row["id"])
        entries.append({
            "id": row["id"], "model_no": model, "variant_no": 0, "number": label(model), "title": project.get("title", ""),
            "folder": facets.get(project, "type") or "", "subfolder": facets.get(project, "subtype") or "", "species": facets.get(project, "species") or "",
            "realm": facets.get(project, "realm") or "", "style": facets.get(project, "style") or "", "palette": facets.get(project, "palette") or "",
            "setting": facets.get(project, "setting") or "", "scale": facets.get(project, "scale") or "", "complexity": facets.get(project, "complexity") or "",
            "rarity": facets.get(project, "rarity") or "", "series": {"kind": "folder", "key": _series_key(project)},
            "blocks": measures.get("blocks", row.get("blocks", 0)), "w": measures.get("w", 0), "h": measures.get("h", 0), "d": measures.get("d", 0),
            "distinct": measures.get("distinct", 0), "difficulty": measures.get("difficulty", 0), "rarity_points": measures.get("rarity_points", 0),
            "rights": row.get("rights", "unknown"), "video_ready": row.get("rights") in RIGHTS_OK and not review.is_package(row), "parts": measures.get("parts", 1), "creator": credit.get("creator", ""),
            "licence": credit.get("licence", ""), "permission": credit.get("permission", ""), "link": credit.get("link", ""),
            "pack": (project.get("source") or {}).get("name", ""), "path": (project.get("source") or {}).get("item", ""),
            "vanilla": not row.get("not_vanilla"), "duplicate_of": keeper if keeper and keeper != row["id"] else "",
            "fingerprints": (project.get("analysis") or {}).get("prints") or {}, "recipe": None,
        })
    return entries


def _series_key(project):
    kind, species, sub = facets.get(project, "type") or "Other", facets.get(project, "species"), facets.get(project, "subtype")
    return f"{kind}/{species or sub}" if (species or sub) else kind


def write_video(rows=None):
    """Writes exports/video-catalog.json and .csv. Returns a summary."""
    rows = rows if rows is not None else library.all_rows()
    entries = video_entries(rows)
    folder().mkdir(parents=True, exist_ok=True)
    document = {"schema": SCHEMA, "made": time.strftime("%Y-%m-%d %H:%M:%S"), "source": "Asset Studio", "count": len(entries),
                "video_ready": sum(1 for e in entries if e["video_ready"]), "entries": entries}
    path = folder() / "video-catalog.json"
    _write(path, json.dumps(document, indent=1, ensure_ascii=False))
    flat = [{k: (json.dumps(v) if isinstance(v, (dict, list)) else v) for k, v in e.items()} for e in entries]
    csv_path = folder() / "video-catalog.csv"
    with open(csv_path.with_suffix(".tmp"), "w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(flat[0]) if flat else ["id"])
        writer.writeheader()
        writer.writerows(flat)
    csv_path.with_suffix(".tmp").replace(csv_path)
    return {"file": str(path), "csv": str(csv_path), "count": len(entries), "video_ready": document["video_ready"],
            "held_back": len(entries) - document["video_ready"]}


# ---------------------------------------------------------------- world export


def _origin(project, blocks):
    xs, ys, zs = zip(*((b[0], b[1], b[2]) for b in blocks))
    low = (min(xs), min(ys), min(zs))
    return {"ground_y": -low[1], "origin_x": -low[0], "origin_z": -low[2], "size": [max(xs) - low[0] + 1, max(ys) - low[1] + 1, max(zs) - low[2] + 1]}


def world_entries(rows):
    keepers = _keepers(_accepted_builds(rows))
    out = []
    for asset_id in ready_ids(rows, world=True):
        project = projects.load(asset_id)
        if keepers.get(asset_id) not in (None, asset_id):
            continue  # a turned or mirrored twin of a kept build: the mod gets one of them
        kind = facets.get(project, "type") or "Other"
        species = facets.get(project, "species") or ""
        palette = facets.get(project, "palette") or ""
        chosen = project["saved"][-1] if project.get("saved") else None
        number = chosen["version"] if chosen else project["versions"][-1]["number"]
        placement = _origin(project, projects.load_version(asset_id, number)["blocks"])
        if kind == "Trees" and species in TREE_VANILLA:
            replaces = {"kind": "tree", "vanilla": TREE_VANILLA[species], "to_confirm": True}
            biomes = TREE_BIOMES[species]
        elif kind == "Houses":
            replaces = {"kind": "village house", "vanilla": None, "to_confirm": True}
            biomes = PALETTE_BIOMES.get(palette, [])
        else:
            replaces = None
            biomes = PALETTE_BIOMES.get(palette, [])
        rarity = facets.get(project, "rarity") or "Common"
        out.append({"id": asset_id, "name": chosen["name"] if chosen else None, "structure": f"vestiges:{project.get('category', 'tree')}/{chosen['name']}" if chosen else None,
                    "title": project.get("title", ""), "type": kind, "species": species, "subtype": facets.get(project, "subtype") or "", "replaces": replaces,
                    "biomes": biomes, "weight": WEIGHT.get(rarity, 5), "rarity": rarity, "scale": facets.get(project, "scale") or "",
                    "complexity": facets.get(project, "complexity") or "", **placement, "vanilla": True,
                    "creator": (project.get("credit") or {}).get("creator", ""), "licence": (project.get("credit") or {}).get("licence", "")})
    return out


def write_world(rows=None):
    """Writes exports/worldgen.json. Returns a summary and the ids that still need a saved variant before Send to mod can place them."""
    rows = rows if rows is not None else library.all_rows()
    entries = world_entries(rows)
    folder().mkdir(parents=True, exist_ok=True)
    document = {"schema": SCHEMA, "made": time.strftime("%Y-%m-%d %H:%M:%S"), "source": "Asset Studio",
                "note": "Biomes, weights and 'replaces' ids are suggestions to confirm with the mod's design; structures are placed by Send to mod.",
                "count": len(entries), "entries": entries}
    path = folder() / "worldgen.json"
    _write(path, json.dumps(document, indent=1, ensure_ascii=False))
    trees = sum(1 for e in entries if e["type"] == "Trees")
    return {"file": str(path), "count": len(entries), "trees": trees, "unsaved": [e["id"] for e in entries if not e["name"]], "ids": [e["id"] for e in entries]}


def prepare_world(rows=None):
    """Saves a variant (with the proposed name) for every world-ready build that has none, so Send to mod can place it. Returns the ids."""
    rows = rows if rows is not None else library.all_rows()
    for asset_id in world_ids(rows):
        project = projects.load(asset_id)
        if project.get("versions") and not project.get("saved"):
            library.save_variant(asset_id, project["versions"][-1]["number"])
    return world_ids(library.all_rows())


def world_ids(rows):
    keepers = _keepers(_accepted_builds(rows))
    return [i for i in ready_ids(rows, world=True) if keepers.get(i) in (None, i)]


def _write(path, text):
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(text, encoding="utf-8")
    temp.replace(path)


def summary(rows=None):
    rows = rows if rows is not None else library.all_rows()
    accepted = _accepted_builds(rows)
    return {"accepted": len(accepted), "video": len(ready_ids(rows)), "world": len(world_ids(rows)),
            "files": {name: {"path": str(folder() / name), "time": (folder() / name).stat().st_mtime} for name in ("video-catalog.json", "video-catalog.csv", "worldgen.json")
                      if (folder() / name).exists()}, "numbers": len(load_numbers())}


# ---------------------------------------------------------------- rights by pack


def packs(rows):
    """Builds grouped by the pack they came from, with how many have their rights noted."""
    groups = {}
    for r in rows:
        if r.get("mode") == "file" or not r.get("versions"):
            continue
        g = groups.setdefault(r.get("source") or "(no pack)", {"source": r.get("source") or "", "count": 0, "unknown": 0, "limited": 0, "creator": "", "licence": ""})
        g["count"] += 1
        g["unknown"] += r.get("rights") == "unknown"
        g["limited"] += r.get("rights") == "limited"
        g["creator"] = g["creator"] or r.get("creator", "")
        g["licence"] = g["licence"] or r.get("licence", "")
    return sorted(groups.values(), key=lambda g: (-g["unknown"], g["source"]))


def set_pack_rights(source, creator="", licence="", permission="", link=""):
    """Notes the creator, licence and permission for every build from one pack. Only fills what is given; empty fields change nothing.
    The author types these: nothing here decides what the rights are."""
    given = {k: str(v).strip()[:500] for k, v in (("creator", creator), ("licence", licence), ("permission", permission), ("link", link)) if str(v).strip()}
    if not given:
        raise ValueError("Type at least one of creator, licence or permission")
    changed = 0
    for row in library.all_rows():
        if row.get("source") == source and row.get("mode") != "file":
            project = projects.load(row["id"])
            credit = project.get("credit") or {}
            credit.update(given)
            project["credit"] = credit
            projects.save(project)
            changed += 1
    library.flush()
    return changed
