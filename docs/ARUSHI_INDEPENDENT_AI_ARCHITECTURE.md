# ARUSHI Independent AI Architecture

Status: approved architecture baseline
Target branch: feature/arushi-independent-ai-core
Base: feature/arushi-connect-push-foundation
Application ID: com.dipayan.nandini

## Non-negotiable preservation rules

- Keep the ARUSHI name, current visual identity, avatar/companion, launcher, chat UI, existing image studio, reminders, accessibility integration, and existing calling screens.
- Keep applicationId and namespace `com.dipayan.nandini`.
- Do not modify PR #11 (ARUSHI Connect messaging foundation) or PR #12 (push notification foundation).
- Do not delete Firebase files, dependencies, configuration, or current Connect code until replacement paths exist, pass tests, and are switched over deliberately.
- Keep the existing build workflow and verify every milestone with CI. A successful build is not proof of device functionality.
- Do not commit secrets, OAuth tokens, private keys, model weights, or personal account data.

## Goal

Build ARUSHI as a user-controlled, local-first personal AI and agent platform using open-weight models and independently managed services. The application code, agent orchestration, memory format, tool interfaces, and backend APIs belong to this project. An open-weight pretrained model is not a model trained from scratch; any fine-tuning or training is a separate, compute-intensive future project.

Free-first means no mandatory paid API or subscription. It does not mean unlimited compute, storage, bandwidth, inference, or guaranteed 24/7 hosting.

## Current repository baseline discovered

- Kotlin Android app; package/namespace/applicationId: com.dipayan.nandini.
- Existing optional offline chat runtime: LiteRT-LM with Qwen3-0.6B, downloaded into app-private storage and SHA-256 checked. It is not currently the default AI path.
- Existing LocalDream image-generation engine is an embedded ARM64 child process and is separate from the text assistant.
- ARUSHI Connect currently depends on Firebase Authentication, Firestore, Firebase Storage, and FCM. The repository also contains Firebase Cloud Functions and rules.
- Existing Connect message history uses a private local SQLite store; the Firebase repository is the network identity/sync/media layer.
- Existing WebRTC VoIP signaling server is a separate Node service prototype. Its README explicitly says it has no account authentication or persistent storage and is not production-ready.
- Existing GitHub Actions APK workflow must be retained.

## Target architecture

### 1. Android application layer

Preserve the current screens and add service interfaces beneath them. UI must not call model runtimes, provider APIs, or remote services directly.

Proposed modules/packages:
- ai/api: AiProvider, ChatRequest, ChatResponse, ModelDescriptor, ToolCall
- ai/runtime: LiteRT-LM adapter first; optional llama.cpp/ExecuTorch adapters only after device benchmarking
- ai/agent: planner, bounded task runner, tool registry, approvals, audit log
- memory: local encrypted profile, conversation index, project notes, user-controlled export/delete
- voice: speech input, Android TTS adapter, optional licensed downloadable neural voice adapter
- integrations/github: OAuth/device authorization, repository read/write operations, Actions run monitoring
- integrations/social: official API adapters only
- backend/api: versioned HTTP/WebSocket API client
- notifications: local scheduled notifications and backend event sync

### 2. AI model and runtime

- Retain the existing Qwen3-0.6B LiteRT-LM implementation as the first compatibility baseline; benchmark it on the target phone before selecting a larger model.
- Introduce an AiProvider interface so the UI and agent tools are independent of the model/runtime.
- Download models only from an explicitly configured trusted model source; verify SHA-256 and license metadata before enabling.
- Store model files in app-private storage, not in the APK or Git repository.
- Make local inference the default and ensure chat remains useful offline.
- A future open-weight model can replace the initial model without rewriting chat, memory, or tool interfaces.
- Do not claim frontier-level capability or unlimited reasoning from a small mobile model. Use bounded tool workflows and report uncertainty honestly.

### 3. Agent and coding system

