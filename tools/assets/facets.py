"""Facets: what we know about an asset (type, species, realm, style...), and who said so.

A facet is a record, not a bare tag. The machine PROPOSES; the author DECIDES. A human decision always beats a proposal, and
re-running the analyser never overwrites one. A rejected proposal is remembered so it isn't offered again.

project["facets"][name] = {
    "value":      what is believed now (the human's choice if there is one, else the best proposal),
    "source":     "human" | "rule" | "fingerprint" | "picture" | "vision" | "import",
    "confidence": 0..1 (1.0 for a human),
    "why":        plain words,
    "status":     "proposed" | "accepted" | "changed" | "rejected",
    "rejected":   values the author turned down,
    "version":    analyser version that made the proposal,
    "decided_at": time of the human decision,
}

project["review"] = {"state": "inbox" | "accepted" | "rejected" | "needs_work", "reason": "", "decided_at": 0}
  inbox      analysed, waiting for the author
  accepted   in the catalog
  rejected   archived with a reason (restorable); delete-at-once is a separate, explicit action
  needs_work open it in the workbench first
"""
import time

FACETS = ("name", "type", "subtype", "species", "realm", "style", "palette", "setting", "scale", "complexity", "rarity", "front", "ground", "season", "kind")
SOURCES = ("human", "rule", "fingerprint", "picture", "vision", "import")
STATES = ("inbox", "accepted", "rejected", "needs_work")
REJECT_REASONS = ("duplicate", "ugly", "wrong", "not vanilla", "too small", "package", "other")


def _book(project):
    return project.setdefault("facets", {})


def get(project, name):
    """The believed value of a facet, or None."""
    record = (project.get("facets") or {}).get(name)
    return record["value"] if record and record.get("status") != "rejected" else None


def record(project, name):
    return (project.get("facets") or {}).get(name)


def propose(project, name, value, source, confidence, why="", version=0):
    """Offer a value. Ignored when a human already decided this facet, or when this exact value was rejected before.
    Returns True when the proposal was stored."""
    if name not in FACETS:
        raise ValueError(f"unknown facet {name!r}")
    if source not in SOURCES or source == "human":
        raise ValueError("proposals come from the machine; use decide() for the author")
    current = _book(project).get(name)
    if current and current.get("source") == "human":
        return False
    if current and current.get("status") == "accepted":
        return False   # the author already agreed with this row: a later analysis doesn't reopen it
    if current and value in current.get("rejected", []):
        return False
    rejected = list(current.get("rejected", [])) if current else []
    # Keep the stronger of two proposals; a newer analyser version replaces its own older one.
    if current and current.get("status") == "proposed" and current.get("version", 0) >= version and current["confidence"] > confidence and current["value"] != value:
        return False
    _book(project)[name] = {"value": value, "source": source, "confidence": round(float(confidence), 3), "why": why,
                            "status": "proposed", "rejected": rejected, "version": version, "decided_at": 0}
    return True


def accept(project, name, now=None):
    """The author agrees with the proposal as it stands."""
    current = _book(project).get(name)
    if not current:
        raise KeyError(name)
    current.update(status="accepted", decided_at=now or time.time())
    return current


def decide(project, name, value, now=None):
    """The author sets a value by hand. Beats every proposal from now on."""
    if name not in FACETS:
        raise ValueError(f"unknown facet {name!r}")
    current = _book(project).get(name) or {}
    same = current.get("value") == value
    _book(project)[name] = {"value": value, "source": "human", "confidence": 1.0, "why": "set by the author",
                            "status": "accepted" if same else "changed", "rejected": list(current.get("rejected", [])),
                            "version": current.get("version", 0), "decided_at": now or time.time()}
    return _book(project)[name]


def reject(project, name, now=None):
    """The author says this proposal is wrong. It is remembered and not offered again; the facet is left empty."""
    current = _book(project).get(name)
    if not current:
        raise KeyError(name)
    rejected = list(current.get("rejected", []))
    if current["value"] not in rejected:
        rejected.append(current["value"])
    _book(project)[name] = {"value": None, "source": "human", "confidence": 1.0, "why": "rejected by the author", "status": "rejected",
                            "rejected": rejected, "version": current.get("version", 0), "decided_at": now or time.time()}


def undecided(project):
    """Facets still waiting for the author."""
    return [n for n, r in (project.get("facets") or {}).items() if r.get("status") == "proposed"]


def review_of(project):
    return project.get("review") or {"state": "inbox", "reason": "", "decided_at": 0}


def set_review(project, state, reason="", now=None):
    if state not in STATES:
        raise ValueError(f"unknown review state {state!r}")
    project["review"] = {"state": state, "reason": reason if state == "rejected" else "", "decided_at": now or time.time()}
    return project["review"]


def snapshot(project):
    """Compact view for the index and the review screen: {facet: (value, status, confidence)}."""
    return {n: (r["value"], r["status"], r["confidence"]) for n, r in (project.get("facets") or {}).items()}


KEY_FACETS = ("type", "species", "subtype")


def confidence_of(project, keys=KEY_FACETS):
    """How sure the machine is about this asset overall: the weakest undecided proposal among the key facets (what it is).
    Realm, palette, scale, complexity and rarity are shown for review but don't hold an asset back from a batch accept.
    1.0 when nothing is undecided."""
    pending = [r["confidence"] for n, r in (project.get("facets") or {}).items() if r.get("status") == "proposed" and n in keys]
    return min(pending) if pending else 1.0
