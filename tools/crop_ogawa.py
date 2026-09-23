"""Crop 真/草 glyphs from the Ogawa ink album of 智永真草千字文.

Source: 小川本墨迹 (京都小川家藏，日本国宝), full-album scans from 书法空间
http://www.9610.com/zhy/moji.zip (index page http://www.9610.com/zhy/13.htm),
unpacked into data/ogawa/pages/.
"""

from __future__ import annotations

import argparse
import shutil
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PAGES_DIR = ROOT / "data" / "ogawa" / "pages"
GLYPH_DIR = ROOT / "data" / "ogawa" / "glyphs"
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "glyphs" / "ogawa"
PREVIEW_PATH = ROOT / "data" / "ogawa" / "preview.jpg"
DEBUG_DIR = ROOT / "data" / "ogawa" / "debug"
SIZE = 512
SAMPLE_COUNT = 40
GLYPH_COUNT = 1000


@dataclass(frozen=True)
class PageSpec:
    page: int
    start: int
    pairs: int
    skip_right: int = 0
    skip_left: int = 0


def page_specs() -> list[PageSpec]:
    specs = [PageSpec(page=2, start=0, pairs=1, skip_right=1)]
    start = 10
    for page in range(3, 52):
        specs.append(PageSpec(page=page, start=start, pairs=2))
        start += 20
    specs.append(PageSpec(page=52, start=990, pairs=1, skip_left=1))
    return specs


def layout_ranges() -> list[tuple[int, int, int]]:
    return [(spec.page, spec.start, spec.start + spec.pairs * 10) for spec in page_specs()]


def self_test() -> None:
    ranges = layout_ranges()
    assert ranges[0] == (2, 0, 10), ranges[0]
    assert ranges[1] == (3, 10, 30), ranges[1]
    assert ranges[-2] == (51, 970, 990), ranges[-2]
    assert ranges[-1] == (52, 990, 1000), ranges[-1]
    covered = []
    for _, start, end in ranges:
        covered.extend(range(start, end))
    assert covered == list(range(1000))
    print("layout ok pages=%s chars=1000" % len(ranges))


def _smooth(values: np.ndarray, radius: int) -> np.ndarray:
    radius = max(1, radius)
    kernel = np.ones(radius * 2 + 1, dtype=np.float64)
    kernel /= kernel.size
    return np.convolve(values.astype(np.float64), kernel, mode="same")


def paper_bbox(gray: np.ndarray) -> tuple[int, int, int, int]:
    h, w = gray.shape
    scale = 4
    small = gray[::scale, ::scale]
    sh, sw = small.shape
    border = np.concatenate([small[:2, :].ravel(), small[-2:, :].ravel(), small[:, :2].ravel(), small[:, -2:].ravel()])
    mount = float(np.percentile(border, 72))
    paper = small < (mount - 14)
    ys, xs = np.where(paper)
    if ys.size < 30:
        return 0, 0, w, h
    top = max(0, int(ys.min()) * scale)
    bottom = min(h, (int(ys.max()) + 1) * scale)
    left = max(0, int(xs.min()) * scale)
    right = min(w, (int(xs.max()) + 1) * scale)
    inset_x = int((right - left) * 0.012)
    inset_y = int((bottom - top) * 0.004)
    return left + inset_x, top, right - inset_x, bottom - inset_y


def ink_mask(gray: np.ndarray) -> np.ndarray:
    """Dark stroke cores only. Paper mottling must stay out, or the row gaps vanish."""
    t = int(np.percentile(gray, 8))
    t = max(45, min(t, 105))
    return gray < t


def _content_span(coverage: np.ndarray, level: float, min_run: int) -> tuple[int, int] | None:
    """First and last ink that belongs to a character, skipping mount edges.

    A scan border is a dark line a few pixels thick. A character body is a
    much longer run. Short runs in the middle are stroke gaps and do not
    move the outer edges.
    """
    runs = [run for run in _ink_runs(coverage, level) if run[1] - run[0] >= min_run]
    if not runs:
        return None
    return runs[0][0], runs[-1][1]