- Use an explicit allow-listed tool registry; the model proposes tool calls but application code validates every argument and permission.
- Initial tools: read project metadata, inspect allowed repository files, search code, create a branch, create/update files on that branch, trigger approved GitHub Actions workflows, and read build logs/artifacts.
- GitHub authentication must use OAuth/device authorization and minimum required scopes. Never ask the user to paste a personal access token into chat or commit it.
- Agent-generated code must be reviewed and tested in CI. No direct push to main, force-push, automatic PR merge, secret access, or production deployment without an explicit approval step.
- Show a task plan, tool activity, changed-file summary, test results, and a final report.
- Do not run arbitrary model-generated shell commands with unrestricted Android app privileges. Any future execution environment must be isolated and resource-limited.

### 4. Independent backend and Firebase migration

Proposed free-first backend candidate: Cloudflare Workers + D1/SQLite + R2 object storage + SQLite-backed Durable Objects for realtime coordination. This is a candidate, not a deployed service or a promise of unlimited hosting.

Backend responsibilities:
- Account registration and session management
- Username discovery and profile metadata
- Direct/group conversation metadata and message delivery
- Media object storage with short-lived authorized access
- VoIP signaling and call-session authorization
- Device registration and notification/event delivery
- OAuth token vault and integration webhooks
- Scheduled news and reminder jobs

Migration sequence:
1. Define versioned backend API contracts and data schemas.
2. Add backend client interfaces to Android without changing the current UI.
3. Implement and test authentication/session migration.
4. Implement Connect profile and username APIs.
5. Implement message sync and realtime transport while preserving SQLite as the local message store.
6. Implement media upload/download and access control.
7. Implement call signaling and test two-device calls.
8. Add account export and migration tooling.
9. Run old and new paths in a controlled test environment; compare behavior and data.
10. Only after acceptance tests pass, switch the app to the new backend and remove Firebase SDK/configuration/functions/rules in a separate cleanup change.

Do not silently migrate or delete existing users' messages, media, or identity records. The owner must be able to export data before any destructive cutover.

### 5. Notifications and background constraints

- Local morning briefings/reminders can be scheduled on-device using Android-supported background scheduling.
- Realtime incoming-call and chat notifications are a separate problem. Android may suspend an app in the background; ordinary WebSockets or frequent polling cannot guarantee wake-up or timely delivery.
- If Firebase/FCM is removed completely, document the reduced background-delivery reliability and test on the target Vivo device. Do not claim guaranteed incoming calls while the app is force-stopped or the OS has restricted it.
- Never silently retain FCM while telling the owner that all Firebase dependencies have been removed.

### 6. Voice

- Keep speech input and the current voice UI behind a VoiceProvider interface.
- First use Android's installed TTS engine for a no-extra-service baseline.
- Add a natural female neural voice only from a downloadable model with a clear license allowing the intended use. Record source, license, model hash, language, and size.
- Avoid random voice-download sites with unclear licensing, malware risk, or personal-data collection.
- Support offline playback where the selected engine permits it. Do not bundle large voice models into the APK.

### 7. External integrations

- GitHub: official OAuth/device authorization and GitHub APIs; branch-scoped changes; CI verification; human approval for merge/deployment.
- Social accounts: official platform APIs and user-authorized scopes only. Analytics availability varies by account type and platform.
- News: fetch from reputable sources, retain attribution, deduplicate, and schedule a concise local morning summary.
- Shopping: use official merchant APIs or user-directed checkout flows where available. Show merchant, items, total, address and delivery details for confirmation before placing an order. Never store payment credentials in ARUSHI.
- Messaging/calls: use platform-supported Android intents, permissions and official APIs. Cellular call forwarding remains carrier-controlled; ARUSHI VoIP is a separate internet-call feature.

## Security and privacy requirements

- Encrypt sensitive local data and tokens at rest using Android Keystore-backed keys.
- Use TLS for all network traffic.
- Store OAuth refresh tokens only in the backend token vault or encrypted Android storage as appropriate; never in logs.
- Enforce per-user authorization on every backend operation.
- Use short-lived media links, upload size limits, rate limits, abuse controls and audit logs.
- Ask before sending messages, publishing content, placing orders, changing account settings, merging code, or deploying to production.
- Provide account export, disconnect/revoke integration, and deletion flows.
- Treat webpage content, repository files, model output and incoming messages as untrusted data, never as executable instructions.

## Milestones and acceptance criteria

