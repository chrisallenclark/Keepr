# Reminders on Android

Keepr's reminders are local notifications: follow-ups ("Priya Raman — Send the
proposal") and rhythm nudges ("3 people to reach out to"). The logic that
decides *what* to remind and *when* is shared with iOS and lives in `core`
(`FollowUpScheduler`, `CadenceNotificationScheduler`). This page is about how
the Android app delivers them, and where Android behaves differently from iOS.

## How it works

iOS hands each reminder to `UNUserNotificationCenter` with a calendar trigger
and the system does the rest. Android has no equivalent, so
`notifications/AndroidNotificationService.kt` implements the same
`NotificationScheduling` contract with three pieces:

| Piece | What it does |
|---|---|
| Pending list | Every scheduled reminder (identifier, title, body, fire date) as JSON in the app-private SharedPreferences file `keepr.reminders`. This is the Android `pendingNotificationRequests()`, and it is what survives reboots. Not in the Room database: it is delivery bookkeeping, not user data. |
| One alarm per reminder | `AlarmManager` `RTC_WAKEUP` alarm whose PendingIntent targets `ReminderReceiver` (not exported). The intent's data URI is `keepr-reminder:<identifier>`, so each identifier has its own alarm and scheduling the same identifier again replaces it. |
| Re-arming | `RescheduleReceiver` sets every pending alarm again on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. `MainActivity` also re-arms on every launch. |

When an alarm fires, `ReminderReceiver` looks the identifier up in the pending
list, removes it, and posts a notification on the **Reminders** channel
(default importance: sound, no heads-up), tagged with the identifier. Tapping it
opens Keepr. A follow-up reminder opens that person's profile on the Follow Up
tab; a rhythm reminder opens Today. iOS just opens the app.

Matching iOS:

- Scheduling an identifier cancels the old one first. Past fire dates are
  ignored, and nothing is scheduled while notifications aren't allowed.
- `cancel(identifier)` removes the pending reminder and a delivered one.
  `cancelReminders(withPrefix:)` and `cancelAll()` remove pending ones only.
- Rhythm reminders are rebuilt from scratch whenever Today appears, as on iOS,
  and also on every launch and every return to the foreground.
- Permission is asked for only when the user turns **Follow-up reminders** on
  in Settings, never at launch.

Android additions:

- Launch and foreground also re-schedule every open follow-up whose reminder is
  still ahead. A follow-up saved while notifications were off is then picked up
  once they're allowed. Turning reminders back on in Settings does the same,
  instead of leaving the earlier ones cancelled.
- A reminder that came due while its alarm was gone (phone off, app
  force-stopped) is delivered when alarms are re-armed. If it is more than 24
  hours stale it's dropped instead, so a phone switched off for a week doesn't
  produce a pile of old nudges.

## Permission states

`authorizationStatus()` maps to iOS's `UNAuthorizationStatus`:

| Android | Status |
|---|---|
| Notifications enabled for the app (`areNotificationsEnabled()`) | `AUTHORIZED` |
| Android 13+, `POST_NOTIFICATIONS` not granted, and Keepr has never asked | `NOT_DETERMINED` |
| Anything else: refused on 13+, or switched off in system Settings on any version | `DENIED` |

Android doesn't report whether an app has ever asked, so the service records
its first request (`meta.askedForPermission` in `keepr.reminders`). Below
Android 13 there is no prompt: notifications start on, and the user can only
turn them off in system Settings. After two refusals on 13+, Android stops
showing the prompt. `requestAuthorization()` then returns `false` straight
away, and Settings shows **Notifications are off in Android Settings**, which
links to the app's notification settings.

## Limitations (the honest list)

**Force-stop.** *Settings → Apps → Keepr → Force stop* (and some task-killer
apps, and `adb shell am force-stop`) cancels all of Keepr's alarms and puts the
app in the "stopped" state. A stopped app gets no broadcasts at all, not even
`BOOT_COMPLETED`, until the user opens it again. Keepr can't change this. When
Keepr is next opened it re-arms everything from the pending list, and reminders
that came due in the meantime are delivered then. Swiping Keepr out of Recents
is **not** a force-stop on stock Android, and reminders keep working.

