This repo is Keepr, the iOS personal relationship and contact manager app (Swift; Firebase backend project keepr-contacts). It is NOT Macrova, Mealixr, Closr, Remli, Aulorvy.com, or Hybrid Performance.

Read `docs/AULORVY_PROFILE.md` first for owner context and never-do rules.

There is no `AGENTS.md` and no `.cursor/rules` in this repo. This file is the agent entry point. `docs/HANDOFF.md` is not in the tree; cloud sync behavior lives in [`docs/CLOUD_SYNC.md`](docs/CLOUD_SYNC.md) and the read API lives in [`API.md`](API.md). Link those files. Do not paste them into new docs.

## Purpose and audience

Keepr is a relationship operating system for one person's iPhone. Messages mixes clients, leads, friends, and family into one inbox. Keepr keeps a single record per person and splits every main screen with one switch: **Business** or **Personal**. A person can be both.

The phone owner is the audience. It is a personal tool, not a shared team CRM. Apple Contacts stay the source of names and numbers. Keepr stores a snapshot so the app still works offline and when Contacts access is denied. The timeline is only what the user logs. The app cannot read iMessage, Mail, or call history (`README.md`, Settings → "What Keepr can't do").

Until someone signs in, data stays on the device (SwiftData). Signing in (Settings → Cloud & Assistant) copies that person's records to their own Firebase account and can mint a revocable read-only token. One phone binds to the first account that signs in. Details: [`docs/CLOUD_SYNC.md`](docs/CLOUD_SYNC.md).

The second way of grouping people is a user-chosen noun (`GroupVocabulary`, default "Group"; presets include Business, Place, App). The product is not tied to one industry.

## Brand and UI rules

Native SwiftUI. No third-party UI kit and no custom font files. Typography is the system text styles (`.body`, `.subheadline`, `.footnote`, `.caption`, `.title3`, and so on) plus SF Symbols. `Keepr/DesignSystem/Theme.swift` says semantic system colors and system type are the look, so the app tracks light mode, dark mode, and Dynamic Type.

The only custom color is the asset-catalog accent, `Keepr/Assets.xcassets/AccentColor.colorset`:

| Appearance | sRGB (red, green, blue, alpha) |
|---|---|
| Any (light) | 0.129, 0.400, 0.400, 1 |
| Dark | 0.353, 0.702, 0.686, 1 |

`docs/APP_STORE_CHECKLIST.md` calculates that accent at about 6.6:1 on white and about 6.9:1 on the standard dark background. Treat the colorset as the source of truth if those ratios and the components ever disagree.

Shared layout tokens in `Theme`:

- Spacing: tight 4, small 8, medium 12, large 20, section 28
- Radius: card 14, chip 8
- Haptics (`Haptics`) only when they confirm an action (complete, save, switch context). Not decoration.

UI conventions that are actually in the app:

- Four tabs in `Keepr/App/RootView.swift`: Today (`sun.max`), People (`person.2`), Follow Up (`bell`), Search (`magnifyingglass`). Settings is a toolbar entry on Today, not a tab.
- The Business / Personal switch is a pinned segmented control in a top safe-area inset on Today, People, and Follow Up. It is stored in `@AppStorage("keepr.contextMode")`. Search ignores it on purpose.
- Shared views live in `Keepr/DesignSystem/` (avatar, cards, filter chips, section index, symbol picker). Screens live in `Keepr/Features/`, one folder per area.
- Tone is calm and plain. Today is quiet when there is nothing to do. Settings includes an honest "What Keepr can't do" page. Empty states use SF Symbols and short sentences. Do not add analytics, marketing chrome, or a splash screen. The launch screen is the generated system one (`INFOPLIST_KEY_UILaunchScreen_Generation = YES`).
- Portrait only. iPhone only (`TARGETED_DEVICE_FAMILY = 1`).

The marketing icon is `Keepr/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` (wired in `Contents.json`). `scripts/prepare-app-icon.sh` runs in CI and adopts a stray PNG upload as that 1024×1024 file. `docs/BUILDING_WITHOUT_A_MAC.md` and the checklist still say the icon slot is empty. That is stale: the PNG has been in the tree since 2026-08-14 (`3e88ba7`).

## Stack

Checked against `main` at `29554e5` (2026-11-24).

