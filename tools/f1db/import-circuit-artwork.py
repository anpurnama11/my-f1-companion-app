#!/usr/bin/env python3
"""Import pinned F1DB circuit SVGs as local Android WebP resources.

The generated files are deliberately checked into res/drawable-nodpi: the
app never receives F1DB JSON and never fetches artwork at runtime.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path


REPOSITORY = "https://raw.githubusercontent.com/f1db/f1db/{revision}/src/assets/circuits/white-outline/{filename}"


def render_svg(source: Path, destination: Path) -> None:
    """Render a 512px SVG through cairosvg or macOS Quick Look + sips."""
    try:
        import cairosvg  # type: ignore
    except ImportError:
        cairosvg = None

    if cairosvg is not None:
        cairosvg.svg2png(url=str(source), write_to=str(destination.with_suffix(".png")), output_width=512, output_height=512)
        png = destination.with_suffix(".png")
        convert_png_to_webp(png, destination)
        png.unlink()
        return

    if shutil.which("qlmanage"):
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            subprocess.run(["qlmanage", "-t", "-s", "512", "-o", temp, str(source)], check=True, stdout=subprocess.DEVNULL)
            png = temp_dir / f"{source.name}.png"
            # Quick Look rasterizes SVGs onto an opaque white canvas. Remove
            # that canvas before encoding, otherwise Compose's SrcIn tint
            # colors the entire 512px square instead of only the circuit.
            transparent_png = temp_dir / "transparent.png"
            make_white_transparent(png, transparent_png)
            convert_png_to_webp(transparent_png, destination)
        return

    raise SystemExit("Install cairosvg or run this importer on macOS with qlmanage")


def make_white_transparent(source: Path, destination: Path) -> None:
    """Remove Quick Look's white background while retaining anti-aliased lines."""
    if not shutil.which("ffmpeg"):
        raise SystemExit("Install ffmpeg to remove the Quick Look white background")
    subprocess.run(
        [
            "ffmpeg", "-y", "-loglevel", "error", "-i", str(source),
            "-vf", "colorkey=0xFFFFFF:0.08:0.0", "-pix_fmt", "rgba", str(destination),
        ],
        check=True,
    )


def convert_png_to_webp(png: Path, destination: Path) -> None:
    if shutil.which("cwebp"):
        subprocess.run(["cwebp", "-quiet", str(png), "-o", str(destination)], check=True)
    elif shutil.which("ffmpeg"):
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(png), str(destination)], check=True)
    else:
        raise SystemExit("Install cwebp or ffmpeg to encode WebP resources")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    tools = Path(__file__).parent
    revision = (tools / "revision.txt").read_text().strip()
    mapping = json.loads((tools / "circuit-artwork-map.json").read_text())
    output = args.repo_root / "app/src/main/res/drawable-nodpi"
    output.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as temp:
        temp_dir = Path(temp)
        for circuit_id, filename in mapping.items():
            source = temp_dir / filename
            url = REPOSITORY.format(revision=revision, filename=filename)
            urllib.request.urlretrieve(url, source)
            render_svg(source, output / f"circuit_{circuit_id}.webp")


if __name__ == "__main__":
    main()
