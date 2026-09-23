"""Geometric glyph cutter for the Ogawa 真草千字文 album.

Three stages, each purely geometric:

1. Columns. Ink is neutral black, paper and stains are brown, so a colour
   mask isolates strokes cleanly even on damaged leaves. Vertical ink
   coverage then gives the columns.
2. Characters inside one column. Every connected ink blob is assigned to
   its column; the blobs of a column are sorted by height and split into
   exactly ten contiguous groups by dynamic programming (one group = one
   character). Strokes are never cut, so a long cursive tail stays with
   its own character.
3. Minimal square. The union box of a character's blobs is padded a
   little and grown to the smallest square that fits between its
   neighbours; the crop is that square.

Usage:
    python tools/geom_crop.py --vis 3,20          # overlays only
    python tools/geom_crop.py                     # crop all, copy sample
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

from crop_ogawa import (
    DEBUG_DIR,
    GLYPH_DIR,
    PREVIEW_PATH,
    SAMPLE_COUNT,
    PageSpec,
    copy_sample,
    page_path,
    page_specs,
    paper_bbox,
    save_webp,
    square_pad,
    write_preview,
)

ROWS = 10


# ---------------------------------------------------------------- helpers


def _smooth(values: np.ndarray, radius: int) -> np.ndarray:
    radius = max(1, radius)
    kernel = np.ones(radius * 2 + 1, dtype=np.float64) / (radius * 2 + 1)
    return np.convolve(values.astype(np.float64), kernel, mode="same")


def _runs(flags: np.ndarray) -> list[tuple[int, int]]:
    out: list[tuple[int, int]] = []
    start = None
    for i, on in enumerate(flags):
        if on and start is None:
            start = i
        elif not on and start is not None:
            out.append((start, i))
            start = None
    if start is not None:
        out.append((start, flags.size))
    return out


def _font(size: int):
    try:
        return ImageFont.truetype("msyh.ttc", size)
    except OSError:
        return ImageFont.load_default()


# ------------------------------------------------------------ stage 0: ink


def ink_mask(rgb: np.ndarray) -> np.ndarray:
    """Neutral dark pixels. Brown paper and stains have R-B around 40."""
    r = rgb[..., 0].astype(np.int16)
    g = rgb[..., 1].astype(np.int16)
    b = rgb[..., 2].astype(np.int16)
    gray = (r + g + b) // 3
    return (gray < 140) & ((r - b) < 24)


@dataclass
class Blob:
    area: int
    x0: int
    y0: int
    x1: int
    y1: int
    cx: float
    cy: float


def _blob_from(sub: np.ndarray, ox: int, oy: int) -> Blob | None:
    ys, xs = np.where(sub)
    if ys.size == 0:
        return None
    return Blob(
        int(ys.size),
        ox + int(xs.min()),
        oy + int(ys.min()),
        ox + int(xs.max()) + 1,
        oy + int(ys.max()) + 1,
        ox + float(xs.mean()),
        oy + float(ys.mean()),
    )


def _split_joined(
    sub: np.ndarray,
    ox: int,
    oy: int,
    pitch: float,
    char_mass: float,
) -> list[Blob]:
    """Cut a blob that holds two characters at its thinnest waist.

    Two cursive characters occasionally touch (a tail lands on the next
    head). Such a blob is much taller than a character, or carries the
    ink of two; cut it where the horizontal cross-section is thinnest in
    its middle half, recursively.
    """
    h = sub.shape[0]
    # size alone cannot tell a big cursive character from two small ones
    # touching, so only clearly over-tall blobs are cut here
    if h <= 1.5 * pitch:
        blob = _blob_from(sub, ox, oy)
        return [blob] if blob else []
    rows = sub.sum(axis=1).astype(np.float64)
    lo, hi = int(h * 0.25), int(h * 0.75)
    if hi <= lo + 2:
        blob = _blob_from(sub, ox, oy)
        return [blob] if blob else []
    cut = lo + int(np.argmin(rows[lo:hi]))
    out = _split_joined(sub[:cut], ox, oy, pitch, char_mass)
    out.extend(_split_joined(sub[cut:], ox, oy + cut, pitch, char_mass))
    return out


def find_blobs(
    mask: np.ndarray,
    min_area: int,
    min_side: int,
    pitch: float,
    chars: int,
) -> list[Blob]:
    labels, count = ndimage.label(mask, structure=np.ones((3, 3), dtype=int))
    if count == 0:
        return []
    idx = np.arange(1, count + 1)
    areas = ndimage.sum_labels(mask, labels, idx)
    slices = ndimage.find_objects(labels)
    keep: list[tuple[int, tuple[slice, slice]]] = []
    for i, sl in enumerate(slices):
        if sl is None:
            continue
        ys, xs = sl
        if int(areas[i]) < min_area or max(ys.stop - ys.start, xs.stop - xs.start) < min_side:
            continue
        keep.append((i, sl))
    char_mass = max(1.0, float(sum(areas[i] for i, _ in keep)) / max(1, chars))
    blobs: list[Blob] = []
    for i, (ys, xs) in keep:
        sub = labels[ys, xs] == (i + 1)
        blobs.extend(_split_joined(sub, xs.start, ys.start, pitch, char_mass))
    return blobs


# -------------------------------------------------------- stage 1: columns


def column_runs(mask: np.ndarray) -> list[tuple[int, int]]:
    """Ink column spans (left to right) as they appear, before any selection."""
    h, w = mask.shape
    cov = _smooth(mask.mean(axis=0), max(3, w // 120))
    level = max(float(cov.max()) * 0.10, 0.003)
    runs = _runs(cov > level)
    if not runs:
        return []
    # merge runs split by a thin gap inside a column (light strokes)
    min_gap = int(w * 0.035)
    merged = [runs[0]]
    for a, b in runs[1:]:
        if a - merged[-1][1] < min_gap:
            merged[-1] = (merged[-1][0], b)
        else:
            merged.append((a, b))
    runs = merged
    # a crack or a wide flourish can bridge two columns: split a run far
    # wider than its neighbours at its own coverage valley
    for _ in range(4):
        widths = [b - a for a, b in runs]
        typical = float(np.median(widths))
        wide_i = [i for i, wd in enumerate(widths) if wd > typical * 1.6 and wd > 40]
        if not wide_i or len(runs) < 2:
            break
        i = wide_i[0]
        a, b = runs[i]
        lo, hi = a + int((b - a) * 0.3), a + int((b - a) * 0.7)
        cut = lo + int(np.argmin(cov[lo:hi]))
        runs = runs[:i] + [(a, cut), (cut, b)] + runs[i + 1 :]
    widths = [b - a for a, b in runs]
    typical = float(np.median(widths))
    return [r for r in runs if (r[1] - r[0]) >= typical * 0.4]


def find_columns(mask: np.ndarray, need: int, skip_right: bool) -> list[tuple[int, int]]:
    """Return `need` column spans (left to right), expanded into the gutters."""
    h, w = mask.shape
    runs = column_runs(mask)
    if not runs:
        raise RuntimeError("no ink columns")
    if len(runs) < need:
        raise RuntimeError(f"expected {need} columns, found {len(runs)} {runs}")
    if len(runs) > need:
        # damaged pair on page 2 sits at the right; the seal on page 52 at the left
        runs = runs[:need] if skip_right else runs[-need:]
    # expand each run into half of its gutters, bounded by the writing width
    gaps = [runs[i + 1][0] - runs[i][1] for i in range(len(runs) - 1)]
    typical_gap = float(np.median(gaps)) if gaps else w * 0.05
    cols: list[tuple[int, int]] = []
    for i, (a, b) in enumerate(runs):
        left = a - (gaps[i - 1] // 2 if i > 0 else int(typical_gap * 0.5))
        right = b + (gaps[i] // 2 if i < len(gaps) else int(typical_gap * 0.5))
        cols.append((max(0, left), min(w, right)))
    return cols


def assign_blobs(blobs: list[Blob], cols: list[tuple[int, int]]) -> list[list[Blob]]:
    buckets: list[list[Blob]] = [[] for _ in cols]
    for blob in blobs:
        best, best_ov = -1, 0
        for i, (a, b) in enumerate(cols):
            ov = min(blob.x1, b) - max(blob.x0, a)
            if ov > best_ov:
                best, best_ov = i, ov
        if best < 0:
            continue
        buckets[best].append(blob)
    return buckets


# ------------------------------------------------- stage 2: split a column


def partition_column(
    blobs: list[Blob],
    pitch: float,
    char_mass: float,
    grid: list[float] | None = None,
    rows: int = ROWS,
) -> list[list[Blob]]:
    """Split the blobs of one column into characters, count unknown.

    Blobs sorted by height are cut into contiguous groups. A group pays for
    vertical spread, for being taller than a character, and for being a
    speck; every group also pays a fixed fee, so the cut count settles
    where the pieces look like single characters.
    """
    blobs = sorted(blobs, key=lambda b: b.cy)
    n = len(blobs)
    if n == 0:
        return []
    w = np.array([b.area for b in blobs], dtype=np.float64)
    y = np.array([b.cy for b in blobs], dtype=np.float64)
    y0s = np.array([b.y0 for b in blobs], dtype=np.float64)
    y1s = np.array([b.y1 for b in blobs], dtype=np.float64)
    cw = np.concatenate([[0.0], np.cumsum(w)])
    cwy = np.concatenate([[0.0], np.cumsum(w * y)])
    cwy2 = np.concatenate([[0.0], np.cumsum(w * y * y)])
    # Two characters one pitch apart have a centroid variance near pitch^2/4;
    # the parts of one character sit well inside that, so a fee between the
    # two keeps single characters whole and neighbours apart.
    fee = 0.20 * char_mass

    def largest_gap(i: int, j: int) -> float:
        # widest blank band inside the group's vertical extent
        reach = y1s[i]
        gap = 0.0
        for t in range(i + 1, j):
            if y0s[t] > reach:
                gap = max(gap, y0s[t] - reach)
            reach = max(reach, y1s[t])
        return gap

    def group_cost(i: int, j: int) -> float:
        mass = cw[j] - cw[i]
        mean = (cwy[j] - cwy[i]) / mass
        var = max(0.0, (cwy2[j] - cwy2[i]) / mass - mean * mean)
        span = float(y1s[i:j].max() - y0s[i:j].min())
        excess = max(0.0, span - 1.20 * pitch)
        # parts of one character sit close; a blank band means two characters
        gap = largest_gap(i, j)
        cost = mass * (
            var / (pitch * pitch) + 3.0 * (excess / pitch) ** 2 + 2.0 * (gap / pitch) ** 2
        )
        if grid is not None:
            # a group that reaches across two row centres holds two characters
            inset = 0.08 * pitch
            lo_y = float(y0s[i:j].min()) + inset
            hi_y = float(y1s[i:j].max()) - inset
            covered = sum(1 for c in grid if lo_y < c < hi_y)
            cost += char_mass * max(0, covered - 1)
        if mass < 0.20 * char_mass:
            cost += char_mass * 0.6 * (1.0 - mass / (0.20 * char_mass))
        return cost + fee

    inf = float("inf")
    # cache: a group never gathers blobs more than three pitches apart
    cost = np.full((n + 1, n + 1), inf)
    for j in range(1, n + 1):
        lo = 0
        while lo < j - 1 and y[lo] < y[j - 1] - 3.0 * pitch:
            lo += 1
        for i in range(lo, j):
            cost[i, j] = group_cost(i, j)
    # dp[k][j]: best cost cutting blobs[:j] into k groups. The column is
    # expected to hold `rows` characters; a mild prior settles the close
    # calls (two small neighbours vs one) without forcing a damaged column.
    max_k = min(n, rows + 4)
    dp = np.full((max_k + 1, n + 1), inf)
    back = np.zeros((max_k + 1, n + 1), dtype=int)
    dp[0, 0] = 0.0
    for k in range(1, max_k + 1):
        for j in range(k, n + 1):
            cands = dp[k - 1, :j] + cost[:j, j]
            i = int(np.argmin(cands))
            dp[k, j] = cands[i]
            back[k, j] = i
    prior = 0.10 * char_mass
    totals = [dp[k, n] + prior * abs(k - rows) for k in range(1, max_k + 1)]
    best_k = 1 + int(np.argmin(totals))
    groups: list[list[Blob]] = []
    j = n
    for k in range(best_k, 0, -1):
        i = back[k, j]
        groups.append(blobs[i:j])
        j = i
    groups.reverse()
    return groups


def assign_slots(
    groups: list[list[Blob]],
    grid: list[float],
    pitch: float,
    char_mass: float,
) -> list[list[Blob]]:
    """Place ordered groups on the row grid, keeping order, leaving gaps.

    More groups than rows: merge neighbours where the merge costs least.
    Fewer: the monotone assignment with the least displacement.
    """
    rows = len(grid)
    groups = [g for g in groups if g]
    while len(groups) > rows:
        best, arg = float("inf"), 0
        for i in range(len(groups) - 1):
            a, b = groups[i], groups[i + 1]
            top = min(x.y0 for x in a + b)
            bottom = max(x.y1 for x in a + b)
            mass_a = sum(x.area for x in a)
            mass_b = sum(x.area for x in b)
            c = (bottom - top) / pitch + 2.0 * min(mass_a, mass_b) / char_mass
            if c < best:
                best, arg = c, i
        groups = groups[:arg] + [groups[arg] + groups[arg + 1]] + groups[arg + 2 :]
    k = len(groups)
    out: list[list[Blob]] = [[] for _ in range(rows)]
    if k == 0:
        return out
    centers = [float(group_center(g)) for g in groups]
    inf = float("inf")
    dp = np.full((k + 1, rows + 1), inf)
    back = np.zeros((k + 1, rows + 1), dtype=int)
    dp[0, :] = 0.0
    for g in range(1, k + 1):
        for r in range(g, rows + 1):
            skip = dp[g, r - 1]
            take = dp[g - 1, r - 1] + ((centers[g - 1] - grid[r - 1]) / pitch) ** 2
            if take <= skip:
                dp[g, r], back[g, r] = take, 1
            else:
                dp[g, r], back[g, r] = skip, 0
    g, r = k, rows
    while g > 0 and r > 0:
        if back[g, r] == 1:
            out[r - 1] = groups[g - 1]
            g -= 1
        r -= 1
    return out


def group_center(group: list[Blob]) -> float | None:
    mass = sum(b.area for b in group)
    if mass <= 0:
        return None
    return sum(b.area * b.cy for b in group) / mass


def regularity(groups: list[list[Blob]], rows: int) -> float:
    """Lower is better: exactly `rows` groups, evenly spaced."""
    if len(groups) != rows:
        return 1e9
    centers = np.array([group_center(g) for g in groups], dtype=np.float64)
    diffs = np.diff(centers)
    if diffs.size == 0 or float(diffs.mean()) <= 0:
        return 1e9
    return float(diffs.std() / diffs.mean()) + (5.0 if float(diffs.min()) <= 0 else 0.0)


# ------------------------------------------------ stage 3: minimal square


def union_box(blobs: list[Blob]) -> tuple[int, int, int, int] | None:
    if not blobs:
        return None
    return (
        min(b.x0 for b in blobs),
        min(b.y0 for b in blobs),
        max(b.x1 for b in blobs),
        max(b.y1 for b in blobs),
    )


def _rect_gap(
    a: tuple[int, int, int, int],
    b: tuple[int, int, int, int],
) -> float:
    """Distance between two axis-aligned boxes; 0 if they touch or overlap."""
    ax0, ay0, ax1, ay1 = a
    bx0, by0, bx1, by1 = b
    dx = max(0, bx0 - ax1, ax0 - bx1)
    dy = max(0, by0 - ay1, ay0 - by1)
    if dx == 0:
        return float(dy)
    if dy == 0:
        return float(dx)
    return float((dx * dx + dy * dy) ** 0.5)


def core_blobs(blobs: list[Blob], pitch: float) -> list[Blob]:
    """Keep the main ink cluster; drop distant stains and neighbour flecks.

    Grow from the largest blob. Stacked stroke fragments (above/below the
    body) are absorbed more readily than side marks: a short dash left of
    盈 is only a pitch-tenth away but is not part of the character, while
    a broken lower tip of 黄 must stay. Lateral blobs therefore need to
    nearly touch, or be large enough to be a real side stroke.
    """
    if not blobs:
        return []
    ordered = sorted(blobs, key=lambda b: -b.area)
    core = [ordered[0]]
    main_area = max(1, ordered[0].area)
    touch = pitch * 0.06
    stack = pitch * 0.14
    far = pitch * 0.35
    grew = True
    while grew:
        grew = False
        ub = union_box(core)
        assert ub is not None
        for b in ordered[1:]:
            if any(
                b.x0 == c.x0 and b.y0 == c.y0 and b.x1 == c.x1 and b.y1 == c.y1 and b.area == c.area
                for c in core
            ):
                continue
            bb = (b.x0, b.y0, b.x1, b.y1)
            gap = _rect_gap(ub, bb)
            x_overlap = min(ub[2], b.x1) - max(ub[0], b.x0)
            lateral = x_overlap <= 0
            if gap <= touch:
                keep = True
            elif not lateral and gap <= stack:
                keep = True
            elif b.area >= 0.15 * main_area and gap <= far:
                keep = True
            else:
                keep = False
            if keep:
                core.append(b)
                grew = True
                break
    return core


def minimal_square(
    box: tuple[int, int, int, int],
    limits: tuple[int, int, int, int],
    pad: int,
) -> tuple[int, int, int, int]:
    """Smallest square around `box`+pad that stays inside `limits` if it can."""
    x0, y0, x1, y1 = box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad
    ll, lt, lr, lb = limits
    # the padded box itself may never be clipped below the ink
    ll, lt = min(ll, x0), min(lt, y0)
    lr, lb = max(lr, x1), max(lb, y1)
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    sx0 = int(round(cx - side / 2.0))
    sy0 = int(round(cy - side / 2.0))
    sx1, sy1 = sx0 + side, sy0 + side
    if sx0 < ll:
        sx1 += ll - sx0
        sx0 = ll
    if sx1 > lr:
        sx0 -= sx1 - lr
        sx1 = lr
    if sy0 < lt:
        sy1 += lt - sy0
        sy0 = lt
    if sy1 > lb:
        sy0 -= sy1 - lb
        sy1 = lb
    sx0, sy0 = max(ll, sx0), max(lt, sy0)
    sx1, sy1 = min(lr, sx1), min(lb, sy1)
    return sx0, sy0, sx1, sy1


# ------------------------------------------------------------- page driver


@dataclass
class Cell:
    index: int
    kind: str
    ink: tuple[int, int, int, int] | None
    box: tuple[int, int, int, int]
    blobs: list[Blob]
    note: str = ""


@dataclass
class PageLayout:
    spec: PageSpec
    rgb: np.ndarray  # writing area
    offset: tuple[int, int]  # writing area offset inside the page
    cols: list[tuple[int, int]]
    cells: list[Cell]


def layout_page(spec: PageSpec) -> PageLayout:
    im = Image.open(page_path(spec.page)).convert("RGB")
    rgb = np.asarray(im)
    gray = np.asarray(im.convert("L"))
    pl, pt, pr, pb = paper_bbox(gray)
    paper = rgb[pt:pb, pl:pr]
    mask = ink_mask(paper)
    return layout_block(spec, paper, mask, (pl, pt))


def layout_block(
    spec: PageSpec,
    paper: np.ndarray,
    mask: np.ndarray,
    offset: tuple[int, int] = (0, 0),
) -> PageLayout:
    """Stages 1-3 on one writing block: `mask` marks stroke pixels.

    Works for ink on paper and for a rubbing (light strokes on black) alike;
    only the mask differs.
    """
    mask = mask.copy()
    # trim the block edge: a scan border or the rubbing frame is stroke-like
    edge = max(6, int(min(mask.shape) * 0.01))
    mask[:edge, :] = False
    mask[-edge:, :] = False
    mask[:, :edge] = False
    mask[:, -edge:] = False

    need = spec.pairs * 2
    cols = find_columns(mask, need, bool(spec.skip_right))
    col_w = float(np.median([b - a for a, b in cols]))
    # character pitch from the ink extent of the whole writing block
    row_cov = _smooth(mask.mean(axis=1), 4)
    inked = np.where(row_cov > max(0.004, float(row_cov.max()) * 0.06))[0]
    pitch_est = (int(inked[-1]) - int(inked[0])) / ROWS if inked.size > 10 else mask.shape[0] / ROWS
    blobs = find_blobs(
        mask,
        min_area=max(16, int(col_w * col_w * 0.0006)),
        min_side=max(5, int(col_w * 0.03)),
        pitch=pitch_est,
        chars=len(cols) * ROWS,
    )
    h = mask.shape[0]
    # scan borders and mount edges: wide flat lines, or flat blobs on the rim
    rim = edge * 3
    blobs = [
        b
        for b in blobs
        if (b.x1 - b.x0) <= col_w * 1.3
        and not ((b.y0 < rim or b.y1 > h - rim) and (b.y1 - b.y0) < pitch_est * 0.2)
    ]
    buckets = assign_blobs(blobs, cols)

    # Stage 2a: each column is cut into characters on its own.
    char_mass = max(1.0, float(sum(b.area for b in blobs)) / (len(cols) * ROWS))
    parts = [partition_column(bucket, pitch_est, char_mass) for bucket in buckets]
    # Stage 2b: the most evenly spaced full column gives the page's ten row
    # centres; every column's characters are then placed on those rows, so
    # a column that lost characters leaves the lost slots empty.
    scores = [regularity(groups, ROWS) for groups in parts]
    best = int(np.argmin(scores))
    if scores[best] < 1e8:
        page_grid = [float(group_center(g)) for g in parts[best]]
    else:
        page_grid = [inked[0] + (r + 0.5) * pitch_est for r in range(ROWS)]

    rtl = list(reversed(range(len(cols))))
    cells: list[Cell] = []
    for pair in range(spec.pairs):
        # Rows drift between the two pairs of a leaf, so a pair prefers the
        # grid of its own most regular column.
        pair_cols = (rtl[pair * 2], rtl[pair * 2 + 1])
        own = min(pair_cols, key=lambda ci: scores[ci])
        grid = [float(group_center(g)) for g in parts[own]] if scores[own] < 1e8 else page_grid
        pitch = float(np.median(np.diff(grid)))
        for kind, ci in (("zhen", pair_cols[0]), ("cao", pair_cols[1])):
            col = cols[ci]
            # Stage 2c: cut again knowing the rows, so two small neighbours
            # that nearly touch are still told apart, then place on the rows.
            groups = partition_column(buckets[ci], pitch, char_mass, grid)
            groups = assign_slots(groups, grid, pitch, char_mass)
            # Drop distant stains before the square is built, so the crop
            # centres on the character rather than on paper damage.
            cores = [core_blobs(g, pitch) for g in groups]
            boxes = [union_box(g) for g in cores]
            pad = max(4, int(pitch * 0.05))
            for row in range(ROWS):
                index = spec.start + pair * 10 + row
                ink = boxes[row]
                note = ""
                if ink is None:
                    # lost or blank character: an empty square on its row
                    cy = int(grid[row])
                    side = int(pitch * 0.8)
                    cx = (col[0] + col[1]) // 2
                    box = (cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2)
                    note = "blank"
                else:
                    prev_bot = boxes[row - 1][3] if row > 0 and boxes[row - 1] else 0
                    next_top = boxes[row + 1][1] if row < ROWS - 1 and boxes[row + 1] else h
                    limits = (col[0], prev_bot, col[1], next_top)
                    box = minimal_square(ink, limits, pad)
                    span = ink[3] - ink[1]
                    if span > pitch * 1.45:
                        note = "tall"
                    elif span < pitch * 0.25:
                        note = "small"
                cells.append(Cell(index, kind, ink, box, cores[row], note))
    # QA flags: a 草 far off its 真 row, or neighbours whose ink overlaps
    by_key = {(c.index, c.kind): c for c in cells}
    for c in cells:
        if c.kind != "cao" or c.ink is None:
            continue
        mate = by_key.get((c.index, "zhen"))
        if mate and mate.ink:
            dy = abs((c.ink[1] + c.ink[3]) - (mate.ink[1] + mate.ink[3])) / 2.0
            if dy > 0.6 * pitch:
                c.note = (c.note + " shift").strip()
    for c in cells:
        nxt = by_key.get((c.index + 1, c.kind))
        if c.ink and nxt and nxt.ink and (c.index + 1 - spec.start) % 10 != 0:
            overlap = c.ink[3] - nxt.ink[1]
            if overlap > 0.15 * pitch:
                nxt.note = (nxt.note + " overlap").strip()
    return PageLayout(spec, paper, offset, cols, cells)


# ---------------------------------------------------------- visualisation


def draw_layout(layout: PageLayout, dest: Path, max_h: int = 2400) -> None:
    overlay = Image.fromarray(layout.rgb.copy())
    draw = ImageDraw.Draw(overlay)
    font = _font(18)
    for a, b in layout.cols:
        draw.line([(a, 0), (a, overlay.height)], fill=(220, 40, 40), width=2)
        draw.line([(b, 0), (b, overlay.height)], fill=(220, 40, 40), width=2)
    for cell in layout.cells:
        color = (20, 150, 70) if cell.kind == "zhen" else (40, 90, 210)
        if cell.ink:
            draw.rectangle(cell.ink, outline=(255, 170, 40), width=1)
        x0, y0, x1, y1 = cell.box
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=color, width=3)
        tag = f"{cell.index:03d}{'真' if cell.kind == 'zhen' else '草'}"
        if cell.note:
            tag += f" {cell.note}"
        draw.text((x0 + 3, y0 + 1), tag, fill=color, font=font)
    overlay.thumbnail((max_h, max_h), Image.Resampling.LANCZOS)
    dest.parent.mkdir(parents=True, exist_ok=True)
    overlay.save(dest, quality=90)


# ------------------------------------------------------------------ main


def erase_stray_ink(
    rgb: np.ndarray,
    box: tuple[int, int, int, int],
    core: list[Blob],
    mask: np.ndarray,
) -> np.ndarray:
    """Paint over ink in the crop that is not part of the character's core.

    The minimal square often has side padding; a neighbour fleck that was
    rejected from the ink box can still sit in that pad (盈's left dash).
    Any ink component that does not touch the core is replaced with paper.
    """
    x0, y0, x1, y1 = box
    patch = rgb[y0:y1, x0:x1].copy()
    if not core:
        return patch
    local_ink = mask[y0:y1, x0:x1]
    core_m = np.zeros(local_ink.shape, dtype=bool)
    for b in core:
        bx0, by0 = max(0, b.x0 - x0), max(0, b.y0 - y0)
        bx1, by1 = min(x1 - x0, b.x1 - x0), min(y1 - y0, b.y1 - y0)
        if bx1 > bx0 and by1 > by0:
            core_m[by0:by1, bx0:bx1] |= local_ink[by0:by1, bx0:bx1]
    if not core_m.any() or not local_ink.any():
        return patch
    core_m = ndimage.binary_dilation(core_m, iterations=3)
    labels, count = ndimage.label(local_ink, structure=np.ones((3, 3), dtype=int))
    keep = set(int(v) for v in np.unique(labels[core_m]) if v)
    stray = local_ink & ~np.isin(labels, list(keep) if keep else [0])
    if not stray.any():
        return patch
    paper_px = patch[~local_ink]
    if paper_px.size:
        paper = np.median(paper_px.reshape(-1, 3), axis=0).astype(np.uint8)
    else:
        paper = np.median(patch.reshape(-1, 3), axis=0).astype(np.uint8)
    patch[stray] = paper
    return patch


def crop_layout(layout: PageLayout) -> list[tuple[int, str, Image.Image]]:
    out = []
    h, w = layout.rgb.shape[:2]
    mask = ink_mask(layout.rgb)
    for cell in layout.cells:
        x0, y0, x1, y1 = cell.box
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(w, max(x0 + 4, x1)), min(h, max(y0 + 4, y1))
        patch = erase_stray_ink(layout.rgb, (x0, y0, x1, y1), cell.blobs, mask)
        out.append((cell.index, cell.kind, square_pad(Image.fromarray(patch))))
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--vis", default="", help="comma pages: overlays only")
    parser.add_argument("--pages", default="", help="comma pages to crop, empty = all")
    parser.add_argument("--no-copy", action="store_true")
    args = parser.parse_args()

    if args.vis:
        for page in (int(p) for p in args.vis.split(",") if p.strip()):
            spec = next(s for s in page_specs() if s.page == page)
            layout = layout_page(spec)
            draw_layout(layout, DEBUG_DIR / f"geom_p{page:02d}.jpg")
            notes = [(c.index, c.kind, c.note) for c in layout.cells if c.note]
            print(f"p{page:02d} cols={layout.cols} notes={notes}")
        return

    wanted = {int(p) for p in args.pages.split(",") if p.strip()} if args.pages else None
    GLYPH_DIR.mkdir(parents=True, exist_ok=True)
    preview: dict[tuple[int, str], Image.Image] = {}
    flagged: list[tuple[int, int, str, str]] = []
    written = 0
    for spec in page_specs():
        if wanted and spec.page not in wanted:
            continue
        layout = layout_page(spec)
        draw_layout(layout, DEBUG_DIR / f"geom_p{spec.page:02d}.jpg", max_h=1600)
        for cell in layout.cells:
            if cell.note:
                flagged.append((spec.page, cell.index, cell.kind, cell.note))
        for index, kind, image in crop_layout(layout):
            save_webp(image, GLYPH_DIR / f"{index:03d}_{kind}.webp")
            if index < SAMPLE_COUNT:
                preview[(index, kind)] = image
            written += 1
        print(f"p{spec.page:02d} ok {spec.start:03d}-{spec.start + spec.pairs * 10 - 1:03d}")
    if preview:
        write_preview(preview, PREVIEW_PATH, 0, SAMPLE_COUNT)
    print(f"wrote {written} crops")
    if flagged:
        print("flagged:")
        for page, index, kind, note in flagged:
            print(f"  p{page:02d} {index:03d}_{kind} {note}")
    if wanted is None and not args.no_copy:
        copy_sample()


if __name__ == "__main__":
    main()
