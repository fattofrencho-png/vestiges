"""The Review screen's logic: which assets to look at, in what order, and what a decision does. No web code here.

Queues (views) are built from the library index rows, so they are instant even for thousands of assets:
  inbox     not reviewed yet, grouped by type and species so similar builds sit next to each other
  sure      inbox, the machine is sure first (fast batch accept)
  unsure    inbox, the machine's weakest guesses first (your answer matters most there)
  groups    inbox builds that have look-alikes (exact duplicates that ignore turning, or the same shape), group by group
  needs     marked "needs work": open them in the workbench first
  accepted  already in the catalog
  rejected  archived; restore any of them
  all       everything that has been analysed

A decision changes the project in memory and the caller saves it. Every decision first stores a snapshot, so Undo can step back.
"""
import copy
import time

import analyse
import facets
import libname

VIEWS = (("inbox", "Not reviewed yet"), ("sure", "Sure first (fast accept)"), ("unsure", "Unsure first"), ("groups", "Look-alikes"),
         ("needs", "Needs work"), ("accepted", "Accepted"), ("rejected", "Archived"), ("all", "Everything"))
SURE = 0.8
UNSURE = 0.5
HISTORY = 30
OLD_KIND_FACETS = ("type",)

VOCAB = {
    "type": list(analyse.TYPES),
    "realm": ["Overworld", "Nether", "End", "Elven", "Dwarven", "Wizard", "Undead", "Fairy", "Dragon", "Frost", "Pirate", "Tribal"],
    "style": ["Medieval", "Gothic", "Nordic", "Japanese", "Chinese", "Mediterranean", "Greek-Roman", "Egyptian", "Arabic", "Aztec-Maya", "Russian",
              "Victorian", "Colonial", "Art Deco", "Industrial", "Steampunk", "Rustic", "Modern", "Futuristic", "Ruined"],
    "palette": ["Plains", "Desert", "Snowy", "Taiga", "Savanna", "Jungle", "Swamp", "Cherry grove", "Dark forest", "Pale garden", "Day", "Night",
                "Sunset", "Spring", "Summer", "Autumn", "Winter", "Halloween", "Christmas", "Lunar New Year"],
    "setting": ["Surface", "Underground", "Underwater", "Floating", "Tree-top", "Cliff-side", "Island"],
    "scale": ["Micro", "Small", "Medium", "Large", "Mega"],
    "complexity": ["Simple", "Standard", "Detailed", "Masterpiece"],
    "rarity": ["Common", "Uncommon", "Rare", "Epic", "Legendary"],
    "species": ["oak", "birch", "spruce", "dark oak", "acacia", "jungle", "mangrove", "cherry", "pale oak", "giant mushroom", "crimson fungus", "warped fungus"],
    "subtype": [],
    "name": [],
    "front": list(analyse.FRONTS),
    "ground": list(analyse.GROUNDS),
    "season": list(analyse.SEASONS),
}
# What the review screen shows, in this order. The first three are what the author checks hardest.
SHOWN = ("name", "type", "subtype", "species", "realm", "style", "palette", "setting", "scale", "complexity", "rarity", "front", "ground", "season")


def is_package(row):
    """A file that holds several separate builds. It can't be accepted as one build until it is split."""
    return row.get("parts", 1) > 1


def _is_build(row):
    return row.get("mode") != "file" and row.get("versions", 0) > 0


def _matches(row, flt):
    if flt.get("type") and row.get("f_type") != flt["type"]:
        return False
    if flt.get("species") and row.get("f_species") != flt["species"]:
        return False
    if flt.get("batch") and row.get("batch") != flt["batch"]:
        return False
    if flt.get("source") and row.get("source") != flt["source"]:
        return False
    words = str(flt.get("q", "")).lower().split()
    text = f"{row.get('title', '')} {row.get('source', '')} {' '.join(row.get('tags', []))}".lower()
    return all(w in text for w in words)


def counts(rows):
    out = {"inbox": 0, "sure": 0, "batch": 0, "unsure": 0, "groups": 0, "needs": 0, "accepted": 0, "rejected": 0, "all": 0, "unanalysed": 0, "today": 0}
    twins = _twins(rows)
    midnight = time.mktime(time.localtime()[:3] + (0, 0, 0, 0, 0, -1))
    grouped = _grouped_ids(rows)
    for row in rows:
        if not _is_build(row):
            continue
        if not row.get("analysed"):
            out["unanalysed"] += 1
            continue
        out["all"] += 1
        state = row.get("review", "inbox")
        if state == "inbox":
            out["inbox"] += 1
            out["sure"] += row.get("conf", 0) >= SURE
            out["batch"] += row.get("conf", 0) >= SURE and row["id"] not in twins and not is_package(row)
            out["unsure"] += row.get("conf", 0) < UNSURE
            out["groups"] += row["id"] in grouped
        elif state in ("accepted", "rejected"):
            out[state] += 1
            out["today"] += row.get("decided", 0) >= midnight
        elif state == "needs_work":
            out["needs"] += 1
    return out


