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

# A failing UI test shouldn't hide the screenshots; fail the job at the end.
TESTS_FAILED=0
./gradlew --no-daemon connectedDebugAndroidTest || TESTS_FAILED=1

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
  echo "$output" | grep -q "OK (1 test)" || { echo "::error::Process-restart phase '$name' failed"; TESTS_FAILED=1; }
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

# ---------------------------------------------------------------------------
# Reminders, with the app's process out of the picture. NotificationsTest
# covers delivery while the test process lives; this covers what only the
# shell can do: kill the process, force-stop the app, reboot the device.
# Reminders are scheduled through a debug-only receiver (DebugReminderReceiver,
# src/debug) because starting an instrumentation run force-stops the app,
# which would cancel the very alarm under test. See docs/NOTIFICATIONS.md.
# The reboot runs last, after the screenshots, so nothing else depends on it.
# ---------------------------------------------------------------------------
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
DEBUG_RECEIVER="$PACKAGE/.notifications.DebugReminderReceiver"
RUN_ID=$(date +%s)
REMINDERS_FAILED=0

pass() { echo "PASS: reminders: $*"; }
fail() { echo "FAIL: reminders: $*"; echo "::error::Reminders: $*"; REMINDERS_FAILED=1; }

reminder_cmd() { # action id [seconds]
  adb shell am broadcast -n "$DEBUG_RECEIVER" -a "com.chrisallenclark.keepr.debug.$1" \
    --es id "$2" --es title "Keepr-$2" --ei seconds "${3:-0}" | tr -d '\r' | grep -o 'data="[^"]*"' || true
}

# A posted notification's key is "<user>|<package>|<id>|<tag>|<uid>"; the tag
# is the reminder id, and every Keepr reminder uses notification id 1.
# (Captured first: with pipefail, grep -q closing the pipe early would read as failure.)
is_posted() {
  local dump
  # --noredact shows the full record on newer versions; the key (with the
  # tag) is printed either way, so the plain dump is a fallback for API 26.
  dump=$( { adb shell dumpsys notification --noredact; adb shell dumpsys notification; } | tr -d '\r')
  grep -qF "|$PACKAGE|1|$1|" <<<"$dump"
}

wait_posted() { # id timeout-seconds
  local waited=0
  while [ "$waited" -lt "$2" ]; do
    if is_posted "$1"; then return 0; fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

# Opening the app clears Android's "stopped" state and re-arms pending
# reminders (MainActivity does both); HOME then leaves it in the background.
open_then_leave() {
  adb shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null
  sleep 3
  adb shell input keyevent KEYCODE_HOME
  sleep 1
}

echo "== Reminders on API $SDK =="
if [ "$SDK" -ge 33 ]; then
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || fail "could not grant POST_NOTIFICATIONS"
fi
open_then_leave
adb shell am set-standby-bucket "$PACKAGE" active 2>/dev/null || true
echo "Reminder service: $(reminder_cmd STATUS none)"

# (a) The app is closed and its process killed, as Android does to any
# background app: the alarm belongs to the system, so the reminder still fires.
ID_CLOSED="smoke-closed-$RUN_ID"
echo "Scheduled: $(reminder_cmd SCHEDULE "$ID_CLOSED" 20)"
adb shell am kill "$PACKAGE" || true
sleep 2
if [ -n "$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')" ]; then
  echo "Note: the app process is still alive after 'am kill'; the activity is closed regardless."
fi
if wait_posted "$ID_CLOSED" 120; then
  pass "(a) fired with the app closed and its process killed"
else
  fail "(a) did not fire within 120s with the app closed"
fi

# (b) Force-stop is different: Android cancels the app's alarms and blocks its
# receivers until the user opens it again. Reopening re-arms from the pending
# list, and a reminder that came due meanwhile is delivered then.
ID_STOPPED="smoke-stopped-$RUN_ID"
open_then_leave
echo "Scheduled: $(reminder_cmd SCHEDULE "$ID_STOPPED" 15)"
adb shell am force-stop "$PACKAGE"
sleep 35
if is_posted "$ID_STOPPED"; then
  echo "Note: (b) the reminder fired despite the force-stop; this Android version kept the alarm."
else
  pass "(b) force-stop held the reminder back, as Android documents"
fi
adb shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null
if wait_posted "$ID_STOPPED" 60; then
  pass "(b) reopening the app after a force-stop re-armed the reminder and it fired"
else
  fail "(b) the reminder did not fire after reopening a force-stopped app"
fi
adb shell input keyevent KEYCODE_HOME

# (c) Reboot: alarms don't survive one; BOOT_COMPLETED re-arms them from the
# pending list without the app being opened. If boot outlasts the fire time,
# the re-arm delivers it as a missed reminder; either way it must post.
ID_BOOT="smoke-boot-$RUN_ID"
echo "Scheduled: $(reminder_cmd SCHEDULE "$ID_BOOT" 90)"
sleep 2
adb reboot
adb wait-for-device
BOOTED=0
for _ in $(seq 1 150); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then BOOTED=1; break; fi
  sleep 2
done
if [ "$BOOTED" != 1 ]; then
  fail "(c) the emulator did not finish rebooting within 300s"
else
  echo "Rebooted; waiting for BOOT_COMPLETED to reach the app (the app is not opened)."
  if wait_posted "$ID_BOOT" 240; then
    pass "(c) fired after a reboot, re-armed by the boot receiver"
  else
    echo "Alarms registered for the app:"
    adb shell dumpsys alarm | tr -d '\r' | grep -iF "$PACKAGE" | head -20 || true
    fail "(c) did not fire within 240s of the reboot"
  fi
fi

if [ "$REMINDERS_FAILED" = 1 ]; then
  echo "::error::Reminder delivery checks failed — see the FAIL lines above"
fi
if [ "$TESTS_FAILED" = 1 ]; then
  echo "::error::On-device tests failed — see emulator-test-reports"
fi
if [ "$TESTS_FAILED" = 1 ] || [ "$REMINDERS_FAILED" = 1 ]; then
  exit 1
fi