| Layer | What the repo uses |
|---|---|
| App language | Swift 5.0 (`SWIFT_VERSION = 5.0`). Xcode project created with tools 16.0 (`LastSwiftUpdateCheck = 1600`, `objectVersion = 77`). `README.md` requires Xcode 16 or later. |
| UI | SwiftUI. UIKit only for haptics and a few UIKit bridges. |
| Local store | SwiftData. Container in `Keepr/Persistence/KeeprStore.swift`. |
| Minimum OS | iOS 17.0 (`IPHONEOS_DEPLOYMENT_TARGET`). iOS 18 limited-contacts APIs are availability-gated. |
| Device | iPhone, portrait. Bundle id `com.chrisallenclark.Keepr`. Display name `Keepr`. Marketing version `0.1` (`MARKETING_VERSION`). Project build number `1` (`CURRENT_PROJECT_VERSION`); the TestFlight workflow replaces that with `github.run_number`. |
| iOS dependencies | None. `packageProductDependencies` is empty. No CocoaPods, no Swift packages, no Firebase iOS SDK. |
| Apple frameworks in use | SwiftUI, SwiftData, Contacts (`Keepr/Services/ContactsService.swift`), UserNotifications, Security (keychain), CryptoKit, OSLog, plus URL schemes `sms:`, `tel:`, `mailto:`. `docs/ARCHITECTURE.md` also names ContactsUI; current `Keepr/` sources do not import it. |
| Cloud client | `Keepr/Sync/` calls Google APIs with `URLSession`: Identity Toolkit (`identitytoolkit.googleapis.com`), secure token (`securetoken.googleapis.com`), Firestore REST, and Firebase Storage REST. Config is three fields in `Keepr/FirebaseOptions.plist` (`PROJECT_ID`, `API_KEY`, `STORAGE_BUCKET`). The session is stored in the keychain under service `com.chrisallenclark.Keepr.cloud`. |
| Tests (app) | Swift Testing (`import Testing`) in `KeeprTests/`. The scheme's Test action includes that target. Source count on this tree: 341 `@Test` attributes and 33 `@Suite` types across 25 files. That count was not executed here. |
| Backend | Firebase project **keepr-contacts**. Auth (email/password), Cloud Firestore, Cloud Storage, and one Cloud Function `api`. Config in `firebase/firebase.json` (functions runtime `nodejs22`). |
| Functions | `firebase/functions/`. Node 22 (`"engines": { "node": "22" }`). Lockfile: `firebase-admin` 13.10.0, `firebase-functions` 6.6.0, dev `firebase` 11.10.0, `firebase-tools` 14.27.0, `@firebase/rules-unit-testing` 4.0.1. `src/index.js` exports `api` as a v2 `onRequest` (256MiB, 60s, max 20 instances, `invoker: "public"`, CORS off). The source does not set a region. |
| CI | GitHub Actions on GitHub-hosted runners. See below. |

On 2026-11-25, `GET https://us-central1-keepr-contacts.cloudfunctions.net/api/v1/health` returned HTTP 200 `{"ok":true,"service":"keepr-api"}`. That matches the base URL in `docs/CLOUD_SYNC.md`.

## Build, run, and test commands

These are the commands the repo actually documents or runs. There is no Makefile, no Fastlane lane, and no Android Gradle build on `main`.

On a Mac (`README.md`):

```bash
open Keepr.xcodeproj
xcodebuild test -scheme Keepr -destination 'platform=iOS Simulator,name=iPhone 16'
```

Set a signing team on the Keepr target before a device run. The project uses Xcode 16 file-system-synchronized groups, so new files under `Keepr/` or `KeeprTests/` are picked up without editing `project.pbxproj`. Sample data is Settings → Development → Sample data. Screenshot runs use debug launch arguments (`-KeeprDemoMode -KeeprScreen … -KeeprContext …`) defined in `Keepr/App/LaunchOptions.swift`; those flags compile out of Release.

Cloud Functions (`docs/CLOUD_SYNC.md`):

```bash
cd firebase/functions
npm ci
npm test                 # node --test test/api.test.js; no emulator
npm run test:rules       # Firebase emulators for Firestore and Storage; needs Java 21
```

GitHub Actions is the path when there is no Mac (`docs/BUILDING_WITHOUT_A_MAC.md`):

