"""The library: a quick index of every asset, search, saved variants, and bulk actions.

The index keeps one small row per asset in memory and in index.json, so listing thousands doesn't open thousands of files.
It's checked against the folders at start, so it can't drift after a crash.
"""
import hashlib
import json
import shutil
import threading
from pathlib import Path

import facets as facet_book
import licences
import naming
import organise
import projects
import review as review_rules

PAGE = 100
_rows = None
_lock = threading.RLock()
_flush_timer = None


def index_file():
    return Path(projects.ROOT) / "index.json"


def block_hash(blocks):
    return hashlib.sha1(json.dumps(blocks, separators=(",", ":")).encode("utf-8")).hexdigest()


def latest_stats(project):
    return project["versions"][-1]["check"]["stats"] if project.get("versions") else None


def _file_size(project):
    file = project.get("file")
    if not file:
        return ""
    if file.get("width"):
        return f"{file['width']}x{file['height']} px"
    size = file.get("bytes", 0)
    return f"{size / 1048576:.1f} MB" if size > 1048576 else f"{max(1, round(size / 1024))} KB"


def organiser_fields(project):
    """What the organiser knows about an asset, as flat index fields: believed facet values, review state, how sure the machine is,
    measured numbers and fingerprints. Empty for assets that haven't been analysed yet."""
    analysis = project.get("analysis") or {}
    measures, prints = analysis.get("measures") or {}, analysis.get("prints") or {}
    out = {"review": facet_book.review_of(project)["state"], "analysed": bool(analysis), "conf": facet_book.confidence_of(project) if analysis else 0,
           "undecided": len(facet_book.undecided(project)), "fp_exact": prints.get("exact", ""), "fp_shape": prints.get("shape", ""),
           "fp_palette": prints.get("palette", ""), "decided": facet_book.review_of(project).get("decided_at", 0), "rights": review_rules.rights_of(project), "analysis_error": bool(project.get("analysis_error")), "parts": measures.get("parts", 1), "w": measures.get("w", 0), "h": measures.get("h", 0), "d": measures.get("d", 0)}
    for name in ("type", "subtype", "species", "realm", "style", "palette", "setting", "scale", "complexity", "rarity", "kind"):
        out["f_" + name] = facet_book.get(project, name) or ""
    return out


def row_of(project):
    latest = project["versions"][-1] if project.get("versions") else None
    credit = project.get("credit") or {}
    licence = licences.of_project(project)
    return {
        "id": project["id"], "title": project.get("title", ""), "kind": project.get("category", "tree"), "mode": project.get("mode", "ai"),
        "status": project.get("status", "review"), "stage": project.get("stage"), "saved": project.get("saved", []), "versions": len(project.get("versions", [])),
        "creator": credit.get("creator", ""), "licence": credit.get("licence", ""), "favorite": bool(project.get("favorite")), "collections": project.get("collections", []), "licence_status": licence["status"], "can_modify": licence["can_modify"], "batch": project.get("batch"), "tags": project.get("tags", []),
        "ftype": (project.get("file") or {}).get("type", ""), "bytes": (project.get("file") or {}).get("bytes", 0),
        "source": (project.get("source") or {}).get("name", ""), "not_vanilla": bool(latest and latest["check"]["errors"]),
        "blocks": latest["check"]["stats"]["blocks"] if latest else 0,
        "size": f"{latest['check']['stats']['width_x']}x{latest['check']['stats']['height']}x{latest['check']['stats']['width_z']}" if latest else _file_size(project),
        "hash": project.get("hash"), "created": project.get("created", 0), "busy": project.get("busy", False),
        **organiser_fields(project),
    }


def _load():
    """Builds the rows once: from the saved index, then re-reads anything newer or missing."""
    global _rows
    with _lock:
        if _rows is not None:
            return _rows
        rows, stamp = {}, 0
        file = index_file()
        if file.exists():
            try:
                rows = json.loads(file.read_text(encoding="utf-8"))
                stamp = file.stat().st_mtime
            except (OSError, ValueError):
                rows = {}
        root = Path(projects.ROOT)
        found = set()
        if root.is_dir():
            for folder in root.iterdir():
                project_file = folder / "project.json"
                if project_file.exists():
                    found.add(folder.name)
                    if folder.name not in rows or project_file.stat().st_mtime > stamp or "collections" not in rows[folder.name] or "review" not in rows[folder.name] or "rights" not in rows[folder.name] or "parts" not in rows[folder.name]:
                        rows[folder.name] = row_of(projects.load(folder.name))
        _rows = {k: v for k, v in rows.items() if k in found}
        return _rows


def _changed():
    """Writes the index a moment later, so a batch of 1,000 imports doesn't write it 1,000 times."""
    global _flush_timer
    if _flush_timer is None:
        _flush_timer = threading.Timer(2.0, flush)
        _flush_timer.daemon = True
        _flush_timer.start()


def flush():
    global _flush_timer
    with _lock:
        _flush_timer = None
        if _rows is None or not Path(projects.ROOT).is_dir():
            return
        temp = index_file().with_suffix(".tmp")
        temp.write_text(json.dumps(_rows), encoding="utf-8")
        temp.replace(index_file())


def _on_save(project):
    with _lock:
        if _rows is not None:
            _rows[project["id"]] = row_of(project)
            _changed()


