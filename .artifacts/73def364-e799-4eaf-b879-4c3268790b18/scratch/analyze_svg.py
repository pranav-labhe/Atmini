import xml.etree.ElementTree as ET
from collections import Counter

svg_path = "app/src/main/ic_atmini-vector.svg"
tree = ET.parse(svg_path)
root = tree.getroot()

fills = Counter()
for path in root.findall("{http://www.w3.org/2000/svg}path"):
    fill = path.get("fill", "none")
    fills[fill] += 1

print("Top fill colors:")
for color, count in fills.most_common(20):
    print(f"  {color}: {count}")
print("Total paths:", len(root.findall("{http://www.w3.org/2000/svg}path")))
