#!/bin/bash
# Runs inside a booted emulator in CI: instrumented tests, then real screenshots
# of the installed app, every listed screen in light and dark appearance.
#
# Usage: emulator-smoke.sh OUT_DIR DEVICE_NAME
# Screens are opened with debug-only launch extras (see LaunchOptions.kt), the
# same way the iOS screenshot job uses -KeeprScreen / -KeeprContext.
set -euo pipefail

OUT="${1:-build/emulator-screenshots}"
DEVICE="${2:-emulator}"
PACKAGE=com.chrisallenclark.keepr
mkdir -p "$OUT"

./gradlew --no-daemon connectedDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk

# Saved data must survive the process being killed, not only the activity
# closing: write in one instrumentation run, force-stop, read in another.
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RUNNER=com.chrisallenclark.keepr.test/androidx.test.runner.AndroidJUnitRunner
phase() {
  local name="$1" method="$2"
  local output
  output=$(adb shell am instrument -w -e keeprPhase "$name" \
    -e class "com.chrisallenclark.keepr.ProcessRestartTest#$method" "$RUNNER")
  echo "$output"
  echo "$output" | grep -q "OK (1 test)" || { echo "::error::Process-restart phase '$name' failed"; exit 1; }
}
adb shell pm clear "$PACKAGE" >/dev/null
phase write writeBeforeKill
adb shell am force-stop "$PACKAGE"
phase read readAfterKill
echo "Data survived a forced stop of the app process."
adb shell pm clear "$PACKAGE" >/dev/null

# A clean, consistent status bar — 9:41, full battery and signal — matching
# the iOS screenshots (System UI demo mode, the Android twin of
# `simctl status_bar override`).
adb shell settings put global sysui_demo_allowed 1 || true
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null || true; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

# "<screen>:<context>" pairs. Names match the iOS screenshot job where an iOS
# counterpart exists, so the comparison step can pair them up.
SCREENS=(
  today:business
  people:business
  followUp:business
  search:business
  today:personal
  profile:business
  gallery-type:none
  gallery-color:none
  gallery-controls:none
  gallery-lists:none
  gallery-icons:none
  gallery-sheet:none
  gallery-menu:none
)

capture() {
  local screen="$1" context="$2" appearance="$3"
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$PACKAGE/.MainActivity" \
    --es keepr.screen "$screen" --es keepr.context "$context" >/dev/null
  sleep 3
  adb exec-out screencap -p > "$OUT/${DEVICE}-${screen}-${context}-${appearance}.png"
}

for appearance in light dark; do
  if [ "$appearance" = dark ]; then
    adb shell cmd uimode night yes || true
  else
    adb shell cmd uimode night no || true
  fi
  for entry in "${SCREENS[@]}"; do
    capture "${entry%%:*}" "${entry##*:}" "$appearance"
  done
done
adb shell cmd uimode night no || true

ls -l "$OUT"
