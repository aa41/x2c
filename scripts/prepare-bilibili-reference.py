#!/usr/bin/env python3
"""Reproducible crops from the user-approved 1080 x 2460 Android screenshot."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/references/bilibili-8.14.0/home.png"
OUTPUT = ROOT / "fixtures/consumer/src/main/res/drawable-nodpi"
BOXES = {
    "banner": (24, 329, 1066, 854),
    # Exclude metrics: live cards render those as real text.
    "cover_0": (12, 935, 533, 1278),
    "cover_1": (547, 935, 1067, 1278),
    "cover_2": (12, 1556, 533, 1898),
    "cover_3": (547, 1556, 1067, 1898),
    "search": (212, 129, 251, 166),
    "game": (865, 125, 927, 174),
    "message": (990, 126, 1045, 171),
    "menu": (996, 248, 1042, 293),
    "home": (81, 2307, 133, 2359),
    "follow": (296, 2305, 350, 2359),
    "member": (728, 2304, 781, 2361),
    "my": (943, 2305, 999, 2359),
    "more": (495, 1485, 508, 1514),
    "up": (37, 1484, 73, 1515),
    "play": (37, 1280, 73, 1308),
    "danmaku": (213, 1280, 250, 1308),
}


def main():
    source = Image.open(SOURCE).convert("RGB")
    if source.size != (1080, 2460):
        raise ValueError("Reference dimensions changed: remeasure crop boxes first")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    assets = []
    for name, box in BOXES.items():
        crop = source.crop(box)
        if name not in {"banner", "cover_0", "cover_1", "cover_2", "cover_3"}:
            # Neutral alpha masks remove both exterior and interior matte.
            mask = Image.new("RGBA", crop.size)
            light_on_dark = name in {"play", "danmaku"}
            pixels = []
            strengths = [min(r, g, b) if light_on_dark else 255 - min(r, g, b)
                         for r, g, b in crop.getdata()]
            maximum = max(1, max(strengths) - 12)
            for strength in strengths:
                alpha = max(0, min(255, round((strength - 12) * 255 / maximum)))
                pixels.append((255, 255, 255, alpha))
            mask.putdata(pixels)
            crop = mask
        path = OUTPUT / ("bili_ref_" + name + ".png")
        crop.save(path, optimize=True)
        assets.append({"name": name, "path": str(path.relative_to(ROOT)),
                       "sourceBox": list(box), "size": list(crop.size), "mode": crop.mode})
    manifest = {"sourceReference": str(SOURCE.relative_to(ROOT)), "appVersion": "8.14.0",
                "scope": "Local UI test only; not official assets or live services", "assets": assets}
    (SOURCE.parent / "assets.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(assets)} reference-only assets")


if __name__ == "__main__":
    main()
