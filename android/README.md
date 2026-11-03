# Keepr for Android

The Android version of Keepr: native Kotlin and Jetpack Compose, built to look and behave
like the iPhone app. The iOS app in the repository root is the reference for both — its
`Domain/` logic and `KeeprTests/` define correct behavior, and its screenshots define the
look.

Everything Android lives in this folder. Nothing here touches the iOS project.

## Status: Phase 2 — working app on local data

The four tabs, profiles and editors run on real, saved data:

* **Today** — needs attention, waiting on a reply, time to reach out, upcoming, going quiet, recent; swipe to complete, snooze, mark reached out or replied.
* **People** — Business/Personal, search, type and group chips with live counts, five sorts, A–Z index, swipe to favorite, long-press to message/call/email, select mode with bulk changes.
* **Follow Up** — overdue / today / this week / later / completed; complete, reopen, snooze, edit, delete.
* **Search** — names, companies, types, memories, interactions, notes, files and follow-ups, grouped by why each person matched.
* **Profiles** — add, edit and delete people; log interactions with facts to remember and a follow-up; add follow-ups, memories, groups and what they do; rhythm ("reach out every…"); connections and details.

Data is stored with Room on the phone only and survives restarts. Screenshot runs use a
separate in-memory database with the sample relationships, never the user's own.

Not yet on Android (next phases): Quick Capture, Brain Dump, conversation notes and files,
contact import and Catch Up, reminders, the group/type/link editors, onboarding and the rest of
Settings.

### Code layout

| | |
|---|---|
| `core/` | Plain Kotlin, no Android: the nine records, all business logic, and the iOS test suite translated test for test (326 tests, plus date-detector tests). Builds and tests anywhere: `gradle -p core test`. |
| `app/` | The Android app: Room database (`data/`), design system (`ui/components`, `ui/theme`), screens (`ui/screens`), on-device tests (`androidTest/`). |

### How the iOS look is reproduced

| | |
|---|---|
| Reference | The iOS app's own simulator screenshots (iOS 26), measured in points |
| Type | Inter / Inter Display (SIL OFL) in place of SF Pro, at the iOS Dynamic Type sizes |
| Color | Apple's semantic colors, plus values sampled from the iOS 26 screenshots |
| Symbols | 172 SF Symbols mapped to Material Symbols Rounded (Apache 2.0) — see `design/sf-symbols.tsv`; regenerate with `scripts/generate_icons.py` |
| Chrome | iOS 26 floating glass tab bar, round glass toolbar buttons, capsule segmented control and search field |
| Back | Android's system back gesture pops pushed screens and closes sheets |

Licenses for the bundled font and symbols are in `app/src/main/assets/licenses/`.

| | |
|---|---|
| Application ID | `com.chrisallenclark.keepr` |
| Minimum Android | 8.0 (API 26) |
| Target | API 36 |
| Language / UI | Kotlin, Jetpack Compose |
| Network | None — the app requests no internet permission |
| Backup | Android cloud backup and device transfer are excluded, matching iOS's local-only stance |

## Building without Android Studio

`.github/workflows/android.yml` runs on GitHub's Linux machines whenever anything under
`android/` changes:

1. **Build, unit test and lint** — produces the installable APK.
2. **Emulator** — boots a small 360dp phone on Android 8.0 and a 412dp Pixel 6 on Android 15,
   runs the on-device tests, and screenshots every screen and gallery page in light and dark.
3. **Compare with iOS** — fetches the latest iOS screenshots and puts each Android screen
   beside its iOS counterpart.

To get the results: **github.com/chrisallenclark/Keepr → Actions → Android → a run →
Artifacts**:

* `keepr-debug-apk` — the app. Unzip, then open the `.apk` on an Android phone (allow
  "install unknown apps" when asked).
* `ios-android-comparison` — iOS and Android side by side (`pixel6-today-business-light.png`
  and so on), plus each iOS reference on its own.
* `emulator-screenshots-small`, `emulator-screenshots-pixel6` — every Android capture.
* `build-reports` — test and lint results.

A run can also be started by hand from the same page with **Run workflow**.

Debug APKs are signed with a throwaway key generated on each run, so installing a newer one
may require uninstalling the old one first. Proper signing arrives with the test
distribution phase.

## Signing and secrets

No key, password or credential is ever committed (`.gitignore` blocks `*.jks`,
`*.keystore`, `keystore.properties` and `google-services.json`). Release signing is read
only from environment variables that CI fills from GitHub secrets:

`KEEPR_KEYSTORE_PATH`, `KEEPR_KEYSTORE_PASSWORD`, `KEEPR_KEY_ALIAS`, `KEEPR_KEY_PASSWORD`

Without them, release builds are produced unsigned.

## Building locally (optional)

Needs JDK 17 and the Android SDK. From this folder:

```bash
./gradlew assembleDebug testDebugUnitTest
```
