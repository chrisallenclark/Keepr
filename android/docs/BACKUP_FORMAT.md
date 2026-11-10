# Keepr backup file format (`.keeprbackup`)

A Keepr backup is a single file holding everything Keepr stores on the phone,
encrypted with a password the user chooses. It is made from **Settings → Data
→ Back Up to File…** and read by **Restore from Backup…** (Settings → Data, or
the first onboarding page on a new phone). The file is written through the
Android Storage Access Framework, so it can live anywhere the user picks
(Google Drive, Downloads, a USB stick). Keepr has no network access; nothing is
uploaded by the app itself.

The code: `core/src/main/kotlin/com/chrisallenclark/keepr/core/backup/` (the
format, encryption and checks, unit-tested on the JVM in
`core/src/test/.../BackupTests.kt`) and `app/.../backup/BackupService.kt` (the
database snapshot, the all-or-nothing restore, reminders).

## Layout

```
header ‖ chunk 0 ‖ chunk 1 ‖ … ‖ chunk n (final)
```

All integers are big-endian.

### Header (version 1.0, 67 bytes, authenticated)

| Offset | Size | Field | Value in 1.0 |
|---|---|---|---|
| 0 | 8 | magic | ASCII `KEEPRBK1` |
| 8 | 2 | format major version | `1` |
| 10 | 2 | format minor version | `0` |
| 12 | 2 | header length (all bytes up to the first chunk) | `67` |
| 14 | 1 | KDF id | `1` = PBKDF2-HMAC-SHA256 |
| 15 | 4 | KDF iterations | `600000` for new backups |
| 19 | 1 | salt length | `16` |
| 20 | 16 | salt | random per backup |
| 36 | 1 | cipher id | `1` = AES-256-GCM, chunked (below) |
| 37 | 1 | nonce prefix length | `8` |
| 38 | 8 | nonce prefix | random per backup |
| 46 | 4 | chunk size (plaintext bytes per chunk) | `262144` (256 KiB) |
| 50 | 1 | password check length | `16` |
| 51 | 16 | password check | see below |

