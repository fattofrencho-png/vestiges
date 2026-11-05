"""Runs the analyser over the library, and repairs wrong kinds. The first round of sorting, before the author reviews anything.

    python triage.py analyse            analyse every build that has not been analysed by the current analyser (safe: only adds facts)
    python triage.py analyse --all      analyse everything again (decisions you made are never overwritten)
    python triage.py categories         show which kinds would change (nothing is written)
    python triage.py categories --apply fix the kinds; the old kind is kept in "category_was"
    python triage.py numbers --import F adopt the model numbers of the video catalog (its card_numbers.json); new ones never clash
    python triage.py summary            counts by type, review state and how sure the machine is

Every command that writes first copies all project.json files to ~/.vestiges-assets/backups/triage-<time>/.
"""
import shutil
import sys
import threading
import time
from collections import Counter
from pathlib import Path

import analyse
import facets
import naming
import projects


def backup_projects(label="triage"):
    """Copies every project.json (small) before a bulk change. Returns the folder."""
    target = Path(projects.HOME) / "backups" / f"{label}-{time.strftime('%Y%m%d_%H%M%S')}"
    target.mkdir(parents=True, exist_ok=True)
    for folder in Path(projects.ROOT).iterdir():
        file = folder / "project.json"
        if file.exists():
            shutil.copy2(file, target / f"{folder.name}.json")
    return target


def analyse_library(everything=False, progress=None):
    """Analyses builds in the library. Returns {'done', 'skipped', 'failed': [(id, reason)]}."""
    summary = {"done": 0, "skipped": 0, "failed": []}
    todo = [p for p in projects.all_projects() if p.get("versions")]
    for number, project in enumerate(todo, 1):
        if not everything and not analyse.needs_analysis(project):
            summary["skipped"] += 1
            continue
        try:
            blocks = projects.load_version(project["id"], project["versions"][-1]["number"])["blocks"]
            analyse.analyse_project(project, blocks)
            projects.save(project)
            summary["done"] += 1
        except Exception as problem:  # one strange build mustn't stop the rest
            summary["failed"].append((project["id"], str(problem)[:120]))
        if progress:
            progress(number, len(todo))
    cluster_pass()
    return summary


_run = {"running": False, "done": 0, "total": 0, "failed": 0}
_run_lock = threading.Lock()


def run_status():
    return dict(_run)


def start_analyse():
    """Analyses new builds in the background (the dashboard button). Returns False when a run is already going."""
    with _run_lock:
        if _run["running"]:
            return False
        _run.update(running=True, done=0, total=0, failed=0)

    def work():
        try:
            def progress(done, total):
                _run.update(done=done, total=total)
            result = analyse_library(False, progress)
            _run["failed"] = len(result["failed"])
        finally:
            _run["running"] = False
    threading.Thread(target=work, daemon=True).start()
    return True


def cluster_pass():
    """Rarity needs to know how scarce a kind is: the only one of its type and species scores higher. Re-proposes rarity for
    analysed builds (decisions by the author are left alone)."""
    items = [p for p in projects.all_projects() if p.get("analysis")]
    kind = lambda p: (facets.get(p, "type"), facets.get(p, "species") or facets.get(p, "subtype") or "")
    sizes = Counter(kind(p) for p in items)
    changed = 0
    for project in items:
        m = project["analysis"]["measures"]
        if "rarity_points" not in m:
            continue
        points = m["rarity_points"] + analyse.scarcity_points(sizes[kind(project)])
        why = f"{points} points: {analyse.rarity_points(m, analyse.scarcity_points(sizes[kind(project)]))[1]}"
        before = (facets.record(project, "rarity") or {}).get("value")
        if facets.propose(project, "rarity", analyse.rarity_name(points), "rule", 0.5, why, analyse.VERSION):
            if (facets.record(project, "rarity") or {}).get("value") != before:
                changed += 1
            projects.save(project)
    return changed


def wrong_categories():
    """Imports whose kind is 'ruin' although neither the name nor the content says so. Returns [(project, new kind)]."""
    out = []
    for project in projects.all_projects():
        if project.get("mode") != "import" or project.get("category") != "ruin" or not project.get("analysis"):
            continue
        words = naming.slug(naming.source_path(project.get("source")) + " " + project.get("title", "")).split("-")
        if any(w.startswith("ruin") or w == "remains" for w in words):
            continue
        new = analyse.OLD_KIND.get(facets.get(project, "type") or "Other", "other")
        if new != project["category"]:
            out.append((project, new))
    return out


def fix_categories(apply=False):
    """Returns Counter of (old kind -> new kind). With apply, backs up and writes; the old kind stays in category_was."""
    found = wrong_categories()
    if apply and found:
        backup_projects("categories")
        for project, new in found:
            project["category_was"] = project["category"]
            project["category"] = new
            projects.save(project)
    return Counter(new for _, new in found)


def summary():
    items = [p for p in projects.all_projects() if p.get("versions")]
    out = {"builds": len(items), "analysed": sum(1 for p in items if p.get("analysis"))}
    out["types"] = Counter(facets.get(p, "type") or "(none)" for p in items)
    out["review"] = Counter(facets.review_of(p)["state"] for p in items)
    out["sure"] = sum(1 for p in items if p.get("analysis") and facets.confidence_of(p) >= 0.8)
    out["unsure"] = sum(1 for p in items if p.get("analysis") and facets.confidence_of(p) < 0.5)
    return out


def main(argv):
    command = argv[1] if len(argv) > 1 else "summary"
    flags = set(argv[2:])
    if command == "analyse":
        backup = backup_projects("triage")
        print("backup:", backup)
        result = analyse_library("--all" in flags, lambda i, n: print(f"\r{i}/{n}", end="") if i % 25 == 0 else None)
        print(f"\ndone {result['done']}, skipped {result['skipped']}, failed {len(result['failed'])}")
        for failure in result["failed"][:10]:
            print("  failed:", failure)
    elif command == "numbers":
        import exporter
        if "--import" in argv:
            adopted, skipped = exporter.import_numbers(argv[argv.index("--import") + 1])
            print(f"adopted {adopted} numbers, skipped {skipped}")
        print(f"{len(exporter.load_numbers())} model numbers in {exporter.numbers_file()}")
    elif command == "categories":
        fixed = fix_categories("--apply" in flags)
        print(("changed" if "--apply" in flags else "would change"), dict(fixed))
    else:
        for key, value in summary().items():
            print(key, dict(value) if isinstance(value, Counter) else value)


if __name__ == "__main__":
    main(sys.argv)
