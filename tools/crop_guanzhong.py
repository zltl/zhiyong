"""Crop 鐪?鑽?glyphs from the 鍏充腑鏈?rubbing of 鏅烘案鐪熻崏鍗冨瓧鏂?

A scan shows an open book: two leaves, each a black rubbing panel with
light strokes. Reading order is right leaf then left leaf, columns right
to left, 鐪?then 鑽? ten characters per column. The first leaf carries a
title column on its right; the last leaf is followed by colophons.

Stages 1-3 (columns, characters, minimal square) are shared with
`geom_crop.py`; only the panel finder and the stroke mask differ.

Usage:
    python tools/crop_guanzhong.py --vis 3,6      # overlays only
    python tools/crop_guanzhong.py                # crop all, copy to assets
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

from crop_ogawa import PageSpec, save_webp
from geom_crop import ROWS, PageLayout, column_runs, draw_layout, layout_block

ROOT = Path(__file__).resolve().parents[1]
PAGES_DIR = ROOT / "data" / "guanzhong" / "pages"
GLYPH_DIR = ROOT / "data" / "guanzhong" / "glyphs"
DEBUG_DIR = ROOT / "data" / "guanzhong" / "debug"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "glyphs" / "guanzhong"
SIZE = 512
FIRST_SCAN = 3
LAST_SCAN = 34
TOTAL = 1000
# share of a tall component's rows that are one thin run, above which it is a crack
CRACK_THIN_FRACTION = 0.5
# scan 4 repeats scan 3
SKIP_SCANS = {4}
# (scan, leaf side 0=right) -> (pairs, skip_right): the first leaf carries
# two title columns on its right before the one 澶╁湴鐜勯粍 pair
LEAF_OVERRIDES: dict[tuple[int, int], tuple[int, bool]] = {(3, 0): (1, True)}


# --------------------------------------------------------------- panels


def find_panels(gray: np.ndarray) -> list[tuple[int, int, int, int]]:
    """Bounding boxes of the black rubbing panels, right to left."""
    scale = 4
    small = gray[::scale, ::scale]
    dark = small < 110
    dark = ndimage.binary_opening(dark, iterations=2)
    # a white crack can split a panel in two; close narrow vertical gaps,
    # but stay well below the gutter between the two leaves
    dark = ndimage.binary_closing(dark, structure=np.ones((1, 21), dtype=bool))
    labels, count = ndimage.label(dark)
    if count == 0:
        return []
    areas = ndimage.sum_labels(dark, labels, np.arange(1, count + 1))
    slices = ndimage.find_objects(labels)
    floor = small.size * 0.04
    panels = []
    for i, sl in enumerate(slices):
        if sl is None or areas[i] < floor:
            continue
        ys, xs = sl
        h = ys.stop - ys.start
        w = xs.stop - xs.start
        if h < w:  # a rubbing leaf is a tall rectangle
            continue
        panels.append((xs.start * scale, ys.start * scale, xs.stop * scale, ys.stop * scale))
    panels.sort(key=lambda b: -b[0])
    return panels


def stroke_mask(gray: np.ndarray) -> np.ndarray:
    """Light strokes on the black rubbing ground."""
    ground = float(np.percentile(gray, 40))
    level = max(120.0, ground + 75.0)
    return gray > level


def erase_cracks(mask: np.ndarray, pitch: float) -> np.ndarray:
    """Drop near-vertical light streaks (stone cracks) longer than a character.

    No stroke runs straight down for more than about one character; a crack
    runs through several. Measure vertical light runs on a slightly widened
    mask so a tilted crack still counts as one run.
    """
    wide = ndimage.binary_dilation(mask, structure=np.ones((1, 7), dtype=bool))
    limit = int(pitch * 1.6)
    h, w = mask.shape
    kill = np.zeros_like(mask)
    ys = np.arange(h)
    # a crack wanders sideways; shear the mask through a few slopes so a
    # tilted crack becomes a straight vertical run
    for slope in np.linspace(-0.16, 0.16, 9):
        shift = np.round(slope * ys).astype(int)
        sheared = np.zeros_like(wide)
        for y in range(h):
            sheared[y] = np.roll(wide[y], shift[y])
        runs = _long_vertical_runs(sheared, limit)
        if not runs.any():
            continue
        for y in range(h):
            kill[y] |= np.roll(runs[y], -shift[y])
    # a hairline crack curves too much for the shear sweep. It is a lone
    # component taller than a character whose every row is one thin run;
    # a character has rows with several runs or a wide horizontal stroke
    labels, count = ndimage.label(mask, structure=np.ones((3, 3), dtype=int))
    thin = max(8, int(pitch * 0.09))
    for i, sl in enumerate(ndimage.find_objects(labels)):
        if sl is None:
            continue
        ys, xs = sl
        if ys.stop - ys.start < pitch * 1.2:
            continue
        comp = labels[sl] == (i + 1)
        rows = comp[comp.any(axis=1)].astype(np.int8)
        starts = (np.diff(rows, axis=1, prepend=0) == 1).sum(axis=1)
        widths = rows.sum(axis=1)
        single_thin = float(np.mean((starts == 1) & (widths <= thin)))
        if single_thin > CRACK_THIN_FRACTION:
            kill[sl] |= comp
    if not kill.any():
        return mask
    kill = ndimage.binary_dilation(kill, structure=np.ones((3, 9), dtype=bool))
    return mask & ~kill


def _long_vertical_runs(flags: np.ndarray, limit: int) -> np.ndarray:
    """Pixels that belong to a vertical True run longer than `limit`."""
    h, w = flags.shape
    f = flags.astype(np.int8)
    padded = np.vstack([np.zeros((1, w), np.int8), f, np.zeros((1, w), np.int8)])
    edges = np.diff(padded, axis=0)
    out = np.zeros_like(flags)
    for x in range(w):
        starts = np.where(edges[:, x] == 1)[0]
        if starts.size == 0:
            continue
        ends = np.where(edges[:, x] == -1)[0]
        long = (ends - starts) > limit
        for a, b in zip(starts[long], ends[long]):
            out[a:b, x] = True
    return out


def square_pad_dark(crop: Image.Image, size: int = SIZE) -> Image.Image:
    """Centre the crop on a square of the rubbing's own black."""
    crop = crop.convert("RGB")
    arr = np.asarray(crop)
    gray = arr.mean(axis=2)
    dark = arr[gray < np.percentile(gray, 45)]
    fill = tuple(int(x) for x in np.median(dark, axis=0)) if dark.size else (24, 24, 24)
    canvas = Image.new("RGB", (size, size), fill)
    scale = min(size / crop.width, size / crop.height) * 0.90
    new_w = max(1, int(crop.width * scale))
    new_h = max(1, int(crop.height * scale))
    resized = crop.resize((new_w, new_h), Image.Resampling.LANCZOS)
    canvas.paste(resized, ((size - new_w) // 2, (size - new_h) // 2))
    return canvas


# ---------------------------------------------------------------- pages


def scan_path(scan: int) -> Path:
    return PAGES_DIR / f"p{scan:02d}.jpg"


def leaf_layouts(scan: int, start: int) -> tuple[list[PageLayout], int]:
    """Lay out both leaves of one scan; returns layouts and the next index."""
    im = Image.open(scan_path(scan)).convert("RGB")
    rgb = np.asarray(im)
    gray = np.asarray(im.convert("L"))
    layouts: list[PageLayout] = []
    for side, (x0, y0, x1, y1) in enumerate(find_panels(gray)):
        if start >= TOTAL:
            break
        # step inside the panel frame: its edge is light like a stroke
        inset_x = int((x1 - x0) * 0.03)
        inset_y = int((y1 - y0) * 0.02)
        px0, py0, px1, py1 = x0 + inset_x, y0 + inset_y, x1 - inset_x, y1 - inset_y
        panel = rgb[py0:py1, px0:px1]
        pgray = gray[py0:py1, px0:px1]
        mask = stroke_mask(pgray)
        mask = erase_cracks(mask, (py1 - py0) / ROWS)
        runs = column_runs(mask)
        if not runs:
            continue
        override = LEAF_OVERRIDES.get((scan, side))
        if override is not None:
            pairs, skip_right = override
        else:
            # a title or colophon column makes the count odd; it is narrower
            # and sits at the right of the first leaf, at the left of the last
            skip_right = False
            if len(runs) % 2 == 1:
                widths = [b - a for a, b in runs]
                narrow = int(np.argmin(widths))
                skip_right = narrow >= len(runs) // 2
            pairs = len(runs) // 2
        pairs = min(pairs, (TOTAL - start + 9) // 10)
        if pairs == 0:
            continue
        spec = PageSpec(page=scan * 10 + side, start=start, pairs=pairs, skip_right=int(skip_right))
        layouts.append(layout_block(spec, panel, mask, (px0, py0)))
        start += pairs * ROWS
    return layouts, start


def all_layouts(only: set[int] | None = None) -> list[PageLayout]:
    out: list[PageLayout] = []
    start = 0
    for scan in range(FIRST_SCAN, LAST_SCAN + 1):
        if scan in SKIP_SCANS:
            continue
        if not scan_path(scan).exists():
            raise SystemExit(f"missing scan {scan_path(scan)}")
        layouts, next_start = leaf_layouts(scan, start)
        got = next_start - start
        print(f"p{scan:02d} leaves={len(layouts)} chars {start:03d}-{next_start - 1:03d} ({got})")
        if only is None or scan in only:
            out.extend(layouts)
        start = next_start
        if start >= TOTAL:
            break
    if start != TOTAL:
        print(f"WARNING: mapped {start} characters, expected {TOTAL}")
    return out


def crop_layout(layout: PageLayout) -> list[tuple[int, str, Image.Image]]:
    out = []
    h, w = layout.rgb.shape[:2]
    for cell in layout.cells:
        x0, y0, x1, y1 = cell.box
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(w, max(x0 + 4, x1)), min(h, max(y0 + 4, y1))
        crop = Image.fromarray(layout.rgb[y0:y1, x0:x1])
        out.append((cell.index, cell.kind, square_pad_dark(crop)))
    return out


def copy_assets() -> None:
    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for src in sorted(GLYPH_DIR.glob("*.webp")):
        shutil.copy2(src, ASSET_DIR / src.name)
        count += 1
    print(f"copied {count} glyphs to {ASSET_DIR}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--vis", default="", help="comma scans: overlays only")
    parser.add_argument("--no-copy", action="store_true")
    args = parser.parse_args()
    DEBUG_DIR.mkdir(parents=True, exist_ok=True)

    if args.vis:
        wanted = {int(p) for p in args.vis.split(",") if p.strip()}
        for layout in all_layouts(wanted):
            draw_layout(layout, DEBUG_DIR / f"geom_p{layout.spec.page:03d}.jpg")
            notes = [(c.index, c.kind, c.note) for c in layout.cells if c.note]
            print(f"  leaf {layout.spec.page} cols={layout.cols} notes={notes}")
        return

    GLYPH_DIR.mkdir(parents=True, exist_ok=True)
    flagged: list[tuple[int, int, str, str]] = []
    written = 0
    for layout in all_layouts():
        draw_layout(layout, DEBUG_DIR / f"geom_p{layout.spec.page:03d}.jpg", max_h=1600)
        for cell in layout.cells:
            if cell.note:
                flagged.append((layout.spec.page, cell.index, cell.kind, cell.note))
        for index, kind, image in crop_layout(layout):
            save_webp(image, GLYPH_DIR / f"{index:03d}_{kind}.webp")
            written += 1
    print(f"wrote {written} crops")
    if flagged:
        print("flagged:")
        for page, index, kind, note in flagged:
            print(f"  leaf {page} {index:03d}_{kind} {note}")
    if not args.no_copy:
        copy_assets()


if __name__ == "__main__":
    main()
