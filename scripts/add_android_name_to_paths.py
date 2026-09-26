import xml.etree.ElementTree as ET
import sys
import os

def add_names_to_paths(xml_path: str, prefix: str = "path"):
    if not os.path.isfile(xml_path):
        print(f"File not found: {xml_path}")
        return
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    tree = ET.parse(xml_path)
    root = tree.getroot()
    count = 0
    for i, path_elem in enumerate(root.iter('path'), start=1):
        # Check if android:name attribute exists
        name_attr = '{http://schemas.android.com/apk/res/android}name'
        if name_attr not in path_elem.attrib:
            path_elem.set(name_attr, f"{prefix}_{i}")
            count += 1
    tree.write(xml_path, encoding='utf-8', xml_declaration=True)
    print(f"Added android:name to {count} <path> elements in {xml_path}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python add_android_name_to_paths.py <drawable_xml_path> [name_prefix]")
        sys.exit(1)
    xml_file = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else "path"
    add_names_to_paths(xml_file, prefix)
