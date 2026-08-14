# Building and installing Keepr without a Mac

The hard constraint: **iOS apps can only be compiled on macOS.** Xcode doesn't exist for
Windows, Linux, iPhone or iPad, and there's no supported way around that. Nothing in this
document changes that fact — it just means you never have to *own* the Mac.

There are three stages, and only the last one costs money.

---

## Stage 1 — Make it compile (free, no Mac, no account)

`.github/workflows/ios.yml` builds the app and runs the test suite on GitHub's macOS
runners on every push. Because this repository is **public**, those runners are free and
effectively unlimited.

You watch it from your iPhone: **github.com/chrisallenclark/Keepr → Actions**. A green check
means the app compiles and 99 tests pass. A red X opens a job summary with the exact
compiler errors, which is everything needed to fix them.

This is also how Claude iterates: push → read the CI log → fix → push again. No Mac in the
loop.

> **If the repository ever goes private:** macOS runner minutes bill at 10× the normal rate,
> so a free plan's 2,000 minutes/month becomes ~200 macOS minutes — roughly 30–40 builds.
> Fine for iteration, but worth knowing before flipping the switch.

## Stage 2 — See it (free, no Mac) — **done**

The `screenshots` job in `.github/workflows/ios.yml` boots a Simulator, installs the
app, and captures every main screen in both light and dark mode. Download them from
**Actions → a run → Artifacts → screenshots** — this works in mobile Safari.

It drives the app with debug-only launch arguments (`-KeeprDemoMode -KeeprScreen today
-KeeprContext business`) rather than a UI-test target: the app loads sample data into a
throwaway in-memory store, skips onboarding, and opens the requested screen. See
`Keepr/App/LaunchOptions.swift` — in a Release build every one of those checks folds to
a constant `false`, so none of it exists in a TestFlight or App Store binary.

## Stage 3 — Put it on your iPhone ($99/year)

Installing on a physical iPhone requires an **Apple Developer Program** membership
(US$99/year, `developer.apple.com/programs`). There is no free path: free "personal team"
provisioning requires Xcode on a Mac cabled to the device.

`.github/workflows/testflight.yml` does the rest. It signs using an **App Store Connect
API key**, which means `xcodebuild -allowProvisioningUpdates` creates and downloads the
certificate and provisioning profile itself — no `.p12`, no Keychain, no `openssl`, no
Mac at any point.

### What you have to create (once, from a browser)

1. **Join the Apple Developer Program** — `developer.apple.com/programs`. Approval can
   take a few hours to a couple of days.

2. **Note your Team ID** — `developer.apple.com/account` → Membership details. A
   10-character string like `A1B2C3D4E5`.

3. **Create an App Store Connect API key** — `appstoreconnect.apple.com` → Users and
   Access → Integrations → App Store Connect API → Team Keys → **+**.
   Give it the **App Manager** role (it must be able to create signing certificates).
   You get three things:
   - **Issuer ID** — shown above the key list
   - **Key ID** — in the key's row
   - **the `.p8` file** — downloadable exactly once, so save it immediately

4. **Create the app record** — App Store Connect → Apps → **+** → New App.
   Platform iOS, Bundle ID `com.chrisallenclark.Keepr`, and pick an SKU (any string).
   You'll need to register that bundle ID first at
   `developer.apple.com/account/resources/identifiers` if it isn't offered.

5. **Add three repository secrets** — GitHub → your repo → Settings → Secrets and
   variables → Actions → New repository secret:

   | Secret | Value |
   |---|---|
   | `APP_STORE_CONNECT_KEY_ID` | the Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | the Issuer ID |
   | `APP_STORE_CONNECT_PRIVATE_KEY` | the **entire contents** of the `.p8` file, including the `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----` lines |

   The Team ID is already set in the workflow (`JC9MQST5S2`). It isn't a secret —
   it's embedded in every distributed build — but an `APPLE_TEAM_ID` secret
   overrides it if the account ever changes.

   **Reusing an existing API key is fine.** Keys are scoped to the *team*, not to an
   app, so a key created for another app signs this one too. Two conditions: it needs
   the **App Manager** role (a Developer-role key can't create signing certificates,
   which this workflow relies on), and you need the original `.p8` file, which Apple
   lets you download only once. If either is in doubt, create a new key — they're
   free, you can hold several, and revoking one doesn't affect the others.

6. **App icon** — already in place; see below for how to replace it.

### Then

GitHub → Actions → **TestFlight** → **Run workflow**. It archives, signs, exports and
uploads. Apple takes another 5–15 minutes to process the build, then it appears in the
**TestFlight** app on your iPhone (install TestFlight from the App Store and sign in with
the same Apple ID).

The workflow checks every prerequisite up front and fails in seconds with a precise
message if something's missing, rather than 20 minutes into a build.

## The app icon

`Keepr/Assets.xcassets/AppIcon.appiconset/` ships with an **empty** icon slot. The repo
deliberately carries no placeholder: an app icon is brand, and a stand-in that looks
almost right is worse than an obvious gap.

To replace it, upload a square PNG **with no alpha channel** from GitHub → the repo →
`Keepr` → `Assets.xcassets` → `AppIcon.appiconset` → **Add file** → **Upload files**.

`scripts/prepare-app-icon.sh` runs at the start of every build and handles the fiddly
part: an upload from an iPhone arrives with a UUID filename and whatever pixel size the
source image happened to be, so the script adopts any stray PNG as `AppIcon-1024.png`,
resizes it to the 1024×1024 Apple requires, warns if it has an alpha channel, and wires
it into the asset catalog. Builds stay green with no icon at all, and pick one up
automatically once it exists.

## The alternative: rent a Mac by the hour

If you'd rather drive Xcode directly — see live previews, use the Simulator interactively,
click through Apple's signing UI — rent one:

| Service | Rough cost | Notes |
|---|---|---|
| MacinCloud | ~$1/hr or ~$30/mo | Managed, Xcode preinstalled, browser or VNC access |
| MacStadium | ~$100+/mo | Dedicated hardware, aimed at teams |
| AWS EC2 Mac | ~$0.65/hr, 24h minimum | Powerful, but the minimum commitment stings |
| Scaleway Apple silicon | ~€0.10/hr | Cheap, EU-based, hourly |

You can connect from an iPhone with a VNC or RDP client, but Xcode on a 6-inch screen is
genuinely painful. This is worth it for a focused session — signing setup, App Store
submission — not for daily development.

## What about Swift Playgrounds?

Swift Playgrounds builds and submits real apps from an **iPad** (not iPhone), but it works on
Swift Package projects, not `.xcodeproj` files, and its capabilities are limited. Converting
Keepr to fit it would be real work with real compromises. Only worth considering if you have
an iPad and Stage 1 + Stage 3 above have been ruled out.

---

## Recommended order

1. Get CI green (free, today).
2. Add screenshot artifacts so you can see and critique the design (free).
3. Buy the $99 membership when you're ready to actually *use* Keepr daily, and wire up
   TestFlight.

Steps 1 and 2 cost nothing and answer "is this real and does it look right?" Only step 3
requires committing money, and by then you'll have seen the thing.
