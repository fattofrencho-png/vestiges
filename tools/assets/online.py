"""Finds builds online, on Modrinth (its public API, no key), and downloads the file the author picks.

Modrinth lists datapacks and mods; both can carry structures, which import_formats already reads out of .zip and .jar files.
Rules: nothing is downloaded until the author clicks Import on one result; the file comes only from Modrinth's own download
host, is checked against Modrinth's published hash, has a size cap, and is only ever read, never run.
Modrinth asks API clients to name themselves, so every request carries a User-Agent (see user_agent()).
"""
import hashlib
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
PAGE = 12
MAX_DOWNLOAD = 100 * 1024 * 1024
FILE_HOSTS = ("cdn.modrinth.com",)
PAGE_HOSTS = ("modrinth.com", "www.modrinth.com")
TYPES = {"datapack": "Datapacks", "mod": "Mods"}
# Categories where builds live. Without this filter a search for "trees" mostly finds game-mechanics packs.
BUILD_CATEGORIES = ("worldgen", "decoration")
WARN_LICENCES = re.compile(r"all-rights-reserved|-ND-|-NC-", re.I)


class OnlineProblem(Exception):
    pass


def user_agent():
    # Modrinth wants a contact in here; the author's is still to confirm, so the default names only the tool.
    return os.environ.get("VESTIGES_USER_AGENT") or "vestiges-asset-studio/0.1 (personal use)"


def _open(url, timeout=30):
    request = urllib.request.Request(url, headers={"User-Agent": user_agent()})
    try:
        return urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as problem:
        if problem.code == 429:
            raise OnlineProblem("Modrinth says too many requests. Wait a minute and try again.") from problem
        raise OnlineProblem(f"Modrinth answered with an error ({problem.code}).") from problem
    except (urllib.error.URLError, OSError) as problem:
        raise OnlineProblem("Couldn't reach Modrinth. Check your internet connection.") from problem


def _get_json(url):
    with _open(url) as reply:
        try:
            return json.load(reply)
        except ValueError as problem:
            raise OnlineProblem("Modrinth sent something unreadable.") from problem


def licence_warning(licence):
    """Words to show next to a licence that limits what you may do, or empty."""
    text = str(licence or "")
    if not text or text.lower() in ("unknown", "licenseref-unknown"):
        return "No licence is given."
    if re.search(r"all-rights-reserved", text, re.I):
        return "All rights reserved: the creator has not allowed reuse."
    if re.search(r"-ND-", text, re.I):
        return "No derivatives: you may not change it."
    if re.search(r"-NC-", text, re.I):
        return "Non-commercial only."
    return ""


def search_url(query, kind="datapack", builds=True, page=0):
    if kind not in TYPES:
        raise OnlineProblem("Choose Datapacks or Mods.")
    facets = [[f"project_type:{kind}"]]
    if builds:
        facets.append([f"categories:{c}" for c in BUILD_CATEGORIES])
    params = {"query": str(query)[:100], "limit": PAGE, "offset": max(int(page), 0) * PAGE, "index": "relevance" if query else "downloads", "facets": json.dumps(facets)}
    return f"{API}/search?" + urllib.parse.urlencode(params)


def clean_hit(hit):
    slug = str(hit.get("slug", ""))
    licence = hit.get("license") or ""
    return {
        "id": str(hit.get("project_id", "")), "slug": slug, "title": str(hit.get("title", slug)), "description": str(hit.get("description", ""))[:300],
        "author": str(hit.get("author", "")), "licence": licence, "warning": licence_warning(licence), "downloads": int(hit.get("downloads", 0)),
        "icon": hit.get("icon_url") or "", "kind": hit.get("project_type", ""), "categories": list(hit.get("display_categories") or hit.get("categories") or [])[:6],
        "url": f"https://modrinth.com/{hit.get('project_type', 'project')}/{slug}",
    }


def search(query, kind="datapack", builds=True, page=0):
    reply = _get_json(search_url(query, kind, builds, page))
    return {"hits": [clean_hit(h) for h in reply.get("hits", [])], "total": int(reply.get("total_hits", 0)), "page": int(page), "per_page": PAGE}


def pick_file(versions):
    """The newest version's main download: a .zip or .jar, or None."""
    for version in versions:
        files = [f for f in version.get("files", []) if str(f.get("filename", "")).lower().endswith((".zip", ".jar"))]
        if files:
            chosen = next((f for f in files if f.get("primary")), files[0])
            return {**chosen, "version": str(version.get("version_number", ""))}
    return None


def check_url(url, hosts=FILE_HOSTS):
    parts = urllib.parse.urlparse(str(url))
    if parts.scheme != "https" or parts.hostname not in hosts:
        raise OnlineProblem("This file is not on Modrinth's download server, so it was not downloaded.")


def safe_name(name):
    return re.sub(r"[^A-Za-z0-9._-]", "_", str(name))[:120] or "download"


def find_file(project_id):
    if not re.fullmatch(r"[A-Za-z0-9]{6,16}", str(project_id or "")):
        raise OnlineProblem("Unknown project.")
    file = pick_file(_get_json(f"{API}/project/{project_id}/version"))
    if not file:
        raise OnlineProblem("This project has no .zip or .jar file to download.")
    return file


def download(file, destination, max_bytes=MAX_DOWNLOAD):
    """Saves the file to destination (a path that does not exist yet). Raises OnlineProblem and leaves nothing behind on any problem."""
    check_url(file.get("url"))
    expected = str((file.get("hashes") or {}).get("sha512", "")).lower()
    digest, size = hashlib.sha512(), 0
    try:
        with _open(file["url"], timeout=60) as reply:
            check_url(reply.geturl())
            if int(reply.headers.get("Content-Length") or 0) > max_bytes:
                raise OnlineProblem(f"This file is over the {max_bytes // 1048576} MB limit, so it was not downloaded.")
            with open(destination, "wb") as out:
                while True:
                    chunk = reply.read(1 << 16)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > max_bytes:
                        raise OnlineProblem(f"This file is over the {max_bytes // 1048576} MB limit, so it was not downloaded.")
                    digest.update(chunk)
                    out.write(chunk)
        if expected and digest.hexdigest() != expected:
            raise OnlineProblem("The download doesn't match Modrinth's checksum, so it was thrown away.")
    except BaseException:
        try:
            os.remove(destination)
        except OSError:
            pass
        raise
    return size
