#!/bin/bash
# Points the asset catalog at the app icon, and normalizes it if needed.
#
# The icon is a binary asset the repo doesn't carry a placeholder for: an app
# icon is brand, and a stand-in that looks almost right is worse than an obvious
# gap. So AppIcon.appiconset ships with an empty slot — which builds fine — and
# this script fills in the filename once a real icon is present.
#
# It is deliberately forgiving about how the icon arrives, because the usual
# route is uploading a photo from an iPhone, which:
#   - names the file something like 4CBE4CAC-A922-...png, and
#   - hands over whatever pixel size the source image happened to be.
# So any stray PNG in the icon set is adopted, and resized to the 1024x1024
# Apple requires. Alpha is reported but not silently altered — flattening
# someone's artwork against a guessed background is not this script's call.
set -euo pipefail

ICON_SET="Keepr/Assets.xcassets/AppIcon.appiconset"
ICON_FILE="AppIcon-1024.png"
ICON_PATH="${ICON_SET}/${ICON_FILE}"

# Adopt a differently-named upload, as long as there's exactly one candidate.
if [ ! -f "$ICON_PATH" ]; then
  candidates=()
  while IFS= read -r found; do
    candidates+=("$found")
  done < <(find "$ICON_SET" -maxdepth 1 -type f -iname "*.png" 2>/dev/null)

  case "${#candidates[@]}" in
    0)
      echo "No icon present — building without one."
      exit 0
      ;;
    1)
      echo "Adopting ${candidates[0]} as ${ICON_FILE}"
      mv "${candidates[0]}" "$ICON_PATH"
      ;;
    *)
      echo "::warning::Several PNGs in ${ICON_SET}; expected one named ${ICON_FILE}. Building without an icon."
      exit 0
      ;;
  esac
fi

# Resize to exactly 1024x1024 if it isn't already. sips ships with macOS; on any
# other machine the check is skipped and CI catches a wrong size later.
if command -v sips >/dev/null 2>&1; then
  width=$(sips -g pixelWidth "$ICON_PATH" | awk '/pixelWidth/ {print $2}')
  height=$(sips -g pixelHeight "$ICON_PATH" | awk '/pixelHeight/ {print $2}')

  if [ "$width" != "1024" ] || [ "$height" != "1024" ]; then
    echo "Icon is ${width}x${height}; resizing to 1024x1024"
    sips -z 1024 1024 "$ICON_PATH" --out "$ICON_PATH" >/dev/null
  fi

  if sips -g hasAlpha "$ICON_PATH" | grep -q "hasAlpha: yes"; then
    echo "::warning::${ICON_FILE} has an alpha channel. App Store Connect rejects transparent marketing icons — re-export it without one."
  fi
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
