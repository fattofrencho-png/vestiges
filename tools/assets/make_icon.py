"""Draws the Asset Studio icon (a grass-topped block) into icon.ico, once, if it isn't there yet."""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent / "icon.ico"


def main():
    if OUT.exists():
        return
    n = 256
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, top, w, h = n // 2, 36, 100, 58
    # isometric cube: top, left, right
    d.polygon([(cx, top), (cx + w, top + h // 2 + 8), (cx, top + h + 16), (cx - w, top + h // 2 + 8)], fill=(96, 168, 64))
    d.polygon([(cx - w, top + h // 2 + 8), (cx, top + h + 16), (cx, n - 30), (cx - w, n - 78)], fill=(120, 84, 52))
    d.polygon([(cx + w, top + h // 2 + 8), (cx, top + h + 16), (cx, n - 30), (cx + w, n - 78)], fill=(96, 66, 40))
    img.save(OUT, sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])


if __name__ == "__main__":
    main()
