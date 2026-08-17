# Keepr — V0.1 Architecture & Product Decisions

Status: **frozen for V0.1**. Changes to anything in this document should be deliberate.

Keepr is a relationship operating system for iPhone: Contacts + lightweight relationship
memory + follow-ups, organized around one idea — **Business ↔ Personal**.

---

## 1. Platform decisions

| Decision | Choice | Why |
|---|---|---|
| Language / UI | Swift 5 language mode, SwiftUI | Native, fastest path to an Apple-feeling product |
| Persistence | SwiftData (local store) | Modern native ORM, no backend, works offline |
| Minimum iOS | **17.0** | SwiftData floor. iOS 18-only APIs (limited Contacts access) are `#available`-gated |
| Devices | iPhone only (`TARGETED_DEVICE_FAMILY = 1`), portrait | Focus. iPad/Mac are roadmap |
| Dependencies | **None** | Everything V0.1 needs exists in Apple frameworks |
| Sync | Local-only at V0.1, CloudKit-ready model | Ship a correct local build first (see §6) |
| Networking | None | No backend, no analytics, no AI API in V0.1 |

Frameworks used: SwiftUI, SwiftData, Contacts, ContactsUI, UserNotifications, UIKit
(haptics only), SF Symbols. **Not** used: EventKit, MessageUI, CloudKit (yet).

## 2. Information architecture

Four tabs. Settings lives behind a toolbar entry on Today, not a tab.

1. **Today** — what needs attention right now (overdue, due today, upcoming, going quiet, recent).
2. **People** — all relationships, filter + sort + search.
3. **Follow Up** — the full task list: overdue / today / upcoming / someday / completed.
4. **Search** — global search across people, memories, interactions, notes.

### The context switch

`ContextMode` is `.business | .personal` — persisted in `@AppStorage("keepr.contextMode")`,
so it survives launches. It is presented as a **pinned segmented control in a
`.safeAreaInset(edge: .top)`** below the navigation bar on Today, People and Follow Up.

Rationale: this is the app's organizing principle, so it earns a permanent, always-reachable
control; a segmented `Picker` is the stock Apple control for "switch the lens on this screen"
and needs no custom chrome. Search deliberately ignores context — you search everything.

A person's own context is `RelationshipContext` = `.business | .personal | .both`.
There is exactly **one** record per person; `.both` surfaces them in either mode.
Never duplicate a person to put them in two contexts.

## 3. Data model

Seven SwiftData models. All properties have defaults, all to-one and to-many relationships
are optional, and no `@Attribute(.unique)` is used — these are the CloudKit requirements,
adopted up front so sync can be switched on without a migration.

```
Person 1─* Interaction        (cascade)
Person 1─* Memory             (cascade)
Person 1─* FollowUp           (cascade)
Person *─* RelationshipTag    (nullify)
Person *─* PersonGroup        (nullify)
Person 1─* PersonLink         (cascade, twice — one array per end)
Interaction  1─* Memory             (nullify — memory outlives its source note)
Interaction  1─* FollowUp           (nullify)
```

Three ways of saying who someone is, deliberately kept apart:

* **type** (`RelationshipTag`) — what they are to you: "Current Client", "Investor". More
  than one is normal and expected.
* **group** (`PersonGroup`) — what the relationship comes through: Life Time, your own
  training company, a meal-prep business, Hinge, a bar. More than one is normal here too.
* **link** (`PersonLink`) — who they are to *another person*: Linda is Alex's parent.

The **word** for the second axis is a user setting (`PreferenceKey.groupLabel`, default
"Group"; see `GroupVocabulary`). A trainer with Life Time / HYP / Meal Prep calls them
Businesses; someone sorting Hinge / Tinder / Bumble calls them Apps. The concept is fixed —
a named list a person can be in several of — and only the noun moves, which is one stored
string rather than a second axis nobody's other use case needs.

Type and place are independent axes and the People screen crosses them with two rows of
chips: "Current Client" spans every gym plus the clients trained at home, "Life Time" holds
clients and the colleague who works there, and both together is who you train at that gym.
Chip counts are computed against the *other* row's selection, so a chip says what it will
give you before it's tapped, and a chip that would give nothing is never shown. Keeping the
axes apart is what stops the taxonomy turning into "Life Time Clients", "Home Clients",
"Iron House Clients" — one relationship, categorized once, seen from either direction.

A link is pairwise and reads differently from each end, which is why it isn't a group of
two. One record serves both profiles: `labelAToB` and `labelBToA` are stored separately so
"Manager" doesn't show up as "Manager" on the report's profile.

* **Person** — the app-owned relationship profile. Named `Person`, not `Relationship`, so it
  doesn't shadow SwiftData's `@Relationship` macro — and it reads better at call sites.
  Caches a *minimal snapshot* of the linked Apple
  contact (name, org, phones, emails, thumbnail) so the app is fast, searchable and useful
  offline and when contact access is denied. `contactIdentifier` is the link back to
  Contacts, which stays the source of truth; the snapshot is written once at import and is
  refreshed on demand through `ContactStoreProviding.contact(withIdentifier:)`.
* **RelationshipTag** — a *model*, not an enum, so the taxonomy can evolve without a
  migration. Seeded once with built-ins (`isBuiltIn`); users can add their own. A built-in
  is found by `builtInKey`, never by visible name — renaming "Family" must not silently
  create a second "Family" on the next import.
* **PersonGroup** — a name, a symbol, an optional detail ("Delray"), and optional
  comma-separated `aliases` ("LT, LTF"). Membership only; no ranking, no roles.
