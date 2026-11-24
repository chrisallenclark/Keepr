import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { tokenHash } from "./api.js";

/**
 * Admin SDK wiring for the read API. Rules do not apply here, so every query
 * is scoped to the uid on the token and nothing else.
 */
export function createFirebaseDeps() {
  const db = getFirestore();
  const bucket = getStorage().bucket();

  return {
    async authenticate(token) {
      const hash = tokenHash(token);
      const snap = await db.collectionGroup("tokens").where("hash", "==", hash).limit(5).get();
      const matches = snap.docs.filter((doc) => !doc.get("revokedAt"));
      if (matches.length !== 1) return null;
      const uid = matches[0].ref.parent.parent?.id;
      if (!uid) return null;
      matches[0].ref.set({ lastUsedAt: FieldValue.serverTimestamp() }, { merge: true }).catch((error) => {
        console.error("token last-used update failed", error?.code || "unknown");
      });
      return { uid };
    },

    async rateLimit(uid) {
      const limit = Number(process.env.KEEPR_RATE_LIMIT_PER_MINUTE || 60);
      const now = Date.now();
      const minute = Math.floor(now / 60000);
      const ref = db.collection("rateLimits").doc(`${uid}_${minute}`);
      const count = await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const next = (snap.exists ? Number(snap.get("count") || 0) : 0) + 1;
        tx.set(ref, { count: next, uid, minute }, { merge: true });
        return next;
      });
      return { allowed: count <= limit, retryAfter: 60 - (Math.floor(now / 1000) % 60) };
    },

    async list(uid, collection) {
      const snap = await db.collection("users").doc(uid).collection(collection).get();
      return snap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    },

    async signDownload(uid, relativePath, ttlSeconds) {
      const objectPath = ownedPath(uid, relativePath);
      if (!objectPath) return null;
      const expiresAt = new Date(Date.now() + ttlSeconds * 1000);
      try {
        const [url] = await bucket.file(objectPath).getSignedUrl({
          action: "read",
          expires: expiresAt
        });
        return { url, expiresAt: expiresAt.toISOString() };
      } catch (error) {
        console.error("signed url unavailable", error?.code || error?.name || "unknown");
        return null;
      }
    },

    async readBlob(uid, relativePath) {
      const objectPath = ownedPath(uid, relativePath);
      if (!objectPath) return null;
      const file = bucket.file(objectPath);
      const [exists] = await file.exists();
      if (!exists) return null;
      const [bytes] = await file.download();
      const [meta] = await file.getMetadata();
      return { bytes, contentType: meta.contentType || "application/octet-stream" };
    }
  };
}

function ownedPath(uid, relativePath) {
  if (typeof relativePath !== "string" || relativePath.length === 0) return null;
  if (relativePath.startsWith("/") || relativePath.includes("..") || relativePath.includes("\\")) return null;
  return `users/${uid}/${relativePath}`;
}
