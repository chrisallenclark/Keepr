import { initializeApp } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import { handleApi } from "./api.js";
import { createFirebaseDeps } from "./firebaseDeps.js";

initializeApp();
const deps = createFirebaseDeps();

export const api = onRequest(
  {
    cors: false,
    invoker: "public",
    memory: "256MiB",
    timeoutSeconds: 60,
    maxInstances: 20
  },
  async (req, res) => {
    try {
      const result = await handleApi(
        {
          method: req.method,
          path: req.path || req.url || "/",
          query: req.query || {},
          header: (name) => req.get(name)
        },
        deps
      );
      res.set("Cache-Control", "private, no-store");
      for (const [key, value] of Object.entries(result.headers || {})) res.set(key, value);
      if (result.bytes) {
        res.set("Content-Type", result.contentType || "application/octet-stream");
        res.status(result.status).send(Buffer.from(result.bytes));
        return;
      }
      res.status(result.status).json(result.json ?? {});
    } catch (error) {
      console.error("keepr api failed", error instanceof Error ? error.name : "unknown");
      res.status(500).json({ error: "internal" });
    }
  }
);