* **Person.workNote** — what they actually do, in the user's words, separate from the
  `company`/`jobTitle` the contact card supplies. The client you train at 6am who runs an
  e-commerce brand is a fact worth surfacing, and it's searchable.

### Cadence

`RelationshipTag.cadenceDays` holds how often someone of that type is worth contacting;
`Person.cadenceDays` overrides it (`nil` inherits, `0` means never chase). `CadenceEngine`
resolves the two — **shortest wins**, since a Current Client who is also a Friend needs the
30-day promise, not the 90-day one — and measures against `lastInteractionAt ?? createdAt`,
so a contact imported today gets the full interval and logging anything resets the clock
with no extra step.

Today shows the overdue as "Time to Reach Out". People with a cadence are excluded from the
older `goingQuiet` heuristic: a rhythm the user set is a better answer than a blanket
21/60-day threshold, and being told twice about one person reads as nagging. Anyone with an
open follow-up is excluded too — a plan already exists.

### Reading shorthand off contact cards

`ContactMarkers.swift` turns "Stanley LT Client" into *Stanley · Business · Current Client ·
Life Time*, with the marker text removed from the name and the original kept as a memory.
The iPhone contact card is never written to.

Vocabulary comes from the user's own records — group names, their initials, tag names, and
any aliases the user typed — never from a built-in list of assumptions. Two rules bound the
damage: a marker must match a **whole word**, and a marker of four characters or fewer must
be **capitalized on the card** ("LT Client" yes, "Lt Colonel" no). Markers in name fields
are removed; markers in the company field are evidence only, since an employer is a fact
rather than a label. If the markers would consume the entire name, the name is left alone.

`ContactMarkerParser.candidates(in:vocabulary:)` closes the setup loop: shorthand seen on
two or more cards but unknown to Keepr is offered at the top of the importer as a group to
create, so the feature works for someone who hasn't configured anything yet.
* **PersonLink** — a labelled connection between two people, with an optional note.
* **Memory** — structured fact: content, category, importance, archived, source interaction.
* **Interaction** — a logged meaningful interaction: kind, date, title, raw note, summary.
  Only ever what the *user* records. See §5.
* **FollowUp** — person, due date, optional time, note, priority, completed, snooze.

Enums are stored as raw `String` (`contextRaw`, `kindRaw`, …) with typed computed accessors.
Raw strings are predicate- and sort-friendly and survive case renames safely.

### Query strategy

`@Query` fetches with a sort descriptor; context/tag/status filtering happens in pure
functions in `Domain/` (`PeopleEngine`, `TodayEngine`, `FollowUpEngine`, `SearchEngine`). At V0.1 scale (thousands of people) this is
comfortably fast, and it makes all the interesting logic unit-testable without a store.
If profiling ever says otherwise, the same predicates move into `@Query`.

## 4. Layering

```
App/          entry point, root tab view, app-wide state
Models/       @Model types + enums + sample data
Domain/       pure, testable logic (filtering, sorting, due dates, going-quiet)
Persistence/  ModelContainer construction, first-run seeding
Services/     Contacts, notifications, capture extraction, comms launching
DesignSystem/ small reusable views + tokens
Features/     one folder per screen
```

Services are protocols (`ContactStoreProviding`, `NotificationScheduling`,
`CaptureExtracting`) with a live implementation, injected through the SwiftUI environment
(`AppEnvironment.swift`). `CommunicationLauncher` is a plain enum — it builds URLs and has
nothing to fake.
Previews and tests inject fakes. That is the only abstraction layer in the app; there is no
repository/view-model ceremony on top of SwiftData.

## 5. Messaging reality

The app **cannot** read iMessage history — no private APIs, no Messages database, ever.
The interaction timeline contains only what the user logs. Outbound actions use supported
URL schemes (`sms:`, `tel:`, `mailto:`) via `openURL`.

## 6. AI: pluggable, never required

`CaptureExtracting` turns a raw quick-capture note into a draft
(`summary`, `[MemoryDraft]`, `FollowUpDraft?`). V0.1 ships `HeuristicCaptureExtractor` —
entirely on-device, no API key, no network. A future model-backed extractor conforms to the
same protocol and the UI does not change. Extraction always produces a **draft the user
confirms**; nothing is ever written to a person's record unreviewed.

## 7. Privacy

* No network calls in V0.1. No analytics. No third-party SDKs.
* Contacts access is requested **just in time**, after a screen that explains the value, and
  the app is fully usable if it's denied (people can be added by hand).
* `CNAuthorizationStatus.limited` (iOS 18+) is a first-class state, not an error.
* Relationship content is never written to `os_log`/console. `Logger` calls are metadata only.
* New-contact detection (`ContactChangeTracker`) stores **contact identifiers only** — no
  names, numbers or addresses — and the first run silently establishes a baseline rather
  than presenting an entire address book as a to-do list.
* Settings offers **Delete All Data**, which empties the store in one action, including the
  seen-contacts baseline.

## 8. CloudKit path (not V0.1)

Model rules above are already satisfied. Enabling sync is: add the iCloud capability +
CloudKit container, switch `ModelConfiguration` to `.automatic`, verify schema in the
CloudKit console, and gate it behind a Settings toggle. Nothing in the UI layer changes.

## 9. Deferred (roadmap, deliberately not built)

Model-backed extraction, voice capture, semantic search, relationship summaries, calendar
and email integration, widgets, App Intents/Siri/Shortcuts, share-sheet capture, Watch,
iPad/Mac, pipeline stages, subscriptions, any backend.
