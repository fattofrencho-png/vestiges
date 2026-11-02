"""Saves every asset to ~/.vestiges-assets/library/<id>/, outside git, so closing the page loses nothing.

project.json holds the request, questions, answers, brief, progress and cost. versions/vN.json.gz holds a version's
plan and blocks. Writes go to a temporary file first, so a crash can't leave half a file.
"""
import gzip
import json
import os
import re
import shutil
import threading
import time
from pathlib import Path

HOME = Path(os.environ.get("VESTIGES_ASSETS_HOME") or Path.home() / ".vestiges-assets")
ROOT = Path(os.environ.get("VESTIGES_PROJECTS") or HOME / "library")
OLD_ROOT = Path(__file__).resolve().parent / "projects"
listeners = []  # told about every saved project
delete_listeners = []
ID_RULE = re.compile(r"[a-z0-9_]{1,64}")
_lock = threading.RLock()


def _path(project_id):
    if not ID_RULE.fullmatch(project_id or ""):
        raise KeyError("unknown asset")
    return ROOT / project_id


def new(request, provider, model, budget):
    # AI assets are found in the same library as imported ones.
    with _lock:
        ROOT.mkdir(parents=True, exist_ok=True)
        stamp = time.strftime("%Y%m%d_%H%M%S")
        project_id, count = stamp, 1
        while (ROOT / project_id).exists():
            count += 1
            project_id = f"{stamp}_{count}"
        project = {
            "id": project_id, "title": "New asset", "request": request, "provider": provider, "model": model,
            "stage": "questions", "busy": True, "progress": [], "error": None,
            "questions": [], "answers": {}, "qa_history": [], "brief": None,
            "changes": [], "versions": [], "saved_version": None, "last_problems": [],
            "attempts": 0, "resume": None, "views_wanted": None, "followup_reason": None,
            "saved": [], "status": "review", "tags": [], "batch": None, "mode": "ai", "category": "tree",
            "budget": budget, "cost": 0.0, "cost_known": True, "input_tokens": 0, "output_tokens": 0,
            "created": time.time(),
        }
        (ROOT / project_id).mkdir()
        save(project)
        return project


def new_import(title, source, credit, category):
    """A project made from someone else's build. It needs no AI, so it skips straight to adapting."""
    with _lock:
        ROOT.mkdir(parents=True, exist_ok=True)
        stamp = time.strftime("%Y%m%d_%H%M%S")
        project_id, count = stamp, 1
        while (ROOT / project_id).exists():
            count += 1
            project_id = f"{stamp}_{count}"
        project = {
            "id": project_id, "mode": "import", "title": title, "request": "", "source": source, "credit": credit, "category": category,
            "provider": None, "model": None, "stage": "adapt", "busy": False, "progress": [], "error": None,
            "questions": [], "answers": {}, "qa_history": [], "brief": None, "changes": [], "versions": [], "saved_version": None,
            "last_problems": [], "attempts": 0, "resume": None, "views_wanted": None, "followup_reason": None,
            "budget": 0, "cost": 0.0, "cost_known": True, "input_tokens": 0, "output_tokens": 0, "created": time.time(),
            "saved": [], "status": "review", "tags": [], "batch": None, "hash": None, "subject": "",
        }
        (ROOT / project_id).mkdir()
        save(project)
        return project


def save(project):
    with _lock:
        folder = _path(project["id"])
        temp = folder / "project.tmp"
        temp.write_text(json.dumps(project, indent=1, ensure_ascii=False), encoding="utf-8")
        temp.replace(folder / "project.json")
    for listener in listeners:
        listener(project)


def load(project_id):
    with _lock:
        file = _path(project_id) / "project.json"
        if not file.exists():
            raise KeyError("unknown asset")
        project = json.loads(file.read_text(encoding="utf-8"))
    return _upgrade(project)


def _upgrade(project):
    """Assets saved before variants existed had one saved version and no status."""
    if "saved" not in project:
        number = project.get("saved_version")
        project["saved"] = [{"version": number, "name": f"{re.sub('[^a-z0-9]+', '-', project.get('title', 'asset').lower()).strip('-')}_var01"}] if number is not None else []
    project.setdefault("status", "saved" if project["saved"] else "review")
    project.setdefault("tags", [])
    project.setdefault("batch", None)
    project.setdefault("mode", "ai")
    return project


def all_projects():
    if not ROOT.exists():
        return []
    out = [load(folder.name) for folder in ROOT.iterdir() if (folder / "project.json").exists()]
    return sorted(out, key=lambda p: p["created"], reverse=True)


def save_version(project_id, number, plan, tree):
    folder = _path(project_id) / "versions"
    folder.mkdir(exist_ok=True)
    raw = json.dumps({"plan": plan, "blocks": tree["blocks"]}, separators=(",", ":")).encode("utf-8")
    temp = folder / f"v{number}.tmp"
    temp.write_bytes(gzip.compress(raw, mtime=0))
    temp.replace(folder / f"v{number}.json.gz")


def load_version(project_id, number):
    file = _path(project_id) / "versions" / f"v{int(number)}.json.gz"
    if not file.exists():
        raise KeyError("unknown version")
    return json.loads(gzip.decompress(file.read_bytes()))


def delete(project_id):
    with _lock:
        folder = _path(project_id)
        if (folder / "project.json").exists():
            shutil.rmtree(folder)
    for listener in delete_listeners:
        listener(project_id)
