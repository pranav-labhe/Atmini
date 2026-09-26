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


def boost_shadow_contrast(input_path: str, strength: float = 2.0,
                           max_luminance: int = 80, falloff: int = 30) -> str:
    """
    Widen the numeric gap between very close dark tones (e.g. a dark-gray
    shadow vs. near-black) so the tracer's clustering can actually tell
    them apart.

    IMPORTANT: this uses a pure POINTWISE remap (same fixed formula applied
    identically to every pixel), NOT a locally-adaptive method like CLAHE.
    CLAHE computes a separate contrast curve per tile and blends between
    neighboring tiles' curves based on position -- that spatial blending
    can turn a single hard edge into a soft multi-step ramp across several
    pixels, which a vector tracer then renders as several thin bands that
    look "blurred". A pointwise remap cannot do that: if two neighboring
    pixels differ before the remap, they still differ after it (just
    spread further apart) -- no neighbor-to-neighbor blending occurs at
    all, so a hard edge stays exactly as hard as it was.

    This also only touches pixels below `max_luminance` (soft falloff
    blend zone above it, blended per-pixel by original value -- still no
    spatial/neighbor interpolation involved). Everything brighter is left
    byte-for-byte untouched.

    Requires: pip install opencv-python numpy
    """
    try:
        import cv2
        import numpy as np
    except ImportError:
        print("Skipping shadow contrast boost: install opencv-python + numpy "
              "(pip install opencv-python numpy)")
        return input_path

    from PIL import Image

    img = Image.open(input_path).convert("RGBA")
    arr = np.array(img)
    rgb = arr[:, :, :3]

    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
    l, a, b = cv2.split(lab)
    l_original = l.astype(np.float32)

    # Pure pointwise gamma remap: gamma < 1 stretches differences between
    # the DARKEST values apart (a power curve with exponent < 1 has a very
    # steep slope near zero), which is what's needed to separate two very
    # close near-black shades. gamma >= 1 would do the opposite --
    # compress near-black differences while expanding mid-range ones --
    # so we deliberately invert `strength` into an exponent below 1 here.
    gamma = 1.0 / max(1.0, strength)
    norm = np.clip(l_original / max(1, max_luminance), 0.0, 1.0)
    stretched = np.power(norm, gamma) * max_luminance
    l_boosted = stretched

    # Soft mask by ORIGINAL luminance value only (still pointwise, not
    # spatial): pixels darker than max_luminance are fully boosted, fading
    # to fully untouched by max_luminance + falloff.
    mask = np.clip((max_luminance + falloff - l_original) / max(1, falloff), 0.0, 1.0)
    l_final = (l_original * (1 - mask) + l_boosted * mask)
    l_final = np.clip(l_final, 0, 255).astype(np.uint8)

    touched_pct = 100.0 * np.count_nonzero(mask > 0.01) / mask.size
    print(f"Shadow boost affecting ~{touched_pct:.1f}% of pixels "
          f"(luminance < {max_luminance + falloff}); rest left untouched, "
          f"pointwise remap only (no spatial blending)")
    if touched_pct > 50:
        print(f"  WARNING: this is touching most of the image, not just true "
              f"shadows -- your source is dark overall. Try a much lower "
              f"--boost-shadows-max-luminance (e.g. 30-40) to target only the "
              f"real near-black boundary.")

    lab = cv2.merge((l_final, a, b))
    boosted_rgb = cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)
    arr[:, :, :3] = boosted_rgb

    out = Image.fromarray(arr, mode="RGBA")
    tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_shadow_boost.png")
    out.save(tmp_path)
    print(f"Boosted shadow contrast (pointwise gamma={gamma}, "
          f"max_luminance={max_luminance}) -> {tmp_path}")
    return tmp_path


