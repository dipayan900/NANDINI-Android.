# NANDINI Android starter

A small native Android starter project for a phone-only workflow. GitHub Actions builds the debug APK in the cloud.

## Included
- Native Kotlin Activity (not a WebView)
- Dark purple chat interface
- Demo replies only; real AI is not connected yet
- Application ID: `com.dipayan.nandini`
- Minimum Android version: Android 8.0 (API 26)

## Upload to GitHub from your phone
1. Create a repository named `NANDINI-Android`.
2. Upload every file and folder in this ZIP to the repository root. Keep `.github/workflows/build-apk.yml` in place.
3. Commit the files to the `main` branch.
4. Open the repository's Actions tab.
5. Select `Build NANDINI APK` and tap `Run workflow` if shown.
6. Open the completed run and download the `NANDINI-debug-apk` artifact.
7. Extract the artifact ZIP and install `app-debug.apk`. Android may ask you to permit installs for the app you use to open it.

This is a debug APK for testing, not a Play Store release. GitHub Actions usage is subject to account/repository limits.

## Firebase later
The package name is `com.dipayan.nandini`. Register this exact ID in Firebase only after the native project exists. Do not commit API keys, passwords, service-account files, or other secrets. This starter does not need Firebase credentials yet.

## If build fails
Open the failed workflow run and share the first useful error message, with any private tokens removed.


## ARUSHI internet VoIP (separate from SIM calls)

A WebRTC audio-call screen and a minimal Node.js WebSocket signaling service are in `app/src/main/java/Com/Nandini/ArushiVoipActivity.kt`, `ArushiVoipClient.kt`, and `voip-server/`.

1. Deploy `voip-server/` to a Node.js 18+ host with HTTPS/WSS; see [voip-server/README.md](voip-server/README.md).
2. Install the APK on both phones and open **ARUSHI Internet Voice Call (VoIP)** from the call assistant screen.
3. Enter the same WSS signaling URL and the same room code on both phones, then tap **Connect / Join room**.
4. For networks where STUN cannot establish a route, configure a TURN URL and credentials in both clients.
5. Test two-way microphone audio with a trusted person. The app shows WebRTC connection states and speech-recognition errors.

The other ARUSHI client speaks the AI voice prompt locally with Android TTS when it receives a prompt message. This lets the remote app user hear the prompt without pretending Android can inject arbitrary TTS into a cellular call uplink. It requires the other participant to run ARUSHI. Speech transcription uses Android's recognizer and speakerphone acoustics, so it may miss speech while WebRTC is using the microphone. This is a prototype, not a verified production calling service. SIM calls remain separate and the AI assistant is not started on outgoing SIM calls.
