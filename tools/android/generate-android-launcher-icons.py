#!/usr/bin/env python3
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "static" / "icon.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}

ADAPTIVE_LAYER_DP = 108
ADAPTIVE_ARTWORK_DP = 96
ADAPTIVE_BACKGROUND = (19, 28, 37, 255)
MONO_TRANSPARENT_THRESHOLD = 40
MONO_OPAQUE_THRESHOLD = 64


def save_foreground(source):
    for density, density_scale in DENSITIES.items():
        layer_px = int(ADAPTIVE_LAYER_DP * density_scale)
        artwork_px = int(ADAPTIVE_ARTWORK_DP * density_scale)
        foreground = source.resize(
            (artwork_px, artwork_px), Image.Resampling.LANCZOS
        )
        canvas = Image.new("RGBA", (layer_px, layer_px), ADAPTIVE_BACKGROUND)
        offset = ((layer_px - artwork_px) // 2,) * 2
        canvas.alpha_composite(foreground, offset)
        output = RES / f"mipmap-{density}" / "ic_launcher_foreground.png"
        canvas.save(output)
        print(f"wrote {output.relative_to(ROOT)}")


def save_monochrome(source):
    for density, density_scale in DENSITIES.items():
        layer_px = int(ADAPTIVE_LAYER_DP * density_scale)
        artwork_px = int(ADAPTIVE_ARTWORK_DP * density_scale)
        artwork = source.resize((artwork_px, artwork_px), Image.Resampling.LANCZOS)
        monochrome = Image.new("RGBA", artwork.size, (255, 255, 255, 0))
        source_pixels = artwork.load()
        mono_pixels = monochrome.load()
        for y in range(artwork.height):
            for x in range(artwork.width):
                r, g, b, source_alpha = source_pixels[x, y]
                strongest = max(r, g, b)
                if strongest <= MONO_TRANSPARENT_THRESHOLD:
                    alpha = 0
                elif strongest >= MONO_OPAQUE_THRESHOLD:
                    alpha = source_alpha
                else:
                    alpha = round(
                        source_alpha
                        * (strongest - MONO_TRANSPARENT_THRESHOLD)
                        / (MONO_OPAQUE_THRESHOLD - MONO_TRANSPARENT_THRESHOLD)
                    )
                mono_pixels[x, y] = (255, 255, 255, alpha)

        canvas = Image.new("RGBA", (layer_px, layer_px), (0, 0, 0, 0))
        offset = ((layer_px - artwork_px) // 2,) * 2
        canvas.alpha_composite(monochrome, offset)
        output = RES / f"mipmap-{density}" / "ic_launcher_monochrome.png"
        canvas.save(output)
        print(f"wrote {output.relative_to(ROOT)}")


def save_bitmap_launcher(source):
    for density, density_scale in DENSITIES.items():
        size = int(48 * density_scale)
        output = RES / f"mipmap-{density}" / "ic_launcher.png"
        source.resize((size, size), Image.Resampling.LANCZOS).save(output)
        print(f"wrote {output.relative_to(ROOT)}")


def main():
    source = Image.open(SOURCE).convert("RGBA")
    if source.width != source.height:
        raise RuntimeError(f"{SOURCE} must be square, got {source.width}x{source.height}")

    save_foreground(source)
    save_monochrome(source)
    save_bitmap_launcher(source)


if __name__ == "__main__":
    main()
