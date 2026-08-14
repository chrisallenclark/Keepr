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

## Stage 2 — See it (free, no Mac)

Once Stage 1 is green, CI can boot the app in a Simulator, walk the main screens and upload
**screenshots as build artifacts**, which download to an iPhone from the Actions tab. That's
how you review the design without a Mac — not as good as holding it, good enough to judge
layout, spacing, dark mode and Dynamic Type.

Not built yet; it's the natural next step after the first green build.

## Stage 3 — Put it on your iPhone (needs $99/year)

To install on a physical iPhone you need an **Apple Developer Program** membership
(US$99/year, `developer.apple.com/programs`) and TestFlight. There is no free path:
free "personal team" provisioning requires Xcode on a Mac physically connected to the device.

With the membership, the whole flow runs from CI and a browser:

1. **Generate a signing identity without a Mac.** The usual instructions say to make a
   certificate request in Keychain Access, but a CSR is just a file — `openssl` produces one
   on any machine (including in CI):
   ```
   openssl req -new -newkey rsa:2048 -nodes -keyout keepr.key -out keepr.csr \
     -subj "/emailAddress=you@example.com/CN=Your Name/C=US"
   ```
   Upload `keepr.csr` at `developer.apple.com/account/resources/certificates` → download the
   `.cer` → convert to a `.p12` with `openssl`. That `.p12` and its password become GitHub
   Actions secrets.
2. **Register the App ID and a provisioning profile** in the same web portal.
3. **Create the app record** in App Store Connect (`appstoreconnect.apple.com`) — this works
   in mobile Safari.
4. **Add an App Store Connect API key** (App Store Connect → Users and Access → Integrations)
   as GitHub secrets, so CI can upload builds.
5. **Extend the workflow** to archive, sign and `xcrun altool`/`xcrun notarytool` upload to
   TestFlight.
6. **Install TestFlight** on your iPhone. Every push then puts a new build on your phone in
   ~10 minutes.

Steps 1–2 are fiddly on a phone screen. Any borrowed laptop with a browser makes them much
easier, and they're one-time.

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
