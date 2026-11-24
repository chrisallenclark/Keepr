import { createHash } from "node:crypto";

export const DEFAULT_LIMIT = 50;
export const MAX_LIMIT = 100;
export const SCAN_CAP = 5000;
export const DEFAULT_URL_TTL_SECONDS = 900;
export const MAX_URL_TTL_SECONDS = 3600;

const CATEGORY_TITLES = {
  work: "Work",
  family: "Family",
  health: "Health",
  progress: "Progress",
  schedule: "Schedule",
  style: "How They Are",
  opportunity: "Opportunity",
  interests: "Interests",
  goals: "Goals",
  preferences: "Preferences",
  personal: "Personal",
  business: "Business",
  importantDate: "Important Date",
  other: "Other"
};

export function tokenHash(token) {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

export function bearerToken(header) {
  if (!header || typeof header !== "string") return null;
  const match = /^Bearer\s+(\S+)$/i.exec(header.trim());
  if (!match) return null;
  const token = match[1];
  if (!token.startsWith("kpr_") || token.length < 20) return null;
  return token;
}

/**
 * @param {object} request method, path, query, header(name)
 * @param {object} deps authenticate, rateLimit, list, signDownload, readBlob
 */
export async function handleApi(request, deps) {
  const method = String(request.method || "GET").toUpperCase();
  const path = normalizePath(request.path || "/");
  if (method !== "GET" && method !== "HEAD") {
    return json(405, { error: "method_not_allowed" });
  }
  if (path === "/v1/health") {
    return json(200, { ok: true, service: "keepr-api" });
  }

  const token = bearerToken(request.header("authorization") || request.header("Authorization"));
  if (!token) return json(401, { error: "unauthorized" });
  const auth = await deps.authenticate(token);
  if (!auth?.uid) return json(401, { error: "unauthorized" });

  const limitDecision = await deps.rateLimit(auth.uid);
  if (!limitDecision?.allowed) {
    return json(429, { error: "rate_limited" }, { retryAfter: limitDecision?.retryAfter ?? 60 });
  }

  const query = normalizeQuery(request.query);
  const ttl = urlTtl(query);
  if (ttl.error) return json(400, ttl.error);

  try {
    const response = await route({ path, query, uid: auth.uid, ttl: ttl.seconds, deps, head: method === "HEAD" });
    return response;
  } catch (error) {
    if (error?.status && error?.body) return json(error.status, error.body);
    throw error;
  }
}

async function route({ path, query, uid, ttl, deps, head }) {
  const contactFiles = path.match(/^\/v1\/contacts\/([^/]+)\/files$/);
  const contactInteractions = path.match(/^\/v1\/contacts\/([^/]+)\/interactions$/);
  const contactNotes = path.match(/^\/v1\/contacts\/([^/]+)\/notes$/);
  const contactSections = path.match(/^\/v1\/contacts\/([^/]+)\/sections$/);
  const contactOne = path.match(/^\/v1\/contacts\/([^/]+)$/);
  const fileContent = path.match(/^\/v1\/files\/([^/]+)\/content$/);
  const fileOne = path.match(/^\/v1\/files\/([^/]+)$/);

  if (path === "/v1/contacts") return pageContacts(uid, query, ttl, deps);
  if (contactSections) return sections(uid, decodeURIComponent(contactSections[1]), ttl, deps);
  if (contactFiles) return pageChildren(uid, "files", decodeURIComponent(contactFiles[1]), query, ttl, deps);
  if (contactInteractions) return pageChildren(uid, "interactions", decodeURIComponent(contactInteractions[1]), query, ttl, deps);
  if (contactNotes) return pageChildren(uid, "notes", decodeURIComponent(contactNotes[1]), query, ttl, deps);
  if (contactOne) return oneContact(uid, decodeURIComponent(contactOne[1]), ttl, deps);
  if (fileContent) return fileBytes(uid, decodeURIComponent(fileContent[1]), deps, head);
  if (fileOne) return oneFile(uid, decodeURIComponent(fileOne[1]), ttl, deps);
  if (path === "/v1/interactions") return pageCollection(uid, "interactions", query, ttl, deps);
  if (path === "/v1/notes") return pageCollection(uid, "notes", query, ttl, deps);
  if (path === "/v1/files") return pageCollection(uid, "files", query, ttl, deps);
  if (path === "/v1/tags") return pageCollection(uid, "tags", query, ttl, deps);
  if (path === "/v1/groups") return pageCollection(uid, "groups", query, ttl, deps);
  if (path === "/v1/links") return pageCollection(uid, "links", query, ttl, deps);
  if (path === "/v1/follow-ups") return followUps(uid, query, deps);
  if (path === "/v1/dates") return dates(uid, query, deps);
  if (path === "/v1/upcoming") return upcoming(uid, query, deps);
  if (path === "/v1/preferences") return preferences(uid, deps);
  return json(404, { error: "not_found" });
}

function normalizePath(path) {
  const withoutQuery = path.split("?")[0];
  const stripped = withoutQuery.replace(/\/+$/, "") || "/";
  return stripped.startsWith("/api/") ? stripped.slice(4) : stripped === "/api" ? "/" : stripped;
}

function normalizeQuery(query) {
  const out = {};
  for (const [key, value] of Object.entries(query || {})) {
    if (Array.isArray(value)) out[key] = value[0] ?? "";
    else if (value == null) out[key] = "";
    else out[key] = String(value);
  }
  return out;
}

function urlTtl(query) {
  if (query.urlTtl == null || query.urlTtl === "") return { seconds: DEFAULT_URL_TTL_SECONDS };
  const seconds = Number(query.urlTtl);
  if (!Number.isInteger(seconds) || seconds < 60 || seconds > MAX_URL_TTL_SECONDS) {
    return { error: { error: "bad_request", message: "urlTtl must be a whole number of seconds from 60 to 3600." } };
  }
  return { seconds };
}

function pageLimit(query) {
  if (query.limit == null || query.limit === "") return { limit: DEFAULT_LIMIT };
  const limit = Number(query.limit);
  if (!Number.isInteger(limit) || limit < 1) {
    throw bad(400, { error: "bad_request", message: "limit must be a whole number from 1 to 100." });
  }
  return { limit: Math.min(limit, MAX_LIMIT) };
}

function dateRange(query, { required }) {
  const from = query.from || "";
  const to = query.to || "";
  if (!from && !to && !required) return { from: null, to: null };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to) || from > to) {
    throw bad(400, { error: "bad_request", message: "from and to are required calendar dates, YYYY-MM-DD, with from on or before to." });
  }
  const span = (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000;
  if (span > 400) {
    throw bad(400, { error: "bad_request", message: "Date ranges are limited to 400 days." });
  }
  return { from, to };
}

