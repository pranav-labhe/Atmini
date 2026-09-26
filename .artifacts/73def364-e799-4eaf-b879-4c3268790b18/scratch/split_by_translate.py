import xml.etree.ElementTree as ET
import re
import os

svg_path = "app/src/main/ic_atmini-vector.svg"
tree = ET.parse(svg_path)
root = tree.getroot()

# Register namespace if any
ET.register_namespace('', "http://www.w3.org/2000/svg")

# Collect paths and their translate y values
paths_by_region = {
    "head": [],
    "chest": [],
    "arms_legs": [],
    "body": []
}

for path in root.findall("{http://www.w3.org/2000/svg}path"):
    transform = path.get("transform", "")
    match = re.search(r"translate\(([\d\.\-]+),\s*([\d\.\-]+)\)", transform)
    if match:
        x, y = float(match.group(1)), float(match.group(2))
        # Categorize based on Y coordinate (top, middle, bottom) and X
        if y < 400:
            paths_by_region["head"].append(path)
        elif 400 <= y < 900:
            paths_by_region["chest"].append(path)
        elif y >= 900:
            paths_by_region["arms_legs"].append(path)
        else:
            paths_by_region["body"].append(path)
    else:
        paths_by_region["body"].append(path)

print("Split counts:")
for region, paths in paths_by_region.items():
    print(f"  {region}: {len(paths)} paths")

# Create output SVGs
width = root.get("width", "2048")
height = root.get("height", "2048")

os.makedirs("app/src/main/res/drawable", exist_ok=True)

for region, paths in paths_by_region.items():
    new_svg = ET.Element("svg", {
        "version": "1.1",
        "xmlns": "http://www.w3.org/2000/svg",
        "width": width,
        "height": height
    })
    for p in paths:
        new_svg.append(p)

    out_path = f"app/src/main/res/drawable/atmini_{region}.svg"
    ET.ElementTree(new_svg).write(out_path, encoding="utf-8", xml_declaration=True)
    print(f"Exported {out_path} ({len(paths)} paths)")
