# Keepr

A relationship operating system for iPhone.

Messages puts your clients, leads, friends and family into one chronological inbox, and the
relationships that matter get buried. Keepr separates them with one idea:

**Business ↔ Personal.**

One switch changes what every screen is about. A person can belong to both — there's still
only one record for them.

Native SwiftUI + SwiftData. No accounts, no backend, no analytics, no third-party
dependencies. Everything stays on the device.

---

## Status: V0.1

The loop that works today:

> Find a person → understand the relationship → remember the context → reach out →
> record what mattered → remember to follow up.

| Area | What's there |
|---|---|
| **Today** | Overdue and due-today follow-ups, upcoming, relationships going quiet, recent activity. Calm when there's nothing to do. |
| **People** | Filter by relationship type, five sort orders, A–Z sections, search, swipe to favorite, context menu to call/text/email. |
| **Follow Up** | Overdue / Today / This Week / Later / Completed, swipe to complete, snooze a day or a week. |
| **Search** | Across names, companies, tags, memories, interactions, notes and follow-ups — grouped by *why* each person matched. Ignores the Business/Personal switch. |
| **Person profile** | Header, one-tap Message / Call / Email / Log, next action, remembered facts, interaction timeline, details. |
| **Log Interaction** | Type, date, note, facts to remember, optional follow-up — one sheet. |
| **Quick Capture** | Type a sentence; on-device extraction proposes the person, the facts and the follow-up. Every suggestion is a toggle you confirm. |
| **Contacts** | Just-in-time permission, full / limited (iOS 18) / denied all handled, multi-select import, manual entry always available. |
| **Reminders** | One local notification per follow-up. Off until you turn reminders on. |
| **Settings** | Permission status, sample data toggle, delete all data, and an honest "what Keepr can't do" page. |

Deliberately **not** built yet: iCloud sync, model-backed extraction, voice capture, semantic
search, widgets, App Intents, calendar/email integration, iPad/Mac. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §9.

## What Keepr can't do

It cannot read your iMessage, Mail or call history. Apple doesn't expose that to App Store
apps, and Keepr doesn't pretend otherwise. The timeline is what *you* record — which is
the useful subset anyway.

## Build

Requires **Xcode 16** or later; targets **iOS 17.0+**, iPhone only.

```bash
open Keepr.xcodeproj
```

Then set your own signing team on the `Keepr` target (Signing & Capabilities → Team) and run.
The project uses Xcode 16 file-system-synchronized groups, so files added under `Keepr/` or
`KeeprTests/` are picked up automatically.

Tests: ⌘U, or

```bash
xcodebuild test -scheme Keepr -destination 'platform=iOS Simulator,name=iPhone 16'
```

To see every screen populated, launch the app and turn on **Settings → Development → Sample
data**. Sample records are flagged and removed independently of anything you add.

## Layout

```
Keepr/
  App/           entry point, tab root, environment wiring
  Models/        SwiftData models, enums, sample data
  Domain/        pure, tested logic — filtering, sorting, due dates, search
  Persistence/   container construction, seeding, delete-all
  Services/      Contacts, notifications, capture extraction, URL launching
  DesignSystem/  the few shared views and spacing constants
  Features/      one folder per screen
KeeprTests/      Swift Testing suites over Domain, Models, persistence, extraction
docs/            ARCHITECTURE.md (frozen V0.1 decisions), APP_STORE_CHECKLIST.md
```

## Privacy

No network calls. No analytics. No SDKs. Contacts are read only to link relationships and are
never written to or uploaded. Relationship content is never written to the console or logs.
Settings can erase everything the app owns in one action.
