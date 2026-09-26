import xml.etree.ElementTree as ET
import os

svg_path = "app/src/main/res/drawable/clean_atmini.svg"
size_kb = os.path.getsize(svg_path) / 1024
tree = ET.parse(svg_path)
root = tree.getroot()
paths = root.findall("{http://www.w3.org/2000/svg}path")
print(f"File size: {size_kb:.2f} KB")
print(f"Number of paths: {len(paths)}")

# Check fill colors
fills = set(p.get("fill") for p in paths)
print(f"Fill colors used: {fills}")
