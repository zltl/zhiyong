"""Download 关中本真草千字文 pages from the University of Tokyo IIIF archive.

Source: 関中本真草千字文 [A005940], 东京大学総合図書館
License: CC BY equivalent (attribution + modification notice)
https://www.lib.u-tokyo.ac.jp/ja/library/contents/archives-top/reuse
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "tools" / "guanzhong_manifest.json"
OUT_DIR = ROOT / "data" / "guanzhong" / "pages"
MANIFEST_URL = (
    "https://da.dl.itc.u-tokyo.ac.jp/portal/repo/iiif/"
    "e281ae55-35d4-7f26-7c79-837db3d3115e/manifest"
)


def load_manifest() -> dict:
    if not MANIFEST.exists():
        MANIFEST.write_bytes(urllib.request.urlopen(MANIFEST_URL, timeout=60).read())
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def pages(manifest: dict) -> list[dict]:
    canvases = manifest["sequences"][0]["canvases"]
    items = []
    for index, canvas in enumerate(canvases, start=1):
        resource = canvas["images"][0]["resource"]
        service = resource["service"]["@id"]
        items.append(
            {
                "index": index,
                "label": canvas["label"],
                "width": resource["width"],
                "height": resource["height"],
                "service": service,
            }
        )
    return items


def download(url: str, dest: Path, attempts: int = 6) -> None:
    """Fetch with retries; the IIIF proxy answers 502 or stalls now and then."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    req = urllib.request.Request(url, headers={"User-Agent": "zhiyong-copybook/1.0"})
    last: Exception | None = None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp, tmp.open("wb") as fh:
                while True:
                    chunk = resp.read(1024 * 256)
                    if not chunk:
                        break
                    fh.write(chunk)
            if tmp.stat().st_size < 10_000:
                raise OSError("short response")
            tmp.replace(dest)
            return
        except Exception as exc:  # noqa: BLE001 - retry on any transport error
            last = exc
            print(f"  retry {attempt + 1}: {exc}")
            time.sleep(3.0 * (attempt + 1))
    raise RuntimeError(f"failed {url}: {last}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-edge", type=int, default=2400, help="IIIF max edge, or 0 for full")
    parser.add_argument("--pages", default="", help="comma list, e.g. 1,2,3,36")
    parser.add_argument("--sleep", type=float, default=0.4)
    args = parser.parse_args()

    items = pages(load_manifest())
    wanted = {int(x) for x in args.pages.split(",") if x.strip()} if args.pages else None
    size = "max" if args.max_edge <= 0 else f"!{args.max_edge},{args.max_edge}"
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for item in items:
        if wanted and item["index"] not in wanted:
            continue
        dest = OUT_DIR / f"p{item['index']:02d}.jpg"
        if dest.exists() and dest.stat().st_size > 10_000:
            print(f"skip {dest.name}")
            continue
        url = f"{item['service']}/full/{size}/0/default.jpg"
        print(f"get {dest.name} {url}")
        download(url, dest)
        print(f"  {dest.stat().st_size} bytes")
        time.sleep(args.sleep)


if __name__ == "__main__":
    main()
