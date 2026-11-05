"""The welcome dashboard: where the catalog stands and what to do next. Pure functions over the library index rows.

Progress is only stated against goals the author has set (targets). A folder with no target shows its count and no bar.
Agreed so far (TAXONOMY.md, 2026-11-05): 8 cards per tree species. Everything else is set by the author on the dashboard.

Targets live in ~/.vestiges-assets/targets.json as {"Trees/oak": 8, "Houses": 40}. A key is a type, or "Trees/<species>".
"""
import json
import time
from pathlib import Path

import analyse
import projects
import review

TREE_SPECIES = ("oak", "birch", "spruce", "dark oak", "acacia", "jungle", "mangrove", "cherry", "pale oak")
DEFAULT_TARGETS = {f"Trees/{s}": 8 for s in TREE_SPECIES}
RIGHTS_OK = ("own", "open", "permission")
SHOW = 4  # actions shown under "Focus next"; the rest sit behind "more"


def targets_file():
    return Path(projects.HOME) / "targets.json"


def load_targets():
    out = dict(DEFAULT_TARGETS)
    try:
        saved = json.loads(targets_file().read_text(encoding="utf-8"))
        for key, value in saved.items():
            if value is None:
                out.pop(key, None)
            elif isinstance(value, int) and 0 < value <= 100000:
                out[key] = value
    except (OSError, ValueError, AttributeError):
        pass
    return out