async function load(uid, collection, deps) {
  const rows = await deps.list(uid, collection);
  const capped = rows.slice(0, SCAN_CAP);
  return { rows: capped.map(normalizeDoc), truncated: rows.length > SCAN_CAP };
}

function normalizeDoc(doc) {
  const out = { id: doc.id };
  for (const [key, value] of Object.entries(doc)) {
    if (key === "id") continue;
    out[key] = normalizeValue(value);
  }
  return out;
}

function normalizeValue(value) {
  if (value == null) return null;
  if (typeof value?.toDate === "function") return value.toDate().toISOString();
  if (value instanceof Date) return value.toISOString();
  if (Array.isArray(value)) return value.map(normalizeValue);
  return value;
}

async function pageContacts(uid, query, ttl, deps) {
  const { limit } = pageLimit(query);
  const { rows, truncated } = await load(uid, "people", deps);
  const tags = (await load(uid, "tags", deps)).rows;
  const groups = (await load(uid, "groups", deps)).rows;
  const needle = (query.q || "").trim().toLowerCase();
  const matched = needle ? rows.filter((row) => contactHaystack(row).includes(needle)) : rows;
  const page = paginate(matched, limit, query.cursor);
  if (page.error) return json(400, page.error);
  const items = [];
  for (const row of page.items) items.push(await presentContact(row, tags, groups, uid, ttl, deps));
  return json(200, { items, nextCursor: page.nextCursor, truncated: truncated || page.truncated });
}

async function oneContact(uid, id, ttl, deps) {
  const { rows } = await load(uid, "people", deps);
  const row = rows.find((item) => item.id === id);
  if (!row) return json(404, { error: "not_found" });
  const tags = (await load(uid, "tags", deps)).rows;
  const groups = (await load(uid, "groups", deps)).rows;
  return json(200, await presentContact(row, tags, groups, uid, ttl, deps));
}

