"""Draws builds with the game's real textures: every face is painted from its own texture, seen from a corner.

Textures are read from the local Minecraft client jar while drawing and are never copied into the repo; only the finished
pictures are kept (by thumbs.py, in ~/.vestiges-assets/thumbs/, outside git). Leaves, grass and water get the game's
forest colours. Small builds keep each texture pixel sharp; large ones are drawn bigger and shrunk so they stay smooth.
Faces hidden behind a solid neighbour are skipped, and the game's see-through leaves are drawn see-through.
"""
import io
import math

from PIL import Image, ImageChops, ImageDraw

SHADE = {"top": 1.0, "south": 0.78, "east": 0.58}
FOLIAGE = (89, 174, 48)
GRASS = (121, 192, 90)
MAX_BLOCKS = 60000
_texture_cache = {}


def _tint_rgb(baked, block):
    tint = baked.get("tint")
    if isinstance(tint, str) and tint.startswith("#") and len(tint) == 7:
        return tuple(int(tint[i:i + 2], 16) for i in (1, 3, 5))
    return FOLIAGE if "leaves" in block or "vine" in block else GRASS


def _load_texture(jar, name):
    """The game's texture as RGBA (first frame when animated), or magenta when the game doesn't have it."""
    cached = _texture_cache.get(name)   # .get, not a membership test: another thread may clear the cache between two lookups
    if cached is not None:
        return cached
    data = jar.texture(name)
    if data is None:
        image = Image.new("RGBA", (2, 2), (200, 0, 200, 255))
    else:
        image = Image.open(io.BytesIO(data)).convert("RGBA")
        if image.height > image.width:
            image = image.crop((0, 0, image.width, image.width))
    _texture_cache[name] = image
    return image


def _light(image, factor, tint):
    """The texture multiplied by the face's shade and tint (both cheap on a 16 x 16 picture)."""
    mult = [min(1.0, factor * (t / 255 if tint else 1.0)) for t in (tint or (255, 255, 255))]
    r, g, b, a = image.split()
    return Image.merge("RGBA", (r.point([int(v * mult[0]) for v in range(256)]), g.point([int(v * mult[1]) for v in range(256)]),
                                b.point([int(v * mult[2]) for v in range(256)]), a))


def _shrink(image, width, height):
    """Averages a texture down (alpha-aware, so see-through edges don't go dark)."""
    return image.convert("RGBa").resize((width, height), Image.BOX).convert("RGBA")


def _shade_of(normal):
    nx, ny, nz = (abs(c) for c in normal)
    up = max(normal[1], 0)
    total = up + nx + nz
    if total < 1e-6:
        return 0.78
    return (up * SHADE["top"] + nz * SHADE["south"] + nx * SHADE["east"]) / total


def _sprites(jar, baked, block, scale, lit):
    """Pictures of each visible face of one block state, as (dx, dy, image, cull) with dx, dy from the block's origin on screen."""
    out = []
    tint_all = baked.get("tint")
    tint_rgb = _tint_rgb(baked, block)
    for quad in baked["quads"]:
        v = quad["v"]
        pts = []
        for i in range(4):
            x, y, z = v[i * 3:i * 3 + 3]
            pts.append(((x - z) * 0.866 * scale, ((x + z) * 0.5 - y) * scale))
        area = sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4))
        if area <= 1e-6:
            continue  # facing away from the viewer (or edge-on)
        e1 = (pts[1][0] - pts[0][0], pts[1][1] - pts[0][1])
        e3 = (pts[3][0] - pts[0][0], pts[3][1] - pts[0][1])
        det = e1[0] * e3[1] - e1[1] * e3[0]
        if abs(det) < 1e-6:
            continue
        # normal from the corners, for the shade
        a = [v[3 + k] - v[k] for k in range(3)]
        b = [v[9 + k] - v[k] for k in range(3)]
        n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
        length = math.sqrt(sum(c * c for c in n)) or 1
        n = [c / length for c in n]
        if n[0] + n[1] + n[2] < 0:
            n = [-c for c in n]
        tinted = bool(quad.get("tinted") or tint_all)
        key = (quad["tex"], round(_shade_of(n), 2), tint_rgb if tinted else None)
        if key not in lit:
            lit[key] = _light(_load_texture(jar, quad["tex"]), key[1], key[2])
        tex = lit[key]
        uv = quad.get("uv") or [0, 0, 1, 0, 1, 1, 0, 1]
        du = max(abs(uv[2] - uv[0]), 1e-6)
        dv = max(abs(uv[5] - uv[3]), 1e-6)
        # screen pixels per texel along each edge; shrink the texture when a texel is smaller than a pixel
        ppt = min(math.hypot(*e1) / (du * tex.width), math.hypot(*e3) / (dv * tex.height))
        if ppt < 1:
            tex = _shrink(tex, max(1, round(tex.width * ppt)), max(1, round(tex.height * ppt)))
        t = [(uv[2 * i] * tex.width, uv[2 * i + 1] * tex.height) for i in range(4)]
        f1 = (t[1][0] - t[0][0], t[1][1] - t[0][1])
        f3 = (t[3][0] - t[0][0], t[3][1] - t[0][1])
        # t = A s + b, solved from the two edges
        a00 = (f1[0] * e3[1] - f3[0] * e1[1]) / det
        a01 = (f3[0] * e1[0] - f1[0] * e3[0]) / det
        a10 = (f1[1] * e3[1] - f3[1] * e1[1]) / det
        a11 = (f3[1] * e1[0] - f1[1] * e3[0]) / det
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        x_min, y_min = math.floor(min(xs)) - 1, math.floor(min(ys)) - 1
        w, h = math.ceil(max(xs)) + 1 - x_min, math.ceil(max(ys)) + 1 - y_min
        c0 = t[0][0] + a00 * (x_min - pts[0][0]) + a01 * (y_min - pts[0][1])
        c1 = t[0][1] + a10 * (x_min - pts[0][0]) + a11 * (y_min - pts[0][1])
        sprite = tex.transform((w, h), Image.AFFINE, (a00, a01, c0, a10, a11, c1), Image.NEAREST)
        # keep only the face's own area (a touch wider so neighbouring faces leave no cracks)
        cx, cy = sum(xs) / 4 - x_min, sum(ys) / 4 - y_min
        grown = []
        for p in pts:
            px, py = p[0] - x_min, p[1] - y_min
            d = math.hypot(px - cx, py - cy) or 1
            grown.append((px + (px - cx) / d * 0.6, py + (py - cy) / d * 0.6))
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).polygon(grown, fill=255)
        sprite.putalpha(ImageChops.multiply(sprite.getchannel("A"), mask))
        out.append((x_min, y_min, sprite, quad.get("cull")))
    return out


