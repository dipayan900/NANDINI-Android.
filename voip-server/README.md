# ARUSHI VoIP signaling server

This is the signaling relay for the separate WebRTC audio-call feature. It does **not** carry call audio; audio travels peer-to-peer through WebRTC, or through a TURN relay configured by the app.

## Deploy on a Node.js host

1. Deploy this folder as a Node.js service (Node 18+).
2. Set the start command to `npm start`.
3. Set `PORT` if your host requires it.
4. Enable HTTPS/WSS at the host/proxy. Use the resulting `wss://...` URL in both ARUSHI clients.
5. Check `https://YOUR-HOST/health` returns JSON with `"status":"ok"`.

The server is a minimal signaling prototype. Use a private/unguessable room code and a trusted TLS host. It currently has no account authentication or persistent storage, so do not treat it as a production service.

## Calling

Install the ARUSHI APK on both Android devices. Both users enter the same room code and signaling URL; the second participant triggers negotiation. Grant microphone permission and keep the app open during the test. Both phones need a working internet connection.

## ICE / TURN

The app starts with Google's public STUN server. STUN alone does not work on every carrier/NAT. For reliable connections, configure a TURN server and add its TURN URL, username and password in the app's ICE settings (or update the app configuration). TURN credentials should be short-lived in a production deployment.

## Limits

- This is VoIP, not a bridge into a cellular/SIM call.
- It needs internet (or another IP route) to reach the other participant.
- Android on-device speech recognition and TTS availability/language packs vary by phone.
- The WebRTC audio path must be tested on two real devices before claiming reliability.
