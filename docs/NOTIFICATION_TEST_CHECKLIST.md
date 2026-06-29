# Push Notification Test Checklist

## Android

### Foreground
- [ ] Send FCM data message while app is open on conversation list → in-app snackbar appears
- [ ] Send FCM data message while viewing a different chat → in-app snackbar appears
- [ ] Send FCM data message while viewing the same chat → no notification shown
- [ ] Tap snackbar is not required; message appears in conversation list

### Background
- [ ] Press Home with app running → send message → system notification appears
- [ ] Tap notification → app opens to correct chat
- [ ] Message synced to local DB before tap

### Killed (data-only FCM — production path)
Backend must send **Android data-only** (`payloadStrategy: android_data_only`, no top-level `notification` block). Verify in backend logs before testing.

- [ ] `adb shell am force-stop com.yapyap` → send message → notification appears
- [ ] While process is dead: `YapFirebaseMessagingService.onMessageReceived` runs (logcat `YapRuntimeDiag`)
- [ ] While process is dead: `APPLICATION_NOTIFICATION_CREATED` (app-built tray, not OS hybrid)
- [ ] While process is dead: `DeliveryAck` + `SOCKET_EMIT message:delivered` (sender sees ✓✓)
- [ ] Tap notification → `PushIntentParser` sees `yapyap_from_notification=true` → chat opens (not launcher-only `ACTION_MAIN`)
- [ ] Swipe app from recents (without force-stop) → same behaviour as above

**Backend log grep (per send):**
```
payloadStrategy":"android_data_only"
hasTopLevelNotification":false
priority":"high"
```

### Rebooted device
- [ ] Reboot device without opening app → send message → notification arrives (requires prior token registration)
- [ ] Open app after reboot → token re-verified with backend on resume

### Permissions (Android 13+)
- [ ] Fresh install prompts for POST_NOTIFICATIONS once
- [ ] Grant permission → device registers with backend
- [ ] Deny permanently → settings screen opens
- [ ] Re-grant in settings → notifications work on next resume

### OEM / battery optimization matrix (physical devices required)

Data-only + `android.priority: high` is correct for production, but some OEMs delay or drop background work. Test on real hardware — emulators do not reproduce Doze/OEM kills.

| OEM | Models to test | Extra steps |
|-----|----------------|-------------|
| **Google Pixel / stock Android** | Any | Baseline — expect reliable delivery |
| **Samsung** | Galaxy (One UI) | Test force-stop + swipe-away; check "Sleeping apps" / "Deep sleeping apps" lists |
| **Xiaomi / Redmi / POCO** | MIUI / HyperOS | Disable battery saver for YapYap; allow autostart; lock app in recents |
| **Oppo / Realme / OnePlus** | ColorOS / OxygenOS | Allow background activity; disable battery optimization for YapYap |
| **Vivo / iQOO** | Funtouch / OriginOS | Allow high background power consumption; add to protected apps |

**Per OEM pass criteria:**
- [ ] Force-stop → message within 30s → tray notification + delivery ack
- [ ] Tap → deep link to correct chat
- [ ] Background (home, not force-stop) → same within 30s
- [ ] After 15 min screen-off (Doze) → message still arrives (may be delayed ≤ few minutes on aggressive OEMs)

**If delivery fails on an OEM:** capture `adb logcat YapRuntimeDiag:I FirebaseMessaging:I *:S` and backend `[PUSH_DIAG] fcm_invoke` with `fcmPayload`. Distinguish backend-not-sent vs OEM-delayed vs client-not-displayed.

**Future client UX (optional):** in-app guidance when repeated push failures detected on known aggressive OEMs (battery/autostart settings). Do not block core flow on this.

---

## iOS

### Foreground
- [ ] Send push while app is open → banner/sound/badge shown
- [ ] Message synced via coordinator
- [ ] Viewing same chat → no duplicate banner (coordinator suppresses)

### Background
- [ ] Press Home → send push → notification appears
- [ ] Tap notification → navigates to correct chat

### Terminated
- [ ] Force-quit app → send push → notification appears
- [ ] Tap notification → cold-start navigates to chat
- [ ] `launchOptions` remote notification handled on cold start

### Rebooted device
- [ ] Reboot device → send push without opening app → notification arrives
- [ ] Open app → FCM token refreshed and registered

### APNs / Firebase
- [ ] Physical device (not simulator) receives remote push
- [ ] `Messaging.messaging().apnsToken` set after registration
- [ ] FCM token delivered to backend via `/api/devices/register`
- [ ] Bundle ID matches `GoogleService-Info.plist` (`com.yapyap.YapYap`)
