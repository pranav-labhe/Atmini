import xml.etree.ElementTree as ET
import re
import os

svg_path = "app/src/main/ic_atmini-vector.svg"
tree = ET.parse(svg_path)
root = tree.getroot()
ET.register_namespace('', "http://www.w3.org/2000/svg")

path_list = root.findall("{http://www.w3.org/2000/svg}path")
ys = []
for path in path_list:
    transform = path.get("transform", "")
    match = re.search(r"translate\(([\d\.\-]+),\s*([\d\.\-]+)\)", transform)
    if match:
        ys.append(float(match.group(2)))
    else:
        ys.append(0.0)

min_y, max_y = min(ys), max(ys)
num_slices = 10
slice_height = (max_y - min_y) / num_slices

slices = [[] for _ in range(num_slices)]
for path, y in zip(path_list, ys):
    idx = int((y - min_y) / slice_height)
    if idx >= num_slices:
        idx = num_slices - 1
    slices[idx].append(path)

width = root.get("width", "2048")
height = root.get("height", "2048")

os.makedirs("app/src/main/res/drawable", exist_ok=True)

for i, paths in enumerate(slices):
    new_svg = ET.Element("svg", {
        "version": "1.1",
        "xmlns": "http://www.w3.org/2000/svg",
        "width": width,
        "height": height
    })
    for p in paths:
        new_svg.append(p)
    out_path = f"app/src/main/res/drawable/atmini_slice_{i}.svg"
    ET.ElementTree(new_svg).write(out_path, encoding="utf-8", xml_declaration=True)
    size_kb = os.path.getsize(out_path) / 1024
    print(f"Slice {i}: {len(paths)} paths, {size_kb:.2f} KB -> {out_path}")
