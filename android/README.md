# Keepr for Android

The Android version of Keepr: native Kotlin and Jetpack Compose, built to look and behave
like the iPhone app. The iOS app in the repository root is the reference for both — its
`Domain/` logic and `KeeprTests/` define correct behavior, and its screenshots define the
look.

Everything Android lives in this folder. Nothing here touches the iOS project.

## Status: Phase 0 — foundation

The project builds, installs and launches to a placeholder screen in light and dark
appearance. No Keepr features yet.

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
2. **Emulator test** — boots Android 8.0 and a current Android, runs the on-device tests,
   and takes real screenshots in light and dark.

To get the results: **github.com/chrisallenclark/Keepr → Actions → Android → a run →
Artifacts**:

* `keepr-debug-apk` — the app. Unzip, then open the `.apk` on an Android phone (allow
  "install unknown apps" when asked).
* `emulator-screenshots-api26`, `emulator-screenshots-api35` — what the app looks like.
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
