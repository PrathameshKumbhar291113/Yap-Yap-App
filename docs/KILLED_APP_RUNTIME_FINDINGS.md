# Killed-App Runtime Findings (2026-06-29)

Captured on physical Android device `RZGL51SX57E` with instrumented debug APK.  
Logs: `docs/killed-app-runtime-20260629-013449/`, `docs/killed-app-runtime-retap-013532/`.

---

## 1. Exact runtime path — killed app (push delivery)

**Test:** force-stop → send message → wait 20s with logcat (messageId=221).

```
Force-stop com.yapyap (pidof empty)
Send message via API
Wait 20s
```

**Observed:** `phase1-push.log` is **empty** — zero `YapRuntimeDiag` lines while process was dead.

| Step | Function | Ran? |
|------|----------|------|
| FCM received on device | (OS / GMS) | Yes (notification visible to user) |
| `FirebaseMessagingService.onMessageReceived` | | **NO** |
| `PushNotificationBridge.deliverMessage` | | **NO** |
| `PushNotificationCoordinator.syncIncomingPushMessage` | | **NO** |
| `MessageDeliveryAcknowledgementService.onMessagePersisted` | | **NO** |
| `SOCKET_EMIT message:delivered` | | **NO** |
| `PushNotificationDisplay.show` / `NotificationManager.notify` | | **NO** |

**Conclusion:** While the process is dead, Android displays the tray notification via the **OS FCM path**. No application code executes. Sender remains at ✓ because no delivery ack runs.

---

## 2. Exact runtime path — notification tap

**Test:** notification tap opens app (messageId=222, full log capture).

```
01:35:34.604  Splash Screen com.yapyap (OS launching MainActivity)
01:35:34.999  ENTER YapYapApplication.onCreate
01:35:35.098  ENTER MainActivity.onCreate
              intent: action=MAIN categories=LAUNCHER extras={none}
01:35:35.122  ENTER MainActivity.handleNotificationIntent (source=onCreate)
01:35:35.122  ENTER PushIntentParser.parse
              fromNotification=false hasPushType=false
              result=null reason=no_from_notification_and_no_type
01:35:35.122  SYSTEM_NOTIFICATION_ONLY candidate=launcher_intent_without_push_extras
01:35:35.122  handleNotificationIntent SKIP — execution stops here
              (no PushNavigationRouter, no executePushNavigation)

01:35:36.270  ENTER FirebaseMessagingService.onMessageReceived  ← 1.2s AFTER activity start
01:35:36.283  APPLICATION_PATH=onMessageReceived_will_deliver_to_coordinator
01:35:36.307  MESSAGE_ACK_SERVICE_ENTER source=fcm messageId=222
              (already_acknowledged — REST sync beat FCM)
01:35:36.325  IN_APP_BANNER (foreground=true, no APPLICATION_NOTIFICATION_CREATED)

01:35:38.815  CHAT_OPENED conversationId=1  ← manual list tap (GestureDetector TAP), NOT push nav
```

**Navigation chain on tap:**

```
MainActivity.onCreate
  → handleNotificationIntent
    → PushIntentParser.parse → null
      → STOP (no onNewIntent, no PushNavigationRouter, no NavController chat route)
```

`PushNavigationRouter.navigate` and `executePushNavigation` **never executed** in the tap-open flow.

---

## 3. Raw FCM payload (from logcat)

```
FCM_RAW from=125027323764
  messageId=0:1782677136137296%5257a80d5257a80d
  sentTime=1782677136115
  ttl=86
  collapseKey=com.yapyap
  priority=1
  originalPriority=1
  messageType=null

FCM_RAW data={
  conversationId=1,
  messageId=222,
  payloadVersion=2,
  messageType=text,
  preview=KilledApp-retap-1782677135958,
  type=new_message,
  click_action=OPEN_CHAT,
  payloadKind=text_message,
  senderName=Alice Test,
  senderId=1
}

FCM_RAW notification
  title=Alice Test
  body=KilledApp-retap-1782677135958
  channelId=messages
  clickAction=OPEN_CHAT
  tag=conv_1
  imageUrl=null
  link=null
  color=null
  sound=default
  ticker=null
  icon=null
  notificationCount=null
  notificationPriority=1
  visibility=null
  eventTime=null
  localOnly=false
  defaultSound=false
  defaultVibrateSettings=false
  defaultLightSettings=false
  sticky=false

NOTIFICATION_BLOCK_PRESENT=true
FCM_NORMALIZED mode=Hybrid osHandled=false
```

Backend source confirms hybrid payload: `textMessage.js` sends both `data` and `notification` blocks.

---

## 4. OS-created vs app-created notification

| Evidence | Meaning |
|----------|---------|
| Process dead 20s → zero app logs | OS displayed notification without app code |
| `NOTIFICATION_BLOCK_PRESENT=true` | FCM hybrid message — Android system tray handler |
| No `APPLICATION_NOTIFICATION_CREATED` before tap | App did not build the visible notification |
| Tap intent: `ACTION_MAIN`, `extras={none}` | OS default launcher PendingIntent, not app `yapyap_from_notification` |

**Verdict:** **SYSTEM notification** (OS-created from FCM `notification` block). App cannot attach its custom PendingIntent to that notification.

