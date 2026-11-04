"""Small picture previews of assets for the Library's icon view.

Drawn here, not by the browser: each block becomes a box in the colour of its game texture (averaged), seen from a corner
with three shades, so a tree looks like a tree. Textures are read from the local game jar while drawing and never copied;
the finished pictures are kept in ~/.vestiges-assets/thumbs/, outside git, and redrawn when an asset gets a new version.
"""
import io
import threading
from pathlib import Path

from PIL import Image, ImageDraw

import projects

REVISION = 2          # change when the drawing changes, so old pictures are redrawn
SIZE = 192            # finished picture, in pixels
SUPER = 3             # drawn this many times larger, then shrunk, for smooth edges
MAX_BLOCKS = 60000
SHADE = {"top": 1.0, "left": 0.78, "right": 0.58}
FOLIAGE = (89, 174, 48)
GRASS = (121, 192, 90)
_colours = {}
_lock = threading.Lock()
_jar_source = None


def folder():
    return projects.HOME / "thumbs"


def _jar():
    if _jar_source is not None:
        return _jar_source()
    import pipeline
    return pipeline.jar()


def _mean(jar, texture):
    data = jar.texture(texture)
    if data is None:
        return (200, 0, 200)
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    if image.height > image.width:  # animated textures are a tall strip: use the first frame
        image = image.crop((0, 0, image.width, image.width))
    pixels = [p for p in image.getdata() if p[3] > 16]
    if not pixels:
        return (128, 128, 128)
    return tuple(sum(p[i] for p in pixels) // len(pixels) for i in range(3))


def _tinted(colour, tint):
    return tuple(c * t // 255 for c, t in zip(colour, tint))


def _tint_rgb(baked, block):
    tint = baked.get("tint")
    if isinstance(tint, str) and tint.startswith("#") and len(tint) == 7:
        return tuple(int(tint[i:i + 2], 16) for i in (1, 3, 5))
    return FOLIAGE if "leaves" in block or "vine" in block else GRASS


def look(jar, state):
    """(top colour, side colour, box) for a block state, or None when it can't be seen (air, barriers, markers).

    box is (x0, y0, z0, x1, y1, z1) in block units, the extent of the block's model.
    """
    with _lock:
        if state in _colours:
            return _colours[state]
    try:
        baked = jar.bake(state)
    except Exception:
        baked = {"quads": [], "kind": "invisible"}
    result = None
    quads = baked.get("quads") or []
    if quads:
        block = state.split("[")[0].replace("minecraft:", "")
        tops = [q for q in quads if q["n"][1] > 0.5]
        sides = [q for q in quads if abs(q["n"][0]) > 0.5 or abs(q["n"][2]) > 0.5]

        def colour(options):
            quad = (options or quads)[0]
            base = _mean(jar, quad["tex"])
            return _tinted(base, _tint_rgb(baked, block)) if quad.get("tinted") or baked.get("tint") else base
        xs = [q["v"][i] for q in quads for i in range(0, 12, 3)]
        ys = [q["v"][i] for q in quads for i in range(1, 12, 3)]
        zs = [q["v"][i] for q in quads for i in range(2, 12, 3)]
        result = (colour(tops), colour(sides), (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)))
    with _lock:
        _colours[state] = result
    return result


def _shade(colour, factor):
    return tuple(min(255, int(c * factor)) for c in colour)


def draw(blocks, jar, size=SIZE):
    """A PNG (bytes) of the blocks, seen from a corner. Empty assets give a small blank picture."""
    seen = []
    for x, y, z, state in blocks[:MAX_BLOCKS]:
        found = look(jar, state)
        if found:
            seen.append((x, y, z, found))
    canvas = size * SUPER
    if not seen:
        return _png(Image.new("RGBA", (size, size), (0, 0, 0, 0)))
    # Projection: screen x = (x - z) * 0.866, screen y = (x + z) * 0.5 - y. Larger x and z are nearer the viewer.
    seen.sort(key=lambda b: (b[0] + b[1] + b[2], b[1]))

    def project(x, y, z):
        return ((x - z) * 0.866, (x + z) * 0.5 - y)
    corners = [project(x + dx, y + dy, z + dz) for x, y, z, _ in seen for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    left, right = min(c[0] for c in corners), max(c[0] for c in corners)
    top, bottom = min(c[1] for c in corners), max(c[1] for c in corners)
    scale = canvas * 0.94 / max(right - left, bottom - top, 1)
    offset_x = (canvas - (right - left) * scale) / 2 - left * scale
    offset_y = (canvas - (bottom - top) * scale) / 2 - top * scale
    image = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)

    def at(x, y, z):
        px, py = project(x, y, z)
        return (px * scale + offset_x, py * scale + offset_y)
    for x, y, z, (top_colour, side_colour, (x0, y0, z0, x1, y1, z1)) in seen:
        x0, x1, y0, y1, z0, z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        pen.polygon([at(x0, y1, z0), at(x1, y1, z0), at(x1, y1, z1), at(x0, y1, z1)], fill=_shade(top_colour, SHADE["top"]))
        pen.polygon([at(x0, y1, z1), at(x1, y1, z1), at(x1, y0, z1), at(x0, y0, z1)], fill=_shade(side_colour, SHADE["left"]))
        pen.polygon([at(x1, y1, z1), at(x1, y1, z0), at(x1, y0, z0), at(x1, y0, z1)], fill=_shade(side_colour, SHADE["right"]))
    return _png(image.resize((size, size), Image.LANCZOS))


def _png(image):
    out = io.BytesIO()
    image.save(out, "PNG", optimize=False)
    return out.getvalue()


def get(project_id):
    """The picture of an asset's latest version, drawn now if there isn't a current one."""
    project = projects.load(project_id)
    if not project.get("versions"):
        return _png(Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0)))
    number = project["versions"][-1]["number"]
    path = folder() / f"{project_id}_v{number}_r{REVISION}.png"
    if path.exists():
        return path.read_bytes()
    data = draw(projects.load_version(project_id, number)["blocks"], _jar())
    folder().mkdir(parents=True, exist_ok=True)
    for old in folder().glob(f"{project_id}_v*.png"):
        old.unlink(missing_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_bytes(data)
    temp.replace(path)
    return data


def forget(project_id):
    for old in folder().glob(f"{project_id}_v*.png") if folder().is_dir() else []:
        old.unlink(missing_ok=True)


projects.delete_listeners.append(forget)
