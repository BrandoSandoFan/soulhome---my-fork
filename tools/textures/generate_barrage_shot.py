# File created ~ 20 - 9 - 2026
"""
Renders SoulBarrageShotModel's texture (#194).

Run it, commit what it writes - the same "a script in the tree, not a downloaded asset" answer
``tools/ambience`` already gives for the soul's ambient sound (#209/#213):

    python3 tools/textures/generate_barrage_shot.py

The shell's model (``SoulBarrageShotModel``) is a single 4x4x4 cube. Minecraft's default box
unwrap for that size occupies the top-left 16x8 pixels of a texture sheet, one cell per face, so
every face is painted with the same dark, faintly lit disc - the shell reads as a plain iron ball
whichever face happens to be toward the camera, which is what lets the renderer skip orienting the
model to the shell's direction of travel at all. The bottom half of the 16x16 sheet is left
transparent; nothing samples it.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent.parent / \
    "src/main/resources/assets/soulhome/textures/entity/soul_barrage_shot.png"

WIDTH, HEIGHT = 16, 16

BASE = (40, 38, 42)
RIM = (18, 17, 19)
HIGHLIGHT = (94, 90, 88)

# The unwrap tiles each 4x4 face across a 4-pixel-tall strip; painting every tile with the same
# lit-disc gradient is what makes the six faces indistinguishable from one another.
CENTER = (3.2, 3.2)
RADIUS = 4.6
HIGHLIGHT_CENTER = (1.1, 1.1)
HIGHLIGHT_RADIUS = 2.0


def shade(face_x: int, face_y: int) -> tuple[int, int, int, int]:
    dx = face_x - CENTER[0]
    dy = face_y - CENTER[1]
    shadow = max(0.0, min(1.0, math.hypot(dx, dy) / RADIUS))

    hx = face_x - HIGHLIGHT_CENTER[0]
    hy = face_y - HIGHLIGHT_CENTER[1]
    highlight = max(0.0, 1.0 - math.hypot(hx, hy) / HIGHLIGHT_RADIUS)

    channel = lambda i: round(
        BASE[i] + (RIM[i] - BASE[i]) * shadow + (HIGHLIGHT[i] - BASE[i]) * highlight * 0.5
    )
    return channel(0), channel(1), channel(2), 255


def main() -> None:
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    pixels = image.load()

    # only the top 8 rows are ever sampled by the model's cube unwrap (2*(4+4) wide, 4+4 tall)
    for y in range(8):
        for x in range(WIDTH):
            pixels[x, y] = shade(x % 4, y % 4)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
