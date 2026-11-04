"""Single files in the Library: textures, sounds, JSON data, models.

A file asset is a folder in the library with project.json (mode "file") and the file itself, so backup, tags, favorites,
collections, licences, search and delete work exactly as for builds. Saved from Look inside (an archive's files) or added
directly. The same content is only kept once.
"""
import hashlib
import re
import struct
import time
from pathlib import Path

import explore
import library
import naming
import projects

MAX_BYTES = 32 * 1024 * 1024
# explore type -> (Library kind, short label). Structures go through Import builds; code, shaders and other files are not kept.
KINDS = {
    "texture": ("texture", "PNG"), "model": ("model", "MODEL"), "sound": ("sound", "OGG"),
    "loot": ("data", "LOOT"), "recipe": ("data", "RECIPE"), "advancement": ("data", "ADV"), "tag": ("data", "TAG"),
    "worldgen": ("data", "WORLD"), "function": ("data", "FUNC"), "data": ("data", "DATA"), "text": ("data", "TEXT"),
}
FILE_KINDS = ("texture", "model", "sound", "data")
CONTENT_TYPES = {".png": "image/png", ".ogg": "audio/ogg", ".json": "application/json", ".mcmeta": "application/json", ".mcfunction": "text/plain", ".txt": "text/plain"}


class FileProblem(Exception):
    pass


def png_size(data):
    if data[:8] == bytes([137, 80, 78, 71, 13, 10, 26, 10]) and len(data) >= 24:
        return struct.unpack(">II", data[16:24])
    return None


def saveable(kind):
    return kind in KINDS


def _title(path):
    stem = Path(path).stem
    return " ".join(w for w in re.split(r"[_\-\s]+", stem) if w)[:60] or "file"


def _tags(path, kind, ftype):
    parts = re.split(r"[/_\-.]+", path.lower())
    words = [w for w in parts if len(w) > 2 and w not in naming.FILLER and w not in ("png", "json", "ogg", "assets", "data", "textures", "models")]
    return sorted({ftype, kind, *words[:8]})[:12]


def create(name, data, archive_name, path, details, batch=None, ftype=None):
    """Saves one file. Returns the new asset's id, or None when this exact content is already in the Library."""
    ftype = ftype or explore.classify(path)
    if not saveable(ftype):
        raise FileProblem(f"{path}: this kind of file is not kept in the Library.")
    if len(data) > MAX_BYTES:
        raise FileProblem(f"{path} is over the 32 MB limit.")
    digest = "f" + hashlib.sha1(data).hexdigest()
    if library.has_hash(digest):
        return None
    kind, _ = KINDS[ftype]
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", Path(path).name)[:100] or "file"
    meta = {"name": safe, "bytes": len(data), "type": ftype, "path": path, "ext": Path(safe).suffix.lower()}
    dims = png_size(data) if meta["ext"] == ".png" else None
    if dims:
        meta["width"], meta["height"] = dims
    credit = {k: str(details.get(k, "")).strip()[:500] for k in ("creator", "permission", "licence", "link")}
    project = projects.new_file(_title(path), {"type": "archive", "name": archive_name, "item": path}, credit, kind, meta, data)
    project.update(hash=digest, subject=naming.slug(_title(path)), tags=_tags(path, kind, ftype), batch=batch)
    projects.save(project)
    return project["id"]


def save_from_archive(source, archive_name, paths, details):
    """Saves picked files out of an archive. Returns {"ids", "duplicates", "skipped": [{path, reason}], "batch"}."""
    picked = list(dict.fromkeys(str(p) for p in paths))[:2000]
    batch = "b" + time.strftime("%Y%m%d_%H%M%S") if len(picked) > 1 else None
    ids, duplicates, skipped = [], 0, []
    for path in picked:
        ftype = explore.classify(path)
        if not saveable(ftype):
            skipped.append({"path": path, "reason": "structures go through Import builds" if ftype == "structure" else "this kind of file isn't kept"})
            continue
        try:
            data = explore.read(source, path, MAX_BYTES)
            made = create(Path(path).name, data, archive_name, path, details, batch, ftype)
        except (explore.ExploreProblem, FileProblem) as problem:
            skipped.append({"path": path, "reason": str(problem)})
            continue
        if made is None:
            duplicates += 1
        else:
            ids.append(made)
    library.flush()
    return {"ids": ids, "duplicates": duplicates, "skipped": skipped, "batch": batch if ids else None}


def content_type(project):
    return CONTENT_TYPES.get((project.get("file") or {}).get("ext", ""), "application/octet-stream")
