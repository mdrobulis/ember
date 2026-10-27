#!/usr/bin/env python3
"""Generate Ember's PNG icons.

The SVG files are the source of truth for shape; this script rasterises the
same idea with Pillow and numpy so it does not depend on a system SVG renderer.
Run from the project root:

    python3 tools/make-icons.py
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICONS = ROOT / "public" / "icons"
S = 2048  # supersampled canvas


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def radial(cx, cy, radius, inner, outer, exponent=1.0):
    y, x = np.mgrid[0:S, 0:S]
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / radius
    d = np.clip(d, 0, 1) ** exponent
    arr = np.zeros((S, S, 3), dtype=np.uint8)
    for i in range(3):
        arr[..., i] = np.clip(inner[i] + (outer[i] - inner[i]) * d, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGB")


def cubic(p0, p1, p2, p3, steps=28):
    out = []
    for n in range(steps + 1):
        t = n / steps
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        out.append((x, y))
    return out


def flame_points(scale=1.0, dy=0.0, inner=False):
    if inner:
        # A smaller, solid cut-out. Keeping it dark gives the mark depth without
        # depending on a second gradient.
        pts = cubic((256, 374), (300, 374), (328, 346), (328, 306))
        pts += cubic((328, 306), (328, 276), (312, 250), (288, 224))[1:]
        pts += cubic((288, 224), (281, 250), (263, 265), (244, 275))[1:]
        pts += cubic((244, 275), (250, 222), (223, 180), (191, 149))[1:]
        pts += cubic((191, 149), (193, 210), (166, 243), (151, 281))[1:]
        pts += cubic((151, 281), (137, 318), (158, 374), (256, 374))[1:]
    else:
        pts = cubic((256, 440), (342, 440), (404, 381), (404, 302))
        pts += cubic((404, 302), (404, 240), (361, 196), (319, 148))[1:]
        pts += cubic((319, 148), (309, 195), (284, 224), (257, 246))[1:]
        pts += cubic((257, 246), (263, 169), (225, 105), (174, 57))[1:]
        pts += cubic((174, 57), (178, 149), (137, 212), (109, 273))[1:]
        pts += cubic((109, 273), (83, 320), (111, 440), (256, 440))[1:]
    k = S / 512.0
    return [(((x - 256) * scale + 256) * k, (((y - 256) * scale + 256 + dy) * k)) for x, y in pts]


def make_icon(path: Path, maskable: bool = False, size: int = 512):
    # Background: a near-black tile with a warm campfire glow.
    base = radial(S * 0.5, S * 0.46, S * 0.74, (23, 30, 42), (7, 9, 14), exponent=1.4)
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(760, 0, -8):
        a = int(2.7 * (1 - r / 760) ** 2.1)
        gd.ellipse((S / 2 - r, S * 0.47 - r, S / 2 + r, S * 0.47 + r), fill=(249, 115, 22, a))
    base = Image.alpha_composite(base.convert("RGBA"), glow).convert("RGB")

    if not maskable:
        # Rounded corners, like an iOS icon.
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=460, fill=255)
    else:
        # Full bleed; content stays inside the central safe zone.
        mask = Image.new("L", (S, S), 255)

    tile = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    tile.paste(base, (0, 0), mask)

    # Halo behind the flame.
    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((S * 0.25, S * 0.16, S * 0.75, S * 0.80), fill=(249, 115, 22, 54))
    halo = halo.filter(ImageFilter.GaussianBlur(90))
    tile = Image.alpha_composite(tile, Image.composite(halo, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask))

    # Flame with a vertical amber→orange gradient.
    flame_mask = Image.new("L", (S, S), 0)
    scale = 0.92 if not maskable else 0.82
    ImageDraw.Draw(flame_mask).polygon(flame_points(scale=scale), fill=255)
    flame_mask = flame_mask.filter(ImageFilter.GaussianBlur(1.2))

    grad = Image.new("RGB", (S, S))
    gd = ImageDraw.Draw(grad)
    for y in range(S):
        t = min(1.0, max(0.0, (y - S * 0.10) / (S * 0.74)))
        gd.line([(0, y), (S, y)], fill=lerp((253, 230, 138), (234, 88, 12), t ** 0.72))
    flame = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    flame.paste(grad, (0, 0), flame_mask)
    # Soft outer glow of the flame itself.
    outer = flame_mask.filter(ImageFilter.GaussianBlur(26))
    tint = Image.new("RGBA", (S, S), (249, 115, 22, 100))
    tile = Image.alpha_composite(tile, Image.composite(tint, Image.new("RGBA", (S, S), (0, 0, 0, 0)), outer))
    tile = Image.alpha_composite(tile, flame)

    # Inner cut-out.
    inner = Image.new("L", (S, S), 0)
    ImageDraw.Draw(inner).polygon(flame_points(scale=scale, inner=True), fill=255)
    inner = inner.filter(ImageFilter.GaussianBlur(1.4))
    cut = Image.new("RGBA", (S, S), (11, 15, 23, 225))
    tile = Image.alpha_composite(tile, Image.composite(cut, Image.new("RGBA", (S, S), (0, 0, 0, 0)), inner))

    # A restrained rim keeps the silhouette crisp on dark home screens.
    if not maskable:
        rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(rim).rounded_rectangle((3, 3, S - 4, S - 4), radius=457, outline=(255, 255, 255, 22), width=4)
        tile = Image.alpha_composite(tile, rim)

    tile = tile.resize((size, size), Image.LANCZOS)
    tile.save(path, "PNG", optimize=True)
    print(f"wrote {path.relative_to(ROOT)}")


def main():
    ICONS.mkdir(parents=True, exist_ok=True)
    make_icon(ICONS / "icon-512.png", size=512)
    make_icon(ICONS / "icon-192.png", size=192)
    make_icon(ICONS / "maskable-512.png", maskable=True, size=512)
    # Apple touch icons look best opaque; flatten onto the brand background.
    make_icon(ICONS / "apple-touch-icon.png", maskable=True, size=180)


if __name__ == "__main__":
    main()
