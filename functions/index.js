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


const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const crypto = require("crypto");

const GOOGLE_CLIENT_ID = defineSecret("ARUSHI_GOOGLE_CLIENT_ID");
const GOOGLE_CLIENT_SECRET = defineSecret("ARUSHI_GOOGLE_CLIENT_SECRET");
const META_APP_ID = defineSecret("ARUSHI_META_APP_ID");
const META_APP_SECRET = defineSecret("ARUSHI_META_APP_SECRET");
const SOCIAL_ENCRYPTION_KEY = defineSecret("ARUSHI_SOCIAL_ENCRYPTION_KEY");

function encryptSocialToken(value, secret) {
  const key = Buffer.from(secret, "base64");
  if (key.length !== 32) throw new Error("ARUSHI_SOCIAL_ENCRYPTION_KEY must be base64 for 32 random bytes");
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
  return [iv, cipher.getAuthTag(), encrypted].map((part) => part.toString("base64")).join(".");
}
function decryptSocialToken(value, secret) {
  const key = Buffer.from(secret, "base64");
  if (key.length !== 32) throw new Error("Invalid social token encryption key");
  const [iv, tag, encrypted] = value.split(".").map((part) => Buffer.from(part, "base64"));
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString("utf8");
}
function callbackUri(req) {
  return `https://${req.get("host")}/socialApi?action=callback`;
}
async function requireSocialUser(req) {
  const header = req.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) throw new Error("Sign-in required");
  return (await require("firebase-admin/auth").getAuth().verifyIdToken(match[1])).uid;
}
async function socialJson(url, options = {}) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error_description || data.error?.message || data.error || `Provider HTTP ${response.status}`);
  return data;
}

