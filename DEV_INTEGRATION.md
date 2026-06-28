# Yap Yap — Local Backend Integration

This guide covers running the **Yap Yap** mobile app against the local [Yap-Yap-Backend](../Yap-Yap-Backend) API on port **8080**.

## Prerequisites

- **Node.js** 18+ and **PostgreSQL** 14+ (or use `docker compose up` in the backend repo)
- Backend repo checked out as a sibling of this project:

  ```
  Projects/
  ├── YapYap/              ← this app
  └── Yap-Yap-Backend/     ← API + Socket.IO
  ```

## 1. Start the backend

```bash
cd ../Yap-Yap-Backend
cp .env.example .env   # first time only — set DB credentials + JWT_SECRET
npm install
npm run migrate:up
npm run dev
```

The server listens on **http://localhost:8080**.

Verify:

```bash
curl http://localhost:8080/
# {"success":true,"message":"Hello World!","data":{"service":"yap-yap-backend","docs":"/api/docs"}}
```

Swagger UI (dev only): http://localhost:8080/api/docs

## 2. Seed test users

If the database is empty, seed two users and a direct conversation between them:

```bash
cd ../Yap-Yap-Backend
npm run seed
```

| Email | Password | Name |
|-------|----------|------|
| `alice@test.com` | `password123` | Alice Test |
| `bob@test.com` | `password123` | Bob Test |

The seed script is idempotent (`ON CONFLICT` upserts) — safe to re-run.

## 3. Run the app (debug)

Build and run a **debug** build. Debug uses the local backend URLs from `gradle.properties`:

| Platform | API (`BASE_URL`) | Socket (`SOCKET_URL`) |
|----------|------------------|------------------------|
| **Android emulator** | `http://10.0.2.2:8080` | `http://10.0.2.2:8080` |
| **iOS Simulator** | `http://localhost:8080` | `http://localhost:8080` |
| **Physical Android / iPhone** (same Wi‑Fi as laptop) | `http://<laptop-lan-ip>:8080` | `http://<laptop-lan-ip>:8080` |

Debug builds pick emulator/simulator vs physical URLs **at runtime**. Physical devices use your Mac’s LAN IP, auto-detected when Gradle builds the app. Override in `local.properties` if needed:

```properties
LAN_HOST=192.168.1.42
# or full URL:
# yapyap.baseUrl=http://192.168.1.42:8080
```

Override order for virtual URLs: `gradle.properties` → built-in defaults. For physical URLs: `yapyap.baseUrl` → `LAN_HOST` → auto-detect → fallback.

**Release builds** always use **`https://yapyap-backend.onrender.com`** for both API and Socket.IO.

```properties
# gradle.properties (debug defaults)
API_BASE_URL_ANDROID=http://10.0.2.2:8080/api
API_BASE_URL_IOS=http://localhost:8080/api
SOCKET_URL_ANDROID=http://10.0.2.2:8080
SOCKET_URL_IOS=http://localhost:8080
```

> Note: API paths in the app already include `/api/...`, so `BASE_URL` is stored as the host root (`http://…:8080`). The `/api` suffix in `API_BASE_URL_*` is stripped at build time.

```bash
# Android
./gradlew :androidApp:assembleDebug
# Install/run from Android Studio

# iOS
# Open iosApp/iosApp.xcodeproj, select Debug, run on Simulator
```

### In-app health check (debug only)

On the **Sign in** screen, tap **Health check**. This calls `GET /` and shows the backend response in a snackbar (e.g. `Hello World! (yap-yap-backend, docs: /api/docs)`).

If the snackbar shows a network error, confirm the backend is running and the platform URL above is reachable.

## 4. Manual test checklist

Use two clients for realtime tests (e.g. Android emulator + iOS Simulator, or two emulators with different accounts).

### Auth & session

- [ ] **Register** a new account (email + password ≥ 8 chars) → lands on main tabs
- [ ] **Logout** from Profile → returned to Sign in; tokens cleared
- [ ] **Login** with seeded user (`alice@test.com` / `password123`) → main tabs
- [ ] **Token refresh**: set a short access TTL in backend `.env` (`JWT_ACCESS_EXPIRES=30s`), log in, wait for expiry, then pull-to-refresh conversations or send a message — request should succeed after silent refresh (or session-expired snackbar if refresh fails)
- [ ] **Logout clears session**: after logout, force-close app and reopen → Sign in screen (no auto-login)

### Conversations & messaging

- [ ] **List conversations** — Chats tab shows seeded Alice↔Bob thread (after seed) or empty state
- [ ] **Start direct chat** — tap **+**, enter the other user's numeric ID (from Profile or DB), open chat
- [ ] **Send message (REST)** — type and send; message appears with pending then sent state (✓)
- [ ] **Receive via socket (second user)** — log in as Bob on a second client while Alice sends a message:
  - **Android & iOS**: message should arrive in realtime via Socket.IO
- [ ] **Typing indicator** — start typing on one device, see indicator on the other (both platforms when socket connected)
- [ ] **Read receipts** — open chat on recipient; sender should see ✓✓ when messages are marked read

### Profile

- [ ] Edit name/phone on Profile → save → values persist after navigating away

## 5. Known limitations

| Area | Status |
|------|--------|
| **iOS Socket.IO** | Implemented via Swift `socket.io-client-swift` + Kotlin `IosSocketBridge`. Auth uses `connect(withPayload: ["token": …])`. |
| **Media messages** | Backend supports `media_url`; the app UI is **text-only** (no image/video picker or media bubble rendering). |
| **Voice / video calls** | Not in app MVP (documented in backend `docs/VOICE_VIDEO_CALLS.md` only). |
| **Release builds** | Point to `https://api.yapyap.app` — not for local dev without changing BuildKonfig flavor. |

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Network error on login | Backend not running, wrong `BASE_URL`, or Android cleartext blocked (use debug build) |
| `401` after idle | Expected when access token expires — refresh should retry; check refresh token in storage |
| Empty conversation list after seed | Log in as Alice or Bob; seed creates membership for those users only |
| Socket never connects (Android) | Confirm `JWT` in handshake; check Logcat for `YapYap` / Ktor logs |
| Socket never connects (iOS) | Confirm Debug build; token passed in connect payload; check Xcode console |
| iOS can't reach localhost | Use Debug scheme (`Info-Debug.plist` ATS exception); physical device needs host IP |

## Related

- Backend README: `../Yap-Yap-Backend/README.md`
- Backend integration scripts: `npm run test` (auth, messaging, socket smoke tests)
