"""Fill 小川本 glyphs that are lost or too damaged to study from 关中本.

Which glyphs: any 小川本 cell with no stroke ink, plus a reviewed list of
cells whose strokes are broken by torn paper or stains. Mottled paper is
common on the first leaves, and a glyph with mottled paper but whole
strokes stays as the manuscript; no threshold separated the two, so the
list is reviewed by eye on `fallback_ranked.jpg`, which ranks every glyph
by how much of its ink box is torn paper or stain.

Export: copy the 关中本 crops as-is (light strokes on black). Do not invert
or recolour them.

Writes tools/ink_fallback.json (read by build_corpus.py). The full 关中本
set lives in app/src/main/assets/glyphs/guanzhong/; this only refreshes
the fallback crops and does not remove the others.
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import crop_guanzhong
import geom_crop
from crop_ogawa import page_specs

ROOT = Path(__file__).resolve().parents[1]
OUT_JSON = ROOT / "tools" / "ink_fallback.json"
DEBUG = ROOT / "data" / "ogawa" / "debug"
OGAWA_GLYPHS = ROOT / "data" / "ogawa" / "glyphs"
GZ_GLYPHS = ROOT / "data" / "guanzhong" / "glyphs"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "glyphs" / "guanzhong"

# strokes broken by torn paper or stain, reviewed on fallback_ranked.jpg
REVIEWED: dict[tuple[int, str], str] = {
    (4, "cao"): "stain",
    (7, "zhen"): "torn",
    (9, "zhen"): "torn",
    (9, "cao"): "torn",
    (10, "cao"): "torn",
    (19, "zhen"): "torn",
    (19, "cao"): "torn",
}


def ogawa_stats() -> dict[tuple[int, str], dict]:
    out: dict[tuple[int, str], dict] = {}
    for spec in page_specs():
        layout = geom_crop.layout_page(spec)
        rgb = layout.rgb.astype(np.int16)
        gray = rgb.mean(axis=2)
        rb = rgb[..., 0] - rgb[..., 2]
        paper_grey = float(np.median(gray[(gray > 120) & (rb > 24)]))
        h, w = gray.shape
        for cell in layout.cells:
            # the character's own ink box: the square's rim may touch the mount
            x0, y0, x1, y1 = cell.ink or cell.box
            x0, y0, x1, y1 = max(0, x0), max(0, y0), min(w, x1), min(h, y1)
            g = gray[y0:y1, x0:x1]
            r = rb[y0:y1, x0:x1]
            ink = (g < 140) & (r < 24)
            torn = g > paper_grey + 40
            stain = (g < paper_grey - 45) & (r >= 24)
            out[(cell.index, cell.kind)] = {
                "lost": cell.ink is None or int(ink.sum()) < 40,
                "damage": float((torn | stain).mean()),
            }
    return out


def rubbing_usable() -> set[tuple[int, str]]:
    return {(c.index, c.kind) for layout in crop_guanzhong.all_layouts() for c in layout.cells if c.ink is not None}


def pick() -> list[dict]:
    print("measuring 小川本 ...")
    ogawa = ogawa_stats()
    print("checking 关中本 ...")
    usable = rubbing_usable()
    picks: list[dict] = []
    for (index, kind), st in sorted(ogawa.items()):
        reason = "lost" if st["lost"] else REVIEWED.get((index, kind), "")
        if not reason:
            continue
        if (index, kind) not in usable:
            print(f"  {index:03d}_{kind}: rubbing unusable, kept as manuscript")
            continue
        picks.append({"index": index, "kind": kind, "reason": reason})
    ranked = sorted(
        ({"index": i, "kind": k, "label": f"d{s['damage']:.2f}"} for (i, k), s in ogawa.items() if not s["lost"]),
        key=lambda p: -float(p["label"][1:]),
    )
    write_sheet(ranked[:48], DEBUG / "fallback_ranked.jpg")
    return picks


def export(picks: list[dict]) -> None:
    """Refresh the fallback crops. The rest of the 关中本 set stays in place."""
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for p in picks:
        name = f"{p['index']:03d}_{p['kind']}.webp"
        src = GZ_GLYPHS / name
        if not src.exists():
            raise SystemExit(f"missing rubbing crop {src}")
        shutil.copy2(src, ASSET_DIR / name)
    print(f"refreshed {len(picks)} fallback glyphs in {ASSET_DIR}")
    write_sheet(
        [{"index": p["index"], "kind": p["kind"], "label": p["reason"]} for p in picks],
        DEBUG / "fallback_sheet.jpg",
        right=ASSET_DIR,
    )


def write_sheet(items: list[dict], dest: Path, right: Path = GZ_GLYPHS) -> None:
    cell = 150
    per_row = 4
    rows = max(1, (len(items) + per_row - 1) // per_row)
    sheet = Image.new("RGB", (per_row * (cell * 2 + 10), rows * (cell + 24)), (245, 240, 230))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("msyh.ttc", 15)
    except OSError:
        font = ImageFont.load_default()
    for i, item in enumerate(items):
        r, c = divmod(i, per_row)
        x, y = c * (cell * 2 + 10), r * (cell + 24)
        name = f"{item['index']:03d}_{item['kind']}.webp"
        for j, folder in enumerate((OGAWA_GLYPHS, right)):
            path = folder / name
            if path.exists():
                sheet.paste(Image.open(path).convert("RGB").resize((cell, cell)), (x + j * cell, y + 22))
        label = f"{item['index']:03d}{'真' if item['kind'] == 'zhen' else '草'} {item['label']}"
        draw.text((x + 2, y + 2), label, fill=(90, 40, 30), font=font)
    dest.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(dest, quality=85)
    print(f"sheet {dest}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--export-only", action="store_true", help="reuse tools/ink_fallback.json")
    args = parser.parse_args()
    if args.export_only:
        picks = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    else:
        picks = pick()
        OUT_JSON.write_text(json.dumps(picks, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"picked {len(picks)} glyphs -> {OUT_JSON}")
    export(picks)


if __name__ == "__main__":
    main()
