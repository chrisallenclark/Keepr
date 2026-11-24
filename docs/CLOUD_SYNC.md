# Keepr cloud sync and the read API

Keepr on the phone stays the place you edit. Signing in copies that store to one Firebase project, per account, and a revocable token can read it. Nothing in this repository is deployed. You do that, once, from the steps at the bottom.

## Where the data lives today

Checked against `main` and the unmerged Android branch `claude/friendly-pasteur-nft1vl` (commit `ee5b77c8`). There is no Firestore database in either place. The only Firebase use on Android is **App Distribution**, which emails test APKs. The Android app requests no internet permission and says so in `android/docs/RELEASE.md`. Android is not on `main`, so this change syncs the iOS app that `main` contains and leaves a schema the Android branch can adopt. Merging that whole app into this change would have mixed two large pieces of work.

### iOS (`main`) — SwiftData, on the phone only

The store is `Keepr/Persistence/KeeprStore.swift`. There is no account and no network until you sign in with this change. Apple Contacts stay the source of names and numbers; Keepr keeps a copy on the person.

| Record | What it is | Fields |
|---|---|---|
| **Person** | The contact / relationship | `id`, `contactIdentifier`, `givenName`, `familyName`, `preferredName`, `company`, `jobTitle`, `workNote`, `phoneNumbers`, `emailAddresses`, `photoData`, `contextRaw`, `priorityRaw`, `statusRaw`, `isFavorite`, `createdAt`, `updatedAt`, `modifiedAt`, `lastInteractionAt`, `howWeMet`, `introducedBy`, `notes`, `birthday`, `postalAddress`, `contactNote`, `cadenceDays`, `awaitingReplySince`, `reviewedAt`, `isSampleData`. Tags and groups are relationships, not columns. |
| **Interaction** | Something you logged. Keepr cannot read iMessage, Mail, or call history. | `id`, `occurredAt`, `createdAt`, `modifiedAt`, `kindRaw`, `title`, `rawNote`, `summary`, `isQuickCapture`, `awaitingReply`, person |
| **Memory** | One fact, on a shelf. The shelves are the profile's sections of "what I remember". | `id`, `content`, `categoryRaw` (work, family, health, progress, schedule, style, opportunity, interests, goals, preferences, personal, business, **importantDate**, other), `importanceRaw`, `createdAt`, `updatedAt`, `modifiedAt`, `isArchived`, person, source interaction |
| **Conversation note** | A whole write-up | `id`, `title`, `body`, `sourceRaw` (recorder, pasted, typed, screenshot), `occurredAt`, `createdAt`, `updatedAt`, `modifiedAt`, `isPinned`, person, source interaction, files |
| **Person file** | Photo, PDF, or other attachment. Bytes were on the phone only. | `id`, `title`, `kindRaw`, `fileExtension`, `data`, `thumbnailData`, `byteCount`, `capturedAt`, `createdAt`, `modifiedAt`, `note`, person, source interaction, conversation note |
| **Follow-up** | A reminder | `id`, `title`, `note`, `dueDate`, `hasTime`, `priorityRaw`, `isCompleted`, `completedAt`, `createdAt`, `modifiedAt`, `lastSnoozedAt`, `notificationIdentifier`, person, source interaction |
| **Relationship tag** | "Current Client", "Family", or one you added | `id`, `name`, `kindRaw`, `isBuiltIn`, `builtInKey`, `aliases`, `cadenceDays`, `sortOrder`, `symbolName`, `createdAt`, `modifiedAt` |
| **Group** | Gym, business, app — the second way you sort people | `id`, `name`, `symbolName`, `detail`, `aliases`, `contextRaw`, `createdAt`, `modifiedAt`, `sortOrder`, `isSampleData` |
| **Link** | Who someone is to someone else | `id`, person A, person B, `labelAToB`, `labelBToA`, `note`, `createdAt`, `modifiedAt` |

There is no Section table and no separate birthday/event table. Profile sections are the screen (`PersonProfileView`): work, memories by category, notes, files, groups, connections, interactions, follow-ups, details. Birthdays are `Person.birthday`. Other important dates are memories whose category is `importantDate` (the words, not a parsed day). Follow-up dates are `FollowUp.dueDate`.

`modifiedAt` is new. It is the sync clock, added with a default so existing rows migrate when the app opens. The other fields are unchanged.

These stay on the phone and are not uploaded:

| Key | Why |
|---|---|
| `keepr.knownContactIdentifiers`, `keepr.hasContactBaseline` | Apple contact ids for "what's new on this phone". They mean nothing on another device. |
| `keepr.hasOnboarded`, `keepr.hasAskedForNotifications`, `keepr.didDefaultToNameSort`, `keepr.seededBuiltInKeys` | This install's bookkeeping |
| `keepr.lastCadenceBacklogNudge` | When this phone last nudged about cadence |
| Sample people and sample groups (`isSampleData`) | Demo data |

These settings do sync: `keepr.groupLabel`, `keepr.contextMode`, `keepr.remindersEnabled`, `keepr.peopleSort`.

### Android (branch only) — Room, on the phone only

`android/app/.../data/Entities.kt` mirrors the iOS fields. Dates are epoch milliseconds. Phones and emails are newline-separated. File bytes live beside the database (`FileBlobStore`), not in the row. Extra columns the iOS app did not have, and the cloud schema now has too:

* `modifiedAt` on every row
* `deletions` (`id`, `recordType`, `deletedAt`) so another device can learn about a delete
* join rows `person_tags` and `person_group_members` with a position

The encrypted `.keeprbackup` file (`android/docs/BACKUP_FORMAT.md`) is a passworded export, not a live account. This API does not read backup files.

## The account

Firebase Auth (email and password), Cloud Firestore, and Cloud Storage, one project.

```text
users/{uid}/people/{id}
users/{uid}/interactions/{id}
users/{uid}/memories/{id}
users/{uid}/notes/{id}
users/{uid}/files/{id}          metadata
users/{uid}/followUps/{id}
users/{uid}/tags/{id}
users/{uid}/groups/{id}
users/{uid}/links/{id}
users/{uid}/deletions/{id}      tombstone: recordType, deletedAt
users/{uid}/preferences/current
users/{uid}/tokens/{id}         hash, prefix, label, createdAt, revokedAt. Never the token.
```

File bytes and the contact photo:

```text
users/{uid}/people/{id}/photo
users/{uid}/files/{id}/original
users/{uid}/files/{id}/thumbnail
```

Dates in Firestore are timestamps. Lists are arrays, not newline-separated strings. Built-in tags use a UUID v5 of `keepr.tag.v1/<builtInKey>` in the DNS namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8`, so "Family" is the same id on every phone (`7e8337bf-c4b2-5639-90f5-62eeda81db66`). Tags you invent keep the id they were created with.

Rules: a signed-in user can read and write only `users/{their uid}/...`. Token documents must be a 64-character hex hash. Rate-limit counters are not readable by the client. Storage is the same path rule. Tests for both are in `firebase/functions/test/rules.test.js`.

The read API is one Cloud Function, `api`. It looks up the token hash with the Admin SDK, then reads only that uid. See [API.md](../API.md).

## What the iPhone does

Until you sign in, behavior is the same as before. The first sync **uploads** every real record and **downloads** records already in the account. It does not delete on that first pass, so turning sync on cannot throw away the phone's copy or the account's copy. Later syncs: an edit goes up, a newer edit from the account comes down (later `modifiedAt` wins), and a delete on one side deletes the other side once both had seen the record. Deleting a person deletes their interactions, memories, notes, files, follow-ups, and links. Delete All Data sets a flag and the next sync clears that account before uploading what's left (the built-in tags).

One phone is bound to the first account that signs in. A second account is refused until you delete all data, so two people's records are not poured together.

Assistant tokens are created in Settings. The plaintext is shown once. Revoke sets `revokedAt`. The API then returns 401.

## Live project

The Firebase project is **keepr-contacts**. Rules, indexes, Storage, and the `api` function are already deployed. The read API is:

https://us-central1-keepr-contacts.cloudfunctions.net/api

TestFlight (`.github/workflows/testflight.yml`, every push to `main`) writes `Keepr/FirebaseOptions.plist` from the repository secret **`FIREBASE_OPTIONS_PLIST_BASE64`**. That value is the plist file encoded as base64, nothing else. If the secret is not set, the step does nothing and the build still uploads; that binary has no cloud sync until you add the secret and ship another build. Do not commit the plist.

On Windows, from the folder that contains the filled-in plist:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("Keepr\FirebaseOptions.plist"))
```

GitHub → Settings → Secrets and variables → Actions → New repository secret. Name it exactly `FIREBASE_OPTIONS_PLIST_BASE64` and paste that one line.

## What you have to do

Do this in a Firebase project that holds only Keepr. The Spark plan can run Auth, Firestore, and Storage. **Cloud Functions need the Blaze plan.** Blaze is pay-as-you-go. A single person's reads and a quiet function stay inside Google's free tier, but it is a billing account, and you should set a budget alert. CI does not deploy Firebase.

