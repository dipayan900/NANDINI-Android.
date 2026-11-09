"use strict";
const http = require("http");
const WebSocket = require("ws");
const PORT = Number(process.env.PORT || 8080);
const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ service: "ARUSHI VoIP signaling", status: "ok" }));
  }
  res.writeHead(404); res.end("Not found");
});
const wss = new WebSocket.Server({ server, maxPayload: 1024 * 1024 });
const rooms = new Map();
function send(ws, value) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value));
}
function remove(ws) {
  if (!ws.room) return;
  const room = rooms.get(ws.room);
  if (room) {
    room.delete(ws);
    for (const peer of room) send(peer, { type: "peer-left", peerId: ws.peerId });
    if (!room.size) rooms.delete(ws.room);
  }
  ws.room = null;
}
wss.on("connection", (ws) => {
  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });
  ws.on("message", (raw) => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return send(ws, { type: "error", message: "Invalid JSON message." }); }
    if (msg.type === "join") {
      const roomName = String(msg.room || "").trim();
      const peerId = String(msg.peerId || "").trim();
      if (!/^[a-zA-Z0-9_-]{4,48}$/.test(roomName) || !/^[a-zA-Z0-9_-]{1,64}$/.test(peerId)) {
        return send(ws, { type: "error", message: "Room code must be 4–48 letters/numbers/dashes/underscores." });
      }
      remove(ws);
      const room = rooms.get(roomName) || new Set();
      if (room.size >= 2) return send(ws, { type: "error", message: "This room already has two participants." });
      ws.room = roomName; ws.peerId = peerId;
      const existing = [...room];
      room.add(ws); rooms.set(roomName, room);
      send(ws, { type: "joined", room: roomName, peers: existing.length });
      for (const peer of existing) send(peer, { type: "peer-joined", peerId });
      return;
    }
    if (!ws.room) return send(ws, { type: "error", message: "Join a room before signaling." });
    if (!["offer", "answer", "ice", "hangup", "tts"].includes(msg.type)) return;
    const room = rooms.get(ws.room);
    if (!room) return;
    const safe = { type: msg.type, from: ws.peerId };
    if (msg.type === "offer" || msg.type === "answer") safe.sdp = msg.sdp;
    if (msg.type === "ice") safe.candidate = msg.candidate;
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
server.listen(PORT, "0.0.0.0", () => console.log("ARUSHI signaling server listening on " + PORT));
