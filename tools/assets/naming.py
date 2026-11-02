"""Proposes detailed, sortable names: kind_subject_materials_detail_WxHxD_source_varNN. No AI: it reads the path and the block counts."""
import re

FILLER = {"data", "minecraft", "structure", "structures", "schematic", "schematics", "schem", "litematic", "worldedit", "nbt",
          "new", "the", "a", "an", "of", "final", "copy", "unnamed", "untitled", "build", "test", "my"}
KINDS = (("house", "house|home|hut|cottage|cabin|farmhouse|shack|villa"), ("tree", "tree|oak|birch|spruce|pine|sapling"),
         ("ruin", "ruin|ruined|ruins|remains"), ("plant", "plant|bush|shrub|flower|fern|undergrowth"),
         ("structure", "tower|castle|camp|fort|temple|dungeon|village|bridge|wall|gate"))
LOG = re.compile(r"(^|_)(log|wood|stem|hyphae)$")
SHAPES = re.compile(r"_(planks|log|wood|stem|hyphae|stairs|slab|wall|fence_gate|fence|leaves|door|trapdoor|button|pressure_plate|sign|hanging_sign|pillar|bricks|brick|tiles|tile|block|sapling)$")
STRIP = re.compile(r"^(stripped_|chiseled_|polished_|cut_|smooth_|cracked_|infested_|waxed_)+")
DETAILS_ONLY = re.compile(r"torch|lantern|redstone|repeater|comparator|piston|hopper|dropper|dispenser|observer|lever|rail|vine|carpet|flower|fern|"
                          r"short_grass|tall_grass|bush|water|lava|bubble|kelp|seagrass|chain|glow_lichen|sculk_vein|ladder|scaffolding|cobweb|barrier|light$|snow$|"
                          r"tripwire|string|bed$|banner|skull|head$|candle|campfire|chest|barrel|furnace|crafting_table|note_block|target|button|pressure_plate")


def slug(text, keep_case_breaks=False):
    text = str(text)
    if keep_case_breaks:
        text = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "-", text)
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def source_path(source):
    """The most descriptive path a source has: an archive's inner path, or the file's own."""
    return str((source or {}).get("item") or (source or {}).get("name") or "").replace("\\", "/")


def subject_from(source, title=""):
    if title:
        words = [w for w in slug(title, True).split("-") if w and w not in FILLER]
        if words:
            return "-".join(words[:6])[:48].strip("-")
    path = source_path(source)
    parts = [p for p in path.split("/") if p]
    if not parts:
        return "unnamed"
    stem = re.sub(r"\.[A-Za-z0-9]+$", "", parts[-1])

    def words_of(text):
        return [w for w in slug(text, True).split("-") if w and w not in FILLER]
    words = words_of(stem)
    while words and words[-1].isdigit():  # plains_house_1 and _2 differ by variant, not by subject
        words.pop()
    for parent in reversed(parts[:-1]):
        if len(words) >= 2:
            break
        words = [w for w in words_of(parent) if w not in words] + words
    return "-".join(words[:6])[:48].strip("-") or "unnamed"


def guess_kind(path, counts):
    words = {w.removesuffix("s") if len(w) > 3 else w for w in slug(path).split("-")} | set(slug(path).split("-"))
    for kind, pattern in KINDS:
        if words & set(pattern.split("|")):
            return kind
    total = sum(counts.values()) or 1
    leaves = sum(n for b, n in counts.items() if b.endswith("_leaves"))
    logs = sum(n for b, n in counts.items() if LOG.search(b))
    if logs and leaves and (leaves + logs) / total >= 0.6:
        return "tree"
    if total <= 40 and not logs:
        return "plant" if leaves or any(re.search("flower|fern|bush|moss", b) for b in counts) else "other"
    return "structure"


def material_word(block):
    name = STRIP.sub("", block.split(":")[-1])
    while True:
        shorter = SHAPES.sub("", name)
        if shorter == name:
            break
        name = shorter
    return name.removeprefix("mossy_")


def materials(counts):
    tally = {}
    for block, count in counts.items():
        if ":" in block or DETAILS_ONLY.search(block):
            continue
        word = material_word(block)
        tally[word] = tally.get(word, 0) + count
    top = sorted(tally.items(), key=lambda pair: (-pair[1], pair[0]))[:2]
    return "-".join(slug(word) for word, _ in top) or "mixed"


def detail(counts):
    total = sum(counts.values()) or 1
    share = lambda pattern: sum(n for b, n in counts.items() if re.search(pattern, b)) / total
    out = []
    if share("moss|lichen") >= 0.03:
        out.append("mossy")
    if share("vine") >= 0.02:
        out.append("vines")
    if share("redstone|repeater|comparator|piston|observer|hopper|dispenser|dropper") >= 0.03:
        out.append("redstone")
    if share("^water$|kelp|seagrass") >= 0.1:
        out.append("water")
    return "-".join(out)


def source_word(source, credit):
    if (credit or {}).get("creator"):
        return slug(credit["creator"])[:24] or "own"
    item = (source or {}).get("item") or ""
    found = re.match(r"^data/([^/]+)/", item)
    if found:
        return slug(found.group(1))[:24]
    if item:
        return slug(re.sub(r"\.[A-Za-z0-9]+$", "", (source or {}).get("name", "")))[:24] or "own"
    return "own"


def base_name(kind, subject, stats, source, credit):
    size = f"{stats['width_x']}x{stats['height']}x{stats['width_z']}"
    parts = [slug(kind) or "other", subject or "unnamed", materials(stats["block_counts"]), detail(stats["block_counts"]), size, source_word(source, credit)]
    return "_".join(p for p in parts if p)


def next_free(base, used):
    number = 1
    while f"{base}_var{number:02d}" in used:
        number += 1
    return f"{base}_var{number:02d}"


def clean(name):
    """A name the author typed, made safe for files and the game."""
    text = re.sub(r"[^a-z0-9_-]+", "-", str(name).strip().lower()).strip("-_")
    text = re.sub(r"-*_-*", "_", re.sub(r"-{2,}", "-", text))
    return re.sub(r"_{2,}", "_", text)[:120]


def unique(name, used):
    """Keeps a free name as it is. A taken one gets the next variant number."""
    if name not in used:
        return name
    return next_free(re.sub(r"_var\d+$", "", name), used)
