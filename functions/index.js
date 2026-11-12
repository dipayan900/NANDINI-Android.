const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.notifyConnectMessage = onDocumentCreated(
  "connect_conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message || !message.senderId) return;

    const db = getFirestore();
    const conversationId = event.params.conversationId;
    const conversation = await db.collection("connect_conversations")
      .doc(conversationId).get();
    if (!conversation.exists) return;

    const members = conversation.get("members") || [];
    const recipients = members.filter((uid) => uid !== message.senderId);
    if (!recipients.length) return;

    const tokenRefs = [];
    for (const uid of recipients) {
      const devices = await db.collection("connect_profiles")
        .doc(uid).collection("devices").get();
      for (const device of devices.docs) {
        const token = device.get("token");
        if (typeof token === "string" && token.length > 0) {
          tokenRefs.push({ ref: device.ref, token });
        }
      }
    }
    if (!tokenRefs.length) return;

    // Never include message text, media URLs, usernames or decrypted content.
    // This payload remains content-free when the app adopts E2EE.
    const result = await getMessaging().sendEachForMulticast({
      tokens: tokenRefs.map((item) => item.token),
      data: {
        type: "connect_message",
        conversationId,
        messageId: event.params.messageId
      },
      android: {
        priority: "high",
        ttl: 7 * 24 * 60 * 60 * 1000
      }
    });

    const stale = [];
    result.responses.forEach((response, index) => {
      if (!response.success) {
        const code = response.error?.code || "";
        if (code.includes("registration-token-not-registered") ||
            code.includes("invalid-registration-token")) {
          stale.push(tokenRefs[index].ref.delete());
        }
      }
    });
    await Promise.all(stale);
  }
);
