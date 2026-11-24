import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { birthdayOccurrences, handleApi, tokenHash } from "../src/api.js";

const ADA = "11111111-1111-4111-8111-111111111111";
const GRACE = "22222222-2222-4222-8222-222222222222";
const TAG = "33333333-3333-4333-8333-333333333333";

function world() {
  const users = {
    alice: {
      people: [
        {
          id: ADA,
          givenName: "Ada",
          familyName: "Lovelace",
          company: "Analytical",
          workNote: "Engines",
          phoneNumbers: ["555"],
          emailAddresses: ["ada@example.com"],
          notes: "Mornings",
          birthday: "1815-12-10T00:00:00.000Z",
          context: "business",
          priority: "high",
          status: "active",
          isFavorite: true,
          cadenceDays: 30,
          tagIds: [TAG],
          groupIds: [],
          photoPath: "people/ada/photo",
          modifiedAt: "2026-03-02T00:00:00.000Z",
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-03-02T00:00:00.000Z"
        },
        {
          id: GRACE,
          givenName: "Grace",
          familyName: "Hopper",
          modifiedAt: "2026-03-01T00:00:00.000Z",
          birthday: "1906-12-09T00:00:00.000Z"
        }
      ],
      tags: [{ id: TAG, name: "Inventor", kind: "business", modifiedAt: "2026-01-01T00:00:00.000Z" }],
      groups: [],
      interactions: [
        { id: "int-1", personId: ADA, kind: "meeting", title: "Difference engine", rawNote: "Long note", occurredAt: "2026-02-01T15:00:00.000Z", modifiedAt: "2026-02-01T15:00:00.000Z" }
      ],
      memories: [
        { id: "mem-1", personId: ADA, category: "importantDate", content: "Daughter's wedding in spring", isArchived: false, modifiedAt: "2026-02-02T00:00:00.000Z" }
      ],
      notes: [
        { id: "note-1", personId: ADA, title: "Consultation", body: "The full write-up", source: "pasted", modifiedAt: "2026-02-03T00:00:00.000Z" }
      ],
      files: [
        {
          id: "file-1",
          personId: ADA,
          title: "Scan",
          kind: "image",
          fileExtension: "jpg",
          byteCount: 4,
          dataPath: "files/file-1/original",
          dataContentType: "image/jpeg",
          modifiedAt: "2026-02-04T00:00:00.000Z"
        }
      ],
      followUps: [
        { id: "fu-1", personId: ADA, title: "Send notes", dueDate: "2026-04-02T00:00:00.000Z", isCompleted: false, modifiedAt: "2026-03-01T00:00:00.000Z" },
        { id: "fu-2", personId: ADA, title: "Done already", dueDate: "2026-04-03T00:00:00.000Z", isCompleted: true, modifiedAt: "2026-03-01T00:00:00.000Z" }
      ],
      links: [
        { id: "link-1", personAId: ADA, personBId: GRACE, labelAToB: "Colleague", labelBToA: "Colleague", modifiedAt: "2026-01-01T00:00:00.000Z" }
      ],
      preferences: [
        { id: "current", groupLabel: "Business", contextMode: "business", remindersEnabled: true, peopleSort: "name", modifiedAt: "2026-03-01T00:00:00.000Z" }
      ]
    },
    bob: {
      people: [{ id: "bob-person", givenName: "Secret", familyName: "Person", modifiedAt: "2026-01-01T00:00:00.000Z" }]
    }
  };
  const blobs = {
    "alice/files/file-1/original": { bytes: Buffer.from([1, 2, 3, 4]), contentType: "image/jpeg" }
  };
  const tokens = new Map([
    [tokenHash("kpr_alice_token_value_123456"), { uid: "alice", revoked: false }],
    [tokenHash("kpr_revoked_token_value_12345"), { uid: "alice", revoked: true }]
  ]);
  let calls = 0;
  return {
    calls: () => calls,
    deps: {
      async authenticate(token) {
        const record = tokens.get(tokenHash(token));
        if (!record || record.revoked) return null;
        return { uid: record.uid };
      },
      async rateLimit(uid) {
        calls += 1;
        if (uid === "limited") return { allowed: false, retryAfter: 12 };
        return { allowed: calls <= 100, retryAfter: 30 };
      },
      async list(uid, collection) {
        return users[uid]?.[collection] ? users[uid][collection].map((row) => ({ ...row })) : [];
      },
      async signDownload(uid, relativePath, ttl) {
        if (relativePath.includes("..")) return null;
        const expires = new Date(Date.UTC(2026, 0, 1, 0, ttl / 60));
        return { url: `https://files.example/${uid}/${relativePath}?ttl=${ttl}`, expiresAt: expires.toISOString() };
      },
      async readBlob(uid, relativePath) {
        return blobs[`${uid}/${relativePath}`] || null;
      }
    }
  };
}