def writing_bbox(mask: np.ndarray) -> tuple[int, int, int, int]:
    h, w = mask.shape
    row_cov = mask.mean(axis=1)
    col_cov = mask.mean(axis=0)
    rows = _content_span(row_cov, 0.018, 18)
    cols = _content_span(col_cov, 0.018, 14)
    if rows is None or cols is None:
        ys = np.where(row_cov > 0.025)[0]
        xs = np.where(col_cov > 0.025)[0]
        if ys.size < 20 or xs.size < 20:
            return 0, 0, w, h
        y0, y1 = int(ys[0]), int(ys[-1]) + 1
        x0, x1 = int(xs[0]), int(xs[-1]) + 1
    else:
        y0, y1 = rows
        x0, x1 = cols
    pad_x = max(2, int((x1 - x0) * 0.01))
    span_y = max(1, y1 - y0)
    pad_top = max(18, int(span_y * 0.02))
    pad_bot = max(12, int(span_y * 0.015))
    return (
        max(0, x0 - pad_x),
        max(0, y0 - pad_top),
        min(w, x1 + pad_x),
        min(h, y1 + pad_bot),
    )


def find_peaks(values: np.ndarray, count: int, min_distance: int, floor: float) -> list[int]:
    work = values.copy()
    edge = max(2, min_distance // 6)
    work[:edge] = 0
    work[-edge:] = 0
    peaks: list[int] = []
    for _ in range(count):
        idx = int(np.argmax(work))
        if work[idx] < floor:
            break
        peaks.append(idx)
        lo = max(0, idx - min_distance)
        hi = min(work.size, idx + min_distance + 1)
        work[lo:hi] = 0
    return sorted(peaks)


def _equal_rows(h: int, count: int) -> list[tuple[int, int]]:
    rows = []
    for i in range(count):
        top = int(round(i * h / count))
        bottom = int(round((i + 1) * h / count))
        rows.append((top, max(top + 1, bottom)))
    return rows


def column_bounds(mask: np.ndarray, want: int) -> list[tuple[int, int]]:
    h, w = mask.shape
    # a real column has ink along much of the height, which drops seals
    coverage = mask.mean(axis=0)
    proj = _smooth(coverage, max(4, w // 80))
    floor = max(float(proj.max()) * 0.18, float(np.percentile(proj, 55)))
    # One peak per column. Distance follows the row height, not the block
    # width: a short page (the last leaf) is only two columns wide, and a
    # width-based distance splits one cursive column into two.
    min_distance = max(80, int(h / 10 * 0.55))
    peaks = find_peaks(proj, want, min_distance=min_distance, floor=floor)
    if len(peaks) > want:
        peaks = sorted(peaks, key=lambda i: float(proj[i]), reverse=True)[:want]
        peaks = sorted(peaks)
    if len(peaks) < 2:
        return _equal_rows(w, max(want, 2))
    max_half = max(16, int(w / max(want, 1) * 0.62))
    floor_span = max(float(proj.max()) * 0.08, float(np.mean(proj)) * 0.35)

    def walk_out(start: int, step: int) -> int:
        x = start
        while 0 < x < w - 1 and abs(x - start) < max_half and proj[x] > floor_span:
            x += step
        return x

    spans: list[tuple[int, int]] = []
    for i, peak in enumerate(peaks):
        if i == 0:
            left = walk_out(peak, -1)
        else:
            lo = peaks[i - 1]
            left = lo + int(np.argmin(proj[lo : peak + 1]))
        if i == len(peaks) - 1:
            right = walk_out(peak, 1) + 1
        else:
            hi = peaks[i + 1]
            right = peak + int(np.argmin(proj[peak : hi + 1]))
        if right <= left + 4:
            right = min(w, left + max(8, w // (want * 2)))
        spans.append((max(0, left), min(w, right)))
    spans.sort()
    # A missing peak (damaged title column) leaves one span two columns wide.
    # Split that span at its own valley. Similar widths are left alone, so a
    # last page that really is two columns stays two columns.
    guard = 0
    while len(spans) < want and guard < want:
        guard += 1
        widths = [b - a for a, b in spans]
        typical = float(np.median(widths))
        widest = max(range(len(spans)), key=lambda i: widths[i])
        if widths[widest] < typical * 1.55 or widths[widest] < 40:
            break
        a, b = spans[widest]
        lo = a + int((b - a) * 0.28)
        hi = a + int((b - a) * 0.72)
        if hi <= lo + 4:
            break
        cut = lo + int(np.argmin(proj[lo:hi]))
        if cut <= a + 8 or cut >= b - 8:
            break
        spans = spans[:widest] + [(a, cut), (cut, b)] + spans[widest + 1 :]
    if len(spans) >= 3:
        widths = np.array([b - a for a, b in spans], dtype=np.float64)
        typical = float(np.median(widths))
        kept = [span for span in spans if (span[1] - span[0]) >= typical * 0.45]
        if len(kept) >= 2:
            spans = kept
    return spans


def row_bounds(mask: np.ndarray, count: int = 10) -> list[tuple[int, int]]:
    """Place row cuts in ink gaps with a dynamic program.

    Leading and trailing blank paper is not part of the ten rows, or the
    first cut lands on the first character.
    """
    h = mask.shape[0]
    if h < count * 8:
        return _equal_rows(h, count)
    proj_full = _smooth(mask.mean(axis=1), max(3, h // 90))
    level = max(0.02, float(proj_full.max()) * 0.12)
    # Drop a mount line. It is darker than the gap but only a few pixels tall.
    content = _content_span(proj_full, level, max(12, h // 80))
    if content is None:
        origin, limit = 0, h
    else:
        margin = max(14, int(h / count * 0.12))
        origin = max(0, content[0] - margin)
        limit = min(h, content[1] + max(8, margin // 2))
    band = mask[origin:limit]
    return [(top + origin, bottom + origin) for top, bottom in _split_rows(band, count)]


def _split_rows(mask: np.ndarray, count: int) -> list[tuple[int, int]]:
    h = mask.shape[0]
    if h < count * 8:
        return _equal_rows(h, count)
    proj = _smooth(mask.mean(axis=1), max(3, h // 90))
    mean_h = h / count
    min_h = max(8, int(mean_h * 0.68))
    max_h = max(min_h + 4, int(mean_h * 1.40))
    # A shift of one row costs `prior`. That still prefers a real gap half a
    # row away, and refuses to swallow the next character for a deeper hole.
    prior = 0.18
    inf = 1e9
    costs: list[np.ndarray] = []
    backs: list[np.ndarray] = []
    first = np.full(h, inf)
    for y in range(min_h, min(h - 1, max_h) + 1):
        expected = mean_h
        first[y] = float(proj[y]) + prior * abs(y - expected) / mean_h
    costs.append(first)
    from collections import deque

    for k in range(2, count):
        prev = costs[-1]
        cur = np.full(h, inf)
        back = np.full(h, -1, dtype=np.int32)
        y_lo = k * min_h
        y_hi = min(h - 1, k * max_h)
        if y_hi < y_lo:
            return _equal_rows(h, count)
        dq: deque[int] = deque()
        added = y_lo - min_h - 1
        expected = k * mean_h
        for y in range(y_lo, y_hi + 1):
            new_p = y - min_h
            while added < new_p:
                added += 1
                if 0 <= added < h and prev[added] < inf / 2:
                    while dq and prev[dq[-1]] >= prev[added]:
                        dq.pop()
                    dq.append(added)
            while dq and dq[0] < y - max_h:
                dq.popleft()
            if not dq:
                continue
            p = dq[0]
            cur[y] = prev[p] + float(proj[y]) + prior * abs(y - expected) / mean_h
            back[y] = p
        costs.append(cur)
        backs.append(back)
    last = costs[-1]
    y_lo = max((count - 1) * min_h, h - max_h)
    y_hi = min(h - min_h, (count - 1) * max_h, h - 1)
    if y_hi < y_lo or float(np.min(last[y_lo : y_hi + 1])) >= inf / 2:
        return _equal_rows(h, count)
    y = y_lo + int(np.argmin(last[y_lo : y_hi + 1]))
    splits = [h, int(y)]
    for back in reversed(backs):
        y = int(back[y])
        if y < 0:
            return _equal_rows(h, count)
        splits.append(y)
    splits.append(0)
    splits.reverse()
    if len(splits) != count + 1 or any(splits[i] <= splits[i - 1] for i in range(1, len(splits))):
        return _equal_rows(h, count)
    # Sit each cut in the middle of its own valley. Do not hunt into the next gap.
    radius = max(4, int(mean_h * 0.16))
    for i in range(1, count):
        lo = max(splits[i - 1] + min_h, splits[i] - radius, 0)
        hi = min(splits[i + 1] - min_h, splits[i] + radius, h - 1)
        if hi <= lo:
            continue
        local = lo + int(np.argmin(proj[lo : hi + 1]))
        level = float(proj[local]) + 0.03
        a = local
        while a > lo and proj[a - 1] <= level:
            a -= 1
        b = local
        while b < hi and proj[b + 1] <= level:
            b += 1
        splits[i] = (a + b) // 2
    overlap = 0
    rows = []
    for i in range(count):
        top = int(splits[i]) - (0 if i == 0 else overlap)
        bottom = int(splits[i + 1]) + (0 if i == count - 1 else overlap)
        rows.append((max(0, top), min(h, bottom)))
    return rows


def _ink_runs(proj: np.ndarray, level: float) -> list[tuple[int, int]]:
    runs: list[tuple[int, int]] = []
    start = None
    for i, value in enumerate(proj):
        on = value > level
        if on and start is None:
            start = i
        elif not on and start is not None:
            runs.append((start, i))
            start = None
    if start is not None:
        runs.append((start, len(proj)))
    return runs


def _neighbor_body_left(
    mask: np.ndarray,
    top: int,
    bottom: int,
    neighbor_l: int,
    neighbor_r: int,
) -> int | None:
    """Left edge of the regular character's body in this row.

    A short cursive flick in the gap is narrower than a real radical, so it
    is ignored here and may still be absorbed by the cursive box.
    """
    if neighbor_r <= neighbor_l + 8:
        return None
    band = mask[top:bottom, neighbor_l:neighbor_r]
    if band.size == 0 or int(band.sum()) < 8:
        return None
    width = neighbor_r - neighbor_l
    proj = _smooth(band.mean(axis=0), max(2, width // 40))
    if float(proj.max()) <= 0:
        return None
    runs = _ink_runs(proj, max(0.018, float(proj.max()) * 0.08))
    min_w = max(22, int(width * 0.14))
    for a, b in runs:
        if b - a >= min_w:
            return neighbor_l + a
    return None


def tight_box(
    mask: np.ndarray,
    box: tuple[int, int, int, int],
    col: tuple[int, int],
    absorb_right: bool = False,
    neighbor_stop: int | None = None,
) -> tuple[int, int, int, int]:
    """Keep the row slot on Y. Tighten X to this column's ink.

    Cursive sits to the left of regular script and often throws a stroke
    across the gap. Those detached strokes belong to the cursive column.
    When `neighbor_stop` is the left edge of the paired regular column,
    the cursive box may enter the gap for a short flick but must stop
    before the regular character's body.
    """
    _slot_l, slot_t, _slot_r, slot_b = box
    col_l, col_r = col
    h, w = mask.shape
    top = max(0, slot_t)
    bottom = min(h, max(top + 4, slot_b))
    width = max(8, col_r - col_l)
    hard = None
    if neighbor_stop is not None:
        body = _neighbor_body_left(
            mask, top, bottom, neighbor_stop, min(w, neighbor_stop + width + 8)
        )
        seam = neighbor_stop - max(4, int(width * 0.03))
        hard = (body - max(6, int(width * 0.04))) if body is not None else seam
    extra = int(width * 0.36)
    lo = max(0, col_l - extra)
    hi = min(w, col_r + extra)
    if hard is not None:
        # Reach the inter-column flick (盈) without crossing the regular body.
        hi = min(w, max(hi, hard + 4))
    band = mask[top:bottom, lo:hi]
    if band.size == 0 or int(band.sum()) < 8:
        return col_l, top, col_r, bottom
    proj = _smooth(band.mean(axis=0), max(2, width // 40))
    level = max(0.018, float(proj.max()) * 0.08)
    runs = _ink_runs(proj, level)
    if not runs:
        return col_l, top, col_r, bottom

    def midpoint(run: tuple[int, int]) -> float:
        return lo + (run[0] + run[1]) / 2

    # Parts of one character (比, 列) stay together when they sit in this column.
    inside = [run for run in runs if col_l - 6 <= midpoint(run) <= col_r + 6]
    if not inside:
        inside = runs
    main = max(inside, key=lambda run: run[1] - run[0])
    a, b = main
    main_w = max(1, main[1] - main[0])
    changed = True
    while changed:
        changed = False
        for run in inside:
            sibling = (run[1] - run[0]) > main_w * 0.4
            # A real radical can sit a little apart. A stray dash from the
            # other column is short, so it only joins across a tight gap.
            limit = int(width * (0.22 if sibling else 0.08))
            if run[1] <= a and a - run[1] <= limit:
                a = min(a, run[0])
                changed = True
            elif run[0] >= b and run[0] - b <= limit:
                b = max(b, run[1])
                changed = True

    if absorb_right:
        # Short flick into the gap (盈). Reject a radical of 真 (枝's 木).
        flick = max(16, int(width * 0.12))
        reach = int(width * 0.55)
        for run in runs:
            if run[0] < b:
                continue
            abs_l = lo + run[0]
            abs_r = lo + run[1]
            run_w = run[1] - run[0]
            if hard is not None and abs_l >= hard:
                break
            if abs_l > col_r + int(width * 0.36):
                break
            if run[0] - b > reach or run_w > flick:
                break
            if hard is not None and abs_r > hard:
                b = max(b, hard - lo)
                break
            b = run[1]

    pad = max(6, int(width * 0.09))
    left = max(0, lo + a - pad)
    right = min(w, lo + b + pad)
    if hard is not None:
        right = min(right, hard)
    if right - left > width * 1.35:
        inset = int(width * 0.08)
        left, right = col_l + inset, max(col_l + inset + 4, col_r - inset)
        if hard is not None:
            right = min(right, hard)
    top, bottom = _drop_edge_speck(mask, left, top, right, bottom)
    return left, top, max(left + 4, right), max(top + 4, bottom)


def _drop_edge_speck(
    mask: np.ndarray, left: int, top: int, right: int, bottom: int
) -> tuple[int, int]:
    """Drop a fleck of the neighbor that crossed the row cut.

    Only a mark a few pixels tall, pressed against the slot edge, with a
    clear gap before this character. Anything taller is part of the glyph
    (cursive tops must not be eaten).
    """
    band = mask[top:bottom, max(0, left) : max(left + 1, right)]
    if band.size == 0 or int(band.sum()) < 8:
        return top, bottom
    proj = _smooth(band.mean(axis=1), 2)
    if float(proj.max()) <= 0:
        return top, bottom
    runs = _ink_runs(proj, max(0.02, float(proj.max()) * 0.12))
    if len(runs) < 2:
        return top, bottom
    slot_h = bottom - top
    # Stricter than the first cut: only a tip against the edge.
    edge = max(10, int(slot_h * 0.05))
    speck_h = max(6, int(slot_h * 0.04))
    gap_need = max(10, int(slot_h * 0.04))
    first = runs[0]
    if (
        first[0] <= 8
        and first[1] - first[0] <= speck_h
        and first[1] <= edge + 2
        and runs[1][0] - first[1] >= gap_need
    ):
        top = top + max(0, runs[1][0] - 2)
    last = runs[-1]
    if (
        slot_h - last[1] <= 8
        and last[1] - last[0] <= speck_h
        and slot_h - last[0] <= edge + 2
        and last[0] - runs[-2][1] >= gap_need
    ):
        bottom = (bottom - slot_h) + runs[-2][1] + 2
    return top, bottom


def square_pad(crop: Image.Image, size: int = SIZE) -> Image.Image:
    crop = crop.convert("RGB")
    arr = np.asarray(crop)
    gray = arr.mean(axis=2)
    light = arr[gray > np.percentile(gray, 55)]
    if light.size:
        median = tuple(int(x) for x in np.median(light, axis=0))
    else:
        median = tuple(int(x) for x in np.median(arr.reshape(-1, 3), axis=0))
    canvas = Image.new("RGB", (size, size), median)
    scale = min(size / crop.width, size / crop.height) * 0.90
    new_w = max(1, int(crop.width * scale))
    new_h = max(1, int(crop.height * scale))
    resized = crop.resize((new_w, new_h), Image.Resampling.LANCZOS)
    canvas.paste(resized, ((size - new_w) // 2, (size - new_h) // 2), _feather_mask(new_w, new_h))
    return canvas


def _feather_mask(w: int, h: int) -> Image.Image:
    """Opaque centre fading out at the rim, so the crop melts into the pad."""
    feather = max(2, int(min(w, h) * 0.07))
    ramp_x = np.clip(np.minimum(np.arange(w), np.arange(w)[::-1]) / feather, 0.0, 1.0)
    ramp_y = np.clip(np.minimum(np.arange(h), np.arange(h)[::-1]) / feather, 0.0, 1.0)
    alpha = np.outer(ramp_y, ramp_x)
    alpha = alpha * alpha * (3.0 - 2.0 * alpha)
    return Image.fromarray((alpha * 255).astype(np.uint8), mode="L")


def page_path(page: int) -> Path:
    return PAGES_DIR / f"{page:02d}.jpg"


def crop_page(spec: PageSpec, debug: bool = False) -> list[tuple[int, str, Image.Image]]:
    im = Image.open(page_path(spec.page)).convert("RGB")
    rgb = np.asarray(im)
    gray = np.asarray(im.convert("L"))
    pl, pt, pr, pb = paper_bbox(gray)
    paper_rgb = rgb[pt:pb, pl:pr]
    paper_gray = gray[pt:pb, pl:pr]
    # Strict cores, not the loose mask: mottling in the mount otherwise
    # looks like a first row and shifts every character down by one.
    wl, wt, wr, wb = writing_bbox(ink_mask(paper_gray))
    write_rgb = paper_rgb[wt:wb, wl:wr]
    write_gray = paper_gray[wt:wb, wl:wr]
    write_mask = ink_mask(write_gray)
    want_cols = 4
    cols = column_bounds(write_mask, want_cols)
    ltr = cols
    need = spec.pairs * 2
    if len(ltr) < need:
        raise RuntimeError(f"p{spec.page:02d} expected {need} cols, got {len(ltr)} {cols}")
    if len(ltr) > need:
        # A damaged or blank pair may still leave an extra peak.
        # Page 2's extra ink is on the right; page 52's seal is on the left.
        if spec.skip_right:
            ltr = ltr[:need]
        else:
            ltr = ltr[-need:]
    rtl = list(reversed(ltr))
    overlay = None
    if debug:
        overlay = Image.fromarray(write_rgb.copy())
        draw = ImageDraw.Draw(overlay)
        for x0, x1 in ltr:
            draw.line([(x0, 0), (x0, overlay.height)], fill=(200, 40, 40), width=3)
            draw.line([(x1, 0), (x1, overlay.height)], fill=(200, 40, 40), width=3)
    glyphs: list[tuple[int, str, Image.Image]] = []
    for pair in range(spec.pairs):
        zhen_col = rtl[pair * 2]
        cao_col = rtl[pair * 2 + 1]
        zx0, zx1 = zhen_col
        zpad = max(2, int((zx1 - zx0) * 0.12))
        zhen_strip = write_mask[:, max(0, zx0 + zpad) : min(write_mask.shape[1], zx1 - zpad)]
        if zhen_strip.size == 0:
            zhen_strip = write_mask[:, zx0:zx1]
        rows = row_bounds(zhen_strip, 10)
        for kind, col in (("zhen", zhen_col), ("cao", cao_col)):
            x0, x1 = col
            for row, (y0, y1) in enumerate(rows):
                index = spec.start + pair * 10 + row
                box = tight_box(
                    write_mask,
                    (x0, y0, x1, y1),
                    col,
                    absorb_right=(kind == "cao"),
                    neighbor_stop=zhen_col[0] if kind == "cao" else None,
                )
                if box[2] - box[0] < 8 or box[3] - box[1] < 8:
                    box = (x0, y0, x1, y1)
                crop = Image.fromarray(write_rgb[box[1] : box[3], box[0] : box[2]])
                glyphs.append((index, kind, square_pad(crop)))
                if overlay is not None:
                    ImageDraw.Draw(overlay).rectangle(
                        [box[0], box[1], box[2], box[3]],
                        outline=(30, 140, 70) if kind == "zhen" else (40, 80, 180),
                        width=2,
                    )
    if overlay is not None:
        DEBUG_DIR.mkdir(parents=True, exist_ok=True)
        overlay.save(DEBUG_DIR / f"p{spec.page:02d}_grid.jpg", quality=86)
    return glyphs


def save_webp(image: Image.Image, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, "WEBP", quality=84, method=4)


def corpus_text() -> str:
    from build_corpus import LINES

    return "".join(LINES)


def write_preview(
    glyphs: dict[tuple[int, str], Image.Image],
    dest: Path = PREVIEW_PATH,
    start: int = 0,
    count: int = SAMPLE_COUNT,
) -> None:
    text = corpus_text()
    cell = 160
    cols = 8
    rows = (count + 3) // 4
    canvas = Image.new("RGB", (cols * cell, rows * cell + 8), (247, 241, 230))
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype("msyh.ttc", 18)
    except OSError:
        font = ImageFont.load_default()
    for group in range(rows):
        for slot in range(4):
            index = start + group * 4 + slot
            if index >= start + count:
                break
            x = slot * 2 * cell
            y = group * cell
            zhen = glyphs.get((index, "zhen"))
            cao = glyphs.get((index, "cao"))
            if zhen is None or cao is None:
                continue
            zhen = zhen.resize((cell - 10, cell - 10), Image.Resampling.LANCZOS)
            cao = cao.resize((cell - 10, cell - 10), Image.Resampling.LANCZOS)
            canvas.paste(zhen, (x + 5, y + 5))
            canvas.paste(cao, (x + cell + 5, y + 5))
            label = text[index] if index < len(text) else ""
            draw.text((x + 8, y + 8), f"{index:03d}{label}真", fill=(90, 40, 30), font=font)
            draw.text((x + cell + 8, y + 8), f"{index:03d}草", fill=(90, 40, 30), font=font)
    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, "JPEG", quality=90)


def copy_sample(count: int = GLYPH_COUNT) -> None:
    """Copy cropped glyphs into app assets. Default is the full album."""
    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    for index in range(count):
        for kind in ("zhen", "cao"):
            name = f"{index:03d}_{kind}.webp"
            src = GLYPH_DIR / name
            if not src.exists():
                raise SystemExit(f"missing {src}")
            shutil.copy2(src, ASSET_DIR / name)
    print(f"copied {count * 2} glyphs to {ASSET_DIR}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--pages", default="", help="comma page numbers, empty means all")
    parser.add_argument("--copy-sample", action="store_true")
    parser.add_argument("--debug", action="store_true")
    args = parser.parse_args()
    self_test()
    if args.self_test:
        return

    wanted = {int(x) for x in args.pages.split(",") if x.strip()} if args.pages else None
    GLYPH_DIR.mkdir(parents=True, exist_ok=True)
    preview_map: dict[tuple[int, str], Image.Image] = {}
    qa_needed = {0, 1, 2, 3, 8, 9, 10, 18, 29, 350, 351, 359, 650, 659, 990, 999}
    written = 0
    for spec in page_specs():
        if wanted and spec.page not in wanted:
            continue
        source = page_path(spec.page)
        if not source.exists():
            raise SystemExit(f"missing page {source}")
        print(f"crop p{spec.page:02d} start={spec.start} cols={spec.pairs * 2}")
        for index, kind, image in crop_page(spec, debug=args.debug):
            save_webp(image, GLYPH_DIR / f"{index:03d}_{kind}.webp")
            if index < SAMPLE_COUNT or index in qa_needed:
                preview_map[(index, kind)] = image
            written += 1
    if any(index < SAMPLE_COUNT for index, _kind in preview_map):
        write_preview(preview_map, PREVIEW_PATH, 0, SAMPLE_COUNT)
        print(f"preview {PREVIEW_PATH}")
    print(f"wrote {written} crops to {GLYPH_DIR}")
    if args.copy_sample or wanted is None:
        copy_sample()


if __name__ == "__main__":
    main()
