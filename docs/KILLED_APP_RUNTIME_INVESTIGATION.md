# Killed-App Push Runtime Investigation

Instrumentation-only pass. **No fixes** until logcat proves the failing function.

## Goal

When the Android app is force-stopped or swiped from Recents (process dead):

1. Notification appears on device
2. Sender stays at ✓ (not ✓✓)
3. Tapping notification does not open the chat

This doc describes how to capture the **exact runtime path** and interpret logs.

---

## Build & Install

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Use a **debug** build — all traces use log tag `YapRuntimeDiag`.

---

## Start Log Capture

In a dedicated terminal:

```bash
./scripts/tail-live-logs.sh --android-only --clear
```

Optional grep for investigation markers only:

```bash
adb logcat -v time YapRuntimeDiag:I '*:S' | grep -E 'ENTER|FCM_RAW|APPLICATION_|SYSTEM_|MESSAGE_ACK|SOCKET_|QUEUE_ACK|FLUSH_ACK|PendingIntent|PushIntentParser|NAVIGATION_|CHAT_OPENED|DEFERRED'
```

---

## Test Procedure

1. **Recipient (Android physical, e.g. Bob userId=2)**
   - Log in
   - Settings → Apps → YapYap → **Force stop** (or swipe away from Recents)
   - Confirm process dead: `adb shell pidof com.yapyap` → empty

2. **Sender (e.g. Alice userId=1)**
   - Send **one** message to Bob

3. **Observe device**
   - Notification appears?
   - Note sender tick state (✓ vs ✓✓)

4. **Capture push-delivery logs** (before tap)
   - Save logcat from step 2 until notification visible

5. **Tap notification**
   - Capture logs through app launch

6. **Save both log files** for analysis

---

## Instrumented Entry Points

| Component | Method | Log prefix |
|-----------|--------|------------|
| Application | `YapYapApplication.onCreate` | `[Lifecycle] ENTER` |
| FCM | `FirebaseMessagingService.onMessageReceived` | `[Firebase] ENTER` + `FCM_RAW` |
| FCM | `onDeletedMessages` | `[Firebase] ENTER` |
| Bridge | `PushNotificationBridge.deliverMessage` | `[PushBridge] ENTER` |
| Coordinator | `handleInboundPush`, `handlePushData`, `handleNewMessage`, `syncIncomingPushMessage`, `updateConversationNotification`, `navigateToChat`, `handleNotificationOpened` | `[PushCoordinator] ENTER` |
| Delivery ack | `MessageDeliveryAcknowledgementService.onMessagePersisted` | `MESSAGE_ACK_SERVICE_ENTER` |
| Delivery ack | `flushPendingAcks` | `FLUSH_ACK` |
| Delivery ack | queue when socket not ready | `QUEUE_ACK` |
| Socket | ready | `SOCKET_CONNECTED` |
| Socket | emit delivered | `SOCKET_EMIT` |
| Socket | ack result | `SOCKET_SUCCESS` / `SOCKET_FAILURE` |
| Notification | `PushNotificationDisplay.show` | `APPLICATION_NOTIFICATION_CREATED` |
| Notification | `NotificationManager.notify` | `[Notification] ENTER` |
| Notification | OS-only path | `SYSTEM_NOTIFICATION_ONLY` |
| PendingIntent | build + extras dump | `PendingIntent.targetIntent` |
| Activity | `MainActivity.onCreate/onNewIntent` | `[Lifecycle] ENTER` + full intent dump |
| Deep link | `PushIntentParser.parse` | `[DeepLink] ENTER` |
| Navigation | `PushNavigationRouter.navigate` | `[DeepLink] ENTER` |
| Navigation | `YapYapNavHost`, `executePushNavigation` | `[Navigation]` |
| Chat | `ChatScreen.compose` | `CHAT_OPENED` |

Every `ENTER` line includes: `ts`, `thread`, `pid`, `process`.

---

## Interpreting Paths

### Path A — Application handles push (expected when `onMessageReceived` runs)

