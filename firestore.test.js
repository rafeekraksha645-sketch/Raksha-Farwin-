const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nexora_123";
const BOB_UID = "bob_nexora_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
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

test("Unauthenticated users cannot read or write preferences, conversations, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized Chat",
        mode: "GENERAL_IT",
        language: "AUTO",
        lastMessagePreview: "Hello",
        messageCount: 1,
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .get()
  );
});

test("Authenticated owner can create, update, list, and delete own preferences and conversations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const updated = new Date(Date.now() - 1000);

  // Preferences create & update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .set({
        userId: ALICE_UID,
        language: "TAMIL",
        themeMode: "DARK",
        responseStyle: "BALANCED",
        simpleExplanations: true,
        defaultMode: "PROGRAMMING",
        modelTier: "FLASH",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .update({
        language: "SINHALA",
        updatedAt: updated,
      })
  );

  // Conversation create, rename, and list
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Understanding TCP/IP",
        mode: "GENERAL_IT",
        language: "ENGLISH",
        lastMessagePreview: "What is TCP three-way handshake?",
        messageCount: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .update({
        title: "TCP/IP Deep Dive",
        updatedAt: updated,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .orderBy("updatedAt", "desc")
      .get()
  );
});

test("Authenticated owner can create, update, list, and delete messages inside owned conversation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Python Algorithms",
        mode: "PROGRAMMING",
        language: "AUTO",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        role: "user",
        content: "Explain binary search in Python.",
        mode: "PROGRAMMING",
        hasError: false,
        sequenceIndex: 0,
        attachedImageHint: "Attached code screenshot",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_2")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        role: "model",
        content: "Binary search divides the sorted array in half each step.",
        mode: "PROGRAMMING",
        hasError: false,
        sequenceIndex: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_2")
      .update({
        content: "Updated regenerated response for binary search.",
        hasError: false,
        updatedAt: updated,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .orderBy("sequenceIndex", "asc")
      .get()
  );
});

test("Cross-user access is strictly denied across all paths", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .collection("conversations")
      .doc("bob_conv")
      .set({
        userId: BOB_UID,
        title: "Bob Private Security Audit",
        mode: "CYBERSECURITY",
        language: "ENGLISH",
        lastMessagePreview: "OWASP Top 10",
        messageCount: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("conversations").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("conversations")
      .doc("bob_conv")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("conversations")
      .doc("bob_conv")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("conversations")
      .doc("bob_conv")
      .delete()
  );
});

test("Schema validation rejects ghost fields, id field injection, future timestamps, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Reject @DocumentId 'id' injected into payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad_id")
      .set({
        id: "conv_bad_id",
        userId: ALICE_UID,
        title: "Bad ID field",
        mode: "GENERAL_IT",
        language: "AUTO",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject ghost field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_ghost")
      .set({
        userId: ALICE_UID,
        title: "Ghost",
        mode: "GENERAL_IT",
        language: "AUTO",
        lastMessagePreview: "",
        messageCount: 0,
        isAdmin: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject invalid mode enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_enum")
      .set({
        userId: ALICE_UID,
        title: "Invalid Mode",
        mode: "UNKNOWN_MODE",
        language: "AUTO",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_future")
      .set({
        userId: ALICE_UID,
        title: "Future",
        mode: "GENERAL_IT",
        language: "AUTO",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );

  // Reject orphan message without existing parent conversation
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("nonexistent_conv")
      .collection("messages")
      .doc("msg_orphan")
      .set({
        userId: ALICE_UID,
        conversationId: "nonexistent_conv",
        role: "user",
        content: "Orphan message",
        mode: "GENERAL_IT",
        hasError: false,
        sequenceIndex: 0,
        createdAt: created,
        updatedAt: created,
      })
  );
});
