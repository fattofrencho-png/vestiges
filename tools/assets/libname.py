"""The names of an asset, agreed with the author on 2026-11-05 (docs/assets/naming-convention.md). One permanent card number (cards.py), three names:

    library   <type>_<species>_<card>_<name>_<size>_<tags>.nbt       e.g. trees_oak_1001-0001_young-oak_11x23x11_forest-autumn.nbt
    video     #1001-0001 · Young oak       catalog id 1001-0001
    game      vestiges:trees/oak_1001_0001                         lowercase, never changes (built from type, species and card number)

The library name is regenerated from the asset's rows whenever one is corrected. Details with nothing to say (Any, Clean, Overworld) are
left out; rarity, complexity and the size class are not in names because they move as the library grows. The author picks one of the
library naming rules in Review (applies to every asset). Nothing here reads the game files.
"""
import json
import re

import cards
import facets
import projects

RULES = (("convention", "Agreed: type, species, card, name, size, tags"), ("size", "Size first: sorts by size"),
         ("number", "Card number first: sorts by number"), ("short", "Short: type, species, card, name, size"))
DEFAULT_RULE = "convention"
NAME_PART = 40
MAX_LENGTH = 200
MAX_TAGS = 3
MAX_ALL_TAGS = 4   # the facet tags (palette, style...) and the asset's own tags together
SKIP = {"any", "no-clear-front", "clean", "overworld", ""}
TAG_FACETS = ("realm", "style", "palette", "setting", "season")
EXTRA_FACETS = ("scale", "complexity", "rarity", "front", "ground")   # only in the rules that carry everything


def slug(text, limit=NAME_PART):
    text = re.sub(r"[^a-z0-9]+", "-", str(text or "").lower()).strip("-")
    return text[:limit].strip("-")


def short_type(value):
    """'Civic and public buildings' -> 'civic', 'Trees' -> 'trees': the first part before 'and'."""
    return slug(value).split("-and-")[0] or "other"


def _rule_file():
    return projects.HOME / "naming_rule.json"


def rule():
    try:
        value = json.loads(_rule_file().read_text(encoding="utf-8")).get("rule")
    except (OSError, ValueError, AttributeError):
        value = None
    return value if value in dict(RULES) else DEFAULT_RULE


def set_rule(value):
    if value not in dict(RULES):
        raise ValueError("Unknown naming rule")
    _rule_file().parent.mkdir(parents=True, exist_ok=True)
    _rule_file().write_text(json.dumps({"rule": value}), encoding="utf-8")


def _facet_tokens(project, names):
    out = []
    for name in names:
        value = slug(facets.get(project, name))
        if name == "front" and value:
            value = "front-" + value
        if name == "ground":
            value = value.replace("has-", "")
        if value not in SKIP:
            out.append(value)
    return out


def _tags(project, taken):
    """The author's own and automatic tags, minus any that only repeat a word already in the name, at most three."""
    words = {w for token in taken for w in str(token).split("-")}
    out = []
    for tag in project.get("tags", []):
        value = slug(tag)
        if value and value not in out and not (set(value.split("-")) <= words):
            out.append(value)
    return out[:MAX_TAGS]


def _size(project):
    m = (project.get("analysis") or {}).get("measures") or {}
    return f"{m['w']}x{m['h']}x{m['d']}" if m.get("w") else ""


def card_of(project):
    """(card, is_preview): the asset's permanent card, or the one it would get if accepted now."""
    if project.get("card"):
        return project["card"], False
    return cards.preview(project), True


def build(project, card, which=None):
    which = which or rule()
    kind = short_type(facets.get(project, "type"))
    group = slug(facets.get(project, "species") or facets.get(project, "subtype")) or "general"
    name = slug(facets.get(project, "name") or project.get("title")) or "unnamed"
    size = _size(project)
    number = cards.short(card) if card else "0000-0000"
    tags = _facet_tokens(project, TAG_FACETS)
    extra = _facet_tokens(project, EXTRA_FACETS)
    scale = slug(facets.get(project, "scale"))
    tags = (tags + _tags(project, [kind, group, name, size, *tags]))[:MAX_ALL_TAGS]
    if which == "short":
        head, tail = [kind, group, number, name, size], []
    elif which == "size":
        head, tail = [kind, scale, size, group, number, name], tags + extra
    elif which == "number":
        head, tail = [number.rjust(10, "0"), kind, group, name, size], tags + extra
    else:
        head, tail = [kind, group, number, name, size], tags
    parts = [p for p in head + tail if p]
    while len("_".join(parts)) + 4 > MAX_LENGTH and tail:
        tail.pop()   # too long: drop the last tags, never the card number or the name
        parts = [p for p in head + tail if p]
    return "_".join(parts) + ".nbt"


def card_label(card):
    return cards.label(card) if card else ""


def video_label(project):
    """What the video cards show: '#1001-0001 · Young oak'."""
    card = project.get("card")
    name = facets.get(project, "name") or project.get("title", "")
    return f"{cards.label(card)} · {name}" if card else name


def game_id(project):
    """The id Minecraft knows the structure by: vestiges:<type>/<species>_<model>_<realm and variant>. None until the asset has a card."""
    card = project.get("card")
    if not card:
        return None
    group = slug(facets.get(project, "species") or facets.get(project, "subtype"), 24).replace("-", "_") or "general"
    folder = short_type(facets.get(project, "type")).replace("-", "_")
    return f"vestiges:{folder}/{group}_{card['model']}_{card['realm']:02d}{card['variant']:02d}"


def of(project):
    """What Review shows: the file name by the chosen rule, the card, and the other rules' names to compare."""
    if not project.get("id"):
        return {}
    card, is_preview = card_of(project)
    chosen = rule()
    return {"file_name": build(project, card, chosen), "card": card_label(card), "preview": is_preview, "rule": chosen, "game_id": game_id(project),
            "options": [{"rule": r, "label": label, "file_name": build(project, card, r)} for r, label in RULES]}


def stamp(project):
    """Keeps the current library name with the asset (when it is accepted, or an accepted row changes)."""
    found = of(project)
    project["library"] = {"file_name": found["file_name"], "card": found["card"]}
    return project["library"]
