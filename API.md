# Keepr read API

A signed-in person's Keepr account, read by a personal access token. There are no write endpoints. The phone creates and revokes tokens in **Settings → Cloud & Assistant**. Keepr stores only the SHA-256 hash of a token.

Base URL, after you deploy:

```text
https://us-central1-YOUR_PROJECT_ID.cloudfunctions.net/api
```

Send the token on every request except `/v1/health`:

```text
Authorization: Bearer kpr_...
```

Dates are UTC timestamps (`2026-04-02T15:04:05.000Z`) unless a parameter says it is a calendar date (`YYYY-MM-DD`). Responses are JSON. List responses look like:

```json
{ "items": [], "nextCursor": null, "truncated": false }
```

`limit` defaults to 50 and cannot be higher than 100. Pass `cursor` from `nextCursor` to get the next page. A search or a date query that had to look at more than 5000 records sets `truncated` to true.

Each token is limited to 60 requests a minute (change with the function environment variable `KEEPR_RATE_LIMIT_PER_MINUTE`). A limited request returns `429` and `Retry-After`.

File and photo URLs last 15 minutes. `urlTtl` can ask for 60 to 3600 seconds. If signing a URL is not available, `downloadUrl` is null and `contentPath` still works with the same bearer token.

| Code | Meaning |
|---|---|
| 401 | Missing, unknown, or revoked token |
| 400 | Bad `limit`, `cursor`, date, or `urlTtl` |
| 404 | That record is not in this account |
| 405 | Anything other than GET |
| 429 | Too many requests |
| 500 | The function failed. No data was changed, because this API cannot change data |

## Endpoints

`GET /v1/health` — no token. `{ "ok": true, "service": "keepr-api" }`.

`GET /v1/contacts?q=&limit=&cursor=` — every contact field: names, company, job, work note, phones, emails, context, priority, status, favorite, cadence, birthday, address, contact note, how you met, introduced by, free notes, dates, tag and group ids plus the tag and group records, and a photo URL when there is a photo. `q` matches name, company, notes, phones, and emails.

`GET /v1/contacts/{id}` — one contact, same shape.

`GET /v1/contacts/{id}/sections` — the profile, in the sections the app shows:

* work (company, job title, work note)
* memories, grouped by category (this is the "sections" of what you remember; there is no separate Section record)
* notes, including the full body
* files, with download URLs
* groups
* connections (who they are to someone else, and the label from this side)
* interactions
* follow-ups
* details (context, tags, birthday, cadence, how you met, notes)

`GET /v1/contacts/{id}/files` and `GET /v1/files/{id}` — file metadata plus `downloadUrl`, `thumbnailUrl`, and `contentPath`.

`GET /v1/files/{id}/content` — the bytes. Same bearer token. `Content-Disposition` carries a cleaned file name.

`GET /v1/contacts/{id}/interactions` and `GET /v1/interactions?from=&to=&q=` — logged interactions. `from` and `to` are optional calendar dates and filter `occurredAt`.

`GET /v1/contacts/{id}/notes` and `GET /v1/notes?q=` — conversation notes, full body.

`GET /v1/tags`, `GET /v1/groups`, `GET /v1/links` — relationship types, groups, and the links between two people.

`GET /v1/follow-ups?from=&to=&includeCompleted=` — follow-ups whose due date falls on those calendar dates. Completed ones are left out unless `includeCompleted=true`.

`GET /v1/dates?from=&to=` — birthdays that occur in the range (every year, including across New Year), and every memory filed under Important Date. Those memories are free text: Keepr does not store a separate event date on them, so they are returned with `"structured": false` and are not dropped just because the range doesn't contain a parsed day.

`GET /v1/upcoming?from=&to=` — birthdays, important-date memories, and follow-ups due in the range, in one response. Follow-ups are the first page; `followUpsNextCursor` continues on `/v1/follow-ups`.

`GET /v1/preferences` — the synced settings: group label, business/personal side, reminders switch, people sort.

## Example

```bash
curl -sS -H "Authorization: Bearer kpr_..." \
  "https://us-central1-YOUR_PROJECT_ID.cloudfunctions.net/api/v1/upcoming?from=2026-11-01&to=2026-11-30"
```

Creating the token, deploying, and the emulator are in [docs/CLOUD_SYNC.md](docs/CLOUD_SYNC.md).
