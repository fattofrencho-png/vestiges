"""Size relative to the type, with the game character as the yardstick (agreed with the author, 2026-11-05).

A 20-block tree is ordinary, a 20-block house is not small and a 20-block tower is short: size only means something inside a type.
Two numbers describe every asset:

  reach    the longest of width, depth and height, in blocks; 'players' is reach divided by the player's height (1.8 blocks).
           A fact that never changes.
  class    Compact, Typical, Grand or Giant, from where the reach sits among the builds of the same type: the smallest quarter is
           Compact, the middle half Typical, the next 20% Grand, the top 5% Giant. It moves as the library grows, so it is never part of a name.

A type with fewer than 8 builds has no distribution yet and uses fixed reaches instead (under 8 Compact, under 24 Typical, under 48 Grand).
"""
import time

MIN_BUILDS = 8
FIXED = ((8, "Compact"), (24, "Typical"), (48, "Grand"), (10 ** 9, "Giant"))
PLAYER_HEIGHT = 1.8
_cache = {"at": 0, "cuts": {}}


def players(reach):
    return round(reach / PLAYER_HEIGHT, 1)


def _reach_of(size_text):
    try:
        return max(int(p) for p in str(size_text).replace("x", " ").replace("×", " ").split())
    except ValueError:
        return 0


def _cuts():
    """{type: (p25, p75, p95)} from the library, rebuilt at most once a minute."""
    if time.time() - _cache["at"] < 60:
        return _cache["cuts"]
    import library
    by_type = {}
    for row in library.all_rows():
        if row.get("mode") == "file" or not row.get("f_type") or row.get("f_kind") in ("Package", "Piece"):
            continue
        reach = _reach_of(row.get("size", ""))
        if reach:
            by_type.setdefault(row["f_type"], []).append(reach)
    cuts = {}
    for type_value, values in by_type.items():
        if len(values) >= MIN_BUILDS:
            values.sort()
            at = lambda f: values[min(len(values) - 1, int(f * len(values)))]
            cuts[type_value] = (at(0.25), at(0.75), at(0.95))
    _cache.update(at=time.time(), cuts=cuts)
    return cuts


def forget():
    _cache["at"] = 0


def class_of(type_value, reach):
    try:
        cut = _cuts().get(type_value)
    except Exception:
        cut = None
    if cut:
        return "Compact" if reach <= cut[0] else "Typical" if reach <= cut[1] else "Grand" if reach <= cut[2] else "Giant"
    return next(name for limit, name in FIXED if reach < limit)
