# Keepr for Android

The Android version of Keepr: native Kotlin and Jetpack Compose, built to look and behave
like the iPhone app. The iOS app in the repository root is the reference for both — its
`Domain/` logic and `KeeprTests/` define correct behavior, and its screenshots define the
look.

Everything Android lives in this folder. Nothing here touches the iOS project.

## Status: Phase 3 — iOS feature parity

Everything the iPhone app does, on local data:

* **Today** — needs attention, waiting on a reply, time to reach out, upcoming, going quiet, recent, and the Catch Up row for new or unsorted contacts.
* **People** — Business/Personal, search, type and group chips with live counts, five sorts, A–Z index, swipe to favorite, long-press actions, select mode with bulk changes and "Link These Two".
* **Follow Up** and **Search** — as in Phase 2.
* **Quick Capture** — write what happened; Keepr works out who it's about, what to remember and what to follow up on, and you review before saving.
* **Brain Dump** — paste or type everything you know about someone; keep the facts that matter, sorted into categories.
* **Profiles** — notes (write, edit, pin, pull facts, delete), files (photos from the camera or library, PDFs and any document; preview, rename, share, delete), the full memory library, groups, connections, rhythm, details.
* **Contacts** — import from the phone's address book with suggestions and no duplicates; Catch Up to accept or categorize new contacts.
* **Organizing** — create and edit groups (with your own word for them), relationship types and symbols, and links between people.
* **Reminders** — local notifications for follow-ups and rhythms; they survive the app closing and a reboot. See `docs/NOTIFICATIONS.md` for Android's limits.
* **Onboarding and Settings** — the iOS first-run pages and every Settings section.

Data is stored with Room on the phone only (file contents in app-private storage) and survives
restarts. Screenshot runs use a separate in-memory database with the sample relationships,
never the user's own.

### Code layout

| | |
|---|---|
| `core/` | Plain Kotlin, no Android: the nine records, all business logic, and the iOS test suite translated test for test (326 tests) plus Android-side tests for capture, notes/files, contacts and organizing (403 in all). Builds and tests anywhere: `gradle -p core test`. |
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
3. **Release build** — builds the release APK testers get (R8-minified, not debuggable),
   signed with a throwaway key made inside the job, on Android 8.0 and 15: checks it's not
   debuggable and has nothing debug-only, installs version 100, writes data, updates to 101
   with `adb install -r`, and checks every record, file and reminder survived. See
   `docs/RELEASE.md`. No signed APK is ever uploaded.
4. **Compare with iOS** — fetches the latest iOS screenshots and puts each Android screen
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
may require uninstalling the old one first. Testers get release builds instead, signed with
the permanent key and delivered by Firebase App Distribution: see **`docs/RELEASE.md`**.

## Signing and secrets

No key, password or credential is ever committed (`.gitignore` blocks `*.jks`,
`*.keystore`, `keystore.properties` and `google-services.json`). Release signing is read
only from environment variables that CI fills from GitHub secrets:

`KEEPR_KEYSTORE_PATH`, `KEEPR_KEYSTORE_PASSWORD`, `KEEPR_KEY_ALIAS`, `KEEPR_KEY_PASSWORD`

Without them, release builds are produced unsigned (the release workflow passes
`-Pkeepr.requireReleaseSigning=true`, which makes that an error instead). The real key is
used only by `.github/workflows/android-release.yml`, started by hand and approved through
the `android-release` environment; it builds, verifies and (if asked) sends the APK to
Firebase App Distribution, and never uploads it as an artifact. Setup for all of this,
written for non-developers: `docs/RELEASE.md`.

| Gradle input | Where | Default |
|---|---|---|
| `keepr.versionName` | `gradle.properties` (the only place the version name is set) | — |
| `KEEPR_VERSION_CODE` | environment; the release workflow sets 1000 + run number | `1` |
| `keepr.testBuildType` | `-P` property: `debug` or `release` (CI release check only) | `debug` |
| `keepr.requireReleaseSigning` | `-P` property: fail if the signing variables are missing | `false` |

Release builds hide Settings → Development (Sample data, Design Gallery), which iOS always
shows.

## Building locally (optional)

Needs JDK 17 and the Android SDK. From this folder:

```bash
./gradlew assembleDebug testDebugUnitTest
```