```
ENTER FirebaseMessagingService.onMessageReceived
FCM_RAW data=...
FCM_RAW notification ... (may be present)
NOTIFICATION_BLOCK_PRESENT=true|false
APPLICATION_PATH=onMessageReceived_will_deliver_to_coordinator
ENTER PushNotificationBridge.deliverMessage
ENTER PushNotificationCoordinator.handleInboundPush
ENTER syncIncomingPushMessage
MESSAGE_ACK_SERVICE_ENTER
ensureSocketConnectedForDeliveryAck
SOCKET_CONNECTED (may be async)
SOCKET_EMIT / SOCKET_SUCCESS OR QUEUE_ACK
APPLICATION_NOTIFICATION_CREATED
ENTER NotificationManager.notify
```

Tap:

```
ENTER MainActivity.onNewIntent (or onCreate)
PendingIntent.targetIntent extras: yapyap_from_notification=true, conversationId=...
PushIntentParser result={...}
ENTER PushNotificationCoordinator.handleNotificationOpened
ENTER PushNavigationRouter.navigate
ENTER executePushNavigation
CHAT_OPENED conversationId=...
```

### Path B — OS handles push (killed + hybrid FCM with `notification` block)

When the FCM payload includes a `notification` block and the app is **background/killed**, Android may display the tray notification **without** calling `onMessageReceived`:

```
(no ENTER FirebaseMessagingService.onMessageReceived)
(no APPLICATION_NOTIFICATION_CREATED)
(no MESSAGE_ACK_SERVICE_ENTER)
(no SOCKET_EMIT)
```

User sees notification (OS-created). Sender stays ✓.

Tap may open launcher with **no push extras**:

```
ENTER MainActivity.onCreate
PushIntentParser fromNotification=false hasPushType=false
PushIntentParser result=null reason=no_from_notification_and_no_type
SYSTEM_NOTIFICATION_ONLY candidate=launcher_intent_without_push_extras
(handleNotificationIntent SKIP — navigation never starts)
```

**Proof of OS ownership:** notification visible + zero `YapRuntimeDiag` Firebase/Notification entries between send and tap.

Static config enabling OS display: `AndroidManifest.xml` meta-data `default_notification_channel_id` + `default_notification_icon`.

---

## Deliverables Checklist

After one killed-app test, fill in from logs:

| # | Question | Log evidence |
|---|----------|--------------|
| 1 | Runtime path for killed push | List all `ENTER` lines in order |
| 2 | Runtime path for notification tap | From tap through `CHAT_OPENED` or stop point |
| 3 | Raw FCM payload | `FCM_RAW` lines (if service ran) |
| 4 | OS vs app notification | `APPLICATION_NOTIFICATION_CREATED` vs absent + `SYSTEM_NOTIFICATION_ONLY` |
| 5 | `FirebaseMessagingService` runs? | `ENTER FirebaseMessagingService.onMessageReceived` yes/no |
| 6 | `MessageDeliveryAcknowledgementService` runs? | `MESSAGE_ACK_SERVICE_ENTER` yes/no |
| 7 | `socket.emit(message:delivered)`? | `SOCKET_EMIT` + `SOCKET_SUCCESS` yes/no |
| 8 | `MainActivity.onNewIntent` runs? | `ENTER MainActivity.onNewIntent` yes/no |
| 9 | Where execution stops | Last `ENTER` before gap |
| 10 | Root cause | One sentence tied to log line |

---

## Timeline Template

Copy and fill from logcat timestamps:

```
T0  FCM received (device) — correlate with send time
T1  ENTER FirebaseMessagingService.onMessageReceived — YES/NO
T2  FCM_RAW dump — attach full lines
T3  ENTER PushNotificationCoordinator.* — YES/NO
T4  MESSAGE_ACK_SERVICE_ENTER — YES/NO
T5  SOCKET_EMIT / QUEUE_ACK — which?
T6  APPLICATION_NOTIFICATION_CREATED — YES/NO
T7  User taps notification
T8  ENTER MainActivity.onCreate/onNewIntent
T9  PushIntentParser result — null or map?
T10 ENTER PushNavigationRouter.navigate — YES/NO
T11 CHAT_OPENED — YES/NO
```

---

## Notes

- Do **not** implement fixes until row 9–10 in the checklist identifies the failing function with log proof.
- If Path B is confirmed, the next fix discussion is **payload shape** (data-only FCM) or **tap intent wiring** — but that is out of scope for this instrumentation pass.
- `Force stop` vs swipe-away: both should kill the process; force stop also prevents restart until user opens the app.
