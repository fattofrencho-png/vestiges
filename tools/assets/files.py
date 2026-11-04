"""Single files in the Library: textures, sounds, JSON data, models.

A file asset is a folder in the library with project.json (mode "file") and the file itself, so backup, tags, favorites,
collections, licences, search and delete work exactly as for builds. Saved from Look inside (an archive's files) or added
directly. The same content is only kept once.
"""
import hashlib
import json
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


# ---- the author's own files ----
OWN_EXTENSIONS = {".png": "texture", ".ogg": "sound", ".json": None, ".mcmeta": None, ".mcfunction": "function", ".txt": "text", ".lang": "text"}
KIND_CHOICES = {"texture": "texture", "sound": "sound", "model": "model", "data": "data"}


def ogg_seconds(data):
    """Length of an Ogg Vorbis sound in seconds, read from its headers, or None."""
    start = data.find(b"\x01vorbis")
    last = data.rfind(b"OggS")
    if start < 0 or last < 0 or start + 16 > len(data) or last + 14 > len(data):
        return None
    rate = struct.unpack("<I", data[start + 12:start + 16])[0]
    granule = struct.unpack("<q", data[last + 6:last + 14])[0]
    return round(granule / rate, 2) if rate and granule > 0 else None


def guess_type(name, path, data, forced=""):
    """The explore type for a file the author brings: from a path like assets/.../textures/, then the extension, then the content."""
    wanted = {"texture": "texture", "sound": "sound", "model": "model", "data": "data"}.get(forced)
    if wanted:
        return wanted
    by_path = explore.classify(path.replace("\\", "/")) if "/" in path.replace("\\", "/") else "other"
    if saveable(by_path):
        return by_path
    ext = Path(name).suffix.lower()
    if ext not in OWN_EXTENSIONS:
        raise FileProblem(f"{name}: only .png, .ogg, .json and text files can be added. Zip files open in Look inside.")
    if OWN_EXTENSIONS[ext]:
        return OWN_EXTENSIONS[ext]
    try:
        content = json.loads(data.decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        return "data"
    if isinstance(content, dict):
        if "elements" in content or "parent" in content or "textures" in content or "variants" in content or "multipart" in content:
            return "model"
        if "pools" in content:
            return "loot"
        if "ingredients" in content or "ingredient" in content or "key" in content and "pattern" in content:
            return "recipe"
        if "criteria" in content:
            return "advancement"
        if "values" in content:
            return "tag"
    return "data"


def create_own(name, data, details, forced="", batch=None, path=""):
    """Saves a file the author brought. Returns the new asset's id, or None when this exact content is already in the Library."""
    ftype = guess_type(name, path or name, data, forced)
    shown = (path or name).replace("\\", "/")
    made = create(name, data, "my files", shown, details, batch, ftype)
    if made:
        project = projects.load(made)
        project["source"] = {"type": "own", "name": "my files", "item": shown}
        meta = project["file"]
        if meta["ext"] == ".ogg":
            seconds = ogg_seconds(data)
            if seconds:
                meta["seconds"] = seconds
        projects.save(project)
    return made


# ---- names ----
NAME_STYLES = (
    ("descriptive", "Descriptive: kind, name and size"),
    ("short", "Short: just the name"),
    ("sorted", "Sorts by size: size first, so a file list orders itself"),
)


def _bits(project):
    file = project.get("file") or {}
    words = [w for w in naming.slug(Path(file.get("name", "")).stem or project.get("title", "")).split("-") if w]
    base = "_".join(words) or "file"
    if file.get("width"):
        size = f"{file['width']}x{file['height']}"
    elif file.get("seconds"):
        size = f"{file['seconds']}s"
    else:
        size = f"{max(1, round(file.get('bytes', 0) / 1024))}kb"
    return base, size, file.get("type") or project.get("category") or "file"


def styled_name(project, style):
    """A descriptive name (without extension) for a file asset, in one of NAME_STYLES. Lowercase letters, numbers and underscores only."""
    base, size, ftype = _bits(project)
    kind = project.get("category") or "file"
    kind_part = kind if ftype == kind or kind == "data" and ftype == "data" else f"{kind}_{ftype}"
    which = style % len(NAME_STYLES)
    if which == 1:
        parts = [base]
    elif which == 2:
        digits = re.sub(r"\D", "", size.split("x")[0]) or "0"
        parts = [digits.zfill(4), base, kind]
    else:
        parts = [kind_part, base, size]
    return naming.clean("_".join(parts)).replace("-", "_")


def rename_file(project_id, new_name):
    """Renames the stored file (the extension is kept) and returns the new file name."""
    project = projects.load(project_id)
    file = project.get("file") or {}
    old = projects.file_path(project_id)
    if not old:
        raise FileProblem("That asset has no file.")
    stem = re.sub(r"[^a-z0-9_.-]+", "_", str(new_name).strip().lower()).strip("._-")[:90]
    if not stem:
        raise FileProblem("Type a name first.")
    final = stem + file.get("ext", "")
    target = old.with_name(final)
    if target != old and target.exists():
        raise FileProblem("This asset already has a file with that name.")
    old.rename(target)
    file["name"] = final
    project["file"] = file
    projects.save(project)
    return final
