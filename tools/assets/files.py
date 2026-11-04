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


# ---- what a file screen shows besides the file ----
def ogg_info(data):
    """Facts about an Ogg Vorbis sound: seconds, sample rate, channels, kilobits per second. Any may be None."""
    start = data.find(b"\x01vorbis")
    info = {"seconds": ogg_seconds(data), "rate": None, "channels": None, "kbps": None}
    if start >= 0 and start + 16 <= len(data):
        info["channels"] = data[start + 11]
        info["rate"] = struct.unpack("<I", data[start + 12:start + 16])[0]
    if info["seconds"]:
        info["kbps"] = round(len(data) * 8 / info["seconds"] / 1000)
    return info


def texture_info(data):
    """Facts about a PNG: size, colours, how much is see-through, and how many animation frames a tall strip holds."""
    from PIL import Image
    import io
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    pixels = list(image.getdata())
    clear = sum(1 for p in pixels if p[3] == 0)
    frames = image.height // image.width if image.width and image.height % image.width == 0 and image.height > image.width else 1
    notes = []
    if image.width != image.height and frames == 1:
        notes.append("Not square: block and item textures are normally square.")
    if image.width & (image.width - 1) or image.height & (image.height - 1):
        if frames == 1:
            notes.append("The size is not a power of two (16, 32, 64...).")
    return {"width": image.width, "height": image.height, "colours": len(set(pixels)), "clear_percent": round(100 * clear / max(len(pixels), 1)),
            "frames": frames, "frame_size": image.width if frames > 1 else None, "notes": notes}


def _locations(value, found, key=""):
    """Names like minecraft:apple that sit under keys which hold an item or block."""
    if isinstance(value, dict):
        kind = str(value.get("type", ""))
        for k, v in value.items():
            if k in ("name", "item", "id", "block") and isinstance(v, str) and v.startswith("minecraft:") and not v.startswith("#") and ("item" in kind or "block" in kind or not kind or k != "name"):
                found.append(v)
            else:
                _locations(v, found, k)
    elif isinstance(value, list):
        for item in value:
            _locations(item, found, key)


def json_check(text, ftype, known=None):
    """Is this JSON valid, laid out readably, and does it look right for its type? Returns {ok, error, pretty, notes}."""
    try:
        content = json.loads(text.lstrip("\ufeff"))
    except ValueError as problem:
        return {"ok": False, "error": f"Not valid JSON: {problem}", "pretty": None, "notes": []}
    notes = []
    if isinstance(content, dict):
        required = {"loot": ("pools",), "recipe": ("type",), "advancement": ("criteria",), "tag": ("values",)}.get(ftype, ())
        for key in required:
            if key not in content:
                notes.append(f'A {ftype} file normally has "{key}", and this one doesn\'t.')
        if ftype == "loot" and isinstance(content.get("pools"), list):
            for number, pool in enumerate(content["pools"], 1):
                if not isinstance(pool, dict) or "rolls" not in pool or "entries" not in pool:
                    notes.append(f'Pool {number} needs "rolls" and "entries".')
        if ftype == "model" and not any(k in content for k in ("parent", "elements", "variants", "multipart")):
            notes.append('A model normally has "parent" or "elements".')
        if known:
            names = []
            _locations(content, names)
            unknown = sorted({n for n in names if n[len("minecraft:"):] not in known})
            if unknown:
                notes.append("Not found as a block or item in this game version (a typo?): " + ", ".join(unknown[:8]))
    return {"ok": True, "error": None, "pretty": json.dumps(content, indent=2, ensure_ascii=False), "notes": notes}


def info(project, jar=None):
    """The facts and checks for a file asset's screen, by kind."""
    path = projects.file_path(project["id"])
    file = project.get("file") or {}
    if not path:
        raise FileProblem("That asset has no file.")
    data = path.read_bytes()
    if file.get("ext") == ".ogg":
        return {"kind": "sound", **ogg_info(data)}
    if file.get("ext") == ".png":
        try:
            return {"kind": "texture", **texture_info(data)}
        except Exception as problem:
            return {"kind": "texture", "error": f"This PNG can't be read: {problem}"}
    if file.get("ext") in (".json", ".mcmeta"):
        known = jar.known_ids() if jar else None
        if len(data) > 1024 * 1024:
            return {"kind": "json", "ok": True, "error": None, "pretty": None, "notes": ["Too big to lay out here."]}
        return {"kind": "json", **json_check(data.decode("utf-8", errors="replace"), file.get("type", ""), known)}
    return {"kind": "other"}


def model_preview(project, jar):
    """A block list the 3D viewer can draw for a model file: {data: {blocks, states}, missing, note}."""
    path = projects.file_path(project["id"])
    if not path:
        raise FileProblem("That asset has no file.")
    try:
        content = json.loads(path.read_text(encoding="utf-8-sig"))
    except ValueError as problem:
        raise FileProblem(f"Not valid JSON, so there is nothing to draw: {problem}") from problem
    if not isinstance(content, dict):
        raise FileProblem("A model file is a JSON object.")
    try:
        baked, missing = jar.bake_model(content)
    except (KeyError, OSError) as problem:
        raise FileProblem(f"The model's parent \"{content.get('parent')}\" isn't in the game, so it can't be drawn here.") from problem
    note = ""
    if baked["kind"] == "invisible":
        note = "This model has no shapes to draw."
    elif missing:
        note = "Textures not in the game (shown pink): " + ", ".join(missing[:6])
    return {"data": {"blocks": [[0, 0, 0, "preview"]], "states": {"preview": baked}}, "missing": missing, "note": note}
