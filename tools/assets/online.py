"""Finds builds online and downloads the file the author picks. Sources: Modrinth (public API), GitHub (public API) and
CurseForge (needs the author's free API key, set in Settings).

Datapacks, mods and repositories can carry structures, which import_formats already reads out of .zip and .jar files.
Rules: nothing is downloaded until the author clicks Import or Look inside on one result; the file comes only from that
source's own download hosts, is checked against the published hash when there is one, has a size cap, and is only ever
read, never run. Sites with no API (Planet Minecraft, minecraft-schematics.com) are only opened in the browser.
Every request carries a User-Agent that names the tool (see user_agent()), as Modrinth and GitHub ask.
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
PAGE_HOSTS = ("modrinth.com", "www.modrinth.com", "github.com", "www.curseforge.com", "curseforge.com", "duckduckgo.com")
TYPES = {"datapack": "Datapacks", "mod": "Mods"}
GITHUB_API = "https://api.github.com"
GITHUB_HOSTS = ("api.github.com", "codeload.github.com")
CURSEFORGE_API = "https://api.curseforge.com/v1"
CURSEFORGE_HOSTS = ("edge.forgecdn.net", "mediafilez.forgecdn.net", "media.forgecdn.net")
CURSEFORGE_CLASSES = {"datapack": 6945, "world": 17, "mod": 6, "resourcepack": 12}
SOURCES = {
    "modrinth": {"label": "Modrinth", "types": [["datapack", "Datapacks"], ["mod", "Mods"]], "key": None,
                 "note": "Datapacks and mods. The licence comes with every result."},
    "github": {"label": "GitHub", "types": [["repo", "Repositories"]], "key": None,
               "note": "Repositories of schematics and datapacks. Most results are mods and tools, and many repositories state no licence."},
    "curseforge": {"label": "CurseForge", "types": [["datapack", "Datapacks"], ["world", "Worlds"], ["mod", "Mods"], ["resourcepack", "Resource packs"]], "key": "curseforge",
                   "note": "Needs a free API key in Settings. Some creators don't allow downloads outside CurseForge. No licence is listed, so check the page."},
}
# Sites with no API: only a search page opens in the browser, and the author downloads and drops the file in.
BROWSE_SITES = (("Planet Minecraft", "planetminecraft.com"), ("minecraft-schematics.com", "minecraft-schematics.com"))
# Categories where builds live. Without this filter a search for "trees" mostly finds game-mechanics packs.
BUILD_CATEGORIES = ("worldgen", "decoration")
WARN_LICENCES = re.compile(r"all-rights-reserved|-ND-|-NC-", re.I)
GITHUB_REPO = re.compile(r"[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")


class OnlineProblem(Exception):
    pass


def user_agent():
    # Modrinth wants a contact in here; the author's is still to confirm, so the default names only the tool.
    return os.environ.get("VESTIGES_USER_AGENT") or "vestiges-asset-studio/0.1 (personal use)"


def _open(url, timeout=30, headers=None, name="The site"):
    request = urllib.request.Request(url, headers={"User-Agent": user_agent(), **(headers or {})})
    try:
        return urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as problem:
        if problem.code == 429 or (problem.code == 403 and "github" in url and problem.headers.get("X-RateLimit-Remaining") == "0"):
            raise OnlineProblem(f"{name} says too many requests. Wait a minute and try again.") from problem
        if problem.code in (401, 403) and "curseforge" in url:
            raise OnlineProblem("CurseForge refused the API key. Check it in Settings.") from problem
        raise OnlineProblem(f"{name} answered with an error ({problem.code}).") from problem
    except (urllib.error.URLError, OSError) as problem:
        raise OnlineProblem(f"Couldn't reach {name}. Check your internet connection.") from problem


def _get_json(url, headers=None, name="Modrinth"):
    with _open(url, headers=headers, name=name) as reply:
        try:
            return json.load(reply)
        except ValueError as problem:
            raise OnlineProblem(f"{name} sent something unreadable.") from problem


def licence_warning(licence):
    """Words to show next to a licence that limits what you may do, or empty."""
    text = str(licence or "")
    if not text or text.lower() in ("unknown", "licenseref-unknown", "noassertion", "other"):
        return "No licence is given."
    if re.search(r"all-rights-reserved", text, re.I):
        return "All rights reserved: the creator has not allowed reuse."
    if re.search(r"-ND-", text, re.I):
        return "No derivatives: you may not change it."
    if re.search(r"-NC-", text, re.I):
        return "Non-commercial only."
    return ""


def _hit(source, id_, title, description, author, licence, downloads, kind, categories, url, extra_warning=""):
    licence = "" if str(licence or "").upper() in ("NOASSERTION", "OTHER") else (licence or "")
    warning = licence_warning(licence)
    if extra_warning:
        warning = (extra_warning + " " + (warning if warning != "No licence is given." else "")).strip()
    return {"source": source, "id": str(id_), "slug": str(id_), "title": str(title), "description": str(description or "")[:300], "author": str(author or ""),
            "licence": licence, "warning": warning, "downloads": int(downloads or 0), "icon": "", "kind": kind,
            "categories": list(categories or [])[:6], "url": url}


# ---------------------------------------------------------------- Modrinth
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
    made = _hit("modrinth", hit.get("project_id", ""), hit.get("title", slug), hit.get("description", ""), hit.get("author", ""), hit.get("license") or "",
                hit.get("downloads", 0), hit.get("project_type", ""), hit.get("display_categories") or hit.get("categories") or [],
                f"https://modrinth.com/{hit.get('project_type', 'project')}/{slug}")
    made["slug"] = slug
    made["icon"] = hit.get("icon_url") or ""
    return made


def _search_modrinth(query, kind, builds, page):
    reply = _get_json(search_url(query, kind, builds, page), name="Modrinth")
    return {"hits": [clean_hit(h) for h in reply.get("hits", [])], "total": int(reply.get("total_hits", 0))}


def pick_file(versions):
    """The newest version's main download: a .zip or .jar, or None."""
    for version in versions:
        files = [f for f in version.get("files", []) if str(f.get("filename", "")).lower().endswith((".zip", ".jar"))]
        if files:
            chosen = next((f for f in files if f.get("primary")), files[0])
            return {**chosen, "version": str(version.get("version_number", "")), "hosts": FILE_HOSTS}
    return None