def defringe(input_path: str, alpha_threshold: int = 180, dilate_px: int = 2) -> str:
    """
    Fix edge color contamination ("fringing"/"halo") left over from
    background removal. A semi-transparent edge pixel's RGB is often a
    genuine blend of subject-color + old-background-color (correct for
    alpha compositing) -- but once we hard-threshold alpha to opaque,
    that blended color becomes a visible wrong-colored ring, which the
    tracer then draws as its own spurious micro-layer.

    IMPORTANT: this only repaints pixels that were genuinely PARTIALLY
    transparent in the original alpha channel (truly blended by the
    matting process). It deliberately does NOT touch any pixel that was
    already fully opaque (alpha ~255), even if that pixel sits right at
    the silhouette edge -- otherwise a real, intentional rim-light or
    highlight along an edge gets mistaken for contamination and erased.

    Requires: pip install opencv-python numpy
    """
    try:
        import cv2
        import numpy as np
    except ImportError:
        print("Skipping defringe step: install opencv-python + numpy for this fix "
              "(pip install opencv-python numpy)")
        return input_path

    from PIL import Image

    img = Image.open(input_path).convert("RGBA")
    if "A" not in img.getbands():
        return input_path

    arr = np.array(img)
    alpha = arr[:, :, 3]

    # Only pixels that were genuinely blended (partial alpha) count as
    # contaminated -- NOT anything just geometrically close to the edge.
    # A pixel already at alpha=255 is a real, fully-opaque color (could be
    # a highlight/rim-light) and must be left untouched.
    genuinely_blended = (alpha > 5) & (alpha < 250)
    halo_band = (genuinely_blended.astype(np.uint8)) * 255

    if dilate_px > 0:
        kernel_d = np.ones((dilate_px * 2 + 1, dilate_px * 2 + 1), np.uint8)
        # Dilate only slightly, and re-intersect with "not fully opaque"
        # so we still never touch a genuinely opaque highlight pixel.
        halo_band = cv2.dilate(halo_band, kernel_d)
        halo_band = cv2.bitwise_and(halo_band, cv2.bitwise_not((alpha >= 250).astype(np.uint8) * 255))

    if cv2.countNonZero(halo_band) == 0:
        return input_path

    rgb = arr[:, :, :3].copy()
    repainted = cv2.inpaint(rgb, halo_band, 3, cv2.INPAINT_TELEA)
    # Only replace RGB where we actually decided to repaint; leave every
    # other pixel (including opaque highlights) byte-for-byte untouched.
    mask3 = (halo_band > 0)[:, :, None]
    arr[:, :, :3] = np.where(mask3, repainted, rgb)

    out = Image.fromarray(arr, mode="RGBA")
    tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_defringed.png")
    out.save(tmp_path)
    print(f"Defringed edge color contamination -> {tmp_path}")
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
    # Only resize/resample if explicitly requested (supersample > 1). No
    # resampling at all is the safest default: every resize is an
    # interpolation between neighboring pixels, which is exactly the kind
    # of operation that can dilute a thin bright line or blend a sharp
    # color boundary. vtracer's own mode="spline" already fits smooth
    # curves to whatever boundary it finds, so resizing purely "for
    # smoothness" is redundant risk for no real benefit.
    if supersample <= 1:
        tmp_path = os.path.join(tempfile.gettempdir(), "vectorize_clean_edges.png")
        img.save(tmp_path)
        print(f"No resampling applied (supersample=1) -> {tmp_path} ({img.size[0]}x{img.size[1]})")
        return tmp_path

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


def try_vtracer(input_path: str, output_path: str, colors: int, min_region_size: int = None) -> bool:
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
    layer_difference = max(0, round(32 / (1 + colors / 8)) - 1)
    # filter_speckle discards any color region smaller than this many pixels,
    # REGARDLESS of color_precision/layer_difference -- this is the actual
    # cause of "missing" color layers (small patches get silently dropped).
    # It's normally tied to --colors, but pushing color fidelity to the max
    # (layer_difference=0) also means every real pixel-level noise
    # fluctuation becomes its own tiny layer -- which looks "broken" up
    # close. --min-region-size lets you set an explicit noise floor
    # independently, without giving up shadow/color separation fidelity.
    filter_speckle = min_region_size if min_region_size is not None else max(1, round(16 / (1 + colors / 16)))
    # corner_threshold/length_threshold control curve smoothness WITHIN a
    # single shape's own outline -- safe to tighten for sharper detail.
    corner_threshold = max(15, round(60 / (1 + colors / 32)))
    length_threshold = max(1.0, round(4.0 / (1 + colors / 64), 2))
    # splice_threshold is different: it controls when the algorithm JOINS
    # separate nearby path fragments into one shape. Lowering it makes that
    # joining more aggressive, which can absorb/merge a small isolated
    # feature (an eyebrow, an eye highlight) into a neighboring larger
    # shape -- causing it to visually vanish as its own layer. Keeping this
    # fixed and conservative regardless of --colors.
    splice_threshold = 45
    print(f"  resolved -> color_precision={color_precision}, "
          f"layer_difference={layer_difference}, filter_speckle={filter_speckle}, "
          f"corner_threshold={corner_threshold}, length_threshold={length_threshold}, "
          f"splice_threshold={splice_threshold}")

    try:
        vtracer.convert_image_to_svg_py(
            input_path,
            output_path,
            colormode="color",
            hierarchical="stacked",
            mode="spline",
            filter_speckle=filter_speckle,
            color_precision=color_precision,
            layer_difference=layer_difference,
            corner_threshold=corner_threshold,
            length_threshold=length_threshold,
            max_iterations=15,
            splice_threshold=splice_threshold,
            path_precision=8,
        )
    except Exception as e:
        if layer_difference == 0:
            print(f"layer_difference=0 was rejected ({e}); retrying with 1...")
            vtracer.convert_image_to_svg_py(
                input_path,
                output_path,
                colormode="color",
                hierarchical="stacked",
                mode="spline",
                filter_speckle=filter_speckle,
                color_precision=color_precision,
                layer_difference=1,
                corner_threshold=corner_threshold,
                length_threshold=length_threshold,
                max_iterations=15,
                splice_threshold=splice_threshold,
                path_precision=8,
            )
        else:
            raise
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