DIRECTIONS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def draw(blocks, jar, look, size=192):
    """RGBA image of the blocks seen from a corner, each face painted with the game's texture."""
    _texture_cache.clear()   # textures are re-read from the jar for each picture, so nothing outlives a game update
    seen = {}
    for x, y, z, state in blocks[:MAX_BLOCKS]:
        found = look(jar, state)
        if found:
            seen[(x, y, z)] = state
    if not seen:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bakes = {}

    def bake(state):
        if state not in bakes:
            try:
                bakes[state] = jar.bake(state)
            except Exception:
                bakes[state] = {"quads": [], "kind": "invisible"}
        return bakes[state]
    solid = {p for p, s in seen.items() if bake(s).get("kind") == "solid"}

    def project(x, y, z):
        return ((x - z) * 0.866, (x + z) * 0.5 - y)
    cs = [project(x + dx, y + dy, z + dz) for x, y, z in seen for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    left, right = min(c[0] for c in cs), max(c[0] for c in cs)
    top, bottom = min(c[1] for c in cs), max(c[1] for c in cs)
    span = max(right - left, bottom - top, 1)
    ppt = 0.866 * (size * 0.94 / span) / 16
    over = 1 if ppt >= 1 else 2 if ppt >= 0.35 else 3     # many blocks per picture: draw larger, then shrink smoothly
    canvas = size * over
    scale = canvas * 0.94 / span
    offset_x = (canvas - (right - left) * scale) / 2 - left * scale
    offset_y = (canvas - (bottom - top) * scale) / 2 - top * scale
    image = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    lit, sprites = {}, {}
    for (x, y, z) in sorted(seen, key=lambda p: (p[0] + p[1] + p[2], p[1])):
        if (x + 1, y, z) in solid and (x, y + 1, z) in solid and (x, y, z + 1) in solid:
            continue   # hidden behind three solid blocks
        state = seen[(x, y, z)]
        if state not in sprites:
            baked = bake(state)
            block = state.split("[")[0].replace("minecraft:", "")
            sprites[state] = _sprites(jar, baked, block, scale, lit)
        px, py = ((x - z) * 0.866 * scale + offset_x), (((x + z) * 0.5 - y) * scale + offset_y)
        for dx, dy, sprite, cull in sprites[state]:
            if cull:
                nx, ny, nz = DIRECTIONS[cull]
                if (x + nx, y + ny, z + nz) in solid:
                    continue
            ox, oy = round(px + dx), round(py + dy)
            # clip to the picture
            sx, sy = max(0, -ox), max(0, -oy)
            ex, ey = min(sprite.width, canvas - ox), min(sprite.height, canvas - oy)
            if ex <= sx or ey <= sy:
                continue
            piece = sprite.crop((sx, sy, ex, ey)) if (sx, sy, ex, ey) != (0, 0, sprite.width, sprite.height) else sprite
            image.alpha_composite(piece, (ox + sx, oy + sy))
    if over > 1:
        image = image.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")
    return image
