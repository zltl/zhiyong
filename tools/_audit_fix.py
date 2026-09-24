"""Score every Ogawa crop; reframe the ones that sit off-center or keep a corner stain."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _fix_glyph import reframe  # noqa: E402
from build_corpus import LINES  # noqa: E402
from crop_ogawa import ASSET_DIR, GLYPH_DIR, ROOT, save_webp  # noqa: E402

REVIEW = ROOT / "data" / "ogawa" / "review"
TEXT = "".join(LINES)

# Zhen is usually denser and more square; Cao is allowed more asymmetry.
THRESH = {
    "zhen": {"dx": 0.08, "dy": 0.10, "corner": 0.018},
    "cao": {"dx": 0.12, "dy": 0.14, "corner": 0.022},
}


def ink_mask(gray: np.ndarray) -> np.ndarray:
    return gray < (float(np.percentile(gray, 72)) - 28)


def score(path: Path) -> dict:
    gray = np.asarray(Image.open(path).convert("L"))
    h, w = gray.shape
    ink = ink_mask(gray)
    count = int(ink.sum())
    if count < 120:
        return {
            "empty": True,
            "bad": False,
            "score": 0.0,
            "dx": 0.0,
            "dy": 0.0,
            "corner": 0.0,
            "ink": count,
            "reasons": ["empty"],
        }
    ys, xs = np.where(ink)
    ml = float(xs.min() / w)
    mr = float(1.0 - (xs.max() + 1) / w)
    mt = float(ys.min() / h)
    mb = float(1.0 - (ys.max() + 1) / h)
    dx = ml - mr
    dy = mt - mb

    # Detached flecks in the four corner blocks, relative to the main body.
    labeled, n = ndimage.label(ink)
    sizes = np.bincount(labeled.ravel())
    main = int(np.argmax(sizes[1:])) + 1
    boxes = ndimage.find_objects(labeled)
    my0, my1 = boxes[main - 1][0].start, boxes[main - 1][0].stop
    mx0, mx1 = boxes[main - 1][1].start, boxes[main - 1][1].stop
    corner_ink = 0
    for i in range(1, n + 1):
        if i == main or sizes[i] < 24 or sizes[i] > sizes[main] * 0.04:
            continue
        sy, sx = boxes[i - 1]
        left = sx.stop <= mx0 - 20
        right = sx.start >= mx1 + 20
        top = sy.stop <= my0 - 20
        bottom = sy.start >= my1 + 20
        if (left or right) and (top or bottom or sy.start > h * 0.55 or sy.stop < h * 0.45):
            if left or right:
                corner_ink += int(sizes[i])
    corner = corner_ink / max(1, count)

    return {
        "empty": False,
        "bad": False,
        "score": abs(dx) + abs(dy) + corner * 4,
        "dx": dx,
        "dy": dy,
        "corner": corner,
        "ink": count,
        "ml": ml,
        "mr": mr,
        "mt": mt,
        "mb": mb,
        "reasons": [],
    }


def is_bad(kind: str, s: dict) -> bool:
    if s["empty"]:
        return False
    t = THRESH[kind]
    reasons = []
    if abs(s["dx"]) >= t["dx"]:
        reasons.append(f"dx={s['dx']:+.2f}")
    if abs(s["dy"]) >= t["dy"]:
        reasons.append(f"dy={s['dy']:+.2f}")
    if s["corner"] >= t["corner"]:
        reasons.append(f"corner={s['corner']:.3f}")
    s["reasons"] = reasons
    s["bad"] = bool(reasons)
    return s["bad"]


def improved(before: dict, after: dict) -> bool:
    if after["empty"] and not before["empty"]:
        return False
    if after["ink"] < before["ink"] * 0.55:
        return False
    return after["score"] + 0.01 < before["score"]


def write_sheet(rows: list[dict], dest: Path) -> None:
    if not rows:
        return
    font = ImageFont.truetype("msyh.ttc", 18)
    cell = 220
    cols = 4
    pairs = [(r, r["kind"]) for r in rows]
    n = len(pairs)
    grid_cols = cols
    grid_rows = (n + cols - 1) // cols
    canvas = Image.new("RGB", (grid_cols * (cell * 2 + 12) + 12, grid_rows * (cell + 36) + 12), (236, 228, 214))
    draw = ImageDraw.Draw(canvas)
    for i, row in enumerate(rows):
        c = i % cols
        r = i // cols
        x = 12 + c * (cell * 2 + 12)
        y = 12 + r * (cell + 36)
        before = Image.open(row["before_path"]).convert("RGB").resize((cell, cell), Image.Resampling.LANCZOS)
        after = Image.open(row["after_path"]).convert("RGB").resize((cell, cell), Image.Resampling.LANCZOS)
        label = f"{row['index']:03d}{row['ch']}{row['kind']} {' '.join(row['reasons'])}"
        draw.text((x, y), label[:42], fill=(90, 40, 30), font=font)
        canvas.paste(before, (x, y + 24))
        canvas.paste(after, (x + cell + 8, y + 24))
    canvas.save(dest, "JPEG", quality=88)


def main() -> None:
    REVIEW.mkdir(parents=True, exist_ok=True)
    flagged: list[tuple[int, str, dict]] = []
    for index in range(1000):
        for kind in ("zhen", "cao"):
            path = GLYPH_DIR / f"{index:03d}_{kind}.webp"
            s = score(path)
            if is_bad(kind, s):
                flagged.append((index, kind, s))
    flagged.sort(key=lambda item: -item[2]["score"])
    print(f"flagged {len(flagged)}")

    fixed_rows: list[dict] = []
    skipped = 0
    for index, kind, before in flagged:
        name = f"{index:03d}_{kind}.webp"
        src = Image.open(GLYPH_DIR / name).convert("RGB")
        tmp_before = REVIEW / f"_before_{name}"
        src.save(tmp_before, "WEBP", quality=84, method=4)
        after_im = reframe(src)
        tmp_after = REVIEW / f"_after_{name}"
        after_im.save(tmp_after, "WEBP", quality=84, method=4)
        after = score(tmp_after)
        if not improved(before, after):
            skipped += 1
            tmp_before.unlink(missing_ok=True)
            tmp_after.unlink(missing_ok=True)
            continue
        save_webp(after_im, GLYPH_DIR / name)
        save_webp(after_im, ASSET_DIR / name)
        fixed_rows.append(
            {
                "index": index,
                "ch": TEXT[index],
                "kind": kind,
                "reasons": before["reasons"],
                "before": round(before["score"], 3),
                "after": round(after["score"], 3),
                "before_path": str(tmp_before),
                "after_path": str(tmp_after),
            }
        )
        print(f"fix {name} {before['score']:.3f}->{after['score']:.3f} {before['reasons']}")

    report = {
        "flagged": len(flagged),
        "fixed": len(fixed_rows),
        "skipped_no_gain": skipped,
        "items": [
            {
                "index": r["index"],
                "ch": r["ch"],
                "kind": r["kind"],
                "reasons": r["reasons"],
                "before": r["before"],
                "after": r["after"],
            }
            for r in fixed_rows
        ],
    }
    (REVIEW / "audit_fix.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_sheet(fixed_rows[:48], REVIEW / "audit_fixed.jpg")
    print(f"fixed {len(fixed_rows)} skipped {skipped}")
    print(REVIEW / "audit_fix.json")


if __name__ == "__main__":
    main()