function call(env, path, { token = "kpr_alice_token_value_123456", method = "GET", headers = {} } = {}) {
  const url = new URL(path, "https://api.example");
  const query = Object.fromEntries(url.searchParams.entries());
  return handleApi(
    {
      method,
      path: url.pathname,
      query,
      header: (name) => {
        if (name.toLowerCase() === "authorization") return token ? `Bearer ${token}` : headers.authorization;
        return headers[name.toLowerCase()] || null;
      }
    },
    env.deps
  );
}

describe("read API", () => {
  it("rejects missing, malformed, and revoked tokens", async () => {
    const env = world();
    assert.equal((await call(env, "/v1/contacts", { token: null })).status, 401);
    assert.equal((await call(env, "/v1/contacts", { token: "not-a-token" })).status, 401);
    assert.equal((await call(env, "/v1/contacts", { token: "kpr_revoked_token_value_12345" })).status, 401);
    assert.equal((await call(env, "/v1/contacts", { method: "POST" })).status, 405);
    assert.equal((await call(env, "/v1/health", { token: null })).json.ok, true);
  });

  it("lists and searches contacts with every stored field", async () => {
    const env = world();
    const page = await call(env, "/v1/contacts?limit=1");
    assert.equal(page.status, 200);
    assert.equal(page.json.items.length, 1);
    assert.equal(page.json.items[0].givenName, "Ada");
    assert.equal(page.json.items[0].workNote, "Engines");
    assert.deepEqual(page.json.items[0].phoneNumbers, ["555"]);
    assert.equal(page.json.items[0].cadenceDays, 30);
    assert.equal(page.json.items[0].tags[0].name, "Inventor");
    assert.match(page.json.items[0].photoUrl, /files\.example\/alice/);
    assert.ok(page.json.nextCursor);

    const next = await call(env, `/v1/contacts?limit=1&cursor=${encodeURIComponent(page.json.nextCursor)}`);
    assert.equal(next.json.items[0].givenName, "Grace");
    assert.equal(next.json.nextCursor, null);

    const search = await call(env, "/v1/contacts?q=hopper");
    assert.deepEqual(search.json.items.map((item) => item.givenName), ["Grace"]);
  });

  it("does not return another user's contacts", async () => {
    const env = world();
    const page = await call(env, "/v1/contacts?q=secret");
    assert.equal(page.json.items.length, 0);
  });

  it("returns sections, files, interactions, and notes for one contact", async () => {
    const env = world();
    const body = (await call(env, `/v1/contacts/${ADA}/sections`)).json;
    const ids = body.sections.map((section) => section.id);
    assert.deepEqual(ids, ["work", "memories", "notes", "files", "groups", "connections", "interactions", "followUps", "details"]);
    assert.equal(body.sections.find((section) => section.id === "notes").notes[0].body, "The full write-up");
    assert.equal(body.sections.find((section) => section.id === "files").files[0].downloadUrl.includes("files/file-1"), true);
    assert.equal(body.sections.find((section) => section.id === "interactions").interactions[0].rawNote, "Long note");
    assert.equal(body.sections.find((section) => section.id === "memories").groups[0].category, "importantDate");
    assert.equal(body.sections.find((section) => section.id === "connections").links[0].label, "Colleague");

    const file = await call(env, "/v1/files/file-1");
    assert.equal(file.json.contentPath, "/v1/files/file-1/content");
    const bytes = await call(env, "/v1/files/file-1/content");
    assert.equal(bytes.status, 200);
    assert.deepEqual([...bytes.bytes], [1, 2, 3, 4]);
    assert.equal(bytes.contentType, "image/jpeg");
  });

  it("returns birthdays and follow-ups inside a date range", async () => {
    const env = world();
    const upcoming = await call(env, "/v1/upcoming?from=2026-12-01&to=2026-12-31");
    assert.deepEqual(upcoming.json.birthdays.map((item) => item.date), ["2026-12-09", "2026-12-10"]);
    assert.equal(upcoming.json.importantDates[0].text, "Daughter's wedding in spring");
    assert.equal(upcoming.json.importantDates[0].structured, false);

    const follow = await call(env, "/v1/follow-ups?from=2026-04-01&to=2026-04-30");
    assert.deepEqual(follow.json.items.map((item) => item.title), ["Send notes"]);
    const withDone = await call(env, "/v1/follow-ups?from=2026-04-01&to=2026-04-30&includeCompleted=true");
    assert.equal(withDone.json.items.length, 2);
    assert.equal((await call(env, "/v1/dates?from=2026-13-01&to=2026-12-01")).status, 400);
  });

  it("serves tags, links, notes search, and preferences", async () => {
    const env = world();
    assert.equal((await call(env, "/v1/tags")).json.items[0].name, "Inventor");
    assert.equal((await call(env, "/v1/links")).json.items[0].labelAToB, "Colleague");
    assert.equal((await call(env, "/v1/notes?q=write-up")).json.items[0].title, "Consultation");
    assert.equal((await call(env, "/v1/preferences")).json.preferences.groupLabel, "Business");
    assert.equal((await call(env, "/v1/contacts/missing")).status, 404);
  });

  it("caps page size and rate limits", async () => {
    const env = world();
    const page = await call(env, "/v1/contacts?limit=1000");
    assert.equal(page.json.items.length, 2);
    assert.equal((await call(env, "/v1/contacts?limit=0")).status, 400);
    env.deps.rateLimit = async () => ({ allowed: false, retryAfter: 9 });
    const limited = await call(env, "/v1/contacts");
    assert.equal(limited.status, 429);
    assert.equal(limited.headers["Retry-After"], "9");
  });

  it("refuses a path that tries to escape the user's files", async () => {
    const env = world();
    let signed = null;
    env.deps.signDownload = async (uid, path) => {
      signed = path;
      return { url: "https://example.invalid", expiresAt: "2026-01-01T00:00:00.000Z" };
    };
    const people = (await call(env, "/v1/contacts?q=ada")).json.items;
    assert.equal(people[0].photoUrl, "https://example.invalid");
    assert.equal(signed, "people/ada/photo");
    signed = "untouched";
    env.deps.list = async (uid, collection) => {
      if (collection !== "files") return [];
      return [{ id: "bad", personId: ADA, title: "x", dataPath: "../bob/secret", modifiedAt: "2026-01-01T00:00:00.000Z" }];
    };
    const file = await call(env, "/v1/files/bad");
    assert.equal(file.json.downloadUrl, null);
    assert.equal(signed, "untouched");
  });
});

describe("birthdayOccurrences", () => {
  it("skips February 29 in a non-leap year", () => {
    assert.deepEqual(birthdayOccurrences("2020-02-29T00:00:00.000Z", "2021-02-01", "2021-02-28"), []);
    assert.deepEqual(birthdayOccurrences("2020-02-29T00:00:00.000Z", "2024-02-01", "2024-02-29"), ["2024-02-29"]);
  });
});

describe("tokenHash", () => {
  it("matches the iOS test vector", () => {
    assert.equal(tokenHash("kpr_test"), "617ccba9509167b993a77ae820c51ab7ebc010d3e592e9c1514068d19d59f805");
  });
});
