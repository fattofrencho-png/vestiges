"""Tags, favorites and collections on an asset.

Tags are lowercase words (hyphens inside). Collections are named groups, any asset can be in several; a collection exists
while it has assets in it. Names match without caring about capital letters, and keep the spelling first used.
"""
import naming

MAX_TAGS = 20
MAX_COLLECTIONS = 20
NAME_LENGTH = 40


def clean_collection(name):
    return " ".join(str(name or "").split())[:NAME_LENGTH]


def _same(a, b):
    return a.casefold() == b.casefold()


def add_tag(project, tag):
    tag = naming.slug(tag)
    tags = project.get("tags", [])
    if tag and tag not in tags and len(tags) < MAX_TAGS:
        project["tags"] = tags + [tag]


def remove_tag(project, tag):
    tag = naming.slug(tag)
    project["tags"] = [t for t in project.get("tags", []) if t != tag]


def add_to_collection(project, name):
    name = clean_collection(name)
    have = project.get("collections", [])
    if name and not any(_same(name, c) for c in have) and len(have) < MAX_COLLECTIONS:
        project["collections"] = have + [name]


def remove_from_collection(project, name):
    name = clean_collection(name)
    project["collections"] = [c for c in project.get("collections", []) if not _same(c, name)]


def rename_collection(project, old, new):
    old, new = clean_collection(old), clean_collection(new)
    if not new or not any(_same(old, c) for c in project.get("collections", [])):
        return
    remove_from_collection(project, old)
    add_to_collection(project, new)


def apply(project, action, data):
    """Changes one project in memory. The caller saves it."""
    if action == "favorite":
        project["favorite"] = bool(data.get("value"))
    elif action == "tag_add":
        add_tag(project, data.get("tag"))
    elif action == "tag_remove":
        remove_tag(project, data.get("tag"))
    elif action == "collection_add":
        add_to_collection(project, data.get("name"))
    elif action == "collection_remove":
        remove_from_collection(project, data.get("name"))
    elif action == "collection_rename":
        rename_collection(project, data.get("name"), data.get("to"))
    else:
        raise ValueError("Unknown action")


ACTIONS = ("favorite", "tag_add", "tag_remove", "collection_add", "collection_remove", "collection_rename")
