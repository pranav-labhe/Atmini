import xml.etree.ElementTree as ET

svg_path = "app/src/main/ic_atmini-vector.svg"
tree = ET.parse(svg_path)
root = tree.getroot()
print("Root tag:", root.tag)
print("Number of children:", len(root))
for i, child in enumerate(root):
    print(f"Child {i}: {child.tag}, attrs={child.attrib}, num_sub={len(child)}")