async function sections(uid, id, ttl, deps) {
  const people = (await load(uid, "people", deps)).rows;
  const person = people.find((item) => item.id === id);
  if (!person) return json(404, { error: "not_found" });
  const tags = (await load(uid, "tags", deps)).rows;
  const groups = (await load(uid, "groups", deps)).rows;
  const memories = (await load(uid, "memories", deps)).rows.filter((row) => row.personId === id);
  const notes = (await load(uid, "notes", deps)).rows.filter((row) => row.personId === id);
  const files = (await load(uid, "files", deps)).rows.filter((row) => row.personId === id);
  const interactions = (await load(uid, "interactions", deps)).rows.filter((row) => row.personId === id);
  const followUps = (await load(uid, "followUps", deps)).rows.filter((row) => row.personId === id);
  const links = (await load(uid, "links", deps)).rows.filter((row) => row.personAId === id || row.personBId === id);
  const presentedFiles = [];
  for (const file of files) presentedFiles.push(await presentFile(file, uid, ttl, deps));
  const memoryGroups = groupMemories(memories);
  return json(200, {
    contactId: id,
    sections: [
      { id: "work", title: "Work", fields: pick(person, ["company", "jobTitle", "workNote"]) },
      { id: "memories", title: "Remembered", groups: memoryGroups },
      { id: "notes", title: "Notes", notes },
      { id: "files", title: "Files", files: presentedFiles },
      {
        id: "groups",
        title: "Groups",
        groups: (person.groupIds || []).map((groupId) => groups.find((group) => group.id === groupId)).filter(Boolean)
      },
      {
        id: "connections",
        title: "Connections",
        links: links.map((link) => ({
          ...link,
          otherPersonId: link.personAId === id ? link.personBId : link.personAId,
          label: link.personAId === id ? link.labelAToB : link.labelBToA
        }))
      },
      { id: "interactions", title: "Interactions", interactions },
      { id: "followUps", title: "Follow-ups", followUps },
      {
        id: "details",
        title: "Details",
        fields: {
          ...pick(person, ["context", "priority", "status", "howWeMet", "introducedBy", "notes", "birthday", "postalAddress", "contactNote", "cadenceDays", "lastInteractionAt", "createdAt"]),
          tags: (person.tagIds || []).map((tagId) => tags.find((tag) => tag.id === tagId)).filter(Boolean)
        }
      }
    ]
  });
}

async function pageChildren(uid, collection, personId, query, ttl, deps) {
  const people = (await load(uid, "people", deps)).rows;
  if (!people.some((person) => person.id === personId)) return json(404, { error: "not_found" });
  const { limit } = pageLimit(query);
  const { rows, truncated } = await load(uid, collection, deps);
  const matched = rows.filter((row) => row.personId === personId);
  return finishPage(matched, limit, query, truncated, uid, ttl, deps, collection);
}

async function pageCollection(uid, collection, query, ttl, deps) {
  const { limit } = pageLimit(query);
  const range = collection === "interactions" ? dateRange(query, { required: false }) : { from: null, to: null };
  const { rows, truncated } = await load(uid, collection, deps);
  const needle = (query.q || "").trim().toLowerCase();
  let matched = rows;
  if (needle) matched = matched.filter((row) => haystack(row).includes(needle));
  if (range.from) {
    matched = matched.filter((row) => {
      const day = String(row.occurredAt || "").slice(0, 10);
      return day >= range.from && day <= range.to;
    });
  }
  return finishPage(matched, limit, query, truncated, uid, ttl, deps, collection);
}

async function finishPage(rows, limit, query, truncated, uid, ttl, deps, collection) {
  const page = paginate(rows, limit, query.cursor);
  if (page.error) return json(400, page.error);
  const items = [];
  for (const row of page.items) {
    items.push(collection === "files" ? await presentFile(row, uid, ttl, deps) : row);
  }
  return json(200, { items, nextCursor: page.nextCursor, truncated: truncated || page.truncated });
}

async function oneFile(uid, id, ttl, deps) {
  const { rows } = await load(uid, "files", deps);
  const row = rows.find((item) => item.id === id);
  if (!row) return json(404, { error: "not_found" });
  return json(200, await presentFile(row, uid, ttl, deps));
}

async function fileBytes(uid, id, deps, head) {
  const { rows } = await load(uid, "files", deps);
  const row = rows.find((item) => item.id === id);
  if (!row) return json(404, { error: "not_found" });
  if (!row.dataPath) return json(404, { error: "not_found" });
  const blob = await deps.readBlob(uid, row.dataPath);
  if (!blob) return json(404, { error: "not_found" });
  return {
    status: 200,
    bytes: head ? new Uint8Array() : blob.bytes,
    contentType: blob.contentType || row.dataContentType || "application/octet-stream",
    headers: {
      "Content-Disposition": `attachment; filename="${safeFilename(row.title, row.fileExtension)}"`,
      "Cache-Control": "private, no-store"
    }
  };
}

