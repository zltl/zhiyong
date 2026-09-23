"""Fill 小川本 glyphs that are lost or too damaged to study from 关中本.

Which glyphs: any 小川本 cell with no stroke ink, plus a reviewed list of
cells whose strokes are broken by torn paper or stains. Mottled paper is
common on the first leaves, and a glyph with mottled paper but whole
strokes stays as the manuscript; no threshold separated the two, so the
list is reviewed by eye on `fallback_ranked.jpg`, which ranks every glyph
by how much of its ink box is torn paper or stain.

Export: the rubbing is light strokes on black. For the ink edition it is
inverted and toned with the paper and ink colours of the same character's
小川本 glyph, so it sits quietly among the manuscript glyphs.

Writes tools/ink_fallback.json (read by build_corpus.py) and the toned
glyphs to app/src/main/assets/glyphs/guanzhong/.
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

import crop_guanzhong
import geom_crop
from crop_ogawa import page_specs, save_webp

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


def toned_rubbing(index: int, kind: str) -> Image.Image:
    """The rubbing glyph as dark ink on the 小川本 paper of the same character."""
    img = Image.open(GZ_GLYPHS / f"{index:03d}_{kind}.webp").convert("L").filter(ImageFilter.MedianFilter(5))
    rub = np.asarray(img).astype(np.float64)
    ground = np.percentile(rub, 60)
    stroke = np.percentile(rub, 98)
    t = np.clip((rub - ground) / max(1.0, stroke - ground), 0.0, 1.0)
    # stone grain leaves specks around the strokes; keep only stroke-sized ink
    core = t > 0.35
    labels, count = ndimage.label(core)
    if count:
        areas = ndimage.sum_labels(core, labels, np.arange(1, count + 1))
        keep = np.isin(labels, 1 + np.where(areas >= 0.002 * core.size)[0])
        near = ndimage.binary_dilation(keep, iterations=4)
        t = t * ndimage.gaussian_filter(near.astype(np.float64), 1.5)
    # stone grain frays the stroke edge; soften it, then firm the edge back up
    t = ndimage.gaussian_filter(t, 2.0)
    t = np.clip((t - 0.12) / 0.6, 0.0, 1.0)
    t = t * t * (3.0 - 2.0 * t)
    ref = np.asarray(Image.open(OGAWA_GLYPHS / f"{index:03d}_{kind}.webp").convert("RGB")).reshape(-1, 3)
    grey = ref.mean(axis=1)
    paper = np.median(ref[grey > np.percentile(grey, 60)], axis=0)
    ink = np.array([38.0, 33.0, 28.0])
    out = paper[None, None, :] * (1.0 - t[..., None]) + ink[None, None, :] * t[..., None]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def export(picks: list[dict]) -> None:
    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for p in picks:
        save_webp(toned_rubbing(p["index"], p["kind"]), ASSET_DIR / f"{p['index']:03d}_{p['kind']}.webp")
    print(f"exported {len(picks)} toned glyphs to {ASSET_DIR}")
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
