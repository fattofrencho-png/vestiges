"""Asset Studio. Double-click "Asset Studio.bat", then use the page it opens. Only this computer can reach it."""
import io
import json
import mimetypes
import os
import shutil
import sys
import threading
import urllib.request
import uuid
import zipfile
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import batches
import edits
import export
import import_formats
import library
import naming
import pipeline
import projects
import providers
import tree_gen

HOST, PORT = "127.0.0.1", 8765
ALLOWED_HOSTS = {f"127.0.0.1:{PORT}", f"localhost:{PORT}"}
WEB = Path(__file__).resolve().parent / "web"
ROOT = Path(__file__).resolve().parents[2]
MOD_STRUCTURES = ROOT / "src/main/resources/data/vestiges/structure"
CREDITS = ROOT / "docs/assets/credits.md"
CATEGORIES = ("tree", "plant", "house", "ruin", "structure", "other")
EXPORT_INDEX = Path(__file__).resolve().parent / "exports.json"
SUGGESTIONS = [
    {"label": "Old forest tree", "text": "An old forest tree, a little bigger than a big vanilla oak, with a thick trunk and a few heavy branches."},
    {"label": "Young forest tree", "text": "A young forest tree, slim and 7 to 10 blocks high, for growing in the shade of bigger trees."},
    {"label": "Fallen log", "text": "A fallen tree trunk lying on the forest floor, partly covered in moss."},
    {"label": "Stump", "text": "An old broken tree stump with moss and small plants growing on it."},
    {"label": "Forest undergrowth", "text": "A low patch of forest undergrowth with bushes, ferns and moss, to scatter between trees."},
]
_baked = {}
_uploads = {}  # token -> (file name, temporary file) until a batch uses them


def bake_states(tree):
    states = {}
    for state in {b[3] for b in tree["blocks"]}:
        if state not in _baked:
            _baked[state] = pipeline.jar().bake(state)
        states[state] = _baked[state]
    return states


def provider_list():
    settings = providers.load_settings()
    out = []
    if providers.fake_enabled():
        out.append({"id": "test", "label": "Test AI (no cost, scripted)", "ready": True, "model": "", "guess": 0.0})
    claude = settings["models"]["claude"]
    out.append({"id": "claude", "label": "Claude", "ready": providers.has_key("claude"), "has_key": providers.has_key("claude"), "model": claude,
                "models": [{"id": k, "label": v["label"]} for k, v in providers.CLAUDE_MODELS.items()],
                "guess": providers.guess_cost("claude", claude)})
    gemini = settings["models"]["gemini"]
    out.append({"id": "gemini", "label": "Gemini", "ready": providers.has_key("gemini") and bool(gemini), "has_key": providers.has_key("gemini"),
                "model": gemini, "guess": None})
    return out


def state():
    settings = providers.load_settings()
    listed = provider_list()
    chosen = settings["provider"]
    if not any(p["id"] == chosen and p["ready"] for p in listed):
        chosen = next((p["id"] for p in listed if p["ready"]), chosen)
    return {"providers": listed, "provider": chosen, "send_images": settings["send_images"], "budget": settings["budget"],
            "safety_fallback": settings["safety_fallback"], "suggestions": SUGGESTIONS, "categories": CATEGORIES}


def saved_version(project_id, number=None):
    """The version and file name to export. Any saved variant can be asked for; by default the newest."""
    project = projects.load(project_id)
    if not project["saved"]:
        raise KeyError("Save a version before exporting it")
    chosen = next((s for s in project["saved"] if s["version"] == number), None) if number is not None else project["saved"][-1]
    if chosen is None:
        raise KeyError("That version isn't saved")
    return project, projects.load_version(project_id, chosen["version"]), chosen["name"]


def save_import(result, title, source, credit, category):
    """One build added straight to the library. Returns its id, or the existing asset's id if it's a duplicate."""
    made = batches.add_build(result, source, {**credit, "kind": category}, None, title)
    if made is None:
        raise import_formats.ImportProblem("The same build is already in the Library.")
    return made