def set_target(key, value):
    """value: a whole number of cards, or None/0 for no target. Returns the targets now in force."""
    key = str(key)
    valid = {t for t in analyse.TYPES} | {f"Trees/{s}" for s in TREE_SPECIES}
    if key not in valid and not key.startswith("Trees/"):
        raise ValueError("Unknown folder")
    value = None if value in (None, "", 0) else int(value)
    if value is not None and not 0 < value <= 100000:
        raise ValueError("Pick a whole number of cards, or leave it empty for no target")
    try:
        saved = json.loads(targets_file().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        saved = {}
    saved[key] = value
    targets_file().parent.mkdir(parents=True, exist_ok=True)
    temp = targets_file().with_suffix(".tmp")
    temp.write_text(json.dumps(saved, indent=1), encoding="utf-8")
    temp.replace(targets_file())
    return load_targets()


def _builds(rows):
    return [r for r in rows if r.get("mode") != "file" and r.get("versions", 0) > 0]


def coverage(rows, targets):
    """One row per folder (and per tree species): accepted, waiting in the inbox, target and gap."""
    builds = [r for r in _builds(rows) if r.get("analysed")]
    species = list(TREE_SPECIES) + sorted({r["f_species"] for r in builds if r.get("f_type") == "Trees" and r.get("f_species") and r["f_species"] not in TREE_SPECIES})
    keys = [("Trees", "")] + [("Trees", s) for s in species] + [(t, "") for t in analyse.TYPES if t != "Trees"]
    out = []
    for type_, sp in keys:
        mine = [r for r in builds if r.get("f_type") == type_ and (not sp or r.get("f_species") == sp)]
        accepted = sum(1 for r in mine if r.get("review") == "accepted")
        inbox = sum(1 for r in mine if r.get("review", "inbox") == "inbox")
        key = f"{type_}/{sp}" if sp else type_
        if sp or type_ != "Trees":
            target = targets.get(key)
        else:  # the Trees total is the sum of its species targets
            sums = [v for k, v in targets.items() if k.startswith("Trees/")]
            target = sum(sums) if sums else targets.get("Trees")
        if not (accepted or inbox or target):
            continue
        out.append({"key": key, "label": sp or type_, "type": type_, "species": sp, "accepted": accepted, "inbox": inbox, "target": target,
                    "gap": max(target - accepted, 0) if target else None, "total": type_ == "Trees" and not sp,
                    "editable": not (type_ == "Trees" and not sp)})
    return out


def feeds(rows):
    """What each consumer could use right now: accepted builds whose rights are noted (videos), and that are also vanilla (world generation).
    A package of many builds is held back from both until it is split."""
    accepted = [r for r in _builds(rows) if r.get("review") == "accepted"]
    packages = [r for r in accepted if review.is_package(r)]
    singles = [r for r in accepted if not review.is_package(r)]
    ok = [r for r in singles if r.get("rights") in RIGHTS_OK]
    world = [r for r in ok if not r.get("not_vanilla")]
    by_species = {}
    for r in world:
        if r.get("f_type") == "Trees" and r.get("f_species"):
            by_species[r["f_species"]] = by_species.get(r["f_species"], 0) + 1
    return {"accepted": len(accepted), "video": len(ok), "world": len(world), "blocked_rights": len(singles) - len(ok),
            "blocked_not_vanilla": len(ok) - len(world), "blocked_packages": len(packages), "world_trees": dict(sorted(by_species.items()))}


def health(rows, backups, now):
    builds = _builds(rows)
    found = analyse.families([r for r in builds if r.get("analysed")])
    last = backups[0]["time"] if backups else None
    return {"no_credit": sum(1 for r in builds if r.get("rights") == "unknown"), "not_vanilla": sum(1 for r in builds if r.get("not_vanilla")),
            "analysis_errors": sum(1 for r in builds if r.get("analysis_error")), "unanalysed": sum(1 for r in builds if not r.get("analysed")),
            "duplicate_groups": len(found["duplicates"]), "shape_groups": len(found["shapes"]),
            "backup_days": None if last is None else round((now - last) / 86400, 1)}


def focus(rows, cover, counts, health_, targets):
    """The next actions, best first. Each is a button: {title, detail, open: {...}}.
    The order is a fixed ladder, so it is easy to explain: analyse what is new; take the quick win of sure builds; fill goals that
    have candidates waiting; settle look-alikes; answer the weakest guesses; then say where goals have nothing waiting."""
    out = []
    if health_["unanalysed"]:
        out.append({"id": "analyse", "title": f"Analyse {health_['unanalysed']} new builds",
                    "detail": "They have no proposals yet, so they can't be reviewed.", "run": "analyse"})
    if counts["batch"] >= 10:
        out.append({"id": "sure", "title": f"Accept {counts['batch']} sure builds in one go",
                    "detail": "The machine is at least 80% sure of what each is. Builds with a turned or mirrored twin are left out. You see a sample before confirming.", "open": {"view": "sure"}})
    for row in sorted((c for c in cover if c["gap"] and c["inbox"] and not c["total"]), key=lambda c: (-min(c["gap"], c["inbox"]), c["label"])):
        fill = min(row["gap"], row["inbox"])
        what = f"{row['label']} {row['type'].lower()}" if row["species"] else row["label"]
        out.append({"id": "goal:" + row["key"], "title": f"Review {row['inbox']} {what}",
                    "detail": f"Fills up to {fill} of the {row['gap']} still missing (goal {row['target']}, accepted {row['accepted']}).",
                    "open": {"view": "inbox", "filter": {"type": row["type"], "species": row["species"]}}})
    if counts["groups"]:
        out.append({"id": "groups", "title": f"Pick keepers among {counts['groups']} look-alike builds",
                    "detail": "Turned or mirrored copies, and the same shape in other materials, are shown together.", "open": {"view": "groups"}})
    if counts["unsure"]:
        out.append({"id": "unsure", "title": f"Answer the {counts['unsure']} weakest guesses",
                    "detail": "Where the machine is least sure, your answer matters most.", "open": {"view": "unsure"}})
    empty = [c for c in cover if c["gap"] and not c["inbox"] and not c["total"]]
    if empty:
        worst = max(empty, key=lambda c: c["gap"])
        out.append({"id": "empty", "title": f"{worst['label']} needs {worst['gap']} more and nothing is waiting",
                    "detail": f"{len(empty)} goals have no candidates in the inbox. Import more builds or make editions.", "go": "import"})
    if health_["no_credit"]:
        out.append({"id": "rights", "title": f"Note the source of {health_['no_credit']} builds",
                    "detail": "Rights are unknown for these, so they can't feed the videos or the mod until you note a creator, licence or permission.", "library": {"no_credit": True}})
    return out


def goal_progress(cover):
    rows = [c for c in cover if c["target"] and not c["total"]]
    if not rows:
        return None
    done = sum(min(c["accepted"], c["target"]) for c in rows)
    goal = sum(c["target"] for c in rows)
    return {"done": done, "goal": goal}


def build(rows, targets, backups=(), running=None, now=None):
    now = now or time.time()
    cover = coverage(rows, targets)
    counts = review.counts(rows)
    h = health(rows, list(backups), now)
    actions = focus(rows, cover, counts, h, targets)
    builds = _builds(rows)
    return {"library": len(builds), "analysed": sum(1 for r in builds if r.get("analysed")), "counts": counts, "coverage": cover,
            "feeds": feeds(rows), "health": h, "focus": actions[:SHOW], "more": actions[SHOW:], "goal": goal_progress(cover), "running": running}
