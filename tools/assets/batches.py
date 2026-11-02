"""Imports many builds in the background. A bad file doesn't stop the rest: it's listed with its reason."""
import json
import threading
import time
from pathlib import Path

import import_formats
import library
import naming
import pipeline
import projects

_lock = threading.RLock()


def _folder():
    folder = Path(projects.ROOT) / "_batches"
    folder.mkdir(parents=True, exist_ok=True)
    return folder


def save(batch):
    file = _folder() / f"{batch['id']}.json"
    temp = file.with_suffix(".tmp")
    with _lock:  # Windows can't replace a file while another thread reads it
        temp.write_text(json.dumps(batch, ensure_ascii=False), encoding="utf-8")
        temp.replace(file)


def load(batch_id):
    if not projects.ID_RULE.fullmatch(batch_id or ""):
        raise KeyError("unknown batch")
    file = _folder() / f"{batch_id}.json"
    with _lock:
        if not file.exists():
            raise KeyError("unknown batch")
        return json.loads(file.read_text(encoding="utf-8"))


def title_from(subject):
    return subject.replace("-", " ").capitalize()[:60] or "Unnamed"


def add_build(result, source, details, batch_id=None, title=""):
    """Turns one read build into a library asset. Returns its id, or None if the same blocks are already there."""
    tree = {"blocks": result["blocks"]}
    value = library.block_hash(result["blocks"])
    if library.has_hash(value):
        return None
    check = pipeline.inspect(tree, lenient=True)
    counts = check["stats"]["block_counts"]
    path = naming.source_path(source)
    kind = details.get("kind") or naming.guess_kind(path, counts)
    subject = naming.subject_from(source, title)
    credit = {k: str(details.get(k, "")).strip()[:500] for k in ("creator", "permission", "licence")}
    tags = sorted({w for w in naming.slug(path).split("-") if len(w) > 2 and w not in naming.FILLER} | set(naming.materials(counts).split("-")))[:12]
    project = projects.new_import(title or title_from(subject), source, credit, kind)
    project.update(hash=value, subject=subject, tags=tags, batch=batch_id)
    number = 1
    projects.save_version(project["id"], number, None, tree)
    project["versions"].append({"number": number, "edits": [], "check": check, "dropped": result["dropped"], "notes": result["notes"]})
    projects.save(project)
    return project["id"]


def start(sources, details, failed_early=()):
    """sources: [{"name", "path", "items": [ids]}]. path is a temporary upload, removed afterwards."""
    total = sum(len(s["items"]) for s in sources)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    with _lock:
        batch_id, count = "b" + stamp, 1
        while (_folder() / f"{batch_id}.json").exists():
            count += 1
            batch_id = f"b{stamp}_{count}"
        batch = {"id": batch_id, "created": time.time(), "running": True, "total": total, "done": 0, "imported": [], "duplicates": [],
                 "failed": [dict(f) for f in failed_early], "current": ""}
        save(batch)

    def work():
        last = 0
        for source in sources:
            for item in source["items"]:
                label = f"{source['name']}: {item}" if item else source["name"]
                batch["current"] = label
                try:
                    result = import_formats.read_item(Path(source["path"]), item)
                    made = add_build(result, {"type": "file", "name": source["name"], "item": item}, details, batch_id)
                    if made:
                        batch["imported"].append(made)
                    else:
                        batch["duplicates"].append(label)
                except import_formats.ImportProblem as problem:
                    batch["failed"].append({"label": label, "reason": str(problem)})
                except Exception as problem:  # one strange file mustn't stop the batch
                    batch["failed"].append({"label": label, "reason": f"Couldn't read it: {problem}"})
                batch["done"] += 1
                if time.time() - last > 0.5:
                    last = time.time()
                    save(batch)
            Path(source["path"]).unlink(missing_ok=True)
        batch.update(running=False, current="")
        save(batch)
        library.flush()
    threading.Thread(target=work, daemon=True).start()
    return batch_id