def add_version(project, blocks, history, dropped=None, notes=None):
    number = project["versions"][-1]["number"] + 1 if project["versions"] else 1
    tree = {"blocks": blocks}
    projects.save_version(project["id"], number, None, tree)
    project["versions"].append({"number": number, "edits": history, "check": pipeline.inspect(tree, lenient=True), "dropped": dropped or {}, "notes": notes or []})
    project.update(stage="adapt", saved_version=None)
    projects.save(project)
    return number


def create_import(data):
    """A box from a saved world. Files go through batches."""
    title = str(data.get("title", "")).strip()[:60]
    credit = {key: str(data.get(key, "")).strip()[:500] for key in ("creator", "permission", "licence")}
    category = data.get("category") or ""
    if category and category not in CATEGORIES:
        raise ValueError("Pick a kind of asset")
    result = import_formats.read_world(data.get("world"), data.get("dimension"), data.get("first"), data.get("second"))
    name = Path(str(data["world"]).strip().strip('"')).name
    source = {"type": "world", "name": name, "dimension": data.get("dimension"), "first": data.get("first"), "second": data.get("second")}
    return {"id": save_import(result, title or f"{name} {data.get('first')}", source, credit, category)}


def set_details(project_id, data):
    project = projects.load(project_id)
    if "kind" in data and data["kind"] in CATEGORIES:
        project["category"] = data["kind"]
    if data.get("title"):
        project["title"] = str(data["title"]).strip()[:60]
    credit = project.get("credit") or {}
    for key in ("creator", "permission", "licence"):
        if key in data:
            credit[key] = str(data[key]).strip()[:500]
    project["credit"] = credit
    if "tags" in data:
        project["tags"] = [naming.slug(t) for t in str(data["tags"]).split(",") if naming.slug(t)][:20]
    projects.save(project)


def bulk(data):
    ids = [str(i) for i in data.get("ids") or []][:5000]
    action = data.get("action")
    if action == "save":
        for project_id in ids:
            project = projects.load(project_id)
            if project.get("versions") and not project["saved"]:
                library.save_variant(project_id, project["versions"][-1]["number"])
    elif action == "delete":
        for project_id in ids:
            projects.delete(project_id)
    elif action == "details":
        for project_id in ids:
            set_details(project_id, {k: v for k, v in data.items() if k in ("kind", "creator", "permission", "licence") and v})
    else:
        raise ValueError("Unknown action")
    library.flush()


def zip_of(ids, kind):
    """Every saved variant of these assets, under their names. Assets with nothing saved use their proposed name."""
    data_version = pipeline.jar().data_version()
    store = io.BytesIO()
    with zipfile.ZipFile(store, "w", zipfile.ZIP_STORED) as archive:
        for project_id in [str(i) for i in ids][:5000]:
            project = projects.load(project_id)
            wanted = project["saved"] or ([{"version": project["versions"][-1]["number"], "name": library.propose(project)}] if project["versions"] else [])
            for entry in wanted:
                version = projects.load_version(project_id, entry["version"])
                body = export.to_schem(version, data_version) if kind == "schem" else export.to_structure(version, data_version, with_air=project.get("mode") == "import")[0]
                archive.writestr(f"{entry['name']}.{'schem' if kind == 'schem' else 'nbt'}", export.write_nbt(body))
    return store.getvalue()


def apply_edit(project_id, base, edit):
    project = projects.load(project_id)
    numbers = [v["number"] for v in project["versions"]]
    if int(base) not in numbers:
        raise KeyError("unknown version")
    previous = next(v for v in project["versions"] if v["number"] == int(base))
    blocks = edits.apply(projects.load_version(project_id, base)["blocks"], pipeline.jar(), edit)
    if not blocks:
        raise edits.EditProblem("That would remove every block.")
    history = previous["edits"] + [edits.describe(edit)]
    return add_version(project, blocks, history, previous["dropped"], previous["notes"])


