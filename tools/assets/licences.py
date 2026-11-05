"""What the author may do with an asset, from its licence and recorded permission.

Five answers, kept simple on purpose:
- own: made by the author (AI-made, or an import with no creator, licence or permission noted)
- open: a licence that allows reuse and changes (MIT, Apache, CC0, CC-BY, GPL ...). Credit is still given.
- permission: the licence is restrictive, but the author wrote down where the creator said yes
- limited: all rights reserved, non-commercial or no-derivatives, and no permission noted
- unknown: made by someone else but no licence and no permission noted (to confirm)

can_modify is True, False, or None when unknown. This is a reminder for the author, not legal advice.
"""
import re

OPEN = re.compile(r"^(mit|apache|bsd|isc|cc0|unlicense|zlib|wtfpl|mpl|l?gpl|public[ -]?domain|cc-by(-sa)?(-\d|$))", re.I)
NO_DERIVATIVES = re.compile(r"-nd(-|$)|no[ -]?deriv", re.I)
RESTRICTED = re.compile(r"all[ -]?rights[ -]?reserved|-nc(-|$)|non[ -]?commercial|-nd(-|$)|no[ -]?deriv", re.I)

WORDS = {
    "own": "Your own work",
    "open": "Open licence",
    "permission": "Permission noted",
    "limited": "Limited: check before using",
    "unknown": "Licence unknown",
}


class LicenceProblem(Exception):
    pass


def of_project(project):
    """The answer for a saved asset. Files taken from an archive count as someone else's work even when nothing is noted."""
    foreign = (project.get("source") or {}).get("type") == "archive"
    return assess(project.get("credit"), project.get("mode") in ("import", "file"), foreign)


def assess(credit, imported=True, foreign=False):
    """credit: {creator, permission, licence, link}. Returns {status, can_modify, words}."""
    credit = credit or {}
    licence = str(credit.get("licence", "")).strip()
    permission = str(credit.get("permission", "")).strip()
    creator = str(credit.get("creator", "")).strip()
    link = str(credit.get("link", "")).strip()
    if not imported or not (creator or licence or permission or link or foreign):
        status, modify = "own", True
    elif licence and OPEN.match(licence) and not RESTRICTED.search(licence):
        status, modify = "open", True
    elif permission:
        status, modify = "permission", True
    elif licence and RESTRICTED.search(licence):
        status, modify = "limited", False
    else:
        status, modify = "unknown", None
    return {"status": status, "can_modify": modify, "words": WORDS[status]}


def note(credit):
    """A line for credits.md about what to check, or empty."""
    result = assess(credit)
    if result["status"] == "limited":
        return "Limited licence and no permission noted: get permission before releasing."
    if result["status"] == "unknown":
        return "Licence unknown (to confirm)."
    return ""
