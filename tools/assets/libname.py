"""The library's own file name for an asset: unique and still readable.

    <type>_<species or subtype>_<model number>-<variant>_<name>.nbt     e.g. trees_oak_0315-00_young-oak.nbt

The model number is the permanent card number (exporter.py), so two builds can share a name and still never clash. The
name part is the one the author checks in Review. Nothing here reads the game files.
"""
import re

import facets

NAME_PART = 40


def slug(text, limit=NAME_PART):
    text = re.sub(r"[^a-z0-9]+", "-", str(text or "").lower()).strip("-")
    return text[:limit].strip("-")


def number_of(project_id):
    """The permanent model number of an asset (given now when it has none yet)."""
    import exporter
    numbers = exporter.load_numbers()
    return numbers.get(project_id) or exporter.assign_numbers([project_id])[project_id]


def build(project, number, variant=0):
    kind = slug(facets.get(project, "type")) or "other"
    group = slug(facets.get(project, "species") or facets.get(project, "subtype")) or "general"
    name = slug(facets.get(project, "name") or project.get("title")) or "unnamed"
    return f"{kind}_{group}_{number:04d}-{variant:02d}_{name}.nbt"


def card(number, variant=0):
    return f"#{number:04d}-{variant:02d}"


def of(project):
    """{'file_name', 'card'} for the Review screen."""
    if not project.get("id"):
        return {}
    number = number_of(project["id"])
    return {"file_name": build(project, number), "card": card(number)}


def stamp(project):
    """Keeps the current library name with the asset (called when the asset is accepted or an accepted row changes)."""
    project["library"] = of(project)
    return project["library"]