async function followUps(uid, query, deps) {
  const range = dateRange(query, { required: true });
  const { limit } = pageLimit(query);
  const includeCompleted = query.includeCompleted === "true";
  const { rows, truncated } = await load(uid, "followUps", deps);
  const matched = rows.filter((row) => {
    if (!includeCompleted && row.isCompleted === true) return false;
    const day = String(row.dueDate || "").slice(0, 10);
    return day >= range.from && day <= range.to;
  });
  const page = paginate(matched, limit, query.cursor, "dueDate");
  if (page.error) return json(400, page.error);
  return json(200, { from: range.from, to: range.to, items: page.items, nextCursor: page.nextCursor, truncated });
}

async function dates(uid, query, deps) {
  const range = dateRange(query, { required: true });
  const { rows: people } = await load(uid, "people", deps);
  const { rows: memories } = await load(uid, "memories", deps);
  const birthdays = [];
  for (const person of people) {
    for (const day of birthdayOccurrences(person.birthday, range.from, range.to)) {
      birthdays.push({
        kind: "birthday",
        date: day,
        contactId: person.id,
        name: displayName(person),
        birthday: person.birthday
      });
    }
  }
  birthdays.sort((a, b) => a.date.localeCompare(b.date) || a.contactId.localeCompare(b.contactId));
  const importantDates = memories
    .filter((memory) => memory.category === "importantDate" && memory.isArchived !== true)
    .map((memory) => ({
      kind: "memory",
      contactId: memory.personId ?? null,
      memoryId: memory.id,
      text: memory.content,
      structured: false,
      date: null
    }));
  return json(200, { from: range.from, to: range.to, birthdays, importantDates });
}

async function upcoming(uid, query, deps) {
  const datesBody = (await dates(uid, query, deps)).json;
  const followBody = (await followUps(uid, query, deps)).json;
  return json(200, {
    from: datesBody.from,
    to: datesBody.to,
    birthdays: datesBody.birthdays,
    importantDates: datesBody.importantDates,
    followUps: followBody.items,
    followUpsNextCursor: followBody.nextCursor
  });
}

async function preferences(uid, deps) {
  const { rows } = await load(uid, "preferences", deps);
  const current = rows.find((row) => row.id === "current") || rows[0] || null;
  return json(200, { preferences: current });
}

export function birthdayOccurrences(birthday, fromDay, toDay) {
  if (!birthday) return [];
  const parsed = new Date(birthday);
  if (Number.isNaN(parsed.getTime())) return [];
  const month = parsed.getUTCMonth();
  const day = parsed.getUTCDate();
  const startYear = Number(fromDay.slice(0, 4));
  const endYear = Number(toDay.slice(0, 4));
  const hits = [];
  for (let year = startYear; year <= endYear; year += 1) {
    const occurrence = new Date(Date.UTC(year, month, day));
    if (occurrence.getUTCMonth() !== month || occurrence.getUTCDate() !== day) continue;
    const key = occurrence.toISOString().slice(0, 10);
    if (key >= fromDay && key <= toDay) hits.push(key);
  }
  return hits;
}

function groupMemories(memories) {
  const buckets = new Map();
  for (const memory of memories) {
    if (memory.isArchived === true) continue;
    const category = memory.category || "other";
    if (!buckets.has(category)) buckets.set(category, []);
    buckets.get(category).push(memory);
  }
  return [...buckets.entries()].map(([category, items]) => ({
    category,
    title: CATEGORY_TITLES[category] || category,
    memories: items
  }));
}

async function presentContact(row, tags, groups, uid, ttl, deps) {
  const photo = await sign(uid, row.photoPath, ttl, deps);
  return {
    id: row.id,
    contactIdentifier: row.contactIdentifier ?? null,
    givenName: row.givenName ?? "",
    familyName: row.familyName ?? "",
    preferredName: row.preferredName ?? null,
    company: row.company ?? null,
    jobTitle: row.jobTitle ?? null,
    workNote: row.workNote ?? null,
    phoneNumbers: row.phoneNumbers ?? [],
    emailAddresses: row.emailAddresses ?? [],
    context: row.context ?? null,
    priority: row.priority ?? null,
    status: row.status ?? null,
    isFavorite: row.isFavorite === true,
    createdAt: row.createdAt ?? null,
    updatedAt: row.updatedAt ?? null,
    modifiedAt: row.modifiedAt ?? null,
    lastInteractionAt: row.lastInteractionAt ?? null,
    howWeMet: row.howWeMet ?? null,
    introducedBy: row.introducedBy ?? null,
    notes: row.notes ?? null,
    birthday: row.birthday ?? null,
    postalAddress: row.postalAddress ?? null,
    contactNote: row.contactNote ?? null,
    cadenceDays: row.cadenceDays ?? null,
    awaitingReplySince: row.awaitingReplySince ?? null,
    reviewedAt: row.reviewedAt ?? null,
    tagIds: row.tagIds ?? [],
    groupIds: row.groupIds ?? [],
    tags: (row.tagIds ?? []).map((id) => tags.find((tag) => tag.id === id)).filter(Boolean),
    groups: (row.groupIds ?? []).map((id) => groups.find((group) => group.id === id)).filter(Boolean),
    photoUrl: photo?.url ?? null,
    photoUrlExpiresAt: photo?.expiresAt ?? null
  };
}