def _on_delete(project_id):
    with _lock:
        if _rows is not None:
            _rows.pop(project_id, None)
            _changed()


projects.listeners.append(_on_save)
projects.delete_listeners.append(_on_delete)


def reset():
    """Forgets the rows, for tests that change folder."""
    global _rows
    with _lock:
        _rows = None


def all_rows():
    """Every index row (a copy of the list; the rows themselves are shared, so don't change them)."""
    return list(_load().values())


def has_hash(value):
    return any(r.get("hash") == value for r in _load().values())


def used_names():
    return {s["name"] for r in _load().values() for s in r.get("saved", [])}


def search(words="", kind="", status="", batch="", no_credit=False, not_vanilla=False, page=0, licence="", tag="", collection="", favorite=False):
    rows = _load().values()
    terms = str(words).lower().split()
    out = []
    for row in rows:
        if kind and row["kind"] != kind or status and row["status"] != status or batch and row.get("batch") != batch:
            continue
        if licence == "check" and row["licence_status"] not in ("limited", "unknown"):
            continue
        if licence and licence != "check" and row["licence_status"] != licence:
            continue
        if favorite and not row["favorite"]:
            continue
        if tag and tag not in row["tags"]:
            continue
        if collection and not any(c.casefold() == collection.casefold() for c in row["collections"]):
            continue
        if no_credit and (row["creator"] or row["mode"] not in ("import", "file")):
            continue
        if not_vanilla and not row["not_vanilla"]:
            continue
        if terms:
            text = " ".join([row["title"], row["kind"], row["source"], row["creator"], " ".join(row["tags"]), " ".join(row["collections"]), row["licence"], " ".join(s["name"] for s in row["saved"])]).lower()
            if not all(t in text for t in terms):
                continue
        out.append(row)
    out.sort(key=lambda r: (-r["created"], r["title"]))
    start = max(int(page), 0) * PAGE
    return {"rows": out[start:start + PAGE], "total": len(out), "page": max(int(page), 0), "page_size": PAGE, "ids": [r["id"] for r in out]}


def facets():
    rows = _load().values()
    tags, groups = {}, {}
    for r in rows:
        for t in r["tags"]:
            tags[t] = tags.get(t, 0) + 1
        for c in r["collections"]:
            groups[c] = groups.get(c, 0) + 1
    common = sorted(tags.items(), key=lambda kv: (-kv[1], kv[0]))[:60]
    return {"kinds": sorted({r["kind"] for r in rows}), "batches": sorted({r["batch"] for r in rows if r.get("batch")}, reverse=True),
            "tags": [{"name": t, "count": n} for t, n in sorted(common)], "collections": [{"name": c, "count": n} for c, n in sorted(groups.items(), key=lambda kv: kv[0].casefold())]}


def collection_ids(name):
    return [r["id"] for r in _load().values() if any(c.casefold() == str(name).casefold() for c in r["collections"])]


def propose(project, number=None):
    """The proposed name for a version. A version that's already saved keeps its name."""
    version = next(v for v in project["versions"] if number is None or v["number"] == number)
    if number is None:
        version = project["versions"][-1]
    for entry in project.get("saved", []):
        if entry["version"] == version["number"]:
            return entry["name"]
    subject = project.get("subject") or naming.subject_from(project.get("source"), project.get("title", ""))
    base = naming.base_name(project.get("category", "tree"), subject, version["check"]["stats"], project.get("source"), project.get("credit"))
    return naming.next_free(base, used_names())


def propose_styled(project, number, style):
    """A new descriptive name for a version (see naming.STYLES). Free of every saved name except the version's own."""
    version = next(v for v in project["versions"] if v["number"] == number)
    own = next((s["name"] for s in project.get("saved", []) if s["version"] == number), None)
    subject = project.get("subject") or naming.subject_from(project.get("source"), project.get("title", ""))
    name = naming.styled_name(style, project.get("category", "tree"), subject, version["check"]["stats"], project.get("source"), project.get("credit"), number)
    return naming.unique(name, used_names() - ({own} if own else set()))


def save_variant(project_id, number, name=None):
    """Saves a version under a name. Saving it again renames it. Each saved version is one variant of the asset."""
    with _lock:
        project = projects.load(project_id)
        if not any(v["number"] == number for v in project["versions"]):
            raise KeyError("unknown version")
        existing = next((s for s in project["saved"] if s["version"] == number), None)
        wanted = naming.clean(name) if name else (existing["name"] if existing else propose(project, number))
        taken = used_names() - ({existing["name"]} if existing else set())
        final = naming.unique(wanted or propose(project, number), taken)
        if existing:
            existing["name"] = final
        else:
            project["saved"].append({"version": number, "name": final})
        project.update(saved_version=number, stage="saved", status="saved")
        projects.save(project)
        return final


def move_old_folder(old):
    """Moves assets from the old folder inside the repo. Never overwrites."""
    old = Path(old)
    if not old.is_dir():
        return 0
    moved = 0
    Path(projects.ROOT).mkdir(parents=True, exist_ok=True)
    for folder in list(old.iterdir()):
        target = Path(projects.ROOT) / folder.name
        if (folder / "project.json").exists() and not target.exists():
            shutil.move(str(folder), str(target))
            moved += 1
    if not any(old.iterdir()):
        old.rmdir()
    return moved
