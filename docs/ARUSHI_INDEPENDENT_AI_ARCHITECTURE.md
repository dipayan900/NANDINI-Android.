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
