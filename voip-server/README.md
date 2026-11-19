# ARUSHI authenticated VoIP signaling

This service relays WebRTC signaling only. Audio travels through WebRTC DTLS-SRTP; the signaling server does not receive microphone audio.

## Deploy

1. Deploy this folder to a Node.js 18+ host with HTTPS/WSS.
2. Add the Firebase Admin service-account JSON as the host secret `FIREBASE_SERVICE_ACCOUNT_JSON`. Never commit the key to Git.
3. Use a service account from the same Firebase project as ARUSHI Connect, with permission to verify ID tokens and read `connect_conversations`.
4. Run `npm install` and `npm start`.
5. Check `/health`; it must return `status: ok` and `firebaseAdmin: true`.
6. Configure the app with the resulting `wss://` endpoint.

## Authentication and room authorization

- Every WebSocket upgrade requires `Authorization: Bearer <Firebase ID token>`.
- Tokens are verified with Firebase Admin SDK, including revocation checks.
- A call room is the ID of an existing ARUSHI Connect conversation.
- The server checks that the authenticated UID is a member of that conversation before allowing entry.
- This version supports two participants per call.
- Signaling messages have payload bounds and a per-socket rate limit.

## TURN

For carrier/NAT traversal, deploy a TURN service and issue short-lived credentials from a trusted backend. Do not store permanent TURN passwords in the APK. The app accepts a TURN URL and credentials; use TLS/WSS and TURN/TLS where supported.

## Scope

This server does not bridge cellular SIM call audio. Multi-party SFU calling and push call invitations are not implemented by this two-party relay.