exports.socialApi = onRequest({
  secrets: [GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, META_APP_ID, META_APP_SECRET, SOCIAL_ENCRYPTION_KEY],
  timeoutSeconds: 60,
  memory: "256MiB"
}, async (req, res) => {
  const db = getFirestore();
  const action = String(req.query.action || "");
  try {
    if (action === "start") {
      const uid = await requireSocialUser(req);
      const provider = String(req.query.provider || "");
      if (!["youtube", "instagram"].includes(provider)) return res.status(400).json({ error: "Unsupported provider" });
      const state = crypto.randomBytes(24).toString("hex");
      const redirectUri = callbackUri(req);
      await db.collection("social_oauth_states").doc(state).set({
        uid, provider, redirectUri, createdAt: Date.now()
      });
      let authUrl;
      if (provider === "youtube") {
        const params = new URLSearchParams({
          client_id: GOOGLE_CLIENT_ID.value(),
          redirect_uri: redirectUri,
          response_type: "code",
          scope: "https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/yt-analytics.readonly",
          access_type: "offline",
          prompt: "consent",
          state
        });
        authUrl = `https://accounts.google.com/o/oauth2/v2/auth?${params}`;
      } else {
        const params = new URLSearchParams({
          client_id: META_APP_ID.value(),
          redirect_uri: redirectUri,
          response_type: "code",
          scope: "instagram_business_basic,instagram_business_manage_insights",
          state
        });
        authUrl = `https://www.instagram.com/oauth/authorize?${params}`;
      }
      return res.json({ authUrl });
    }

    if (action === "callback") {
      const state = String(req.query.state || "");
      const code = String(req.query.code || "");
      const saved = await db.collection("social_oauth_states").doc(state).get();
      if (!saved.exists || !code || Date.now() - (saved.get("createdAt") || 0) > 10 * 60 * 1000) {
        return res.redirect("com.dipayan.nandini://social-auth?status=failed&reason=expired_state");
      }
      const { uid, provider, redirectUri } = saved.data();
      await saved.ref.delete();
      let tokenData;
      let account = {};
      if (provider === "youtube") {
        tokenData = await socialJson("https://oauth2.googleapis.com/token", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            code, client_id: GOOGLE_CLIENT_ID.value(), client_secret: GOOGLE_CLIENT_SECRET.value(),
            redirect_uri: redirectUri, grant_type: "authorization_code"
          })
        });
        const profile = await socialJson("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true", {
          headers: { Authorization: `Bearer ${tokenData.access_token}` }
        });
        const channel = profile.items?.[0];
        account = {
          name: channel?.snippet?.title || "YouTube channel",
          channelId: channel?.id || "",
          subscribers: Number(channel?.statistics?.subscriberCount || 0),
          views: Number(channel?.statistics?.viewCount || 0),
          videos: Number(channel?.statistics?.videoCount || 0)
        };
      } else if (provider === "instagram") {
        const form = new URLSearchParams({
          client_id: META_APP_ID.value(), client_secret: META_APP_SECRET.value(),
          grant_type: "authorization_code", redirect_uri: redirectUri, code
        });
        tokenData = await socialJson("https://api.instagram.com/oauth/access_token", {
          method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: form
        });
        const longToken = await socialJson(
          `https://graph.instagram.com/access_token?grant_type=ig_exchange&client_secret=${encodeURIComponent(META_APP_SECRET.value())}&access_token=${encodeURIComponent(tokenData.access_token)}`
        );
        tokenData.access_token = longToken.access_token || tokenData.access_token;
        tokenData.expires_in = longToken.expires_in || tokenData.expires_in;
        const profile = await socialJson(
          `https://graph.instagram.com/me?fields=user_id,username,account_type,media_count&access_token=${encodeURIComponent(tokenData.access_token)}`
        );
        account = { name: profile.username || "Instagram professional account", userId: profile.user_id, accountType: profile.account_type, mediaCount: Number(profile.media_count || 0) };
      }
      await db.collection("social_accounts").doc(`${uid}_${provider}`).set({
        uid, provider, account,
        token: encryptSocialToken(tokenData.access_token, SOCIAL_ENCRYPTION_KEY.value()),
        refreshToken: tokenData.refresh_token ? encryptSocialToken(tokenData.refresh_token, SOCIAL_ENCRYPTION_KEY.value()) : "",
        expiresAt: Date.now() + Number(tokenData.expires_in || 3600) * 1000,
        updatedAt: Date.now()
      });
      return res.redirect(`com.dipayan.nandini://social-auth?status=connected&provider=${provider}`);
    }

    const uid = await requireSocialUser(req);
    if (action === "disconnect") {
      const batch = db.batch();
      for (const provider of ["youtube", "instagram"]) {
        batch.delete(db.collection("social_accounts").doc(`${uid}_${provider}`));
      }
      await batch.commit();
      return res.json({ ok: true });
    }

    if (action === "analytics") {
      const accounts = [];
      for (const provider of ["youtube", "instagram"]) {
        const ref = db.collection("social_accounts").doc(`${uid}_${provider}`);
        const snap = await ref.get();
        if (!snap.exists) continue;
        const data = snap.data();
        let accessToken = decryptSocialToken(data.token, SOCIAL_ENCRYPTION_KEY.value());
        if (provider === "youtube" && data.expiresAt < Date.now() + 60_000 && data.refreshToken) {
          const refresh = decryptSocialToken(data.refreshToken, SOCIAL_ENCRYPTION_KEY.value());
          const fresh = await socialJson("https://oauth2.googleapis.com/token", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
              client_id: GOOGLE_CLIENT_ID.value(), client_secret: GOOGLE_CLIENT_SECRET.value(),
              refresh_token: refresh, grant_type: "refresh_token"
            })
          });
          accessToken = fresh.access_token;
          await ref.update({
            token: encryptSocialToken(accessToken, SOCIAL_ENCRYPTION_KEY.value()),
            expiresAt: Date.now() + Number(fresh.expires_in || 3600) * 1000
          });
        }
        let metrics = {};
        if (provider === "youtube") {
          const end = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
          const start = new Date(Date.now() - 29 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
          const report = await socialJson(
            `https://youtubeanalytics.googleapis.com/v2/reports?ids=channel%3D%3DMINE&startDate=${start}&endDate=${end}&metrics=views,likes,comments,subscribersGained&dimensions=day`,
            { headers: { Authorization: `Bearer ${accessToken}` } }
          );
          metrics = { last28Days: report.rows || [], columns: report.columnHeaders || [] };
        } else {
          const id = data.account.userId || "me";
          const insight = await socialJson(
            `https://graph.instagram.com/${encodeURIComponent(id)}/insights?metric=reach,accounts_engaged,total_interactions,views&period=day&access_token=${encodeURIComponent(accessToken)}`
          );
          metrics = { insights: insight.data || [] };
        }
        accounts.push({ provider, name: data.account.name, account: data.account, metrics });
      }
      return res.json({ accounts });
    }
    return res.status(404).json({ error: "Unknown social API action" });
  } catch (error) {
    console.error("ARUSHI social API error", error?.message || error);
    if (action === "callback") return res.redirect("com.dipayan.nandini://social-auth?status=failed&reason=provider_error");
    return res.status(400).json({ error: error?.message || "Social integration failed" });
  }
});