def vectorize(input_path: str, output_path: str, backend: str, colors: int, min_region_size: int = None) -> None:
    if backend == "embed":
        try_embed(input_path, output_path)
        return
    if backend == "vtracer":
        if not try_vtracer(input_path, output_path, colors, min_region_size):
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
    if try_vtracer(input_path, output_path, colors, min_region_size):
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
        "--boost-shadows",
        action="store_true",
        help="Widen contrast between very close dark tones (e.g. dark-gray vs "
             "black) before tracing, so the tracer can tell them apart. This "
             "DELIBERATELY shifts color values slightly -- opt-in only.",
    )
    parser.add_argument(
        "--boost-shadows-strength",
        type=float,
        default=2.0,
        help="CLAHE clip limit for --boost-shadows; higher = stronger contrast push (default: 2.0)",
    )
    parser.add_argument(
        "--boost-shadows-max-luminance",
        type=int,
        default=80,
        help="Only pixels darker than this (0-255 LAB luminance) are affected by "
             "--boost-shadows; everything brighter is left completely untouched (default: 80)",
    )
    parser.add_argument(
        "--boost-shadows-falloff",
        type=int,
        default=30,
        help="Soft blend zone (in luminance levels) above --boost-shadows-max-luminance, "
             "to avoid a hard visible seam (default: 30)",
    )
    parser.add_argument(
        "--remove-bg",
        action="store_true",
        help="Remove the image background before vectorizing (requires: pip install rembg onnxruntime)",
    )
    parser.add_argument(
        "--no-defringe",
        action="store_true",
        help="Skip the edge color-decontamination step (disables halo/fringe fix after background removal)",
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
        default=1,
        help="Resize factor applied before tracing. Default 1 = no resampling at "
             "all (safest -- avoids any risk of interpolation blending/diluting "
             "colors). Only raise this if your source image's resolution is "
             "genuinely too low; vtracer's spline mode already produces smooth "
             "curves without it.",
    )
    parser.add_argument(
        "--min-region-size",
        type=int,
        default=None,
        help="Minimum pixel-region size to keep as its own layer (vtracer's "
             "filter_speckle), set independently of --colors. Raise this "
             "(e.g. 4-8) to discard genuine pixel-noise fragments causing a "
             "'broken pixels' look up close, without lowering --colors and "
             "losing shadow/color separation fidelity. Default: derived from --colors.",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        sys.exit(f"Input file not found: {args.input}")

    working_input = args.input
    if args.remove_bg:
        working_input = remove_background(working_input)
        if not args.no_defringe:
            working_input = defringe(working_input, args.alpha_threshold)
    if not args.no_clean_edges:
        working_input = clean_edges(working_input, args.alpha_threshold, args.supersample)
    working_input = upscale_image(working_input, args.upscale)
    if args.boost_shadows:
        working_input = boost_shadow_contrast(
            working_input, args.boost_shadows_strength,
            args.boost_shadows_max_luminance, args.boost_shadows_falloff,
        )
    vectorize(working_input, args.output, args.backend, args.colors, args.min_region_size)
    print(f"Done. Vector output written to: {args.output}")


if __name__ == "__main__":
    main()