- `.github/workflows/ios.yml` ("Build & Test"), on every push to every branch, every pull request, and `workflow_dispatch`. `macos-latest`. Runs `./scripts/prepare-app-icon.sh`, then `xcodebuild build` and `xcodebuild test` with `CODE_SIGNING_ALLOWED=NO` against whichever iPhone simulator the runner has. A second job boots a simulator and uploads light/dark screenshots (artifact `screenshots`, 30 days). Logs artifact `build-logs` is kept 7 days. Concurrency group `ios-${{ github.ref }}` cancels older runs.
- `.github/workflows/api.yml` ("Cloud API"), on pushes and pull requests that touch `firebase/**` or that workflow file, plus `workflow_dispatch`. `ubuntu-latest`, Node 22. Job `api` runs `npm test`. Job `rules` installs Temurin Java 21 and runs `npm run test:rules`. This workflow does not deploy.

`docs/BUILDING_WITHOUT_A_MAC.md` still says a green check means 99 tests pass. The current tree has the larger Swift Testing count above. Trust the CI log for a pass/fail number.

## Deploy target and release process

Ship target for the iOS app is **TestFlight**, not a local Mac archive. The owner has no Mac; compilation happens on GitHub's macOS runners.

`.github/workflows/testflight.yml` ("TestFlight"):

