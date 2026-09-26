#!/usr/bin/env python3
"""
vectorize_image.py
-------------------
Convert a raster image (PNG/JPG/etc.) into a high-resolution vector
SVG file.

It tries three backends, in order of quality, and uses whichever is
available:

  1. vtracer   - best for FULL-COLOR images (photos, logos, art).
                 pip install vtracer
  2. potrace   - best for B/W or line-art images (very clean curves).
                 sudo apt-get install potrace   (or brew install potrace)
  3. OpenCV    - pure-Python contour-based fallback, no external
                 binaries required. Lower fidelity than the above two,
                 but always works.
                 pip install opencv-python numpy

Usage:
    python vectorize_image.py input.png output.svg
    python vectorize_image.py input.png output.svg --backend vtracer
    python vectorize_image.py input.png output.svg --backend potrace
    python vectorize_image.py input.png output.svg --backend opencv --upscale 2

Options:
    --backend {auto,vtracer,potrace,opencv}   Force a specific backend (default: auto)
    --upscale FLOAT                           Upscale the source image before tracing,
                                               for extra-crisp detail (default: 1.0)
    --colors INT                              (vtracer/opencv) approximate number of
                                               color layers to trace (default: 8)
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile


def remove_background(input_path: str) -> str:
    """
    Remove the background from the image, producing a transparent-PNG
    working copy. Requires: pip install rembg onnxruntime
    Falls back to raising a clear error if rembg isn't installed.
    """
    try:
        from rembg import remove
    except ImportError:
        sys.exit(
            "Background removal requested but 'rembg' is not installed.\n"
            "Install it with: pip install rembg onnxruntime"
        )

    from PIL import Image

    print("Removing background (rembg)...")
    img = Image.open(input_path).convert("RGBA")
    out = remove(img)  # returns an RGBA image with background made transparent

    tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_nobg.png")
    out.save(tmp_path)
    print(f"Background removed -> {tmp_path}")
    return tmp_path


def upscale_image(input_path: str, factor: float) -> str:
    """Return a path to an (optionally upscaled) working copy of the image."""
    if factor == 1.0:
        return input_path

    from PIL import Image

    img = Image.open(input_path)
    new_size = (int(img.width * factor), int(img.height * factor))
    # LANCZOS gives the sharpest result for upscaling before tracing
    img = img.convert("RGBA").resize(new_size, Image.LANCZOS)

    tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_upscaled.png")
    img.save(tmp_path)
    print(f"Upscaled source image to {new_size[0]}x{new_size[1]} -> {tmp_path}")
    return tmp_path


def try_vtracer(input_path: str, output_path: str, colors: int) -> bool:
    try:
        import vtracer
    except ImportError:
        return False

    print("Using backend: vtracer (full-color tracing)")
    vtracer.convert_image_to_svg_py(
        input_path,
        output_path,
        colormode="color",       # "color" or "binary"
        hierarchical="stacked",  # better layering for complex images
        mode="spline",           # smooth curves instead of polygons
        filter_speckle=2,        # low value = keep fine detail (remove only tiny noise)
        color_precision=8,       # bits per channel when clustering colors
        layer_difference=8,      # lower = more distinct color layers = more detail
        corner_threshold=40,     # lower = preserves sharper corners
        length_threshold=2.0,    # lower = keeps shorter/finer path segments
        max_iterations=15,
        splice_threshold=30,
        path_precision=8,        # higher = more precise anchor points
    )
    return True


def try_potrace(input_path: str, output_path: str) -> bool:
    potrace_bin = shutil.which("potrace")
    if not potrace_bin:
        return False

    print("Using backend: potrace (line-art / B&W tracing)")
    from PIL import Image

    with tempfile.TemporaryDirectory() as tmpdir:
        bmp_path = os.path.join(tmpdir, "trace_input.bmp")
        # potrace needs a bitmap; convert + threshold to B/W first
        img = Image.open(input_path).convert("L")
        img = img.point(lambda p: 255 if p > 128 else 0)
        img = img.convert("1")
        img.save(bmp_path)

        subprocess.run(
            [
                potrace_bin,
                bmp_path,
                "-s",                # output SVG
                "-o", output_path,
                "--turdsize", "2",   # suppress tiny speckles
                "--opttolerance", "0.2",
                "--alphamax", "1",   # smooth curve corners
            ],
            check=True,
        )
    return True


def try_opencv(input_path: str, output_path: str, colors: int) -> bool:
    try:
        import cv2
        import numpy as np
    except ImportError:
        return False

    print("Using backend: OpenCV contour tracing (fallback)")

    img = cv2.imread(input_path, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError(f"Could not read image: {input_path}")

    h, w = img.shape[:2]

    # Reduce to a limited color palette via k-means, then trace each
    # color layer's contours separately for a posterized vector look.
    Z = img.reshape((-1, 3)).astype(np.float32)
    k = max(2, colors)
    criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 0.5)
    _, labels, centers = cv2.kmeans(Z, k, None, criteria, 5, cv2.KMEANS_PP_CENTERS)
    centers = centers.astype(np.uint8)
    quantized = centers[labels.flatten()].reshape(img.shape)

    svg_paths = []
    for i, color in enumerate(centers):
        mask = (labels.flatten() == i).reshape(h, w).astype(np.uint8) * 255
        mask = cv2.medianBlur(mask, 5)  # smooth mask edges before tracing
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_TC89_KCOS)
        b, g, r = [int(c) for c in color]
        for cnt in contours:
            if cv2.contourArea(cnt) < 8:
                continue
            # Smooth the polygon slightly for cleaner curves
            eps = 0.5
            cnt = cv2.approxPolyDP(cnt, eps, True)
            pts = cnt.reshape(-1, 2)
            if len(pts) < 3:
                continue
            d = "M " + " L ".join(f"{x},{y}" for x, y in pts) + " Z"
            svg_paths.append(f'<path d="{d}" fill="rgb({r},{g},{b})" />')

    svg_content = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n'
        + "\n".join(svg_paths)
        + "\n</svg>"
    )

    with open(output_path, "w") as f:
        f.write(svg_content)
    return True


def vectorize(input_path: str, output_path: str, backend: str, colors: int) -> None:
    if backend == "vtracer":
        if not try_vtracer(input_path, output_path, colors):
            sys.exit("vtracer backend requested but not installed. Run: pip install vtracer")
        return
    if backend == "potrace":
        if not try_potrace(input_path, output_path):
            sys.exit("potrace backend requested but binary not found on PATH.")
        return
    if backend == "opencv":
        if not try_opencv(input_path, output_path, colors):
            sys.exit("opencv backend requested but not installed. Run: pip install opencv-python numpy")
        return

    # auto: try best-quality options first
    if try_vtracer(input_path, output_path, colors):
        return
    if try_potrace(input_path, output_path):
        return
    if try_opencv(input_path, output_path, colors):
        return

    sys.exit(
        "No vectorization backend available.\n"
        "Install one of:\n"
        "  pip install vtracer\n"
        "  sudo apt-get install potrace\n"
        "  pip install opencv-python numpy"
    )


def main():
    parser = argparse.ArgumentParser(description="Vectorize a raster image into SVG.")
    parser.add_argument("input", help="Path to the input image (PNG, JPG, etc.)")
    parser.add_argument("output", help="Path to write the output SVG file")
    parser.add_argument(
        "--backend",
        choices=["auto", "vtracer", "potrace", "opencv"],
        default="auto",
        help="Which vectorization backend to use (default: auto)",
    )
    parser.add_argument(
        "--upscale",
        type=float,
        default=1.0,
        help="Upscale factor applied before tracing, for crisper detail (default: 1.0)",
    )
    parser.add_argument(
        "--colors",
        type=int,
        default=12,
        help="Approximate number of color layers to trace (vtracer/opencv only, default: 12)",
    )
    parser.add_argument(
        "--remove-bg",
        action="store_true",
        help="Remove the image background before vectorizing (requires: pip install rembg onnxruntime)",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        sys.exit(f"Input file not found: {args.input}")

    working_input = args.input
    if args.remove_bg:
        working_input = remove_background(working_input)
    working_input = upscale_image(working_input, args.upscale)
    vectorize(working_input, args.output, args.backend, args.colors)
    print(f"Done. Vector output written to: {args.output}")


if __name__ == "__main__":
    main()
