"""Looks inside a datapack, resource pack, mod or any .zip / .jar: lists every file by type, previews one, and exports a pick.

Nothing is ever unpacked to disk by the listing or a preview. Files are read one at a time from the archive by their exact
name, with size caps, and an export is a new zip with the same paths. Nothing inside is ever run (.class files are only counted).
"""
import io
import re
import zipfile
from pathlib import Path

MAX_ENTRIES = 20000
MAX_PREVIEW = 400 * 1024        # text shown on screen
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_EXPORT_BYTES = 256 * 1024 * 1024

# (type, label, path pattern); the first match wins
TYPES = (
    ("structure", "Structures", r"^data/[^/]+/structures?/.+\.(nbt|snbt)$"),
    ("loot", "Loot tables", r"^data/[^/]+/loot_tables?/.+\.json$"),
    ("recipe", "Recipes", r"^data/[^/]+/recipes?/.+\.json$"),
    ("advancement", "Advancements", r"^data/[^/]+/advancements?/.+\.json$"),
    ("tag", "Tags", r"^data/[^/]+/tags/.+\.json$"),
    ("worldgen", "World generation", r"^data/[^/]+/(worldgen|dimension|dimension_type)/.+\.json$"),
    ("function", "Functions", r"^data/[^/]+/functions?/.+\.mcfunction$"),
    ("data", "Other data", r"^data/.+"),
    ("texture", "Textures", r"^assets/[^/]+/textures/.+\.(png|mcmeta)$"),
    ("model", "Models", r"^assets/[^/]+/(models|blockstates|items|equipment)/.+\.json$"),
    ("sound", "Sounds", r"^assets/[^/]+/sounds(/.+\.ogg|\.json)$"),
    ("text", "Languages and fonts", r"^assets/[^/]+/(lang|font|texts)/.+"),
    ("shader", "Shaders", r"^assets/[^/]+/(shaders|post_effect|atlases|particles)/.+"),
    ("info", "Pack info", r"^(pack\.mcmeta|pack\.png|fabric\.mod\.json|quilt\.mod\.json|META-INF/.*|.*\.toml|credits.*|licen[cs]e.*|readme.*)$"),
    ("code", "Code (not extracted)", r".*\.(class|java|kt)$"),
)
_COMPILED = [(key, label, re.compile(pattern, re.I)) for key, label, pattern in TYPES]
LABELS = {key: label for key, label, _ in TYPES} | {"other": "Other files"}
TEXT = re.compile(r"\.(json|mcmeta|mcfunction|snbt|toml|txt|md|properties|cfg|lang|fsh|vsh|glsl)$|^(licen[cs]e|readme|credits)", re.I)


class ExploreProblem(Exception):
    pass


WRAPPED = re.compile(r"^[^/]+/((?:data|assets)/.+|pack\.mcmeta|fabric\.mod\.json|readme.*|licen[cs]e.*|credits.*)$", re.I)


def classify(path):
    """The type of a file. A repository download wraps everything in one folder (repo-abc123/data/...), which is looked through."""
    inner = WRAPPED.match(path)
    for target in ([inner.group(1)] if inner else []) + [path]:
        for key, _, pattern in _COMPILED:
            if pattern.match(target):
                return key
    return "other"


def _safe(path):
    """A name that is a plain relative path inside an archive: no drive, no '..', no leading slash."""
    parts = path.replace("\\", "/").split("/")
    return bool(path) and not path.startswith("/") and ".." not in parts and ":" not in parts[0]


def open_archive(source):
    if isinstance(source, zipfile.ZipFile):
        return source
    try:
        return zipfile.ZipFile(source if isinstance(source, Path) else io.BytesIO(source))
    except (zipfile.BadZipFile, OSError) as problem:
        raise ExploreProblem("This isn't a .zip or .jar archive, or it is damaged.") from problem


def listing(source):
    """{"entries": [{path, type, size}], "types": [{type, label, count}], "total", "truncated", "namespaces"}"""
    archive = open_archive(source)
    entries, counts, spaces = [], {}, set()
    infos = [i for i in archive.infolist() if not i.is_dir()]
    for info in infos[:MAX_ENTRIES]:
        path = info.filename.replace("\\", "/")
        if not _safe(path):
            continue
        kind = classify(path)
        counts[kind] = counts.get(kind, 0) + 1
        found = re.match(r"^(?:data|assets)/([^/]+)/", path)
        if found:
            spaces.add(found.group(1))
        entries.append({"path": path, "type": kind, "size": info.file_size})
    order = [key for key, _, _ in _COMPILED] + ["other"]
    types = [{"type": k, "label": LABELS[k], "count": counts[k]} for k in order if k in counts]
    entries.sort(key=lambda e: (order.index(e["type"]), e["path"]))
    return {"entries": entries, "types": types, "total": len(infos), "truncated": len(infos) > MAX_ENTRIES, "namespaces": sorted(spaces)}


def read(source, path, limit=MAX_FILE_BYTES):
    """The bytes of one file, by its exact name."""
    archive = open_archive(source)
    try:
        info = archive.getinfo(path)
    except KeyError as problem:
        raise ExploreProblem("That file isn't in this archive.") from problem
    if info.is_dir() or not _safe(info.filename) or info.file_size > limit:
        raise ExploreProblem("That file is too big to open here." if info.file_size > limit else "That isn't a file.")
    return archive.read(info)


def preview(source, path):
    """What to show for a file: {"kind": "image"|"text"|"sound"|"none", ...}."""
    lower = path.lower()
    if lower.endswith(".png"):
        return {"kind": "image"}
    if lower.endswith(".ogg"):
        return {"kind": "sound"}
    if TEXT.search(lower) or classify(path) in ("data", "loot", "recipe", "advancement", "tag", "worldgen", "function", "model"):
        data = read(source, path, MAX_PREVIEW * 4)
        text = data[:MAX_PREVIEW].decode("utf-8", errors="replace")
        return {"kind": "text", "text": text, "cut": len(data) > MAX_PREVIEW}
    return {"kind": "none"}


def export(source, paths, keep_folders=True):
    """A new zip (bytes) of the picked files, under the same paths, or flat when keep_folders is False."""
    archive = open_archive(source)
    out, total, used = io.BytesIO(), 0, set()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as target:
        for path in list(dict.fromkeys(paths))[:MAX_ENTRIES]:
            if not _safe(str(path)):
                raise ExploreProblem("One of the files has an unsafe name, so nothing was exported.")
            try:
                info = archive.getinfo(path)
            except KeyError as problem:
                raise ExploreProblem(f"{path} isn't in this archive.") from problem
            if info.is_dir():
                continue
            total += info.file_size
            if info.file_size > MAX_FILE_BYTES or total > MAX_EXPORT_BYTES:
                raise ExploreProblem("The pick is over the size limit (256 MB). Pick fewer files.")
            name = path if keep_folders else Path(path).name
            while name in used:  # flat export: two files can share a name
                name = f"{Path(path).parent.name}_{name}"
            used.add(name)
            target.writestr(name, archive.read(info))
    return out.getvalue()
