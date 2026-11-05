"""Permanent card numbers, agreed with the author on 2026-11-05 (docs/assets/naming-convention.md).

    #SNNN-SSNN      e.g. #1001-0001 = the first oak, #1001-0101 = its Nether version

    SNNN  the model: the folder number times 1000 plus a counter (Trees are 1001 to 1999, Houses 5001 to 5999, folder 28 is 28001 and up)
    SS    the realm code: 00 Overworld (regular), 01 Nether, 02 End, 03 Elven ...
    NN    the variant counter inside that model and realm, 01 to 99

A number is given when the author accepts an asset (from its Type and Realm) and never changes after that. A build with the same shape as an
accepted one (the same design in other materials, such as the plains and the savanna weaponsmith) becomes a variant of that model.
The counters live in ~/.vestiges-assets/card_registry.json, outside git.
"""
import json
import threading

import facets
import projects

FOLDERS = ("Trees", "Gardens and plants", "Terrain", "Water", "Houses", "Interiors", "Furniture and props", "Towers", "Gates", "Walls",
           "Castles and fortresses", "Forts and camps", "Workshops and shops", "Civic and public buildings", "Streets and plazas", "Farms",
           "Industry and tech", "Bridges and roads", "Ships and vehicles", "Harbours and stations", "Temples, shrines and graves",
           "Portals and magic sites", "Details", "Techniques and how-to", "Starter and survival", "Statues and pixel art",
           "Sports and entertainment", "Mega builds and cities")
REALMS = ("Overworld", "Nether", "End", "Elven", "Dwarven", "Wizard", "Undead", "Fairy", "Dragon", "Frost", "Pirate", "Tribal")
MAX_MODELS, MAX_VARIANTS = 999, 99
_lock = threading.RLock()


def folder_number(type_value):
    """1 for Trees ... 28 for Mega builds and cities; None for a type without a folder (Other)."""
    return FOLDERS.index(type_value) + 1 if type_value in FOLDERS else None


def realm_code(realm_value):
    return REALMS.index(realm_value) if realm_value in REALMS else 0


def _file():
    return projects.HOME / "card_registry.json"


def _load():
    try:
        data = json.loads(_file().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        data = {}
    return {"folders": data.get("folders", {}), "variants": data.get("variants", {}), "shapes": data.get("shapes", {}), "exacts": data.get("exacts", [])}


def _save(data):
    _file().parent.mkdir(parents=True, exist_ok=True)
    temp = _file().with_suffix(".tmp")
    temp.write_text(json.dumps(data, indent=0, sort_keys=True), encoding="utf-8")
    temp.replace(_file())


def label(card):
    """For people: #1001-0101."""
    return f"#{card['model']}-{card['realm']:02d}{card['variant']:02d}"


def short(card):
    """For file names, catalogs and ids: 1001-0101."""
    return f"{card['model']}-{card['realm']:02d}{card['variant']:02d}"


def _decide(project, data):
    """The card this asset would get now, without writing anything. None when it has no folder yet."""
    folder = folder_number(facets.get(project, "type"))
    if folder is None:
        return None
    realm = realm_code(facets.get(project, "realm"))
    prints = (project.get("analysis") or {}).get("prints") or {}
    shape = prints.get("shape")
    model = data["shapes"].get(shape) if shape else None
    if model and model // 1000 != folder:
        model = None   # the same shape was filed in another folder: not a twin of that model
    if prints.get("exact") in data["exacts"]:
        model = None   # an exact copy (turned or mirrored) of a numbered build is a duplicate, not a variant: it gets a model of its own
    if model is None:
        counter = int(data["folders"].get(str(folder), 0)) + 1
        if counter > MAX_MODELS:
            raise ValueError(f"Folder {folder} is full: {MAX_MODELS} models")
        model = folder * 1000 + counter
        variant = 1
    else:
        variant = int(data["variants"].get(f"{model}-{realm}", 0)) + 1
        if variant > MAX_VARIANTS:
            raise ValueError(f"Model {model} has {MAX_VARIANTS} variants in this realm already")
    return {"model": model, "realm": realm, "variant": variant, "shape": shape or "", "exact": prints.get("exact") or ""}


def preview(project):
    """What the asset would be numbered if it were accepted now (not reserved)."""
    with _lock:
        return _decide(project, _load())


def assign(project):
    """Gives the asset its permanent card when it has none yet. Returns the card, or None when it has no folder. The caller saves the project."""
    if project.get("card"):
        return project["card"]
    with _lock:
        data = _load()
        card = _decide(project, data)
        if card is None:
            return None
        folder = card["model"] // 1000
        data["folders"][str(folder)] = max(int(data["folders"].get(str(folder), 0)), card["model"] - folder * 1000)
        data["variants"][f"{card['model']}-{card['realm']}"] = card["variant"]
        if card["shape"]:
            data["shapes"].setdefault(card["shape"], card["model"])
        if card["exact"] and card["exact"] not in data["exacts"]:
            data["exacts"].append(card["exact"])
        _save(data)
        project["card"] = {k: card[k] for k in ("model", "realm", "variant")}
        return project["card"]
