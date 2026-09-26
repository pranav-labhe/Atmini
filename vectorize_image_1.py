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


def clean_edges(input_path: str, alpha_threshold: int = 180, supersample: int = 4) -> str:
    """
    Fix the two most common causes of bad-looking vector output:

    1. "Blurry / soft borders" - rembg (and JPEG artifacts) leave a
       semi-transparent halo around the subject. Vtracer/potrace trace
       that halo as extra color layers, which looks like a blurred edge.
       Fix: hard-threshold the alpha channel to a clean binary mask.

    2. "Jagged / pixelated edges" - a hard binary mask traced at native
       resolution produces stair-stepped edges. Fix: supersample the
       mask up, then smooth it slightly with anti-aliasing, so the
       tracer has clean sub-pixel curvature to fit a smooth bezier to.
    """
    from PIL import Image, ImageFilter
    import numpy as np

    img = Image.open(input_path).convert("RGBA")

    if "A" in img.getbands():
        arr = np.array(img)
        alpha = arr[:, :, 3]
        # Hard-threshold: fully opaque or fully transparent, no halo
        alpha = np.where(alpha >= alpha_threshold, 255, 0).astype("uint8")
        arr[:, :, 3] = alpha
        img = Image.fromarray(arr, mode="RGBA")

    # Supersample up, then anti-alias ONLY the alpha (edge) channel.
    # IMPORTANT: blurring the RGB channels here would smear neighboring
    # colors together (e.g. a dark-gray shadow bleeding into pure black),
    # destroying exactly the fine shade distinctions --colors is meant to
    # preserve. So we blur transparency only, and leave color data untouched.
    w, h = img.size
    big = img.resize((w * supersample, h * supersample), Image.LANCZOS)

    if "A" in big.getbands():
        r, g, b, a = big.split()
        a = a.filter(ImageFilter.GaussianBlur(radius=supersample * 0.6))
        a = a.point(lambda p: 255 if p >= 128 else 0)
        big = Image.merge("RGBA", (r, g, b, a))

    tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_clean_edges.png")
    big.save(tmp_path)
    print(f"Cleaned + supersampled edges -> {tmp_path} ({big.size[0]}x{big.size[1]})")
    return tmp_path


def try_embed(input_path: str, output_path: str) -> bool:
    """
    Not a trace — wraps the original raster image inside an SVG container
    at full resolution. Pixel-perfect / sharp, but NOT true scalable vector:
    it will pixelate if scaled up past the source resolution. Use this when
    you want an .svg file but the source is photo-like (gradients, shading)
    and true tracing looks blotchy.
    """
    import base64
    from PIL import Image

    print("Using backend: embed (pixel-perfect raster wrapped in SVG, not traced)")

    img = Image.open(input_path)
    w, h = img.size

    with open(input_path, "rb") as f:
        raw = f.read()
    ext = os.path.splitext(input_path)[1].lower().lstrip(".")
    mime = "image/png" if ext == "png" else "image/jpeg"
    b64 = base64.b64encode(raw).decode("ascii")

    svg_content = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n'
        f'<image width="{w}" height="{h}" '
        f'href="data:{mime};base64,{b64}" />\n'
        f'</svg>'
    )

    with open(output_path, "w") as f:
        f.write(svg_content)
    return True


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
    # color_precision is genuinely capped at 8 by vtracer itself (hard library limit).
    # layer_difference has no real floor in the library other than 1 -- lower it
    # further for higher --colors so you keep getting more/finer shade bands
    # (a closer approximation of a gradient) instead of silently capping out.
    color_precision = 8
    layer_difference = max(1, round(32 / (1 + colors / 8)))
    # filter_speckle discards any color region smaller than this many pixels,
    # REGARDLESS of color_precision/layer_difference -- this is the actual
    # cause of "missing" color layers (small patches get silently dropped).
    # Lower it as --colors goes up so small/rare-colored regions survive.
    filter_speckle = max(1, round(16 / (1 + colors / 16)))
    print(f"  resolved -> color_precision={color_precision}, "
          f"layer_difference={layer_difference}, filter_speckle={filter_speckle}")

    vtracer.convert_image_to_svg_py(
        input_path,
        output_path,
        colormode="color",       # "color" or "binary"
        hierarchical="stacked",  # better layering for complex images
        mode="spline",           # smooth curves instead of polygons
        filter_speckle=filter_speckle,  # too low = jagged/noisy edges, too high = drops small color layers
        color_precision=color_precision,  # bits per channel when clustering colors
        layer_difference=layer_difference,  # lower = more distinct color layers = more detail
        corner_threshold=60,     # too low makes edges look jagged/pixelated
        length_threshold=4.0,    # too low overfits to pixel staircases
        max_iterations=15,
        splice_threshold=45,
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
    if colors > 100:
        print(f"Note: {colors} color clusters is a lot -- this may take a while and "
              f"produce a very large SVG.")

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
    if backend == "embed":
        try_embed(input_path, output_path)
        return
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
        choices=["auto", "vtracer", "potrace", "opencv", "embed"],
        default="auto",
        help="Which method to use. 'embed' wraps the original raster in an SVG "
             "instead of tracing shapes -- best for photo-like images that look "
             "blotchy when traced (default: auto)",
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
        help="Requested color/shade fidelity. Higher = more, finer shade bands "
             "approximating gradients (vtracer: no artificial cap, though color "
             "quantization itself maxes out around --colors 256+; opencv: used "
             "directly as k-means cluster count, uncapped but slow above ~100). "
             "Default: 12",
    )
    parser.add_argument(
        "--remove-bg",
        action="store_true",
        help="Remove the image background before vectorizing (requires: pip install rembg onnxruntime)",
    )
    parser.add_argument(
        "--no-clean-edges",
        action="store_true",
        help="Skip the edge-cleaning step (disables halo removal + edge supersampling)",
    )
    parser.add_argument(
        "--alpha-threshold",
        type=int,
        default=180,
        help="Alpha cutoff (0-255) used to remove soft/halo edges from a transparent background (default: 180)",
    )
    parser.add_argument(
        "--supersample",
        type=int,
        default=4,
        help="Factor to supersample edges before tracing, for sharp-but-smooth curves (default: 4)",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        sys.exit(f"Input file not found: {args.input}")

    working_input = args.input
    if args.remove_bg:
        working_input = remove_background(working_input)
    if not args.no_clean_edges:
        working_input = clean_edges(working_input, args.alpha_threshold, args.supersample)
    working_input = upscale_image(working_input, args.upscale)
    vectorize(working_input, args.output, args.backend, args.colors)
    print(f"Done. Vector output written to: {args.output}")


if __name__ == "__main__":
    main()