### Milestone 0 — Baseline and branch safety
- This branch is based on feature/arushi-connect-push-foundation.
- PR #11 and PR #12 remain untouched.
- Record current build and key app behavior before implementation.

### Milestone 1 — AI provider boundary
- Existing Qwen3-0.6B runtime is wrapped behind AiProvider.
- Existing chat screen can select local AI without Firebase AI.
- Offline chat works after model download and after app restart.
- Model download verifies integrity and reports insufficient storage/memory cleanly.

### Milestone 2 — Memory and agent foundation
- Local persistent memory survives app restart.
- User can inspect and clear stored memory.
- Tool calls are allow-listed, permission-checked, logged, and cancellable.
- No autonomous destructive action.

### Milestone 3 — GitHub agent
- Connect GitHub using supported OAuth/device flow.
- Read-only repository inspection first.
- Branch-scoped file changes and CI run monitoring.
- No main-branch push or automatic deployment.

### Milestone 4 — Independent backend
- API, auth, Connect and media replacement tested without Firebase.
- Local SQLite message history remains authoritative on-device.
- Migration/export verified before cutover.

### Milestone 5 — Voice and proactive notifications
- Natural licensed female voice works locally.
- Morning briefing can be scheduled locally.
- Background delivery limitations are clearly surfaced and tested.

### Milestone 6 — Integrations
- Official social analytics, news, and user-confirmed shopping flows.
- All integrations can be disconnected and their tokens revoked.

## Immediate next engineering task

Start with Milestone 0 and Milestone 1 only. Inspect the complete current LocalAiRuntime and chat integration points, then add the AiProvider abstraction in this branch without changing existing Firebase Connect code or visual screens. Build and test before expanding scope.


## Calling, caller assistant, and message handoff requirements

### Existing code discovered (not yet certified as production-ready)

- `ArushiCallAssistantActivity` requests Android's default dialer role, exposes a dial pad, and has answer/reject/end controls.
- `ArushiInCallService` observes Telecom calls and can request a delayed auto-answer when the owner has explicitly enabled it.
- `ArushiVoiceCallAssistant` uses Android SpeechRecognizer and TextToSpeech to introduce itself as ARUSHI, disclose that it may transcribe speech, ask for a caller name/message, and save a transcript.
- `ArushiVoipClient` and `ArushiVoipActivity` implement an early WebRTC internet-call prototype that expects a separately hosted signaling server and optional TURN credentials.
- The existing code itself documents that cellular call uplink/downlink audio is not guaranteed. A successful APK build does not prove that the caller can hear ARUSHI or that ARUSHI can hear the caller.

### Required call experience

1. Incoming SIM calls: display caller identity when Android provides it; allow the owner to answer, reject, or silence; optional auto-answer must be explicitly enabled, visibly indicated, and cancellable.
2. AI receptionist mode: identify itself as an AI assistant before any conversation; ask whether the caller agrees to leave a message; collect only the information the owner has configured; disclose any transcription; stop on request; save the transcript locally and show it to the owner.
3. Outgoing SIM calls: allow contact/number selection and show a confirmation screen before Android Telecom places the call. Never auto-dial a person based only on model output.
4. ARUSHI-to-ARUSHI internet calls: complete authenticated account discovery, invitation/acceptance, call setup, TURN-backed NAT traversal, audio routing, reconnection, hangup, and missed-call notifications. Do not ship a public unauthenticated signaling room or hard-coded TURN secret.
5. Cross-platform callers: do not promise an AI can join ordinary cellular calls from every phone. For reliable AI conversation, use an explicitly configured AI receptionist number/SIP or VoIP endpoint where the service provider supports media access and applicable consent/disclosure. SIM-call audio capture by an ordinary Android app is restricted and device/OEM-dependent.
6. Message handoff: let the owner review the caller's transcript, choose a recipient, edit the message, and send using the Android share/SMS/messaging app. Any direct send integration must show recipient and full message and require an explicit send confirmation.
7. Message forwarding: never read and forward all private messages in the background. Support only user-selected messages and destinations, with a preview and confirmation. Use official APIs and Android role/permission requirements.
8. Call logs/transcripts: store locally with clear retention controls, do not upload by default, do not record audio unless separately lawful, explicitly enabled, and disclosed to all participants.

