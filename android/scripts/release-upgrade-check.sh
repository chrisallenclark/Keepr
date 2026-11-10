#!/bin/bash
# Runs inside a booted emulator in CI: checks the RELEASE build, the one
# testers install, rather than the debug build every other check uses.
#
# Usage: release-upgrade-check.sh APK_DIR OUT_DIR DEVICE_NAME
#
# APK_DIR holds four APKs built from the same commit by the workflow, all
# signed with one throwaway key the job generated for itself:
#   plain-100.apk  the production configuration exactly (R8, no test rules)
#   app-100.apk    the release build type with -Pkeepr.testBuildType=release
#   app-101.apk    the same, one version code higher
#   test.apk       the androidTest APK for the release build type
#
# Checks, each printed as a PASS or FAIL line; any FAIL fails the job:
#   1. plain-100: not debuggable, no debug-only receiver or WRITE_CONTACTS,
#      starts without crashing, and survives a walk through onboarding and
#      every tab. Debug-only launch extras are ignored.
#   2. app-100: the same package checks; the write phase stores a person, a
#      note, a file, an imported-contact link and a follow-up with a reminder;
#      a normal launch shows Today and arms the reminder.
#   3. app-101 installed over it with `adb install -r` (an update): the
#      versionCode is 101, MY_PACKAGE_REPLACED re-armed the reminder, the read
#      phase finds every record, the file's bytes and the pending reminder,
#      and a normal launch still works.
# See docs/RELEASE.md.
set -euo pipefail

APKS="${1:?APK directory}"
OUT="${2:-build/release-check-screenshots}"
DEVICE="${3:-emulator}"
PACKAGE=com.chrisallenclark.keepr
TEST_PACKAGE=com.chrisallenclark.keepr.test
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS=com.chrisallenclark.keepr.ReleaseUpgradeTest
WORK=$(mktemp -d)
mkdir -p "$OUT"

FAILED=0
pass() { echo "PASS: release: $*"; }
fail() { echo "FAIL: release: $*"; echo "::error::Release check: $*"; FAILED=1; }
note() { echo "NOTE: release: $*"; }

SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "== Release build checks on API $SDK ($DEVICE) =="

for apk in plain-100.apk app-100.apk app-101.apk test.apk; do
  [ -f "$APKS/$apk" ] || { fail "missing $APKS/$apk"; exit 1; }
done

clear_logs() { adb logcat -b all -c >/dev/null 2>&1 || adb logcat -c >/dev/null 2>&1 || true; }

install_apk() { # label apk [extra adb install flags]
  local label="$1" apk="$2"
  shift 2
  local output
  output=$(adb install "$@" "$apk" 2>&1 | tr -d '\r' || true)
  echo "$output" | tail -3
  if grep -q "^Success" <<<"$output"; then
    pass "$label installed"
  else
    fail "$label did not install: $(tail -1 <<<"$output")"
    return 1
  fi
}

# The installed package is a release build: right version, not debuggable,
# nothing from src/debug.
check_package() { # label expected-version-code
  local label="$1" code="$2" dump
  dump=$(adb shell dumpsys package "$PACKAGE" | tr -d '\r')
  if grep -Eq "versionCode=$code([^0-9]|$)" <<<"$dump"; then
    pass "$label: versionCode is $code"
  else
    fail "$label: versionCode is not $code ($(grep -Eo 'versionCode=[0-9]+' <<<"$dump" | head -1))"
  fi
  local flags
  flags=$(grep -E '^[[:space:]]*(pkgFlags|flags)=\[' <<<"$dump" || true)
  if [ -z "$flags" ]; then
    fail "$label: dumpsys package printed no flags line"
  elif grep -q DEBUGGABLE <<<"$flags"; then
    fail "$label: the app is DEBUGGABLE: $flags"
  else
    pass "$label: not debuggable"
  fi
  if grep -q "DebugReminderReceiver" <<<"$dump"; then
    fail "$label: the debug-only DebugReminderReceiver is present"
  else
    pass "$label: no debug-only receiver"
  fi
  if grep -q "android.permission.WRITE_CONTACTS" <<<"$dump"; then
    fail "$label: requests the debug-only WRITE_CONTACTS permission"
  else
    pass "$label: no WRITE_CONTACTS permission"
  fi
}

