"""Draw crop edge lines on pages and on finished glyph sheets."""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from crop_ogawa import (
    column_bounds,
    crop_page,
    ink_mask,
    page_path,
    page_specs,
    paper_bbox,
    row_bounds,
    tight_box,
    writing_bbox,
    SAMPLE_COUNT,
    GLYPH_DIR,
)

OUT = Path(r"l:\ltl\repos\zhiyong\data\ogawa\debug")
OUT.mkdir(parents=True, exist_ok=True)


def font(size: int = 18):
    try:
        return ImageFont.truetype("msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


def draw_page_edges(page: int) -> Path:
    for spec in page_specs():
        if spec.page != page:
            continue
        break
    else:
        raise SystemExit("no page %s" % page)

    im = Image.open(page_path(page)).convert("RGB")
    rgb = np.asarray(im)
    gray = np.asarray(im.convert("L"))
    pl, pt, pr, pb = paper_bbox(gray)
    paper_rgb = rgb[pt:pb, pl:pr]
    paper_gray = gray[pt:pb, pl:pr]
    wl, wt, wr, wb = writing_bbox(ink_mask(paper_gray))
    write_rgb = paper_rgb[wt:wb, wl:wr]
    write_mask = ink_mask(paper_gray[wt:wb, wl:wr])
    cols = column_bounds(write_mask, 4)
    ltr = list(cols)
    need = spec.pairs * 2
    if len(ltr) > need:
        ltr = ltr[:need] if spec.skip_right else ltr[-need:]
    rtl = list(reversed(ltr))

    overlay = Image.fromarray(write_rgb.copy())
    draw = ImageDraw.Draw(overlay)
    label = font(22)
    for x0, x1 in ltr:
        draw.line([(x0, 0), (x0, overlay.height)], fill=(200, 40, 40), width=2)
        draw.line([(x1, 0), (x1, overlay.height)], fill=(200, 40, 40), width=2)

    for pair in range(spec.pairs):
        zhen_col = rtl[pair * 2]
        cao_col = rtl[pair * 2 + 1]
        zx0, zx1 = zhen_col
        zpad = max(2, int((zx1 - zx0) * 0.12))
        strip = write_mask[:, max(0, zx0 + zpad) : min(write_mask.shape[1], zx1 - zpad)]
        rows = row_bounds(strip, 10)
        for kind, col in (("zhen", zhen_col), ("cao", cao_col)):
            color = (30, 160, 70) if kind == "zhen" else (40, 90, 210)
            for row, (y0, y1) in enumerate(rows):
                index = spec.start + pair * 10 + row
                box = tight_box(
                    write_mask,
                    (col[0], y0, col[1], y1),
                    col,
                    absorb_right=(kind == "cao"),
                    neighbor_stop=zhen_col[0] if kind == "cao" else None,
                )
                draw.rectangle([box[0], box[1], box[2], box[3]], outline=color, width=3)
                draw.text((box[0] + 4, box[1] + 4), "%03d" % index, fill=color, font=label)

    dest = OUT / ("edges_p%02d.jpg" % page)
    # Keep readable size for chat
    vis = overlay.copy()
    vis.thumbnail((1100, 2200), Image.Resampling.LANCZOS)
    vis.save(dest, quality=88)
    print("wrote", dest, "full", overlay.size)
    return dest


def ink_bbox(arr: np.ndarray) -> tuple[int, int, int, int] | None:
    paper = int(np.percentile(arr, 72))
    ink = arr < (paper - 28)
    ys, xs = np.where(ink)
    if ys.size < 40:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def glyph_sheet(indices: list[int], name: str) -> Path:
    cell = 168
    cols = 8
    rows = (len(indices) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * cell, rows * cell * 2 + 8), (236, 228, 214))
    draw = ImageDraw.Draw(canvas)
    f = font(15)
    for n, index in enumerate(indices):
        col = n % cols
        row = n // cols
        x = col * cell
        y = row * cell * 2
        for slot, kind in enumerate(("zhen", "cao")):
            path = GLYPH_DIR / ("%03d_%s.webp" % (index, kind))
            im = Image.open(path).convert("RGB")
            im = im.resize((cell - 10, cell - 24), Image.Resampling.LANCZOS)
            gray = np.asarray(im.convert("L"))
            canvas.paste(im, (x + 5, y + 20 + slot * (cell - 16)))
            # outer crop edge
            ox0, oy0 = x + 5, y + 20 + slot * (cell - 16)
            ox1, oy1 = ox0 + im.width - 1, oy0 + im.height - 1
            color = (30, 140, 70) if kind == "zhen" else (40, 90, 200)
            draw.rectangle([ox0, oy0, ox1, oy1], outline=color, width=2)
            bbox = ink_bbox(gray)
            if bbox:
                ix0, iy0, ix1, iy1 = bbox
                draw.rectangle(
                    [ox0 + ix0, oy0 + iy0, ox0 + ix1, oy0 + iy1],
                    outline=(220, 40, 40),
                    width=2,
                )
        draw.text((x + 6, y + 2), "%03d" % index, fill=(70, 40, 30), font=f)
    dest = OUT / name
    canvas.save(dest, quality=90)
    print("wrote", dest)
    return dest


def main() -> None:
    for page in (2, 3, 20, 27, 35, 52):
        draw_page_edges(page)
    glyph_sheet(list(range(0, SAMPLE_COUNT)), "edges_first40.jpg")
    glyph_sheet(list(range(350, 370)), "edges_mid.jpg")
    glyph_sheet(list(range(990, 1000)), "edges_end.jpg")


if __name__ == "__main__":
    main()
