const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("unauthenticated user cannot read or write vaccines", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("users/user123/vaccines").get());
  await assertFails(
    unauthedDb.collection("users/user123/vaccines").doc("1").set({
      id: 1,
      name: "BCG",
      currentStock: 10,
    })
  );
});

test("authenticated user can write valid vaccine to their own path", async () => {
  const authedDb = testEnv.authenticatedContext("user123").firestore();
  await assertSucceeds(
    authedDb.collection("users/user123/vaccines").doc("1").set({
      id: 1,
      name: "BCG",
      currentStock: 10,
    })
  );
});

test("user cannot read or write to another user path", async () => {
  const authedDb = testEnv.authenticatedContext("user123").firestore();
  await assertFails(authedDb.collection("users/otherUser/vaccines").get());
  await assertFails(
    authedDb.collection("users/otherUser/vaccines").doc("1").set({
      id: 1,
      name: "BCG",
      currentStock: 10,
    })
  );
});

test("authenticated user can write clinic profile to their own path", async () => {
  const authedDb = testEnv.authenticatedContext("user123").firestore();
  await assertSucceeds(
    authedDb.collection("users/user123/clinicProfile").doc("profile").set({
      clinicName: "Dr Clinic",
      doctorName: "Dr Priyank",
      phone: "9876543210",
    })
  );
});
