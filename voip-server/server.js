"use strict";
const http = require("http");
const WebSocket = require("ws");
const admin = require("firebase-admin");
const PORT = Number(process.env.PORT || 8080);
const MAX_ROOM_MEMBERS = 2;
const MAX_PAYLOAD = 128 * 1024;

try {
  if (!admin.apps.length) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    if (raw) admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
    else admin.initializeApp({ credential: admin.credential.applicationDefault() });
  }
} catch (error) {
  console.error("Firebase Admin initialization failed:", error.message);
}
const authReady = admin.apps.length > 0;
const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(authReady ? 200 : 503, { "content-type": "application/json" });
    return res.end(JSON.stringify({ service: "ARUSHI authenticated VoIP signaling", status: authReady ? "ok" : "configuration_required", firebaseAdmin: authReady }));
  }
  res.writeHead(404); res.end("Not found");
});
const wss = new WebSocket.Server({ noServer: true, maxPayload: MAX_PAYLOAD });
const rooms = new Map();
const recentMessages = new WeakMap();

function send(ws, value) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value));
}
function rejectUpgrade(socket, status, message) {
  socket.write(`HTTP/1.1 ${status} ${message}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  socket.destroy();
}
server.on("upgrade", async (request, socket, head) => {
  if (!authReady) return rejectUpgrade(socket, "503", "Service Unavailable");
  const match = /^Bearer ([A-Za-z0-9._-]+)$/.exec(String(request.headers.authorization || ""));
  if (!match) return rejectUpgrade(socket, "401", "Unauthorized");
  try {
    const decoded = await admin.auth().verifyIdToken(match[1], true);
    request.arushiUid = decoded.uid;
    wss.handleUpgrade(request, socket, head, ws => {
      ws.uid = decoded.uid;
      wss.emit("connection", ws, request);
    });
  } catch (_) {
    rejectUpgrade(socket, "401", "Unauthorized");
  }
});
function remove(ws) {
  if (!ws.room) return;
  const room = rooms.get(ws.room);
  if (room) {
    room.delete(ws);
    for (const peer of room) send(peer, { type: "peer-left", peerId: ws.uid });
    if (!room.size) rooms.delete(ws.room);
  }
  ws.room = null;
}
async function canJoinConversation(uid, conversationId) {
  if (!/^[A-Za-z0-9_-]{4,128}$/.test(conversationId)) return false;
  const doc = await admin.firestore().collection("connect_conversations").doc(conversationId).get();
  if (!doc.exists) return false;
  const members = doc.get("members");
  return Array.isArray(members) && members.includes(uid);
}
wss.on("connection", ws => {
  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });
  ws.on("message", async raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { return send(ws, { type: "error", message: "Invalid JSON message." }); }
    const now = Date.now();
    const recent = (recentMessages.get(ws) || []).filter(time => now - time < 1000);
    if (recent.length >= 30) return send(ws, { type: "error", message: "Signaling rate limit reached." });
    recent.push(now); recentMessages.set(ws, recent);
    if (msg.type === "join") {
      const conversationId = String(msg.room || "").trim();
      try {
        if (!(await canJoinConversation(ws.uid, conversationId))) {
          return send(ws, { type: "error", message: "You are not a member of this ARUSHI Connect conversation." });
        }
      } catch (_) {
        return send(ws, { type: "error", message: "Could not verify conversation membership." });
      }
      remove(ws);
      const room = rooms.get(conversationId) || new Set();
      if ([...room].some(peer => peer.uid === ws.uid)) return send(ws, { type: "error", message: "This account is already connected to this call." });
      if (room.size >= MAX_ROOM_MEMBERS) return send(ws, { type: "error", message: "This call already has two participants." });
      ws.room = conversationId;
      const existing = [...room];
      room.add(ws); rooms.set(conversationId, room);
      send(ws, { type: "joined", room: conversationId, peers: existing.length });
      for (const peer of existing) send(peer, { type: "peer-joined", peerId: ws.uid });
      return;
    }
    if (!ws.room) return send(ws, { type: "error", message: "Join a Connect conversation before signaling." });
    if (!["offer", "answer", "ice", "hangup", "tts"].includes(msg.type)) return;
    const room = rooms.get(ws.room);
    if (!room) return;
    const safe = { type: msg.type, from: ws.uid };
    if (msg.type === "offer" || msg.type === "answer") {
      const sdp = String(msg.sdp || "");
      if (!sdp || sdp.length > 64 * 1024) return send(ws, { type: "error", message: "Invalid SDP payload." });
      safe.sdp = sdp;
    }
    if (msg.type === "ice") {
      const c = msg.candidate;
      if (!c || String(c.candidate || "").length > 4096) return send(ws, { type: "error", message: "Invalid ICE candidate." });
      safe.candidate = { sdpMid: String(c.sdpMid || "").slice(0, 128), sdpMLineIndex: Number(c.sdpMLineIndex || 0), candidate: String(c.candidate) };
    }
    if (msg.type === "tts") safe.text = String(msg.text || "").slice(0, 500);
    for (const peer of room) if (peer !== ws) send(peer, safe);
  });
  ws.on("close", () => remove(ws));
  ws.on("error", () => remove(ws));
});
const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) { ws.terminate(); continue; }
    ws.isAlive = false; ws.ping();
  }
}, 30000);
wss.on("close", () => clearInterval(heartbeat));
server.listen(PORT, "0.0.0.0", () => console.log("ARUSHI authenticated signaling listening on " + PORT));