async function presentFile(row, uid, ttl, deps) {
  const download = await sign(uid, row.dataPath, ttl, deps);
  const thumbnail = await sign(uid, row.thumbnailPath, ttl, deps);
  return {
    ...row,
    downloadUrl: download?.url ?? null,
    downloadUrlExpiresAt: download?.expiresAt ?? null,
    thumbnailUrl: thumbnail?.url ?? null,
    thumbnailUrlExpiresAt: thumbnail?.expiresAt ?? null,
    contentPath: `/v1/files/${encodeURIComponent(row.id)}/content`
  };
}

async function sign(uid, relativePath, ttl, deps) {
  if (!relativePath || typeof relativePath !== "string") return null;
  if (relativePath.startsWith("/") || relativePath.includes("..") || relativePath.includes("\\")) return null;
  return deps.signDownload(uid, relativePath, ttl);
}

function contactHaystack(row) {
  return [
    row.givenName, row.familyName, row.preferredName, row.company, row.jobTitle, row.workNote,
    row.notes, row.contactNote, row.howWeMet, row.introducedBy, row.postalAddress,
    ...(row.phoneNumbers || []), ...(row.emailAddresses || [])
  ].filter(Boolean).join("\n").toLowerCase();
}

function haystack(row) {
  return Object.values(row).flatMap((value) => (Array.isArray(value) ? value : [value]))
    .filter((value) => typeof value === "string")
    .join("\n")
    .toLowerCase();
}

function displayName(person) {
  const preferred = String(person.preferredName || "").trim();
  if (preferred) return preferred;
  const name = [person.givenName, person.familyName].filter(Boolean).join(" ").trim();
  return name || "New Person";
}

function pick(source, keys) {
  const out = {};
  for (const key of keys) out[key] = source[key] ?? null;
  return out;
}

function paginate(items, limit, cursor, timeField = "modifiedAt") {
  const sorted = items.slice().sort((a, b) => compareItems(a, b, timeField));
  let start = 0;
  if (cursor) {
    const decoded = decodeCursor(cursor);
    if (!decoded) return { error: { error: "bad_request", message: "cursor is not valid." } };
    const index = sorted.findIndex((item) => item.id === decoded.id && String(item[timeField] || "") === decoded.time);
    start = index === -1 ? sorted.length : index + 1;
  }
  const page = sorted.slice(start, start + limit);
  const last = page[page.length - 1];
  const nextCursor = start + limit < sorted.length && last
    ? encodeCursor(last.id, String(last[timeField] || ""))
    : null;
  return { items: page, nextCursor, truncated: false };
}

function compareItems(a, b, timeField) {
  const at = String(a[timeField] || "");
  const bt = String(b[timeField] || "");
  if (at !== bt) return at < bt ? 1 : -1;
  if (a.id !== b.id) return a.id < b.id ? 1 : -1;
  return 0;
}

function encodeCursor(id, time) {
  return Buffer.from(JSON.stringify({ id, time }), "utf8").toString("base64url");
}

function decodeCursor(cursor) {
  try {
    const parsed = JSON.parse(Buffer.from(cursor, "base64url").toString("utf8"));
    if (!parsed || typeof parsed.id !== "string" || typeof parsed.time !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
}

function safeFilename(title, ext) {
  const base = String(title || "file").replace(/[^\w .\-]+/g, "").trim().slice(0, 80) || "file";
  const extension = String(ext || "").replace(/[^\w]+/g, "");
  return extension ? `${base}.${extension}` : base;
}

function bad(status, body) {
  const error = new Error(body.error || "bad_request");
  error.status = status;
  error.body = body;
  return error;
}

function json(status, body, extra = {}) {
  const headers = { "Cache-Control": "private, no-store" };
  if (extra.retryAfter) headers["Retry-After"] = String(extra.retryAfter);
  return { status, json: body, headers };
}
