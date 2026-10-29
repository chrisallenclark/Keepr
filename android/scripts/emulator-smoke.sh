#!/bin/bash
# Runs inside a booted emulator in CI: instrumented tests, then real screenshots
# of the installed app in light and dark appearance.
set -euo pipefail

OUT="${1:-build/emulator-screenshots}"
API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
mkdir -p "$OUT"

./gradlew --no-daemon connectedDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk

capture() {
  local name="$1"
  adb shell am force-stop com.chrisallenclark.keepr
  adb shell am start -W -n com.chrisallenclark.keepr/.MainActivity >/dev/null
  sleep 3
  adb exec-out screencap -p > "$OUT/api${API}-${name}.png"
}

adb shell cmd uimode night no || true
capture light
adb shell cmd uimode night yes || true
capture dark
adb shell cmd uimode night no || true

ls -l "$OUT"