- Runs on every push to `main`, and on `workflow_dispatch` (optional tester notes). It ignores `docs/**` and `**.md`, so a documentation commit does not spend a TestFlight build.
- Runner `macos-latest`, timeout 45 minutes. Concurrency group `testflight` does not cancel an in-progress upload.
- Requires repository secrets `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, and `APP_STORE_CONNECT_PRIVATE_KEY` (the `.p8` body). Team id defaults to `JC9MQST5S2` inside the workflow; secret `APPLE_TEAM_ID` overrides it. The workflow says the team id is not a secret because it is embedded in distributed builds.
- Writes `Keepr/FirebaseOptions.plist` from secret `FIREBASE_OPTIONS_PLIST_BASE64` when that secret is set. If the secret is empty, the step exits 0 and the binary ships with cloud sync off. The plist is gitignored. Never commit it.
- Archives unsigned (`CODE_SIGNING_ALLOWED=NO`), exports an App Store Connect IPA with `-allowProvisioningUpdates`, and uploads with `xcrun altool --upload-app`. `CURRENT_PROJECT_VERSION` for that archive is `github.run_number`. The workflow's own summary says Apple usually takes 5–15 minutes before the build shows in TestFlight. The IPA is also uploaded as artifact `ipa` (7 days).
- Setup steps for the Apple Developer Program, the API key, and the app record are in `docs/BUILDING_WITHOUT_A_MAC.md`. Bundle id to register: `com.chrisallenclark.Keepr`.

What CI does **not** do, and what stays manual:

- CI does not deploy Firebase. From `firebase/`, after copying `firebase/.firebaserc.example` to the gitignored `firebase/.firebaserc`, the documented commands are `firebase deploy --only firestore:rules,firestore:indexes,storage` and `firebase deploy --only functions`. Functions need the Blaze plan. Full steps, IAM, and the unauthenticated-invoke note are in [`docs/CLOUD_SYNC.md`](docs/CLOUD_SYNC.md). Do not run those deploys unless Chris says so.
- Nothing in the workflows submits the app for App Store review. `docs/APP_STORE_CHECKLIST.md` is the pre-submission list. Tag `v0.1.0` points at `665025a` (2026-08-14, "Trigger TestFlight from a version tag"). The current TestFlight workflow does not trigger on tags.

The GitHub repo `chrisallenclark/Keepr` is public, default branch `main` (checked 2026-11-25). `docs/BUILDING_WITHOUT_A_MAC.md` relies on that: public macOS minutes are free, and a private repo would bill macOS runners at 10×.

## Current status

Dates below are commit dates on `main` unless noted.

**Shipped in the tree**

- 2026-08-13 `ca484b9` initial commit. 2026-08-14 `6fc6748` is the V0.1 SwiftUI app. `README.md` still titles the status "V0.1" and describes the loop: find a person, understand the relationship, remember context, reach out, record what mattered, follow up. Areas it lists: Today, People, Follow Up, Search, person profile, log interaction, quick capture (on-device heuristic, user confirms every suggestion), Contacts import (full / limited / denied), local follow-up reminders, Settings (including delete-all and "what Keepr can't do").
- Later `main` commits added, and the source plus `KeeprTests/` still contain: groups and custom relationship types, person links, the catch-up / review queue, contact-card shorthand, cadence and outreach, Brain Dump (2026-08-25, merge `8f674f4`), profile files and content search (2026-10-27, merge `0177a36`), conversation notes (2026-10-27, merge `2df31e2`).
- 2026-11-24 merge `29554e5` (PR #1, `cursor/keepr-cloud-sync-api-20f2`) added per-user Firebase sync and the read-only assistant API. That is `HEAD` of `main` at the time of this file. Product behavior of sync and the API: [`docs/CLOUD_SYNC.md`](docs/CLOUD_SYNC.md) and [`API.md`](API.md).
- The `api` function answered its health check on 2026-11-25 (see Stack).

**Documented as not built**

`docs/ARCHITECTURE.md` §9 (frozen V0.1 decisions) still defers: model-backed extraction, voice capture, semantic search, relationship summaries, calendar and email integration, widgets, App Intents / Siri / Shortcuts, share-sheet capture, Watch, iPad/Mac, pipeline stages, subscriptions, and "any backend." The backend line is superseded by the 2026-11-24 sync work. Use `docs/CLOUD_SYNC.md` for anything about accounts, Firestore, or the API. The rest of §9 is still the roadmap the architecture doc states.

`docs/ARCHITECTURE.md` also still says "no network" and "no dependencies" for V0.1. The local-only path is still true until sign-in. The sync code is the exception, and it uses URLSession rather than a Firebase iOS SDK.

**Doc drift to know about**

- `docs/APP_STORE_CHECKLIST.md` "Current blockers" says `Keepr/App`, `Keepr/Features`, and the other feature folders are empty and the app does not build, and that the 1024 icon is missing. On `main` those folders have source, there is an `@main` in `Keepr/App/KeeprApp.swift`, and `AppIcon-1024.png` is present. Many boxes in that checklist are still unchecked (privacy policy URL, support URL, App Privacy answers, device QA, and others). Which of those still block a submission needs a human pass.
- `docs/BUILDING_WITHOUT_A_MAC.md` is stale on the empty icon slot and on "99 tests."
- `docs/HANDOFF.md` is not in the repo.

**Android**

`main` has no `android/` tree. Application id `com.aulorvy.keepr` is on the unmerged remote branch `claude/friendly-pasteur-nft1vl` (tip `ee5b77c`, 2026-11-13), in `android/app/build.gradle.kts` (`namespace` and `applicationId`). `docs/CLOUD_SYNC.md` describes that branch as Room-on-device, not on `main`, and says this repo's sync change did not add an Android client. Other remote branches that are not `main`: `claude/app-ui-redesign-review-xi7g0j` (2026-08-25), `claude/keepr-ios-app-9qgoe4` (2026-10-27), `cursor/keepr-cloud-sync-api-20f2` (merged). Whether any unmerged branch is still active work is not recorded in the tree.

TBD (Chris to confirm): is Android only that unmerged branch in `chrisallenclark/Keepr`, or does `com.aulorvy.keepr` also live in another repository?

TBD (Chris to confirm): there is no `docs/HANDOFF.md` here. Should agents treat `docs/CLOUD_SYNC.md` plus this file as the handoff, or is a handoff doc missing?

TBD (Chris to confirm): do the Firestore rules, indexes, and Storage rules deployed on project `keepr-contacts` still match `firebase/` in this repo? Only `GET /v1/health` was checked on 2026-11-25.

TBD (Chris to confirm): is a TestFlight build currently available to testers, and are the App Store Connect secrets set? The workflow is in the repo. App Store Connect state is not.

TBD (Chris to confirm): which open items in `docs/APP_STORE_CHECKLIST.md` are still true, given the "empty source / missing icon" blockers are out of date?

## Repo-specific rules

Global rules, short form. The full text is `docs/AULORVY_PROFILE.md`.

- Work on a branch and open a pull request. Chris reviews and merges. Do not push to `main`, merge, or deploy unless he explicitly says so.
- Do not add or change secrets, API keys, hosting env vars, or new integrations or services without his OK.
- Never commit secret files. In this repo that includes `Keepr/FirebaseOptions.plist`, `firebase/.firebaserc`, `.env`, `*.p8`, `*.p12`, `*.mobileprovision`, and `*.provisionprofile` (see `.gitignore`). The TestFlight workflow writes the plist from `FIREBASE_OPTIONS_PLIST_BASE64`. The example templates are `firebase/FirebaseOptions.plist.example` and `firebase/.firebaserc.example`.
- Keep this business separate from Macrova, Mealixr, Closr, Remli, Aulorvy.com, and Hybrid Performance. Do not share their data, customers, branding, or accounts.
- Separate verified facts from assumptions. If the repo does not say it, write `TBD (Chris to confirm):` and the question. Do not invent versions, URLs, or status.

Rules that are specific to this codebase:

- Do not add a third-party UI kit, analytics SDK, or a Firebase iOS SPM package to satisfy something `URLSession` already does. `docs/ARCHITECTURE.md` forbids private APIs and forbids reading the Messages database.
- Relationship content stays out of logs. `Logger` usage is metadata only.
- Quick capture and any future extractor must leave a draft the user confirms. `CaptureExtracting` is the seam (`docs/ARCHITECTURE.md` §6). The shipped extractor is on-device.
- One `Person` record. Use `RelationshipContext.both` instead of duplicating someone into Business and Personal.
- New Info.plist keys are `INFOPLIST_KEY_…` build settings. `GENERATE_INFOPLIST_FILE = YES`. There is no hand-written `Info.plist`. `DEVELOPMENT_TEAM` is intentionally unset in `project.pbxproj`.
- `docs/CLOUD_SYNC.md` says to use a Firebase project that holds only Keepr.
- Prefer editing the existing docs when a fact changes, so this file and `docs/CLOUD_SYNC.md` do not drift the way the checklist already has.

## Key paths map

| Path | Role |
|---|---|
| `Keepr/App/` | `@main` (`KeeprApp.swift`), tab root (`RootView.swift`), environment injection, demo launch flags. |
| `Keepr/Models/` | SwiftData models (`Person`, interactions, memories, follow-ups, notes, files, tags, groups, links) and sample data. |
| `Keepr/Domain/` | Pure logic: people, today, follow-ups, search, cadence, capture helpers, review queue. This is what `KeeprTests/` mostly covers. |
| `Keepr/Persistence/KeeprStore.swift` | Local SwiftData container, seeding, delete-all. |
| `Keepr/Sync/` | Optional Firebase account sync, keychain session, transport, planner. |
| `Keepr/Features/` | Screens: Today, People, Person, Follow Ups, Search, Capture, Review, Onboarding, Settings (including Cloud & Assistant). |
| `Keepr/DesignSystem/Theme.swift` | Spacing, corner radius, haptics. Neighbor files are the shared views. |
| `Keepr/Assets.xcassets/` | Accent color and the 1024 app icon. |
| `Keepr.xcodeproj/` | App and `KeeprTests` targets, shared scheme `Keepr`. |
| `firebase/` | `firebase.json`, Firestore rules and indexes, Storage rules, function source and tests, plist and `.firebaserc` examples. |
| `firebase/functions/src/index.js` | Cloud Function `api`. HTTP behavior is `src/api.js`. Contract: `API.md`. |
| `.github/workflows/ios.yml` | Compile, test, and screenshot on every push and pull request. |
| `.github/workflows/testflight.yml` | Sign and upload to TestFlight on non-doc pushes to `main`. |
| `.github/workflows/api.yml` | Functions unit tests and security-rules tests. Does not deploy. |
| `docs/CLOUD_SYNC.md` | Account model, deploy steps, and what stays on the phone. Read this instead of duplicating it. |
| `docs/ARCHITECTURE.md` | Frozen V0.1 product decisions. §9 is the deferred list. The "no backend" lines predate sync. |
| `docs/BUILDING_WITHOUT_A_MAC.md` | How CI, screenshots, and TestFlight replace a Mac. Icon and test-count sentences are stale. |
| `docs/APP_STORE_CHECKLIST.md` | Pre-submission checklist. Blocker section is partly stale. |
| `docs/AULORVY_PROFILE.md` | Shared owner profile and never-do rules. Do not edit it in this repo to "improve" it; the Aulorvy.com copy wins if they differ. |
| `scripts/prepare-app-icon.sh` | Normalizes an uploaded PNG into `AppIcon-1024.png` at the start of each Apple workflow. |