def write_credits(index):
    lines = ["# Credits", "", "Builds made by others and used in this mod, with the permission for each. Generated by Asset Studio: don't edit by hand.", ""]
    for stem, entry in sorted(index.items()):
        credit = entry.get("credit")
        if credit and credit.get("creator"):
            lines.append(f"- **{entry['title']}** (`{entry['category']}/{stem}`): by {credit['creator']}." + (f" Permission: {credit['permission']}." if credit.get("permission") else "") + (f" Licence: {credit['licence']}." if credit.get("licence") else ""))
    CREDITS.write_text("\n".join(lines) + "\n", encoding="utf-8")


def add_to_mod(project_id, number=None):
    project, version, name = saved_version(project_id, number)
    errors = pipeline.inspect(version, lenient=True)["errors"]
    if errors:
        raise ValueError("The mod only uses vanilla blocks. " + "; ".join(errors))
    imported = project.get("mode") == "import"
    structure, placement = export.to_structure(version, pipeline.jar().data_version(), with_air=imported)
    category = project.get("category", "tree")
    folder = MOD_STRUCTURES / category
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{name}.nbt").write_bytes(export.write_nbt(structure))
    index = json.loads(EXPORT_INDEX.read_text(encoding="utf-8")) if EXPORT_INDEX.exists() else {}
    index[name] = {"from": project_id, "title": project["title"], "category": category, "credit": project.get("credit"), **placement}
    EXPORT_INDEX.write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_credits(index)
    return {"file": f"data/vestiges/structure/{category}/{name}.nbt", "name": f"vestiges:{category}/{name}", **placement}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, body, kind="application/json", code=200, name=None):
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        if name:
            self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.end_headers()
        self.wfile.write(body)

    def send_json(self, value, code=200):
        self.send(json.dumps(value).encode("utf-8"), code=code)

    def do_GET(self):
        if self.headers.get("Host") not in ALLOWED_HOSTS:  # stops other websites reaching this tool by a trick address
            return self.send_json({"error": "not allowed"}, 403)
        url = urlparse(self.path)
        query = {k: v[0] for k, v in parse_qs(url.query).items()}
        try:
            if url.path == "/api/whoami":
                return self.send_json({"tool": "vestiges-asset-studio"})
            if url.path == "/api/state":
                return self.send_json(state())
            if url.path == "/api/project":
                return self.send_json(projects.load(query.get("id", "")))
            if url.path == "/api/version":
                version = projects.load_version(query.get("id", ""), int(query.get("v", -1)))
                return self.send_json({"blocks": version["blocks"], "states": bake_states(version)})
            if url.path == "/api/library":
                return self.send_json({**library.search(query.get("q", ""), query.get("kind", ""), query.get("status", ""), query.get("batch", ""),
                                                        query.get("no_credit") == "1", query.get("not_vanilla") == "1", int(query.get("page", 0))), **library.facets()})
            if url.path == "/api/batch":
                return self.send_json(batches.load(query.get("id", "")))
            if url.path == "/api/propose":
                return self.send_json({"name": library.propose(projects.load(query.get("id", "")), int(query["v"]))})
            if url.path == "/api/import/saves":
                extra = [("this tool's test game", ROOT / "build/run/clientGameTest/saves")]
                return self.send_json(import_formats.find_saves(extra))
            if url.path == "/api/blocks":
                return self.send_json(sorted(b.replace("minecraft:", "") for b in pipeline.jar().block_ids()))
            if url.path == "/api/reference":
                tree = tree_gen.vanilla_oak()
                return self.send_json({"blocks": tree["blocks"], "states": bake_states(tree)})
            if url.path == "/api/export":
                project, version, name = saved_version(query.get("id", ""), int(query["v"]) if "v" in query else None)
                data_version = pipeline.jar().data_version()
                if query.get("format") == "schem":
                    return self.send(export.write_nbt(export.to_schem(version, data_version)), "application/octet-stream", name=name + ".schem")
                structure, _ = export.to_structure(version, data_version, with_air=project.get("mode") == "import")
                return self.send(export.write_nbt(structure), "application/octet-stream", name=name + ".nbt")
            if url.path.startswith("/mc/"):
                png = pipeline.jar().texture(url.path[len("/mc/"):])
                return self.send(png, "image/png") if png else self.send_json({"error": "no texture"}, 404)
            path = (WEB / (url.path.lstrip("/") or "index.html")).resolve()
            if not path.is_relative_to(WEB) or not path.is_file():
                return self.send_json({"error": "not found"}, 404)
            kind = "text/javascript" if path.suffix == ".js" else mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            return self.send(path.read_bytes(), kind)
        except KeyError as problem:
            return self.send_json({"error": str(problem.args[0]) if problem.args else "not found"}, 404)
        except Exception as problem:
            return self.send_json({"error": str(problem)}, 500)

    def do_POST(self):
        # These can spend money, so they only accept the tool's own page.
        origin = self.headers.get("Origin", "")
        url = urlparse(self.path)
        upload = url.path == "/api/import/upload"
        wanted = "application/octet-stream" if upload else "application/json"
        if (self.headers.get("Host") not in ALLOWED_HOSTS or self.headers.get("Content-Type") != wanted
                or origin.replace("http://", "") not in ALLOWED_HOSTS):
            return self.send_json({"error": "not allowed"}, 403)
        try:
            if upload:
                return self.receive_upload(parse_qs(url.query).get("name", ["build"])[0])
            data = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            if url.path == "/api/library/zip":
                return self.send(zip_of(data.get("ids") or [], data.get("format")), "application/zip", name="asset-library.zip")
            return self.send_json(self.action(url.path, data))
        except KeyError as problem:
            return self.send_json({"error": str(problem.args[0]) if problem.args else "not found"}, 404)
        except providers.ProviderError as problem:
            return self.send_json({"error": str(problem)}, 400)
        except (import_formats.ImportProblem, edits.EditProblem) as problem:
            return self.send_json({"error": str(problem)}, 400)
        except ValueError as problem:
            return self.send_json({"error": f"Bad request: {problem}"}, 400)
        except Exception as problem:
            return self.send_json({"error": str(problem)}, 500)

    def receive_upload(self, name):
        size = int(self.headers.get("Content-Length", 0))
        if size > import_formats.MAX_FILE:
            self.close_connection = True
            return self.send_json({"error": "This file is over the 256 MB limit."}, 400)
        folder = providers.HOME / "uploads"
        folder.mkdir(parents=True, exist_ok=True)
        token = uuid.uuid4().hex
        path = folder / token
        left = size
        with open(path, "wb") as out:
            while left > 0:
                chunk = self.rfile.read(min(left, 1 << 20))
                if not chunk:
                    break
                out.write(chunk)
                left -= len(chunk)
        try:
            items = import_formats.scan(name, path)
        except import_formats.ImportProblem:
            path.unlink(missing_ok=True)
            raise
        _uploads[token] = (name, path)
        return self.send_json({"token": token, "items": items, "name": name})

    def action(self, path, data):
        project_id = data.get("id", "")
        if path == "/api/import/create":
            return create_import(data)
        if path == "/api/batch/create":
            sources = []
            for entry in data.get("sources") or []:
                held = _uploads.pop(str(entry.get("token")), None)
                if held is None:
                    raise ValueError("Choose the files again: the tool has restarted since")
                sources.append({"name": held[0], "path": str(held[1]), "items": [str(i) for i in entry.get("items") or []]})
            details = {k: str(data.get(k, "")) for k in ("kind", "creator", "permission", "licence")}
            if details["kind"] and details["kind"] not in CATEGORIES:
                raise ValueError("Pick a kind of asset")
            return {"id": batches.start(sources, details, data.get("failed") or [])}
        if path == "/api/save":
            return {"name": library.save_variant(project_id, int(data["version"]), data.get("name"))}
        if path == "/api/status":
            project = projects.load(project_id)
            project["status"] = "skipped" if data.get("status") == "skipped" else "review"
            projects.save(project)
            return {"ok": True}
        if path == "/api/details":
            set_details(project_id, data)
            return {"ok": True}
        if path == "/api/library/bulk":
            bulk(data)
            return {"ok": True}
        if path == "/api/edit":
            return {"version": apply_edit(project_id, data.get("base"), data.get("edit") or {})}
        if path == "/api/start":
            settings = providers.load_settings()
            request = str(data.get("request", "")).strip()[:4000]
            provider = data.get("provider")
            if not request:
                raise ValueError("Write a request first")
            if provider not in {p["id"] for p in provider_list() if p["ready"]}:
                raise providers.ProviderError("This AI isn't set up yet. Add its key in Settings.")
            providers.save_settings({"provider": provider})
            return {"id": pipeline.start(request, provider, settings["models"].get(provider, ""), float(settings["budget"]))}
        if path == "/api/answers":
            pipeline.answers(project_id, data.get("answers") or {})
        elif path == "/api/brief/confirm":
            pipeline.confirm_brief(project_id)
        elif path == "/api/brief/change":
            pipeline.change_brief(project_id, str(data.get("text", ""))[:4000])
        elif path == "/api/change":
            pipeline.request_change(project_id, str(data.get("text", ""))[:4000])
        elif path == "/api/agree":
            pipeline.agree(project_id, int(data.get("version")))
        elif path == "/api/resume":
            pipeline.resume(project_id, min(max(float(data.get("extra_budget", 0)), 0), 5))
        elif path == "/api/views":
            pipeline.receive_views(project_id, data.get("images") or [])
        elif path == "/api/export/mod":
            return add_to_mod(project_id, int(data["version"]) if data.get("version") is not None else None)
        elif path == "/api/delete":
            projects.delete(project_id)
        elif path == "/api/settings":
            providers.save_settings(data.get("settings") or {})
            for name in ("claude", "gemini"):
                if name in (data.get("keys") or {}):
                    providers.set_key(name, str(data["keys"][name]))
        elif path == "/api/settings/test":
            name = data.get("provider")
            providers.get(name, providers.load_settings()["models"].get(name, "")).test()
        elif path == "/api/settings/gemini-models":
            return {"models": providers.gemini_models()}
        else:
            raise KeyError("not found")
        return {"ok": True}


def already_running():
    try:
        with urllib.request.urlopen(f"http://{HOST}:{PORT}/api/whoami", timeout=2) as reply:
            return json.load(reply).get("tool") == "vestiges-asset-studio"
    except (OSError, ValueError):
        return False


def main():
    address = f"http://{HOST}:{PORT}"
    show = "--no-browser" not in sys.argv
    if already_running():
        print(f"Asset Studio is already running: {address}")
        if show:
            webbrowser.open(address)
        return
    try:
        server = ThreadingHTTPServer((HOST, PORT), Handler)
    except OSError:
        print(f"Port {PORT} is being used by another program, so Asset Studio can't start.")
        sys.exit(1)
    moved = 0 if os.environ.get("VESTIGES_PROJECTS") else library.move_old_folder(projects.OLD_ROOT)
    if moved:
        print(f"Moved {moved} assets to the library in {projects.ROOT}")
    shutil.rmtree(providers.HOME / "uploads", ignore_errors=True)
    pipeline.recover_after_restart()
    print(f"Asset Studio: {address}")
    print("Keep this window open while you use it. Close it to stop.")
    if show:
        threading.Timer(0.5, webbrowser.open, [address]).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
