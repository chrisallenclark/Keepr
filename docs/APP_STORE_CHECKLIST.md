# Keepr — App Store pre-submission checklist

Status legend: **[x] done** — verified true in this repo today · **[ ] todo** — not done yet ·
**[?] verify** — depends on something only you can check (an Apple account, a real device,
or current Apple documentation).

> **Read this first.** App Store requirements change, and they change without warning. Every
> item below that involves an Apple *rule* (required screenshot sizes, App Privacy question
> wording, privacy-manifest enforcement, review guideline numbers) must be re-checked against
> the live sources before you submit:
> [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/),
> App Store Connect Help, and the Developer documentation. Nothing in this file is a
> substitute for reading those. Where I was not confident a rule is current, I marked it
> `[?] verify` rather than asserting it.

---

## 1. Bundle configuration

- [x] Bundle identifier `com.chrisallenclark.Keepr` set in the app target
      (`PRODUCT_BUNDLE_IDENTIFIER`, both Debug and Release).
- [x] Display name `Keepr` (`INFOPLIST_KEY_CFBundleDisplayName`).
- [x] `MARKETING_VERSION = 0.1` (→ `CFBundleShortVersionString`) and
      `CURRENT_PROJECT_VERSION = 1` (→ `CFBundleVersion`).
- [x] `GENERATE_INFOPLIST_FILE = YES` — there is no hand-written `Info.plist`; every key is a
      build setting. Add new keys as `INFOPLIST_KEY_…` build settings, not as a new file.
- [ ] **Bundle ID registered** in your Apple Developer account and an App Store Connect app
      record created with the same ID. Must match exactly.
- [?] Every build you upload needs a `CFBundleVersion` higher than the last one uploaded for
      that `CFBundleShortVersionString`. Bump `CURRENT_PROJECT_VERSION` for each TestFlight
      build; bump `MARKETING_VERSION` for each public release.
- [?] **Export compliance.** The app has no networking and no custom cryptography, so it
      should qualify for the standard exemption. You can stop App Store Connect asking on
      every upload by adding `ITSAppUsesNonExemptEncryption = NO` to the Info.plist. Confirm
      the key actually lands in the built `Info.plist` (Product → Show Build Folder →
      `Keepr.app/Info.plist`) before relying on it, and confirm the answer is still correct
      if you ever add networking.

## 2. Signing & capabilities

- [x] `CODE_SIGN_STYLE = Automatic`.
- [x] `DEVELOPMENT_TEAM` is deliberately **not** set in `project.pbxproj` — pick your team in
      Xcode → target → Signing & Capabilities the first time you open the project. Xcode will
      write it into the project file at that point.
- [x] No entitlements file and no capabilities are required for V0.1. Contacts and local
      notifications are permission prompts, not entitlements.
- [ ] Confirm "Automatically manage signing" produces a valid provisioning profile for a real
      device, then again for an App Store distribution archive.
- [?] If you later turn on CloudKit sync (see `ARCHITECTURE.md` §8), that *does* add the
      iCloud capability, an entitlements file, and a new App Privacy conversation. Not now.

## 3. Deployment target & device support

- [x] `IPHONEOS_DEPLOYMENT_TARGET = 17.0` at the project level and on both targets.
- [x] `TARGETED_DEVICE_FAMILY = 1` — iPhone only.
- [x] `INFOPLIST_KEY_UISupportedInterfaceOrientations = UIInterfaceOrientationPortrait` —
      portrait only.
- [?] Because the app is iPhone-only, it will still install and run on iPad in compatibility
      mode. Launch it on an iPad once and make sure it is not embarrassing; reviewers do look.
- [?] iOS 18+ Contacts *limited access* is an `#available`-gated path per the architecture
      doc. Test it on an OS that actually supports it — the limited-access picker cannot be
      exercised on iOS 17.

## 4. App icon

- [ ] **The 1024×1024 icon is not drawn yet.** `Keepr/Assets.xcassets/AppIcon.appiconset/`
      contains an empty universal 1024×1024 slot with no image file. **The app cannot be
      submitted until you fill it.** Drop a `1024x1024` PNG into the slot in Xcode.
- [?] Apple's marketing icon rules that I am confident about: it must be square, 1024×1024,
      and must **not** contain an alpha channel or transparency. Flattened, opaque PNG.
      Verify current format guidance in the Human Interface Guidelines → App icons, which is
      also where the iOS 18+ dark/tinted icon variants are described (optional, not required).
- [x] `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` so the catalog entry is actually used.
- [ ] After adding the icon, archive and check the icon renders on the Home Screen, in
      Settings, in Spotlight, and in the App Switcher. Xcode generates the smaller sizes from
      the 1024 automatically; look at the small ones, because detail that reads at 1024 turns
      to mud at 40pt.

## 5. Launch experience

- [x] `INFOPLIST_KEY_UILaunchScreen_Generation = YES` — a plain generated launch screen, no
      storyboard file to maintain. This is the modern default and is fine.
