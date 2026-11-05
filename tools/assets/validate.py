"""Checks on a finished asset. Errors must be fixed; warnings are for the author to judge."""
import re
from collections import Counter, deque

SIDES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
WOOD = re.compile(r"(^|[:_])(log|wood|stem|hyphae)(\[|$)")
# Normal world generation features may only touch the 3x3 chunks around their own chunk. Anything wider
# is at risk of being cut, depending on where it starts, and should be placed as a structure instead.
SAFE_FOOTPRINT = 32
INVISIBLE = {"minecraft:barrier", "minecraft:light", "minecraft:structure_void"}


DATA_BLOCKS = {"command_block", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw", "test_block", "test_instance_block"}


def name_of(state):
    return state.split("[", 1)[0]


def check(tree, known_blocks=None, invisible=(), lenient=False):
    """invisible: blocks with no model. Besides barriers these are drawn by the game's code (chests, signs).
    lenient: for imported builds, where the author may want hand-placed leaves or floating parts, those only warn."""
    blocks = {(x, y, z): state for x, y, z, state in tree["blocks"]}
    errors, warnings = [], []
    names = Counter(name_of(s) for s in blocks.values())

    if known_blocks is not None:
        unknown = sorted(set(names) - known_blocks)
        if unknown:
            errors.append("Not vanilla blocks: " + ", ".join(unknown))

    # Leaves: unless permanent, the stored distance must be the true one, and no more than 6.
    logs = {p for p, s in blocks.items() if WOOD.search(s)}
    leaves = {p for p, s in blocks.items() if "_leaves" in s}
    true = {}
    queue = deque()
    for p in leaves:
        if any((p[0] + a, p[1] + b, p[2] + c) in logs for a, b, c in SIDES):
            true[p] = 1
            queue.append(p)
    while queue:
        p = queue.popleft()
        for a, b, c in SIDES:
            n = (p[0] + a, p[1] + b, p[2] + c)
            if n in leaves and n not in true:
                true[n] = true[p] + 1
                queue.append(n)
    permanent = {p for p in leaves if "persistent=true" in blocks[p]}
    decaying = sum(1 for p in leaves - permanent if true.get(p, 99) > 6)
    wrong = 0
    for p in leaves - permanent:
        found = re.search(r"distance=(\d+)", blocks[p])
        if found is None or int(found.group(1)) != true.get(p, 99):
            wrong += 1
    soft = warnings if lenient else errors
    if decaying:
        soft.append(f"{decaying} leaves are too far from a log and would decay")
    if wrong:
        soft.append(f"{wrong} leaves have the wrong saved distance")
    if permanent:
        warnings.append(f"{len(permanent)} leaves are permanent, so they won't fall when the tree is cut.")

    # Floating: every block must connect, face to face, to something at or below the ground.
    seen = {p for p in blocks if p[1] <= 0}
    queue = deque(seen)
    while queue:
        p = queue.popleft()
        for a, b, c in SIDES:
            n = (p[0] + a, p[1] + b, p[2] + c)
            if n in blocks and n not in seen:
                seen.add(n)
                queue.append(n)
    floating = len(blocks) - len(seen)
    if floating:
        soft.append(f"{floating} blocks are floating")

    if names.get("minecraft:barrier"):
        warnings.append(f"{names['minecraft:barrier']} barriers: players can't see them, and can't break them in survival.")
    data_blocks = sorted(n for n in names if n.split(":")[-1] in DATA_BLOCKS)
    if data_blocks:
        warnings.append("Blocks players shouldn't get: " + ", ".join(n.replace("minecraft:", "") for n in data_blocks))
    unknown_names = set() if known_blocks is None else set(names) - known_blocks
    drawn_by_code = sorted(set(invisible) & set(names) - INVISIBLE - unknown_names)
    if drawn_by_code:
        warnings.append("The preview can't draw these, so they show as outlines: " + ", ".join(n.replace("minecraft:", "") for n in drawn_by_code))

    above = [p for p in blocks if p[1] >= 0] or list(blocks) or [(0, 0, 0)]
    xs, ys, zs = zip(*above)
    width_x, width_z = max(xs) - min(xs) + 1, max(zs) - min(zs) + 1
    if max(width_x, width_z) > SAFE_FOOTPRINT:
        warnings.append(
            f"{max(width_x, width_z)} blocks wide: too wide for normal tree or plant generation "
            f"(safe up to about {SAFE_FOOTPRINT}). It must be placed as a structure.")
    low = min((p[1] for p in blocks), default=0)
    stats = {
        "height": max(ys) + 1,
        "roots_depth": max(-low, 0),
        "width_x": width_x,
        "width_z": width_z,
        "blocks": len(blocks),
        "logs": len(logs),
        "leaves": len(leaves),
        "block_counts": {k.replace("minecraft:", ""): v for k, v in names.most_common()},
    }
    return {"ok": not errors, "errors": errors, "warnings": warnings, "stats": stats}


def check_brief(result, constraints):
    """Measures an asset against the agreed brief. Returns a list of plain-word failures."""
    stats = result["stats"]
    used = stats["block_counts"]
    width = max(stats["width_x"], stats["width_z"])
    failures = []

    def limit(key, value, words):
        wanted = constraints.get(key)
        if wanted is None:
            return
        if (key.startswith("min") and value < wanted) or (key.startswith("max") and value > wanted):
            failures.append(f"{words} is {value}, but the brief says {key.replace('_', ' ')} {wanted}")

    limit("min_height", stats["height"], "Height")
    limit("max_height", stats["height"], "Height")
    limit("min_width", width, "Width")
    limit("max_width", width, "Width")
    for block in constraints.get("required_blocks") or []:
        if block.replace("minecraft:", "") not in used:
            failures.append(f"The brief asks for {block}, but it isn't used")
    for block in constraints.get("forbidden_blocks") or []:
        if block.replace("minecraft:", "") in used:
            failures.append(f"The brief forbids {block}, but it is used")
    return failures