def _find_modrinth(project_id):
    if not re.fullmatch(r"[A-Za-z0-9]{6,16}", str(project_id or "")):
        raise OnlineProblem("Unknown project.")
    file = pick_file(_get_json(f"{API}/project/{project_id}/version", name="Modrinth"))
    if not file:
        raise OnlineProblem("This project has no .zip or .jar file to download.")
    return file


# ---------------------------------------------------------------- GitHub
def github_query(query, builds=True):
    words = " ".join(str(query).split())[:100] or "schematic"
    suffix = " schematic OR litematic OR schem OR datapack OR structure" if builds else ""
    return f"{words} minecraft{suffix} fork:false archived:false"


def github_url(query, builds=True, page=0):
    params = {"q": github_query(query, builds), "per_page": PAGE, "page": max(int(page), 0) + 1, "sort": "stars", "order": "desc"}
    return f"{GITHUB_API}/search/repositories?" + urllib.parse.urlencode(params)


def clean_github(repo):
    licence = (repo.get("license") or {}).get("spdx_id") or ""
    made = _hit("github", repo.get("full_name", ""), repo.get("name", ""), repo.get("description"), (repo.get("owner") or {}).get("login", ""), licence,
                repo.get("stargazers_count", 0), "repo", repo.get("topics") or [], repo.get("html_url") or f"https://github.com/{repo.get('full_name', '')}")
    made["size_kb"] = int(repo.get("size", 0))
    return made


def _search_github(query, builds, page):
    reply = _get_json(github_url(query, builds, page), headers={"Accept": "application/vnd.github+json"}, name="GitHub")
    return {"hits": [clean_github(r) for r in reply.get("items", [])], "total": min(int(reply.get("total_count", 0)), 1000)}


def _find_github(full_name):
    if not GITHUB_REPO.fullmatch(str(full_name or "")):
        raise OnlineProblem("Unknown repository.")
    repo = _get_json(f"{GITHUB_API}/repos/{full_name}", headers={"Accept": "application/vnd.github+json"}, name="GitHub")
    if int(repo.get("size", 0)) * 1024 > MAX_DOWNLOAD:
        raise OnlineProblem("This repository is over the 100 MB limit, so it was not downloaded.")
    return {"url": f"{GITHUB_API}/repos/{full_name}/zipball", "filename": safe_name(repo.get("name", "repository")) + ".zip",
            "version": repo.get("default_branch") or "main", "hashes": {}, "hosts": GITHUB_HOSTS}


# ---------------------------------------------------------------- CurseForge
def _curse_headers():
    import providers
    try:
        return {"x-api-key": providers._key("curseforge"), "Accept": "application/json"}
    except providers.ProviderError as problem:
        raise OnlineProblem("Add your CurseForge API key in Settings first (a free key from console.curseforge.com).") from problem


def curseforge_url(query, kind="datapack", page=0):
    if kind not in CURSEFORGE_CLASSES:
        raise OnlineProblem("Choose a type to search.")
    params = {"gameId": 432, "classId": CURSEFORGE_CLASSES[kind], "searchFilter": str(query)[:100], "pageSize": PAGE, "index": max(int(page), 0) * PAGE,
              "sortField": 6 if not query else 2, "sortOrder": "desc"}
    return f"{CURSEFORGE_API}/mods/search?" + urllib.parse.urlencode(params)