### Device and service acceptance tests

- Test on the actual Vivo target phone with SIM incoming/outgoing calls, Bluetooth headset, wired headset, speaker, screen locked, app backgrounded, denied permissions, and OS battery restrictions.
- Verify whether the far-end caller hears the AI greeting and whether caller speech is reliably transcribed. If either direction fails, mark cellular AI conversation unsupported on that device rather than pretending it works.
- Test two ARUSHI devices on separate networks with TURN enabled, authenticated signaling, call accept/reject, mute, speaker route, disconnect, reconnect, and notification behavior.
- Test a non-ARUSHI caller through a separately provisioned AI receptionist endpoint if that feature is required.
- Test message review, recipient selection, confirmation, cancellation, and local transcript deletion.
- Keep ordinary phone dialing and emergency calling available through Android's supported phone app. Never intercept or obstruct emergency calls.


## Implementation status — 2026-11-19

### Added on `feature/arushi-independent-ai-core`
- `AiProvider` interface and router now isolate chat UI from provider selection; Firebase Gemini remains the cloud default and LiteRT/Qwen remains opt-in offline.
- `ArushiSecureMemory` adds AES-GCM encrypted local records using an Android Keystore-managed key. The export method is explicit and returns decrypted values only to the calling code; UI export/delete controls and migration of existing plaintext preferences are still required.
- `ArushiAgentRuntime` adds an allow-listed tool registry, structured task steps, per-invocation approval, permission checks, progress callbacks, cancellation checks and a bounded audit log. It is a runtime foundation only: no model-driven planner, production tool adapters, background worker, or user-facing execution console is connected yet.
- The Command Center planner remains a manual local task tracker. Its detail display formatting was corrected, but it must not be described as autonomous execution.
- Caller-message forwarding remains a review-and-edit handoff to Android's messaging app. It is not a universal background forwarding service. Android app sandboxing and end-to-end encrypted app boundaries prevent an app from reading/sending every other app's private messages. Any future notification-based relay must be opt-in, visibly enabled, app-allowlisted, limited to notification content actually exposed by the source app, and require explicit user review before forwarding.
- The current VoIP signaling server remains unauthenticated and in-memory; public STUN only is insufficient for reliable carrier/NAT coverage. No production TURN service, authenticated invite tokens, incoming-call push pipeline, persistent group backend, or two-device acceptance test has been completed.
- The current voice assistant still uses Android SpeechRecognizer and Android TTS. The CI workflow bundles Sherpa-ONNX and a mobile keyword-spotting model, but this alone does not provide neural TTS or prove reliable hands-free speech recognition on the target device.
- Existing image studio and companion assets remain in the project; no replacement of those working systems is intended.
- No social provider OAuth integrations, provider-approved analytics scopes, shopping checkout integrations, or production account export/deletion flow have been completed.

### Production gates still required
1. Build and test the current branch; run Android unit/instrumentation tests and exercise encrypted storage on-device.
2. Connect the agent runtime to a model-produced plan, implement trusted app-owned tools, move long tasks to a cancellable worker, and add a user-visible approval/progress/audit console.
3. Add GitHub OAuth/device authorization with an app-owned OAuth client, short-lived access tokens, least privilege, branch-scoped repository tools, Actions monitoring, and explicit deployment approvals.
4. Deploy an authenticated signaling/backend service, issue short-lived room-scoped credentials, configure a real TURN provider, add push call invitations and group persistence, and test on multiple networks/devices.
5. Add licensed neural TTS assets/runtime and an offline speech-recognition path with language selection, battery controls and clear microphone indicators; benchmark on the target Vivo device.
6. Implement official social integrations only after each provider app is registered and approved for the necessary scopes.
7. Implement checkout via supported merchant/provider flows, with a final confirmation in the provider UI; never store payment credentials in ARUSHI.
8. Add encrypted export/import/delete screens and account revocation controls, and migrate existing local/cloud data only after backups and user approval.

A successful APK build validates compilation and packaging only. It does not satisfy any of the network, provider approval, push-delivery, TURN, SIM-call audio, neural voice, privacy migration or device acceptance gates above.