**Inexact timing (Doze, exact alarms).** Keepr uses
`setExactAndAllowWhileIdle` when exact alarms are allowed and
`setAndAllowWhileIdle` otherwise:

- Android 8–11: exact alarms are always allowed.
- Android 12–13: `SCHEDULE_EXACT_ALARM` is granted at install, but the user
  can revoke it under *Alarms & reminders*.
- Android 14+: `SCHEDULE_EXACT_ALARM` is **denied by default** for new installs.
  Keepr never sends people to that settings page, because a 9 am nudge arriving
  at 9:07 is fine. `USE_EXACT_ALARM` (granted automatically) is deliberately not
  declared: Google Play allows it only for alarm-clock and calendar apps.

Inexact alarms can be delivered late. Outside Doze that's usually a few seconds
to minutes. In deep Doze (screen off, still, unplugged for a while), Android
batches "allow while idle" alarms into maintenance windows, which can delay a
reminder by several minutes or more. Exact while-idle alarms are also
rate-limited in Doze, though far less. App standby buckets can delay apps that
haven't been opened in a long time.

**OEM battery management.** Some manufacturers (Xiaomi/MIUI, Huawei/EMUI,
OnePlus, Oppo/ColorOS, Vivo, some Samsung settings such as "Put unused apps to
sleep"/"Deep sleeping apps") kill or freeze background apps hard enough that
alarms are delayed or never delivered, and some treat swipe-from-Recents as a
force-stop. Users on those phones may need to exempt Keepr from battery
optimization or allow it to "auto-start". Keepr doesn't ask for
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, because Play restricts it and reminders
don't justify it. See <https://dontkillmyapp.com>.

**Direct Boot.** The pending list is in credential-encrypted storage, so
re-arming waits for `BOOT_COMPLETED`, which arrives after the first unlock.
Reminders due between a reboot and the first unlock are delivered at unlock.

**Time zones.** A reminder stores an absolute moment. iOS stores calendar
components, so its 9 am follow-up stays 9 am after travel. On Android, rhythm
reminders are rebuilt at local 9 am the next time the app is opened. A follow-up
reminder keeps its original moment until the follow-up is edited or the app
reschedules it on launch.

**Backup.** `allowBackup="false"`: neither the data nor the pending list moves
to a new phone.

## Testing

- `app/src/androidTest/.../NotificationsTest.kt` (runs in every emulator job):
  grants `POST_NOTIFICATIONS` on 13+, schedules reminders a few seconds out
  with no activity running, and checks `NotificationManager.activeNotifications`.
  It covers posting, content, cancel (pending and delivered), replacement by
  identifier, prefix cancel, past dates, the persisted pending list, and
  re-arming a missed reminder exactly once.
- `scripts/emulator-smoke.sh` (API 26 and API 35 emulators) uses the debug-only
  `DebugReminderReceiver` (`src/debug`, guarded by the shell-only `DUMP`
  permission) to schedule from adb. An instrumentation run can't be used here,
  because starting one force-stops the target app and cancels its alarms. The
  script checks posted notifications with `dumpsys notification --noredact` and
  prints `PASS:`/`FAIL:` lines. Any FAIL fails the job.
  - **(a)** App closed (HOME) and its process killed with `am kill`: the reminder fires.
  - **(b)** `am force-stop`: the reminder is held back (documented Android
    behavior, reported but not failed if a version differs). Reopening the app
    re-arms it, and it fires.
  - **(c)** `adb reboot`, wait for `sys.boot_completed`, don't open the app:
    `BOOT_COMPLETED` re-arms it, and it fires. This check runs last, after the
    screenshots.

Schedule one by hand on a debug build:

```sh
adb shell am broadcast -n com.aulorvy.keepr/.notifications.DebugReminderReceiver \
  -a com.aulorvy.keepr.debug.SCHEDULE --es id manual-1 --es title Hello --ei seconds 30
adb shell am broadcast -n com.aulorvy.keepr/.notifications.DebugReminderReceiver \
  -a com.aulorvy.keepr.debug.STATUS      # status=AUTHORIZED exact=false pending=manual-1
adb shell appops set com.aulorvy.keepr SCHEDULE_EXACT_ALARM allow   # try the exact path (12+)
```