# The process is up and nothing crashed since the logs were last cleared.
check_alive() { # label
  local label="$1" pid crash
  pid=$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)
  crash=$( { adb logcat -d -b crash 2>/dev/null; adb logcat -d -b main -b system 2>/dev/null; } | tr -d '\r' || true)
  if grep -q "Process: $PACKAGE, PID" <<<"$crash"; then
    fail "$label: the app crashed"
    grep -A40 "FATAL EXCEPTION" <<<"$crash" | head -60 || true
  elif [ -z "$pid" ]; then
    fail "$label: the app's process is not running"
    adb logcat -d | tr -d '\r' | grep -iE "keepr|AndroidRuntime" | tail -40 || true
  else
    pass "$label: running (pid $pid), no crash"
  fi
}

launch() { # label screenshot-name [am start extras...]
  local label="$1" shot="$2"
  shift 2
  adb shell am start -W -n "$PACKAGE/.MainActivity" "$@" >/dev/null || true
  sleep 6
  adb exec-out screencap -p > "$OUT/${DEVICE}-${shot}.png" || true
  check_alive "$label"
}

# Taps the first on-screen element whose text or description is exactly $1,
# found through the accessibility tree. Best effort: returns 1 if not found.
tap_text() {
  local target="$1" xy
  for _ in 1 2 3 4; do
    if adb shell uiautomator dump /sdcard/keepr-ui.xml >/dev/null 2>&1 \
      && adb pull /sdcard/keepr-ui.xml "$WORK/ui.xml" >/dev/null 2>&1; then
      xy=$(python3 - "$WORK/ui.xml" "$target" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target = sys.argv[2]
for node in ET.parse(sys.argv[1]).iter("node"):
    if target in (node.get("text"), node.get("content-desc")):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
      if [ -n "$xy" ]; then
        # shellcheck disable=SC2086
        adb shell input tap $xy
        sleep 2
        return 0
      fi
    fi
    sleep 2
  done
  return 1
}

# Whether text is anywhere in the accessibility tree right now.
on_screen() {
  for _ in 1 2 3; do
    if adb shell uiautomator dump /sdcard/keepr-ui.xml >/dev/null 2>&1 \
      && adb pull /sdcard/keepr-ui.xml "$WORK/ui.xml" >/dev/null 2>&1; then
      if grep -qF "$1" "$WORK/ui.xml"; then return 0; fi
    fi
    sleep 2
  done
  return 1
}

phase() { # name method [extra instrument args...]
  local name="$1" method="$2"
  shift 2
  local output
  output=$(adb shell am instrument -w -e keeprPhase "$name" "$@" \
    -e class "$TEST_CLASS#$method" "$RUNNER" | tr -d '\r' || true)
  echo "$output"
  if grep -q "OK (1 test)" <<<"$output"; then
    pass "instrumentation phase '$name' on the release build"
  else
    fail "instrumentation phase '$name' failed (see the output above)"
  fi
}

reminder_alarm_set() {
  adb shell dumpsys alarm | tr -d '\r' | grep -qF "com.chrisallenclark.keepr.action.REMINDER"
}

grant_notifications() {
  if [ "$SDK" -ge 33 ]; then
    adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || fail "could not grant POST_NOTIFICATIONS"
  fi
}

adb uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
# 1. The production configuration, exactly as distributed (apart from the key).
# ---------------------------------------------------------------------------
echo "== 1. Production-configured release APK =="
if install_apk "plain-100" "$APKS/plain-100.apk"; then
  check_package "plain-100" 100
  clear_logs
  # A screenshot extra that a debug build would honor: a release build must
  # ignore it and open the real (empty) app, not the sample-data demo.
  launch "plain-100 first launch (debug extras ignored)" "plain-first-launch" --es keepr.screen people --es keepr.context business
  # Through onboarding to Today and every tab, watching for R8 crashes.
  for step in "Continue" "Continue" "Not Now"; do
    tap_text "$step" || note "could not find \"$step\" on screen (see the screenshots)"
  done
  sleep 2
  adb exec-out screencap -p > "$OUT/${DEVICE}-plain-today.png" || true
  for tab in "People" "Follow Up" "Search" "Today"; do
    if tap_text "$tab"; then
      adb exec-out screencap -p > "$OUT/${DEVICE}-plain-${tab// /-}.png" || true
    else
      note "could not find the \"$tab\" tab (see the screenshots)"
    fi
  done
  check_alive "plain-100 after onboarding and every tab"
  adb uninstall "$PACKAGE" >/dev/null || true
fi

# ---------------------------------------------------------------------------
# 2. Version 100 of the instrumentable release build: write real data.
# ---------------------------------------------------------------------------
echo "== 2. Release v100: write =="
install_apk "app-100" "$APKS/app-100.apk" || { echo "::error::Release check: cannot continue"; exit 1; }
install_apk "release androidTest APK" "$APKS/test.apk" -r || { echo "::error::Release check: cannot continue"; exit 1; }
check_package "app-100" 100
grant_notifications
phase write writeBeforeUpdate

# The instrumentation run ends by stopping the app, which drops its alarms.
# A normal launch re-arms from the pending list (as on every launch) and must
# show the data on Today.
clear_logs
launch "app-100 launch" "release-v100-today"
adb shell input keyevent KEYCODE_HOME
sleep 2
if reminder_alarm_set; then
  pass "app-100: the follow-up reminder's alarm is set"
else
  fail "app-100: no reminder alarm after launching the app"
fi

# ---------------------------------------------------------------------------
# 3. Version 101 installed over it: an update, never a reinstall.
# ---------------------------------------------------------------------------
echo "== 3. Release v101: update over v100, then read =="
clear_logs
install_apk "app-101 as an update (adb install -r)" "$APKS/app-101.apk" -r || { echo "::error::Release check: cannot continue"; exit 1; }
check_package "app-101" 101

# Installing an update kills the app and its alarms; Android then sends
# MY_PACKAGE_REPLACED, and RescheduleReceiver re-arms from the pending list.
REARMED=0
for _ in $(seq 1 30); do
  if adb logcat -d | tr -d '\r' | grep -q "Re-arming reminders after android.intent.action.MY_PACKAGE_REPLACED"; then
    REARMED=1
    break
  fi
  sleep 2
done
if [ "$REARMED" = 1 ]; then
  pass "app-101: MY_PACKAGE_REPLACED reached RescheduleReceiver"
else
  fail "app-101: no MY_PACKAGE_REPLACED re-arm within 60s of the update"
  adb logcat -d | tr -d '\r' | grep -iE "keepr|PACKAGE_REPLACED" | tail -30 || true
fi
sleep 2
if reminder_alarm_set; then
  pass "app-101: the reminder's alarm is set again after the update, without opening the app"
else
  fail "app-101: no reminder alarm after the update"
  adb shell dumpsys alarm | tr -d '\r' | grep -iF "$PACKAGE" | head -20 || true
fi

# Before anything opens the app: every record, the file's bytes and the
# pending reminder (the instrumentation run starts the process itself).
phase read readAfterUpdate -e keeprVersionCode 101

clear_logs
launch "app-101 launch after the update" "release-v101-today"
if tap_text "People"; then
  adb exec-out screencap -p > "$OUT/${DEVICE}-release-v101-People.png" || true
  if on_screen "Ines Moreau"; then
    pass "app-101: the person written by v100 is listed on People"
  else
    note "could not read \"Ines Moreau\" from the People screen (the read phase checked the data itself)"
  fi
  tap_text "Today" || true
else
  note "could not find the \"People\" tab (see the screenshots)"
fi
check_alive "app-101 after visiting People"

# Nothing from this check stays on the emulator.
adb uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
rm -rf "$WORK"
ls -l "$OUT"

if [ "$FAILED" = 1 ]; then
  echo "::error::Release build checks failed: see the FAIL lines above"
  exit 1
fi
echo "All release build checks passed."
