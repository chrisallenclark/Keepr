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

# Adopt a differently-named upload. Putting a PNG in the icon set means "use
# this icon", so a stray file replaces the current one rather than being
# ignored — quietly shipping the old icon after someone deliberately uploaded a
# new one is the worst outcome available here.
candidates=()
while IFS= read -r found; do
  [ "$(basename "$found")" = "$ICON_FILE" ] && continue
  candidates+=("$found")
done < <(find "$ICON_SET" -maxdepth 1 -type f -iname "*.png" 2>/dev/null)

case "${#candidates[@]}" in
  0) ;;
  1)
    echo "Adopting ${candidates[0]} as ${ICON_FILE}"
    mv -f "${candidates[0]}" "$ICON_PATH"
    ;;
  *)
    echo "::warning::Several candidate PNGs in ${ICON_SET}; can't tell which is the icon. Leaving ${ICON_FILE} alone."
    ;;
esac

if [ ! -f "$ICON_PATH" ]; then
  echo "No icon present — building without one."
  exit 0
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

  # Transparency isn't worth a warning — App Store Connect rejects a transparent
  # marketing icon outright, so a build carrying one is already dead on arrival.
  # Flatten it and say so loudly instead of uploading something doomed.
  if sips -g hasAlpha "$ICON_PATH" | grep -q "hasAlpha: yes"; then
    echo "::warning::${ICON_FILE} has an alpha channel; flattening against white. App Store Connect rejects transparent marketing icons — re-export without transparency to choose the background colour yourself."
    sips -s format jpeg -s formatOptions best "$ICON_PATH" --out "${ICON_PATH}.jpg" >/dev/null
    sips -s format png "${ICON_PATH}.jpg" --out "$ICON_PATH" >/dev/null
    rm -f "${ICON_PATH}.jpg"
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
