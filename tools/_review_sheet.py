"""Lay out every Ogawa crop and score how far the ink sits from center."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_corpus import LINES  # noqa: E402

GLYPH_DIR = ROOT / "data" / "ogawa" / "glyphs"
OUT = ROOT / "data" / "ogawa" / "review"
TEXT = "".join(LINES)
CELL = 96
LABEL_H = 22


def ink_stats(path: Path) -> dict:
    gray = np.asarray(Image.open(path).convert("L"))
    h, w = gray.shape
    paper = float(np.percentile(gray, 72))
    ink = gray < (paper - 22)
    count = int(ink.sum())
    if count < 80:
        return {
            "empty": True,
            "dx": 0.0,
            "dy": 0.0,
            "shift": 0.0,
            "ml": 0.0,
            "mr": 0.0,
            "mt": 0.0,
            "mb": 0.0,
            "ink": count,
        }
    ys, xs = np.where(ink)
    cx = float(xs.mean() / w - 0.5)
    cy = float(ys.mean() / h - 0.5)
    ml = float(xs.min() / w)
    mr = float(1.0 - (xs.max() + 1) / w)
    mt = float(ys.min() / h)
    mb = float(1.0 - (ys.max() + 1) / h)
    # Positive dx: more margin on the left, ink sits to the right.
    return {
        "empty": False,
        "dx": ml - mr,
        "dy": mt - mb,
        "shift": abs(cx) + abs(cy),
        "cx": cx,
        "cy": cy,
        "ml": ml,
        "mr": mr,
        "mt": mt,
        "mb": mb,
        "ink": count,
    }


def load_font(size: int) -> ImageFont.ImageFont:
    for name in ("msyh.ttc", "simsun.ttc", "C:/Windows/Fonts/msyh.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def thumb(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGB")
    return im.resize((CELL - 8, CELL - 8), Image.Resampling.LANCZOS)


def draw_pair(canvas, draw, font, index, x, y, bad: set[str]) -> None:
    ch = TEXT[index] if index < len(TEXT) else "?"
    draw.rectangle([x, y, x + CELL * 2, y + LABEL_H + CELL], fill=(247, 241, 230))
    draw.text((x + 4, y + 2), f"{index:03d} {ch}", fill=(70, 36, 24), font=font)
    for slot, kind in enumerate(("zhen", "cao")):
        px = x + slot * CELL
        py = y + LABEL_H
        path = GLYPH_DIR / f"{index:03d}_{kind}.webp"
        canvas.paste(thumb(path), (px + 4, py + 4))
        if kind in bad:
            draw.rectangle([px + 2, py + 2, px + CELL - 2, py + CELL - 2], outline=(196, 40, 36), width=3)


def write_sheets(flagged: dict[int, set[str]]) -> None:
    font = load_font(16)
    cols = 10
    per = 100
    for page in range(10):
        start = page * per
        rows = 10
        w = cols * CELL * 2 + 8
        h = rows * (CELL + LABEL_H) + 8
        canvas = Image.new("RGB", (w, h), (232, 224, 210))
        draw = ImageDraw.Draw(canvas)
        for n in range(per):
            index = start + n
            col = n % cols
            row = n // cols
            draw_pair(
                canvas,
                draw,
                font,
                index,
                4 + col * CELL * 2,
                4 + row * (CELL + LABEL_H),
                flagged.get(index, set()),
            )
        dest = OUT / f"all_{page + 1:02d}.jpg"
        canvas.save(dest, "JPEG", quality=86)
        print("sheet", dest.name, dest.stat().st_size)


def write_worst(rows: list[dict]) -> None:
    font = load_font(15)
    show = rows[:48]
    cols = 8
    cell_w = CELL * 2
    cell_h = CELL + LABEL_H + 18
    rows_n = (len(show) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * cell_w + 8, rows_n * cell_h + 8), (232, 224, 210))
    draw = ImageDraw.Draw(canvas)
    for n, item in enumerate(show):
        col = n % cols
        row = n // cols
        x = 4 + col * cell_w
        y = 4 + row * cell_h
        kinds = set()
        if item["zhen_bad"]:
            kinds.add("zhen")
        if item["cao_bad"]:
            kinds.add("cao")
        draw_pair(canvas, draw, font, item["index"], x, y, kinds)
        note = item["note"]
        draw.text((x + 4, y + LABEL_H + CELL - 2), note, fill=(140, 30, 24), font=font)
    dest = OUT / "worst.jpg"
    canvas.save(dest, "JPEG", quality=88)
    print("worst", dest, dest.stat().st_size)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    records = []
    flagged: dict[int, set[str]] = {}
    for index in range(1000):
        z = ink_stats(GLYPH_DIR / f"{index:03d}_zhen.webp")
        c = ink_stats(GLYPH_DIR / f"{index:03d}_cao.webp")
        z_bad = (not z["empty"]) and (abs(z["dx"]) >= 0.10 or abs(z["dy"]) >= 0.12)
        c_bad = (not c["empty"]) and (abs(c["dx"]) >= 0.14 or abs(c["dy"]) >= 0.16)
        # Grass is naturally asymmetric; only flag a clear shove.
        if z_bad or c_bad:
            flagged[index] = set()
            if z_bad:
                flagged[index].add("zhen")
            if c_bad:
                flagged[index].add("cao")
        score = 0.0
        note_bits = []
        if z_bad:
            score += abs(z["dx"]) + abs(z["dy"])
            note_bits.append(f"真{z['dx']:+.2f},{z['dy']:+.2f}")
        if c_bad:
            score += (abs(c["dx"]) + abs(c["dy"])) * 0.7
            note_bits.append(f"草{c['dx']:+.2f},{c['dy']:+.2f}")
        records.append(
            {
                "index": index,
                "ch": TEXT[index],
                "zhen": z,
                "cao": c,
                "zhen_bad": z_bad,
                "cao_bad": c_bad,
                "score": score,
                "note": " ".join(note_bits),
            }
        )
    ranked = sorted((r for r in records if r["score"] > 0), key=lambda r: -r["score"])
    (OUT / "offset.json").write_text(
        json.dumps(
            [
                {
                    "index": r["index"],
                    "ch": r["ch"],
                    "score": round(r["score"], 3),
                    "note": r["note"],
                    "zhen_bad": r["zhen_bad"],
                    "cao_bad": r["cao_bad"],
                }
                for r in ranked
            ],
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print("flagged", len(ranked))
    write_sheets(flagged)
    write_worst(ranked)


if __name__ == "__main__":
    main()