def _grouped_ids(rows):
    found = analyse.families(rows)
    return {i for group in found["duplicates"] + found["shapes"] for i in group}


def queue(rows, view="inbox", flt=None):
    """Ordered asset ids for a view. rows: library index rows."""
    flt = flt or {}
    rows = [r for r in rows if _is_build(r) and r.get("analysed")]
    pool = [r for r in rows if _matches(r, flt)]
    inbox = [r for r in pool if r.get("review", "inbox") == "inbox"]
    kind = lambda r: (r.get("f_type") or "~", r.get("f_species") or r.get("f_subtype") or "~", -r.get("blocks", 0), r["id"])
    if view == "inbox":
        chosen = sorted(inbox, key=kind)
    elif view == "sure":
        chosen = sorted([r for r in inbox if r.get("conf", 0) >= SURE], key=lambda r: (-r.get("conf", 0),) + kind(r))
    elif view == "unsure":
        chosen = sorted(inbox, key=lambda r: (r.get("conf", 0),) + kind(r))
    elif view == "groups":
        return _in_groups(inbox, rows)
    elif view == "needs":
        chosen = sorted([r for r in pool if r.get("review") == "needs_work"], key=kind)
    elif view in ("accepted", "rejected"):
        chosen = sorted([r for r in pool if r.get("review") == view], key=lambda r: -r.get("decided", 0))
    else:
        chosen = sorted(pool, key=kind)
    return [r["id"] for r in chosen]


def _in_groups(inbox, rows):
    """Inbox builds that have look-alikes, one group after another, the likely keeper first in each."""
    wanted = {r["id"] for r in inbox}
    by_id = {r["id"]: r for r in rows}
    found = analyse.families(rows)
    out, seen = [], set()
    for group in sorted(found["duplicates"] + found["shapes"], key=lambda g: -len(g)):
        members = [i for i in group if i in wanted and i not in seen]
        members.sort(key=lambda i: (-by_id[i].get("blocks", 0), by_id[i].get("created", 0)))
        out += members
        seen.update(members)
    return out


def related(rows, asset_id):
    """Look-alikes of one asset: exact duplicates (any turn or mirror), same shape in other materials, same materials.
    Each entry is small enough to show as a card. 'keeper' is the member of the duplicate group worth keeping."""
    by_id = {r["id"]: r for r in rows}
    me = by_id.get(asset_id)
    if not me:
        return {"duplicates": [], "shapes": [], "palettes": [], "keeper": None}

    def lite(row):
        return {"id": row["id"], "title": row.get("title", ""), "blocks": row.get("blocks", 0), "review": row.get("review", "inbox"), "type": row.get("f_type", "")}

    def same(key):
        return [lite(r) for r in rows if r["id"] != asset_id and me.get(key) and r.get(key) == me[key]]
    duplicates = same("fp_exact")
    shapes = [r for r in same("fp_shape") if r["id"] not in {d["id"] for d in duplicates}]
    palettes = [r for r in same("fp_palette") if r["id"] not in {d["id"] for d in duplicates}][:12]
    keeper = None
    if duplicates:
        group = [by_id[d["id"]] for d in duplicates] + [me]
        keeper = max(group, key=lambda r: (r.get("review") == "accepted", r.get("blocks", 0), -r.get("created", 0)))["id"]
    return {"duplicates": duplicates, "shapes": shapes[:12], "palettes": palettes, "keeper": keeper}


# ---------------------------------------------------------------- decisions


def _snapshot(project):
    return copy.deepcopy({"facets": project.get("facets") or {}, "review": project.get("review"), "category": project.get("category")})


def _remember(project):
    history = project.setdefault("review_history", [])
    history.append(_snapshot(project))
    del history[:-HISTORY]


def undo(project):
    """Step back one decision on this asset. Returns False when there is nothing to undo."""
    history = project.get("review_history") or []
    if not history:
        return False
    before = history.pop()
    project["facets"] = before["facets"]
    if before["review"] is None:
        project.pop("review", None)
    else:
        project["review"] = before["review"]
    if before["category"] is not None:
        project["category"] = before["category"]
    return True


