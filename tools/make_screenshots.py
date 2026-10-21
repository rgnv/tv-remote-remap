#!/usr/bin/env python3
"""Normalize device screenshots for the repo: 1280x720, metadata stripped."""
import os
import sys
from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else "/var/lib/hermes/cache/scratch"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "screenshots")

SHOTS = [
    ("shot_main.png", "main.png"),
    ("shot_detail.png", "button.png"),
    ("shot_picker.png", "actions.png"),
    ("shot_config.png", "webhook.png"),
    ("shot_apps.png", "apps.png"),
    ("shot_settings.png", "settings.png"),
]

os.makedirs(OUT, exist_ok=True)
for src, dst in SHOTS:
    path = os.path.join(SRC, src)
    if not os.path.exists(path):
        print("missing", path)
        continue
    im = Image.open(path).convert("RGB")
    im = im.resize((1280, 720), Image.LANCZOS)
    im.save(os.path.join(OUT, dst), "PNG", optimize=True)
    print(dst, os.path.getsize(os.path.join(OUT, dst)))