1. [Firebase console](https://console.firebase.google.com) → create a project. Turn Analytics off.
2. **Authentication → Sign-in method → Email/Password → Enable.**
3. **Build → Firestore Database → Create database.** Production mode is fine; the rules in this repo replace the default. Pick a region and keep the function in that region (`us-central1` unless you change the URL in `API.md`).
4. **Build → Storage → Get started.**
5. **Project settings → General → Your apps → Add app → iOS.** Bundle id `com.chrisallenclark.Keepr`. Download `GoogleService-Info.plist` only to copy three values out of it, or skip the plist and copy them from the project settings:
   * `PROJECT_ID`
   * Web API key (`API_KEY`)
   * Storage bucket (`STORAGE_BUCKET`), often `YOUR_PROJECT_ID.firebasestorage.app`
6. Copy `firebase/FirebaseOptions.plist.example` to `Keepr/FirebaseOptions.plist` and fill those three in. That file is gitignored. Rebuild the iOS app. Restrict the API key (Google Cloud → APIs & Services → Credentials) to the iOS bundle id and to Identity Toolkit, Token Service, Cloud Firestore, and Cloud Storage.
7. Copy `firebase/.firebaserc.example` to `firebase/.firebaserc` and set the project id. Install the CLI (`npm i -g firebase-tools`), `firebase login`.
8. From `firebase/`:

   ```bash
   firebase deploy --only firestore:rules,firestore:indexes,storage
   firebase deploy --only functions
   ```

   Deploying functions is the step that requires Blaze. If the deploy log prints an index URL, open it and create that index. The token lookup needs a collection-group index on `tokens.hash`.
9. The function's service account needs to sign download URLs. In Google Cloud → IAM, grant the function's service account **Service Account Token Creator** on itself. Until that works, file metadata still includes `contentPath`, which downloads with the bearer token and does not need the extra role.
10. Cloud Run will refuse public callers until you allow unauthenticated invoke on the `api` function. The data is not public: the function returns 401 without a Keepr token. In the deploy log or Cloud Run, allow unauthenticated invocations for `api` only.
11. Optional: Firestore TTL on `rateLimits.expiresAt` is not required. Old minute-counters are tiny.
12. On the iPhone: Settings → Cloud & Assistant → Create Account. Sync runs, including the records already on the phone. Create a token, copy it, then:

    ```bash
    curl -sS -H "Authorization: Bearer kpr_..." \
      "https://us-central1-keepr-contacts.cloudfunctions.net/api/v1/contacts?limit=1"
    ```

### Tests, before you deploy

```bash
cd firebase/functions
npm ci
npm test                 # API, no emulator
npm run test:rules       # needs Java 21; downloads the Firebase emulators
```

The rules test refuses one user reading another user's people, tokens, and files, refuses a token that is not a hash, and refuses client writes to the rate-limit counters.

iOS tests are the existing `xcodebuild test` workflow plus `KeeprTests/CloudSyncTests.swift` (round trip, first sync does not delete, a second sync is quiet, a remote person downloads, a local delete writes a tombstone).

## Android, when that branch is merged

Use the same paths and the same field names. Convert Room's epoch milliseconds to timestamps and newline-separated lists to arrays. Push `deletions` as tombstones. Do not upload `isSampleData` rows. Built-in tags must use the UUID v5 above or the account will grow a second "Family". Sign in with the same Firebase Auth user. This pull request does not add that client, because the Android app is not on `main`.

## Risks

* **Two phones edit the same record while offline.** The phone that syncs later wins. The earlier edit is replaced. The first sync of a phone does not delete, and it unions by id, so existing data is not dropped. The same person created independently on two phones before either syncs becomes two records. They are not merged by name.
* **One phone, one account.** Switching accounts is refused until Delete All Data, so the records on the phone are not copied into the wrong account.
* **Blaze billing.** Set a budget. The function is the only part that requires it.
* **Signed URLs** are bearer links for their lifetime (15 minutes unless you ask for longer). Anyone who sees one can download that file until it expires. `contentPath` needs the Keepr token instead.
* **The API key in the app is not a secret.** Firebase expects it in the client. Restrict it by bundle id and API. The real secret is the user's password, the refresh token in the iPhone keychain, and the assistant token, which is shown once and stored only as a hash.
* **App Store privacy.** Once someone can sign in, "Data Not Collected" is no longer true. See `docs/APP_STORE_CHECKLIST.md`.
* **The backend for keepr-contacts is deployed.** A TestFlight build still needs `FIREBASE_OPTIONS_PLIST_BASE64` or it ships with cloud sync off. The Swift sync tests run on the Mac CI runner.