def _sync_kind(project):
    """Keep the Library's older 'kind' filter right once the type is settled."""
    value = facets.get(project, "type")
    if value and project.get("mode") == "import":
        project["category"] = analyse.OLD_KIND.get(value, project.get("category"))


def restamp(project):
    """An accepted asset keeps its library file name up to date with its rows."""
    if facets.review_of(project).get("state") == "accepted":
        libname.stamp(project)


def accept_all(project):
    """Agree with everything the machine proposed, and file the asset as accepted."""
    _remember(project)
    for name in facets.undecided(project):
        facets.accept(project, name)
    facets.set_review(project, "accepted")
    _sync_kind(project)
    restamp(project)


def accept_facet(project, name):
    _remember(project)
    facets.accept(project, name)
    _sync_kind(project)
    restamp(project)


def change(project, name, value):
    value = " ".join(str(value).split())[:60 if name == "name" else 40]
    if not value:
        raise ValueError("Pick a value")
    _remember(project)
    facets.decide(project, name, value)
    _sync_kind(project)
    restamp(project)


def reject_facet(project, name):
    _remember(project)
    facets.reject(project, name)


def reject_asset(project, reason="other"):
    """Archive: hidden from the catalog, kept on disk, restorable."""
    _remember(project)
    facets.set_review(project, "rejected", reason if reason in facets.REJECT_REASONS else "other")


def needs_work(project):
    _remember(project)
    facets.set_review(project, "needs_work")


def restore(project):
    """Back into the inbox (from archive, or from needs work)."""
    _remember(project)
    facets.set_review(project, "inbox")


def _twins(rows):
    """Ids that have an exact twin (the same build turned or mirrored): one of them must be chosen as the keeper first."""
    return {i for group in analyse.families([r for r in rows if _is_build(r) and r.get("analysed")])["duplicates"] for i in group}


def batch_accept_ids(rows, flt=None, minimum=SURE, limit=1000):
    """Ids a batch accept would touch: inbox builds the machine is sure about, within the filter, leaving out builds with an exact twin
    (those are for the look-alike queue, where you pick the keeper) and packages of many builds."""
    flt = flt or {}
    ids = queue(rows, "sure", flt)
    by_id = {r["id"]: r for r in rows}
    twins = _twins(rows)
    return [i for i in ids if by_id[i].get("conf", 0) >= minimum and i not in twins and not is_package(by_id[i])][:limit]


import re

_OPEN = re.compile(r"^(mit|apache|bsd|isc|cc0|unlicense|zlib|wtfpl|mpl|l?gpl|public[ -]?domain|cc-by(-sa)?(-\d|$))", re.I)
_RESTRICTED = re.compile(r"all[ -]?rights[ -]?reserved|-nc(-|$)|non[ -]?commercial|-nd(-|$)|no[ -]?deriv", re.I)


def rights_of(project):
    """What the author has RECORDED about the right to use a build (the same rule the video catalog uses, stricter than the
    Library's own licence status, which treats an import with no creator as 'own'):
    own (made here), open, permission, limited (restricted licence and no permission), unknown (made by someone else, nothing noted)."""
    credit = project.get("credit") or {}
    licence = str(credit.get("licence", "")).strip()
    if project.get("mode") not in ("import", "file"):
        return "own"
    if licence and _OPEN.match(licence) and not _RESTRICTED.search(licence):
        return "open"
    if str(credit.get("permission", "")).strip():
        return "permission"
    if licence and _RESTRICTED.search(licence):
        return "limited"
    return "unknown"


def view_of(project, rows=None):
    """Everything the screen needs about one asset, ready to show."""
    records = project.get("facets") or {}
    shown = []
    for name in SHOWN:
        record = records.get(name)
        if not record:
            continue
        shown.append({"facet": name, "value": record["value"], "source": record["source"], "confidence": record["confidence"], "why": record["why"],
                      "status": record["status"], "rejected": record.get("rejected", []), "options": VOCAB.get(name, [])})
    measures = (project.get("analysis") or {}).get("measures") or {}
    keep = ("blocks", "w", "h", "d", "distinct", "logs", "leaves", "doors", "beds", "glass", "workstations", "crops", "roofed", "enclosed",
            "symmetry", "floating", "parts", "difficulty", "rarity_points", "top_blocks")
    return {"library": libname.of(project), "facets": shown, "measures": {k: measures[k] for k in keep if k in measures}, "review": facets.review_of(project),
            "confidence": facets.confidence_of(project), "can_undo": bool(project.get("review_history"))}
