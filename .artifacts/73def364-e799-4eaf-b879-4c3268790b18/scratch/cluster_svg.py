import xml.etree.ElementTree as ET
import re
from collections import defaultdict

svg_path = "app/src/main/ic_atmini-vector.svg"
tree = ET.parse(svg_path)
root = tree.getroot()

translates = []
for path in root.findall("{http://www.w3.org/2000/svg}path"):
    transform = path.get("transform", "")
    match = re.search(r"translate\(([\d\.\-]+),\s*([\d\.\-]+)\)", transform)
    if match:
        x, y = float(match.group(1)), float(match.group(2))
        translates.append((x, y))
    else:
        translates.append((0.0, 0.0))

xs = [t[0] for t in translates]
ys = [t[1] for t in translates]

print(f"X range: {min(xs)} to {max(xs)}")
print(f"Y range: {min(ys)} to {max(ys)}")

# Cluster by translate ranges
# Let's count unique translate values
from collections import Counter
trans_counts = Counter(translates)
print("Top translate groups:")
for t, count in trans_counts.most_common(15):
    print(f"  translate{t}: {count} paths")
