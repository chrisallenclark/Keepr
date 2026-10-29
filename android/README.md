# Keepr for Android

The Android version of Keepr: native Kotlin and Jetpack Compose, built to look and behave
like the iPhone app. The iOS app in the repository root is the reference for both — its
`Domain/` logic and `KeeprTests/` define correct behavior, and its screenshots define the
look.

Everything Android lives in this folder. Nothing here touches the iOS project.

## Status: Phase 1 — design system

The app opens to Keepr's four tabs drawn with the Android design system, using the iOS demo
content so they can be compared with the iPhone screenshots. The screens are a **design
preview**: nothing is saved yet, and buttons other than tabs, rows (open a profile), swipes and
the context switch do nothing. The data layer and real features arrive in the next phases.

Today's gear button opens the **Design Gallery**: typography, colors, controls, lists and
swipe actions, every symbol, a sheet and a menu.

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