A reader refuses a **newer major version** before reading further ("This backup
was made by a newer version of Keepr. Update the app, then try again."). A
newer *minor* version may append fields after offset 67 and raise the header
length; older readers skip them (they are still authenticated, because the
whole header is). Readers bound the values they accept (iterations ≤ 10 million,
chunk size 1 KiB–16 MiB, header ≤ 4 KiB) so a damaged header can't hang the app.

### Keys

```
master       = PBKDF2-HMAC-SHA256(NFC(password), salt, iterations, 32 bytes)
key          = HMAC-SHA256(master, "Keepr backup v1 encryption key")
check        = HMAC-SHA256(master, "Keepr backup v1 password check")[0..16)
```

The password is Unicode-normalized (NFC) first, so the same password typed on
a different keyboard derives the same key. `check` is stored in the header; a
mismatch means a wrong password ("That password doesn't open this backup."),
which lets the app tell a wrong password from a damaged file. It costs an
attacker exactly as much per guess as trying to decrypt does.

The iteration count is in the header, so it can be raised for new backups
without breaking old ones. There is no way to recover a forgotten password.

### Chunks (STREAM construction)

The plaintext is cut into chunks of `chunk size` bytes; the last chunk may be
shorter (or empty). Chunk *i* is sealed independently with AES-256-GCM:

- nonce (12 bytes) = nonce prefix (8) ‖ *i* as u32
- associated data = header bytes ‖ *i* as u32 ‖ final flag (u8: 1 for the last chunk, else 0)
- stored as ciphertext ‖ 16-byte tag, so every chunk but the last is exactly
  `chunk size + 16` bytes

Because each chunk's position and "final" flag are authenticated, any change is
detected: flipping a bit anywhere, reordering, dropping or repeating chunks,
cutting the file short (even exactly at a chunk boundary — the last remaining
chunk then fails as "final"), or appending anything after the final chunk.
Large attachments stream through one chunk at a time; neither writing nor
reading needs the whole backup in memory.

## Plaintext container

Inside the encryption is a sequence of named entries:

```
entry := u8 1 ‖ u16 name length ‖ name (UTF-8) ‖ u64 size ‖ size bytes
end   := u8 0
```

The end marker must be the last byte of the final chunk. Entries, in order:

1. `manifest.json` (always first)
2. `records.json`
3. one entry per blob: `blobs/person-photo/<personId>`,
   `blobs/file-data/<fileId>`, `blobs/file-thumbnail/<fileId>`

Entries with other names are skipped (room for later minor versions); a
`blobs/…` entry the manifest doesn't list is refused.

### manifest.json

```json
{
  "formatVersion": 1,
  "formatMinorVersion": 0,
  "createdAt": "2026-11-10T09:30:00Z",
  "app": { "platform": "android", "versionName": "1.0.0", "versionCode": 12, "databaseSchemaVersion": 1 },
  "counts": { "people": 2, "interactions": 1, "memories": 1, "followUps": 1, "notes": 1, "files": 2,
              "tags": 9, "groups": 1, "links": 1, "personTags": 2, "groupMembers": 1, "deletions": 1 },
  "records": { "name": "records.json", "size": 4567, "sha256": "…" },
  "blobs": [ { "name": "blobs/file-data/…", "size": 700000, "sha256": "…" } ]
}
```

`formatVersion` greater than the reader's major version is refused with the
"newer Keepr" message. Unknown fields anywhere are ignored.

### records.json

One array per database table, field for field as `app/.../data/Entities.kt`
stores them: the same names, the same raw enum strings, dates as epoch
milliseconds, string lists (phone numbers, email addresses, aliases)
newline-separated, and each row's `modifiedAt`. Byte columns are not inlined:
`people[].photoBlob`, `files[].dataBlob` and `files[].thumbnailBlob` name the
blob entry holding them (or are null).

```
people, interactions, memories, followUps, notes (conversation notes), files,
tags (relationship types), groups, links (person links),
personTags {personId, tagId, position}, groupMembers {personId, groupId, position},
deletions {id, recordType, deletedAt}   — tombstones, kept for a future sync
settings { "<key>": { "type": "string" | "bool" | "stringList", "value": … } }
```

Unknown tables, fields, and settings of unknown types are ignored; a missing
optional field reads as null.

### Settings

Only an explicit whitelist travels (`BackupSettings.included`):

| Key | Why |
|---|---|
| `keepr.contextMode` | Business/Personal side last shown |
| `keepr.groupLabel` | the user's word for groups |
| `keepr.remindersEnabled` | follow-up reminders on/off |
| `keepr.hasOnboarded` | (a restored phone is always marked onboarded) |
| `keepr.peopleSort`, `keepr.didDefaultToNameSort` | People list order |
| `keepr.lastCadenceBacklogNudge` | rhythm: stops the "you're behind" nudge repeating right after a restore |
| `keepr.seededBuiltInKeys` | built-in relationship types already offered, so deleted ones don't come back |

Deliberately **not** included:

- **The seen-contacts baseline** (`keepr.knownContactIdentifiers`,
  `keepr.hasContactBaseline`). Contact identifiers are per device: on a new
  phone the old list would match nothing, so Catch Up would treat every
  contact as new. Restoring resets the tracker instead (as Delete All Data
  does); the next look at the address book quietly sets a fresh baseline.
  Catch Up on the new phone therefore starts empty, which is correct.
- Whether notification/contacts permission was already requested: that's this
  phone's permission state.
- Pending-reminder bookkeeping: reminders are rebuilt from the data.

## Checks on restore

Restoring never overwrites silently, and is all-or-nothing:

1. **Stage and verify everything** into a private staging folder in the app's
   cache (removed on success and on failure): header, password check, every
   chunk's tag, each blob's length and SHA-256 against the manifest, the
   records' hash, the JSON, the counts against the manifest, unique ids, and
   that every reference (person, interaction, note, type, group) resolves.
2. **Preview and confirm**: the backup's date and counts; if the phone already
   has data, it's shown beside the backup's and the user must confirm
   ("Replace N people with M people from the backup?").
3. **Replace**, with saves paused: the file contents are swapped in (the
   current set is kept aside as `files/person_files.previous`), then every
   table is emptied and refilled in **one Room transaction**. If anything
   throws, the transaction rolls back and the kept-aside files are put back.
   If the process dies mid-swap, the next launch puts back any file a record
   still needs (contents never change under an id, so this is right on either
   side of the commit).
4. Only after the commit: whitelisted settings applied, contact baseline reset,
   the in-memory graph rebuilt from disk, every reminder cancelled and
   rescheduled from the restored data (the same code path a launch uses,
   honouring the restored reminders switch and this phone's notification
   permission; past-due follow-ups aren't re-notified).

## Changing the format

- Additive changes (new fields, tables, settings, entry types, header fields
  at the end): bump `FORMAT_MINOR`; older readers ignore them.
- Anything an older reader would misread: bump `FORMAT_MAJOR` (header and
  manifest); older readers will ask the user to update.
- Raising the KDF cost: change `DEFAULT_ITERATIONS`; old backups keep theirs.
