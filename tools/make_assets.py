#!/usr/bin/env python3
"""Generate launcher icon and TV banner (no external assets needed)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (10, 12, 16)
SURF = (26, 32, 42)
ACCENT = (91, 140, 255)
ACCENT2 = (61, 220, 151)
FG = (242, 245, 249)


def rounded(size, radius, fill):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=fill)
    return img


def icon(size):
    img = rounded(size, int(size * 0.22), BG)
    d = ImageDraw.Draw(img)
    # subtle top glow
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-size * 0.3, -size * 0.55, size * 1.3, size * 0.55],
               fill=(91, 140, 255, 60))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)

    # remote body
    w, h = size * 0.26, size * 0.62
    x0 = (size - w) / 2
    y0 = size * 0.19
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=w * 0.45, fill=SURF,
                        outline=(70, 82, 102), width=max(1, int(size * 0.012)))

    # dpad ring
    cx = size / 2
    cy = y0 + h * 0.30
    r = w * 0.30
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ACCENT, width=max(2, int(size * 0.026)))

    # accent app button
    br = w * 0.16
    by = y0 + h * 0.62
    d.ellipse([cx - br, by - br, cx + br, by + br], fill=ACCENT)
    # second button
    d.ellipse([cx - br * 2.6 - br, by - br, cx - br * 2.6 + br, by + br],
              fill=(61, 220, 151))
    return img


def banner(w, h):
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    # gradient wash
    for y in range(h):
        t = y / h
        col = (int(10 + 8 * t), int(12 + 12 * t), int(16 + 24 * t))
        d.line([(0, y), (w, y)], fill=col)
    # accent bar
    d.rectangle([0, h - 6, w, h], fill=ACCENT)
    # icon at left
    ic = icon(int(h * 0.62))
    img.alpha_composite(ic, (int(h * 0.18), int(h * 0.19)))

    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    try:
        f1 = ImageFont.truetype(font_path, int(h * 0.20))
        f2 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(h * 0.10))
    except OSError:
        f1 = f2 = ImageFont.load_default()
    tx = int(h * 0.98)
    d.text((tx, h * 0.30), "TV Remote Remap", font=f1, fill=FG)
    d.text((tx, h * 0.56), "Short press · Long press · Double press · Webhooks", font=f2,
           fill=(138, 148, 166))
    return img


for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    out = os.path.join(ROOT, "res", f"mipmap-{density}")
    os.makedirs(out, exist_ok=True)
    icon(px).save(os.path.join(out, "ic_launcher.png"))

os.makedirs(os.path.join(ROOT, "res", "drawable-xhdpi"), exist_ok=True)
banner(320, 180).save(os.path.join(ROOT, "res", "drawable-xhdpi", "banner.png"))
print("assets written")
