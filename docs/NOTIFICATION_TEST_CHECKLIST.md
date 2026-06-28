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

### Killed
- [ ] Swipe app from recents → send message → system notification appears
- [ ] Tap notification → app cold-starts into correct chat
- [ ] `adb shell am force-stop com.yapyap` then send → notification still arrives (not force-stop blocked)

### Rebooted device
- [ ] Reboot device without opening app → send message → notification arrives (requires prior token registration)
- [ ] Open app after reboot → token re-verified with backend on resume

### Permissions (Android 13+)
- [ ] Fresh install prompts for POST_NOTIFICATIONS once
- [ ] Grant permission → device registers with backend
- [ ] Deny permanently → settings screen opens
- [ ] Re-grant in settings → notifications work on next resume

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