def clean_curseforge(mod, kind="datapack"):
    authors = mod.get("authors") or [{}]
    page = (mod.get("links") or {}).get("websiteUrl") or f"https://www.curseforge.com/minecraft/search?search={urllib.parse.quote(str(mod.get('name', '')))}"
    note = "" if mod.get("allowModDistribution") is not False else "The creator only allows downloads on CurseForge."
    return _hit("curseforge", mod.get("id", ""), mod.get("name", ""), mod.get("summary"), authors[0].get("name", ""), "", mod.get("downloadCount", 0), kind,
                [c.get("name", "") for c in mod.get("categories") or []], page, note)


def _search_curseforge(query, kind, page):
    reply = _get_json(curseforge_url(query, kind, page), headers=_curse_headers(), name="CurseForge")
    pagination = reply.get("pagination") or {}
    return {"hits": [clean_curseforge(m, kind) for m in reply.get("data", [])], "total": int(pagination.get("totalCount", len(reply.get("data", []))))}


def pick_curseforge_file(files):
    """The newest file CurseForge lets us download: a .zip or .jar with a download address. None if there is none."""
    for entry in files:
        name = str(entry.get("fileName", ""))
        if entry.get("downloadUrl") and name.lower().endswith((".zip", ".jar")):
            hashes = {("sha1" if h.get("algo") == 1 else "md5"): str(h.get("value", "")).lower() for h in entry.get("hashes") or []}
            return {"url": entry["downloadUrl"], "filename": name, "version": str(entry.get("displayName", "")), "hashes": hashes,
                    "size": int(entry.get("fileLength", 0)), "hosts": CURSEFORGE_HOSTS}
    return None


def _find_curseforge(mod_id):
    if not re.fullmatch(r"\d{1,9}", str(mod_id or "")):
        raise OnlineProblem("Unknown project.")
    files = _get_json(f"{CURSEFORGE_API}/mods/{mod_id}/files?pageSize=10", headers=_curse_headers(), name="CurseForge").get("data", [])
    file = pick_curseforge_file(files)
    if not file:
        raise OnlineProblem("The creator only allows downloads on CurseForge's own site. Use Open on CurseForge, download it there, then drop the file into Look inside.")
    return file


# ---------------------------------------------------------------- shared
def sources():
    import providers
    return [{"id": key, "label": value["label"], "types": value["types"], "note": value["note"], "needs_key": bool(value["key"]),
             "has_key": bool(value["key"]) and providers.has_key(value["key"])} for key, value in SOURCES.items()]


def search(query, kind="datapack", builds=True, page=0, source="modrinth"):
    if source == "modrinth":
        found = _search_modrinth(query, kind, builds, page)
    elif source == "github":
        found = _search_github(query, builds, page)
    elif source == "curseforge":
        found = _search_curseforge(query, kind, page)
    else:
        raise OnlineProblem("Unknown source.")
    return {**found, "page": int(page), "per_page": PAGE, "source": source}


def browse_url(site, query):
    """A search page in the browser for sites with no API. Goes through a web search, since those sites block programs."""
    hosts = dict(BROWSE_SITES)
    if site not in hosts:
        raise OnlineProblem("Unknown site.")
    words = " ".join(str(query).split())[:100] or "minecraft schematic"
    return "https://duckduckgo.com/?" + urllib.parse.urlencode({"q": f"site:{hosts[site]} {words}"})


def check_url(url, hosts=FILE_HOSTS):
    parts = urllib.parse.urlparse(str(url))
    if parts.scheme != "https" or parts.hostname not in hosts:
        raise OnlineProblem("This file is not on the source's own download server, so it was not downloaded.")


def safe_name(name):
    return re.sub(r"[^A-Za-z0-9._-]", "_", str(name))[:120] or "download"


def find_file(project_id, source="modrinth"):
    if source == "modrinth":
        return _find_modrinth(project_id)
    if source == "github":
        return _find_github(project_id)
    if source == "curseforge":
        return _find_curseforge(project_id)
    raise OnlineProblem("Unknown source.")


def download(file, destination, max_bytes=MAX_DOWNLOAD):
    """Saves the file to destination (a path that does not exist yet). Raises OnlineProblem and leaves nothing behind on any problem."""
    hosts = tuple(file.get("hosts") or FILE_HOSTS)
    check_url(file.get("url"), hosts)
    expected = {algo: str(value).lower() for algo, value in (file.get("hashes") or {}).items() if algo in ("sha512", "sha1", "md5") and value}
    digests = {algo: hashlib.new(algo) for algo in expected}
    size = 0
    headers = {"Accept": "application/octet-stream"} if "api.github.com" in str(file.get("url")) else None
    try:
        with _open(file["url"], timeout=60, headers=headers, name="The download server") as reply:
            check_url(reply.geturl(), hosts)
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
                    for digest in digests.values():
                        digest.update(chunk)
                    out.write(chunk)
        if any(digests[algo].hexdigest() != value for algo, value in expected.items()):
            raise OnlineProblem("The download doesn't match the published checksum, so it was thrown away.")
    except BaseException:
        try:
            os.remove(destination)
        except OSError:
            pass
        raise
    return size
