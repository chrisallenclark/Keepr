import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, before, after } from "node:test";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from "firebase/firestore";
import { ref, uploadString, getBytes } from "firebase/storage";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const projectId = "demo-keepr";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules: readFileSync(resolve(root, "firestore.rules"), "utf8") },
    storage: { rules: readFileSync(resolve(root, "storage.rules"), "utf8") }
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

function token(createdAt = new Date()) {
  return {
    schemaVersion: 1,
    hash: "a".repeat(64),
    prefix: "kpr_abcd",
    label: "Assistant",
    createdAt,
    revokedAt: null
  };
}

describe("firestore rules", () => {
  it("lets a user read and write only their own records", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    const stranger = testEnv.unauthenticatedContext().firestore();
    const person = doc(alice, "users/alice/people/p1");
    await assertSucceeds(setDoc(person, { givenName: "Ada", modifiedAt: new Date() }));
    await assertSucceeds(getDoc(person));
    await assertFails(getDoc(doc(bob, "users/alice/people/p1")));
    await assertFails(setDoc(doc(bob, "users/alice/people/p2"), { givenName: "No" }));
    await assertFails(getDoc(doc(stranger, "users/alice/people/p1")));
    await assertFails(getDoc(doc(alice, "users/bob/people/p1")));
  });

  it("stores tokens as hashes and keeps them private", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const bob = testEnv.authenticatedContext("bob").firestore();
    const ref = doc(alice, "users/alice/tokens/t1");
    await assertSucceeds(setDoc(ref, token()));
    await assertFails(setDoc(doc(alice, "users/alice/tokens/raw"), {
      ...token(),
      hash: "kpr_this_is_not_a_hash"
    }));
    await assertFails(getDoc(doc(bob, "users/alice/tokens/t1")));
    await assertSucceeds(updateDoc(ref, { revokedAt: new Date() }));
    await assertFails(updateDoc(ref, { hash: "b".repeat(64) }));
    await assertSucceeds(deleteDoc(ref));
  });

  it("blocks client access to rate limits", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, "rateLimits/alice_1"), { count: 1 }));
    await assertFails(getDoc(doc(alice, "users/alice/rateLimits/minute")));
  });
});

describe("storage rules", () => {
  it("lets a user read only their own files", async () => {
    const alice = testEnv.authenticatedContext("alice").storage();
    const bob = testEnv.authenticatedContext("bob").storage();
    const file = ref(alice, "users/alice/files/scan");
    await assertSucceeds(uploadString(file, "hello"));
    await assertSucceeds(getBytes(file));
    await assertFails(getBytes(ref(bob, "users/alice/files/scan")));
    await assertFails(uploadString(ref(bob, "users/alice/files/other"), "nope"));
  });
});
