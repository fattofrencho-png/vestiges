"""Backups of the library (~/.vestiges-assets/library/), kept in ~/.vestiges-assets/backups/.

One dated zip per backup. The app makes one at start when the library changed since the last one, keeps the newest
MAX_KEPT, and restoring first makes a "before restore" backup, so a restore can always be undone.
API keys and settings are not in the library, so they are never in a backup.
"""
import re
import shutil
import threading
import time
import zipfile
from pathlib import Path

import library
import projects

MAX_KEPT = 10        # automatic and manual backups
MAX_SAFETY = 3       # "before restore" backups
MIN_GAP_HOURS = 20   # the start-up backup waits at least this long after the last one
NAME = re.compile(r"library_\d{8}_\d{6}_(auto|manual|before-restore)\.zip")
_lock = threading.Lock()


class BackupProblem(Exception):
    pass


def folder():
    return projects.HOME / "backups"


def _asset_folders():
    root = Path(projects.ROOT)
    return [p for p in root.iterdir() if p.is_dir()] if root.is_dir() else []


def _last_change():
    """Newest modification time of anything in the library."""
    root = Path(projects.ROOT)
    newest = 0.0
    if not root.is_dir():
        return newest
    for path in root.rglob("*"):
        try:
            newest = max(newest, path.stat().st_mtime)
        except OSError:
            pass
    return newest


def create(reason="manual"):
    """Zips the whole library. Returns the backup's row, or None when there is nothing to save."""
    with _lock:
        assets = _asset_folders()
        if not assets:
            return None
        folder().mkdir(parents=True, exist_ok=True)
        name = f"library_{time.strftime('%Y%m%d_%H%M%S')}_{reason}.zip"
        while (folder() / name).exists():  # names carry seconds, so wait for the next one
            time.sleep(1.1)
            name = f"library_{time.strftime('%Y%m%d_%H%M%S')}_{reason}.zip"
        final = folder() / name
        temp = final.with_suffix(".tmp")
        root = Path(projects.ROOT)
        try:
            with zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(root.rglob("*")):
                    if path.is_file():
                        archive.write(path, path.relative_to(root).as_posix())
            temp.replace(final)
        finally:
            temp.unlink(missing_ok=True)
        _prune()
        return _row(final)


def _row(path):
    stat = path.stat()
    try:
        with zipfile.ZipFile(path) as archive:
            assets = sum(1 for n in archive.namelist() if n.count("/") == 1 and n.endswith("/project.json"))
    except zipfile.BadZipFile:
        assets = -1
    return {"name": path.name, "bytes": stat.st_size, "time": stat.st_mtime, "reason": NAME.fullmatch(path.name).group(1), "assets": assets}


def list_backups():
    if not folder().is_dir():
        return []
    rows = [_row(p) for p in folder().iterdir() if p.is_file() and NAME.fullmatch(p.name)]
    return sorted(rows, key=lambda r: r["name"], reverse=True)


def _prune():
    rows = list_backups()
    normal = [r for r in rows if r["reason"] != "before-restore"]
    safety = [r for r in rows if r["reason"] == "before-restore"]
    for row in normal[MAX_KEPT:] + safety[MAX_SAFETY:]:
        (folder() / row["name"]).unlink(missing_ok=True)


def auto_if_due():
    """The start-up backup: only when the library changed since the last backup and the last one is old enough."""
    rows = list_backups()
    if rows and time.time() - rows[0]["time"] < MIN_GAP_HOURS * 3600:
        return None
    if rows and _last_change() <= rows[0]["time"]:
        return None
    return create("auto")


def restore(name):
    """Replaces the library with a backup. Makes a 'before restore' backup first."""
    if not NAME.fullmatch(name or ""):
        raise KeyError("unknown backup")
    source = folder() / name
    if not source.is_file():
        raise KeyError("unknown backup")
    try:
        busy = any(r.get("busy") for r in library._load().values())
    except Exception:  # a damaged library is the reason to restore, so it must not block it
        busy = False
    if busy:
        raise BackupProblem("An asset is still being made. Wait for it to finish, then restore.")
    root = Path(projects.ROOT)
    staging = root.with_name(root.name + ".restoring")
    old = root.with_name(root.name + ".replaced")
    shutil.rmtree(staging, ignore_errors=True)
    shutil.rmtree(old, ignore_errors=True)
    try:
        with zipfile.ZipFile(source) as archive:
            bad = archive.testzip()
            if bad:
                raise BackupProblem(f"This backup is damaged ({bad}). Nothing was changed.")
            staging.mkdir(parents=True)
            for member in archive.infolist():
                target = (staging / member.filename).resolve()
                if not target.is_relative_to(staging.resolve()):
                    raise BackupProblem("This backup has a file outside its folder, so it was not restored.")
            archive.extractall(staging)
    except zipfile.BadZipFile as problem:
        shutil.rmtree(staging, ignore_errors=True)
        raise BackupProblem("This backup can't be read. Nothing was changed.") from problem
    except BackupProblem:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    create("before-restore")
    with projects._lock:
        if root.exists():
            root.rename(old)
        staging.rename(root)
    library.reset()
    # The 'before restore' backup holds the replaced library, so the folder it came from can go.
    shutil.rmtree(old, ignore_errors=True)
    return {"restored": name, "assets": len(_asset_folders())}
