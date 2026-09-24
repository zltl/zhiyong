"""Re-frame single glyph crops: drop detached stains, then fit the box to the ink."""

from __future__ import annotations

import io
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
from crop_ogawa import ASSET_DIR, GLYPH_DIR, ROOT, save_webp, square_pad  # noqa: E402

REVIEW = ROOT / "data" / "ogawa" / "review"
GAP = 40
PAD = 16


def head_image(name: str) -> Image.Image:
    rel = f"app/src/main/assets/glyphs/ogawa/{name}"
    data = subprocess.run(["git", "show", f"HEAD:{rel}"], cwd=ROOT, capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(data)).convert("RGB")


def reframe(im: Image.Image) -> Image.Image:
    gray = np.asarray(im.convert("L"))
    h, w = gray.shape
    ink = gray < (float(np.percentile(gray, 72)) - 28)
    labeled, count = ndimage.label(ink)
    sizes = np.bincount(labeled.ravel())
    boxes = ndimage.find_objects(labeled)
    main = int(np.argmax(sizes[1:])) + 1
    kept = {main}
    y0, y1 = boxes[main - 1][0].start, boxes[main - 1][0].stop
    x0, x1 = boxes[main - 1][1].start, boxes[main - 1][1].stop
    # Paper mottling is a chain of small flecks; letting them join walks the box out to a stain.
    min_part = max(40, int(sizes[main] * 0.02))
    grown = True
    while grown:
        grown = False
        for i in range(1, count + 1):
            if i in kept or sizes[i] < min_part:
                continue
            sy, sx = boxes[i - 1]
            tall = sy.stop - sy.start
            if tall > h * 0.25 and tall > (sx.stop - sx.start) * 5:
                continue
            if sx.stop < x0 - GAP or sx.start > x1 + GAP or sy.stop < y0 - GAP or sy.start > y1 + GAP:
                continue
            kept.add(i)
            y0, y1 = min(y0, sy.start), max(y1, sy.stop)
            x0, x1 = min(x0, sx.start), max(x1, sx.stop)
            grown = True
    box = (max(0, x0 - PAD), max(0, y0 - PAD), min(w, x1 + PAD), min(h, y1 + PAD))
    return square_pad(im.crop(box))


def main() -> None:
    names = sys.argv[1:]
    if not names:
        raise SystemExit("usage: _fix_glyph.py 180_zhen 419_zhen ...")
    size = 300
    font = ImageFont.truetype("msyh.ttc", 22)
    sheet = Image.new("RGB", (size * 2 + 30, len(names) * (size + 44) + 10), (240, 233, 220))
    draw = ImageDraw.Draw(sheet)
    for row, stem in enumerate(names):
        name = f"{stem}.webp"
        before = head_image(name)
        after = reframe(before)
        save_webp(after, GLYPH_DIR / name)
        save_webp(after, ASSET_DIR / name)
        y = 10 + row * (size + 44)
        draw.text((10, y), f"{stem} 改前", fill=(90, 40, 30), font=font)
        draw.text((size + 20, y), f"{stem} 改后", fill=(90, 40, 30), font=font)
        sheet.paste(before.resize((size, size), Image.Resampling.LANCZOS), (10, y + 34))
        sheet.paste(after.resize((size, size), Image.Resampling.LANCZOS), (size + 20, y + 34))
        print("fixed", name)
    dest = REVIEW / "changed_fix.jpg"
    sheet.save(dest, "JPEG", quality=90)
    print(dest)


if __name__ == "__main__":
    main()