- [ ] The launch screen should look like the app's first frame, not like a splash ad. Once
      the Today screen exists, check that the hand-off from launch screen to first render
      does not flash a different background color.
- [ ] **Crash-free launch, cold.** Delete the app, reinstall, launch. Then launch with
      Contacts permission denied. Then launch with notification permission denied. A crash on
      first launch is the single most common reason a build gets rejected.

## 6. Privacy strings & permissions

- [x] `INFOPLIST_KEY_NSContactsUsageDescription` is set and explains the *specific* benefit
      and that data stays on device:
      > "Keepr links your relationship profiles to your contacts so you can see who's who and
      > reach them with one tap. Your contacts never leave your device."
      Purpose strings that are vague ("we need your contacts") are a known rejection reason.
      Do not shorten this one without re-reading Guideline 5.1.1.
- [x] **There is no `NSUserNotificationsUsageDescription` key on iOS, and none is invented
      here.** Local notification permission is requested at runtime via
      `UNUserNotificationCenter.requestAuthorization` and the system supplies the prompt text.
      Do not add an Info.plist string for it.
- [ ] Request Contacts access **just in time**, after a screen that explains why — per
      `ARCHITECTURE.md` §7. Never on first launch, cold.
- [ ] Verify the app is fully usable when Contacts access is **denied** and when it is
      **limited** (iOS 18+). Reviewers routinely deny permissions to see what breaks.
- [ ] Verify the app is fully usable when notification authorization is denied.
- [ ] Confirm that no relationship content (names, notes, memories) is written to
      `os_log`/Console — `ARCHITECTURE.md` §7 commits to metadata-only logging.
- [?] **Privacy manifest (`PrivacyInfo.xcprivacy`).** The app uses `@AppStorage`, which is
      `UserDefaults`, and `UserDefaults` is on Apple's "required reason API" list. For a
      first-party app with no third-party SDKs the usual declaration is category
      `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1` (the app accessing its
      own defaults). I am **not** certain how strictly this is enforced for apps that ship no
      third-party SDKs, and the reason codes have been revised. Check Apple's
      "Describing use of required reason API" documentation before submitting; if in doubt,
      add the manifest — it costs nothing and an `ITMS-91053` upload warning costs a day.

## 7. App Privacy answers (App Store Connect "nutrition label")

- [ ] Answer the App Privacy questionnaire. **Based on the architecture as designed, the
      correct answer is "Data Not Collected."**
- [x] The design supports that answer: no network calls, no analytics SDK, no third-party
      dependencies, no backend, local-only SwiftData store, Contacts data cached on device
      only (`ARCHITECTURE.md` §1, §5, §7).
- [ ] **This answer is only true for as long as it stays true.** "Data Not Collected" means
      *nothing* leaves the device. The moment you add analytics, crash reporting, a
      model-backed extractor that calls an API (`ARCHITECTURE.md` §6), or CloudKit sync, you
      must revisit this and update the label — and CloudKit in particular is a real
      conversation, not a checkbox. Re-verify the answer on every release, not just the first.
- [?] Reading contacts on-device without transmitting them is, on my reading, "not collected"
      under Apple's definition of *collect* (transmitting data off device). Read Apple's own
      definition on the App Privacy Details page and satisfy yourself before you attest — you
      are the one signing the declaration.

## 8. URLs & metadata (App Store Connect)

- [ ] **Privacy policy URL** — required for all apps, and it must be a live, reachable URL
      you control. It needs to say what the app does and does not collect. It must not 404 at
      review time.
- [ ] **Support URL** — required. A real page where a user can reach you. A `mailto:` link on
      a plain page is acceptable; an empty placeholder is not.
- [ ] Marketing URL — optional, leave blank if you do not have one.
- [ ] App name, subtitle, promotional text, description, keywords, category
      (Productivity or Lifestyle both fit Keepr), age rating questionnaire, copyright.
- [ ] Screenshots for every currently-required iPhone display size.
      **[?] verify** the exact required sizes in App Store Connect — Apple changes them with
      each hardware generation and I will not guess at the current list. Screenshots must show
      the real app; mock-ups and device frames containing content the app doesn't produce get
      rejected. Use plausible sample data, not `asdf`.
- [ ] App Review notes: state plainly that the app is local-only with no account and no login,
      and explain that Contacts access is optional and the app works without it. A demo
      account is not needed because there is no account.

## 9. Accessibility

Not optional-nice-to-have — accessibility failures are both a rejection risk and the thing
that makes an app feel amateur.

- [ ] **Dynamic Type**: every text style scales. Test at the largest accessibility size
      (Settings → Accessibility → Display & Text Size → Larger Text, slider to max). Nothing
      clipped, nothing truncated to uselessness. Avoid fixed-height rows.