---

## 5. Does `FirebaseMessagingService` run?

| Scenario | Answer |
|----------|--------|
| Process dead (20s window, msg 221) | **NO** |
| After tap opens app (msg 222) | **YES** — 1.2s after `MainActivity.onCreate`, thread `Firebase-Messaging-Intent-Handle` |

---

## 6. Does `MessageDeliveryAcknowledgementService` run?

| Scenario | Answer |
|----------|--------|
| Process dead | **NO** |
| After app opens (msg 221, cold start via launcher) | **YES** — but `source=rest` from `ConversationRepositoryImpl.refreshConversations`, not `source=fcm` |
| After FCM arrives while app foreground (msg 222) | **YES** — `MESSAGE_ACK_SERVICE_ENTER source=fcm` but skipped `already_acknowledged` |

---

## 7. Does `socket.emit(message:delivered)` execute?

| Scenario | Answer |
|----------|--------|
| Process dead | **NO** |
| After app opens (msg 221) | **YES** — after `SOCKET_CONNECTED`, via REST-triggered ack: `SOCKET_EMIT` → `SOCKET_SUCCESS` |
| While killed (before app open) | **NO** — this is why sender stays ✓ |

---

## 8. Does `MainActivity.onNewIntent()` execute?

**NO** on notification tap cold start. Activity launched via `onCreate` with launcher intent.  
`onNewIntent` only runs for warm start with new intent — not observed in this capture.

---

## 9. Exact function where execution stops

### Push delivery (killed):
Entire app pipeline — first missing function: **`FirebaseMessagingService.onMessageReceived`** (process not running).

### Notification tap:
**`PushIntentParser.parse`** → returns `null` at `reason=no_from_notification_and_no_type`  
Called from **`MainActivity.handleNotificationIntent:138`**.

Downstream never reached:
- `PushNotificationCoordinator.handleNotificationOpened`
- `PushNavigationRouter.navigate`
- `executePushNavigation`
- `CHAT_OPENED` (via push path)

---

## 10. Root cause (log-backed)

**Two linked failures:**

1. **Delivery ack while killed:** Hybrid FCM payload (`notification` + `data`) is handled by Android when the app process is dead. `YapFirebaseMessagingService.onMessageReceived` does not run until the process starts. No `MESSAGE_ACK_SERVICE_ENTER` / `SOCKET_EMIT` → sender stays ✓.

2. **Tap navigation:** OS notification tap launches `MainActivity` with a **default launcher intent** (`ACTION_MAIN`, no push extras). `PushIntentParser` rejects it → navigation chain never starts. Chat only opens if the user manually taps a conversation (observed `GestureDetector TAP` before `CHAT_OPENED`).

**Not speculative:** Each claim maps to a specific log line in `docs/killed-app-runtime-retap-013532/full.log`.

---

## Timeline (notification tap, message 222)

```
T+0.0s   OS shows notification (FCM hybrid, process waking)
T+0.4s   Splash Screen / MainActivity launch begins
T+0.5s   YapYapApplication.onCreate
T+0.6s   MainActivity.onCreate — launcher intent, extras=none
T+0.6s   PushIntentParser → null → NAVIGATION STOPS
T+1.3s   FirebaseMessagingService.onMessageReceived (FCM data processed)
T+1.3s   syncIncomingPushMessage, MESSAGE_ACK (already acked via REST)
T+1.3s   IN_APP_BANNER (foreground, no custom tray notification)
T+3.8s   User manual tap → ChatScreen.compose CHAT_OPENED
```

---

## Reproduce

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
./scripts/test-killed-app-runtime.sh
# Or manual: force-stop → tail-live-logs.sh --android-only --clear → send message → tap notification
```

Filter: `adb logcat -v time YapRuntimeDiag:I '*:S'`

---

## Post-fix verification (Android data-only backend)

**Status:** Backend now sends platform-specific payloads (`Yap-Yap-Backend/src/services/push/fcmMessageBuilder.js`):
- **Android:** data-only, `android.priority: high`, no `notification` / `android.notification` blocks
- **iOS:** hybrid unchanged (APNs alert + data)

The runtime capture in this document used the **previous hybrid payload** (`hasNotification: true` in backend logs, `NOTIFICATION_BLOCK_PRESENT=true` on device). Re-run `./scripts/test-killed-app-runtime.sh` after deploying the current backend and confirm:

| Signal | Hybrid (this capture) | Data-only (expected) |
|--------|----------------------|----------------------|
| Process dead, push arrives | No app logs | `onMessageReceived` + `APPLICATION_NOTIFICATION_CREATED` |
| Delivery ack while killed | No | `DeliveryAck` + `SOCKET_EMIT` |
| Notification tap intent | `ACTION_MAIN`, extras none | `yapyap_from_notification=true`, push data extras |
| Deep link on tap | No | `PushNavigationRouter` → chat |

Payload validation (no Firebase send): `node Yap-Yap-Backend/scripts/test-fcm-payload.js`

OEM matrix: see `docs/NOTIFICATION_TEST_CHECKLIST.md`.

This document remains evidence for the **hybrid root cause**; re-test supersedes tap/delivery conclusions once data-only is deployed.
