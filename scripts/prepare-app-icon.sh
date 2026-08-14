#!/bin/bash
# Points the asset catalog at the app icon, if one has been added.
#
# The icon is a binary asset the repo doesn't carry a placeholder for: an app
# icon is brand, and a stand-in that looks almost right is worse than an obvious
# gap. So AppIcon.appiconset ships with an empty slot — which builds fine — and
# this script fills in the filename once a real AppIcon-1024.png is present.
#
# Drop the file at Keepr/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
# (1024x1024, PNG, no alpha channel) and every build picks it up from then on.
set -euo pipefail

ICON_SET="Keepr/Assets.xcassets/AppIcon.appiconset"
ICON_FILE="AppIcon-1024.png"

if [ ! -f "${ICON_SET}/${ICON_FILE}" ]; then
  echo "No ${ICON_FILE} present — building without an app icon."
  exit 0
fi

python3 - "$ICON_SET" "$ICON_FILE" <<'PY'
import json
import sys

icon_set, icon_file = sys.argv[1], sys.argv[2]
path = f"{icon_set}/Contents.json"

with open(path) as file:
    catalog = json.load(file)

for image in catalog.get("images", []):
    image["filename"] = icon_file

with open(path, "w") as file:
    json.dump(catalog, file, indent=2)
    file.write("\n")

print(f"Wired {icon_file} into {path}")
PY