- [ ] **VoiceOver**: navigate every screen with the screen curtain on. Every control has a
      meaningful label; icon-only buttons (the capture button, tab bar icons, follow-up
      complete toggles) need explicit `.accessibilityLabel`. Announce state changes.
- [ ] **Contrast**: the accent color in `AccentColor.colorset` computes to roughly 6.6:1
      against white in light mode and roughly 6.9:1 against the standard dark system
      background — both clear WCAG AA (4.5:1) for normal text. *That is the accent against
      those backgrounds only.* Re-check any custom foreground/background pairs the design
      system introduces, and check secondary/tertiary label colors on colored surfaces.
- [ ] **Tap targets**: minimum 44×44 pt for every interactive element, per the HIG. Small
      chevrons and inline "x" buttons are the usual offenders.
- [ ] **Reduce Motion** and **Increase Contrast** honored if you add any custom animation or
      translucency.
- [ ] Test in both Light and Dark appearance. The colorset already has a dark variant.

## 10. Code & build hygiene

- [x] **No private APIs, no third-party SDKs, no Swift packages.** The project has zero
      package dependencies and the architecture forbids reading iMessage history or any other
      unsupported data source (`ARCHITECTURE.md` §5). This is the correct posture — do not
      relax it.
- [x] Release configuration is real: `SWIFT_COMPILATION_MODE = wholemodule`,
      `VALIDATE_PRODUCT = YES`, `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`,
      `ENABLE_NS_ASSERTIONS = NO`.
- [ ] **Build and archive in Release**, not Debug. Debug-only code paths, sample-data seeding,
      and `#if DEBUG` fixtures must not ship. Grep for `DEBUG` before you archive.
- [ ] Zero compiler warnings in the Release build. Treat every SwiftData or concurrency
      warning as a bug, not noise.
- [ ] Run the static analyzer (Product → Analyze) once.
- [ ] `KeeprTests` passes. The scheme's Test action already includes the test target.
- [ ] No `print()` of user content, no `TODO`/`FIXME` visible in the UI, no placeholder
      strings ("Lorem ipsum", "Coming soon") in a shipped screen. An unfinished-looking
      screen is a Guideline 2.1 rejection.

## 11. Testing on real hardware

- [ ] **Run on a physical iPhone.** The Simulator does not exercise Contacts permissions,
      notification delivery, haptics, or real-world performance. All four matter here.
- [ ] Test with a large real contact list (hundreds to thousands of contacts) — the
      architecture does in-memory filtering (`ARCHITECTURE.md` §3), which is a deliberate
      trade-off that needs to be measured, not assumed.
- [ ] Test scheduled follow-up notifications actually fire: schedule one, background the app,
      wait for it, tap it, confirm it deep-links to the right person.
- [ ] Test on the oldest device you support running iOS 17.
- [ ] Test upgrade behavior: install build N, add data, install build N+1 over it, confirm the
      SwiftData store migrates and no data is lost. Do this for every schema change.

## 12. TestFlight

- [ ] Upload a build and run it through internal TestFlight yourself before any external
      testing. The TestFlight build is signed and optimized differently than your local run;
      things break here that never broke in Xcode.
- [?] External TestFlight testing requires Beta App Review and needs beta test information
      (what to test, feedback email) filled in. Internal testers (up to your team) do not.
      Confirm the current tester limits and review requirements in App Store Connect Help.
- [ ] Fix everything TestFlight's crash reporting surfaces before submitting for review.

## 13. Data deletion & user control

- [ ] **Delete All Data** in Settings, per `ARCHITECTURE.md` §7 — empties the SwiftData store
      in one action. Not built yet (the `Features/Settings` folder is empty). Ship it: it is
      the honest counterpart to "your data stays on your device," and it is what a privacy-
      conscious reviewer will look for.
- [ ] Confirm deleting the app removes all app data (it does, for a local-only SwiftData
      store with no App Group and no iCloud — verify there is no shared container).
- [x] Apple's **account deletion** requirement (Guideline 5.1.1(v)) applies to apps that let
      users *create an account*. Keepr has no accounts, no login, and no backend, so it does
      not apply. Note that this changes the day you add sign-in.

---

## Current blockers — what actually stops a submission today

1. **No app icon.** The 1024×1024 slot is an empty placeholder. Hard blocker.
2. **The app is not finished.** `Keepr/App/`, `Keepr/Persistence/`, `Keepr/Services/`,
   `Keepr/DesignSystem/` and every folder under `Keepr/Features/` are currently empty — there
   is no `@main` entry point, so the app target does not yet build. Only `Models/` and
   `Domain/` have source in them. Everything in sections 5, 9, 11 and 13 is untestable until
   that changes.
3. **No signing team selected**, no App Store Connect record, no privacy policy URL, no
   support URL.
4. **App Privacy questionnaire not answered**, and the privacy-manifest question in §6 is
   unresolved.

Sections 1–4 and 10's first two items are genuinely done. Nothing else has been verified,
because nothing else *can* be verified from this repository alone.
