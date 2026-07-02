# Call Media Production Readiness

This document summarizes the production LiveKit media pass, what was implemented, known limitations, and verification status.

## LiveKit SDK Versions

| Platform | Version | Source |
|----------|---------|--------|
| Android | **2.24.1** | `gradle/libs.versions.toml` |
| iOS | **2.15.1** | Swift Package Manager (`iosApp`) |

## Files Changed (Production Media Pass)

### Common (shared)

| File | Change |
|------|--------|
| `core/call/AndroidCallMediaController.kt` | Real media pipeline, `CALL_*` stage logging, track events, audio focus, reconnect republish |
| `core/call/CallMediaSessionBinder.kt` | `canJoinMedia` gate, foreground hooks, failure logging |
| `core/call/CallMediaCredentialsStore.kt` | `canJoinMedia` flag per session |
| `core/call/CallRingtoneController.kt` | Ringtone interface |
| `core/call/CallRingtoneCoordinator.kt` | Incoming ring / outgoing ringback lifecycle |
| `core/call/CallMediaPlatformHooks.kt` | expect/actual platform hooks |
| `core/call/CallCoordinator.kt` | Starts ringtone coordinator |
| `core/call/di/CallModule.kt` | DI wiring |
| `feature/call/data/repository/CallRepositoryImpl.kt` | Stores `canJoinMedia` from reconciliation |
| `feature/call/presentation/CallVideoRenderer.kt` | `CallVideoFit` (Fill / Fit) |
| `feature/call/presentation/ActiveCallScreen.kt` | Local PiP uses `Fit`, mirror front camera |

### Android

| File | Change |
|------|--------|
| `core/call/CallAudioFocusManager.kt` | Voice-communication audio focus |
| `core/call/CallForegroundService.kt` | Foreground service during active call |
| `core/call/AndroidCallRingtoneController.kt` | System ringtone + vibration / ringback tone |
| `core/call/CallMediaPlatformHooks.android.kt` | Starts/stops foreground service |
| `core/di/PlatformModule.android.kt` | Ringtone DI |
| `androidApp/.../AndroidManifest.xml` | FGS permissions + service declaration |

### iOS

| File | Change |
|------|--------|
| `iosApp/YapLiveKitBridge.swift` | Full stage logging, track mute/unmute, audio subscribe |
| `core/call/IosLiveKitBridgeRegistry.kt` | `iosLiveKitLogStage`, video fit param |
| `core/call/IosCallRingtoneController.kt` | System sound loop + vibration |
| `core/call/IosLiveKitVideoViewFactory.kt` | Fit parameter |
| `feature/call/presentation/CallVideoRenderer.ios.kt` | Fit + mirror |

## Implemented vs Remaining

### Implemented

1. **Real LiveKit media** when `LIVEKIT_DISABLED` is false (stub tokens still route to fake media).
2. **Stage diagnostics** — filter logcat/console for `CALL_ROOM_*`, `CALL_LOCAL_*`, `CALL_REMOTE_*`, `CALL_CAMERA_*`, `CALL_MIC_*`, `CALL_RINGTONE_*`.
3. **Lazy permissions** — microphone for voice; camera + microphone for video (existing `CallPermissionPromptController`).
4. **Android audio** — speaker, earpiece, Bluetooth SCO, audio focus, `MODE_IN_COMMUNICATION`.
5. **iOS audio** — `AVAudioSession` voice chat, speaker/receiver override.
6. **Video renderer** — remote fullscreen (Fill), local PiP (Fit, mirrored).
7. **Remote track events** — join/leave, publish, subscribe, mute/unmute update UI flows.
8. **Connection recovery** — LiveKit reconnect + republish local tracks on `RoomEvent.Reconnected`.
9. **Ringtone / ringback** — plays on Incoming/Outgoing; stops on accept/reject/end/timeout transitions.
10. **Android foreground service** — ongoing notification during Connected state.
11. **`canJoinMedia`** — media join deferred until backend reconciliation allows it.

### Not Yet Implemented (Phase 2)

| Area | Status |
|------|--------|
| Android ConnectionService / Telecom | Not started — required for native incoming UI when killed |
| iOS CallKit + PushKit VoIP | Not started — required for native incoming UI when killed |
| Terminated-app native incoming call UI | Not started |
| Full cross-device E2E test matrix | Not executed in CI |

Foreground incoming (socket → overlay → ringtone) works via existing `CallExperienceHost` + new ringtone coordinator.

Background/killed native call UI requires Phase 2 native integrations above.

## Diagnostic Stages Reference

Use these tags to pinpoint where media stops:

```
CALL_ROOM_CONNECTING → CALL_ROOM_CONNECTED → CALL_LOCAL_AUDIO_CREATED →
CALL_LOCAL_AUDIO_PUBLISHED → CALL_LOCAL_VIDEO_CREATED → CALL_LOCAL_VIDEO_PUBLISHED →
CALL_REMOTE_PARTICIPANT_JOINED → CALL_REMOTE_AUDIO_TRACK → CALL_REMOTE_VIDEO_TRACK
```

Failure path: `CALL_ROOM_FAILED`, `CALL_MEDIA_JOIN_DEFERRED` (canJoinMedia=false).

## Simulator Limitations

| Platform | Voice | Video | Notes |
|----------|-------|-------|-------|
| Android Emulator | Yes | Yes (limited) | Camera may use webcam; Bluetooth routing N/A |
| iOS Simulator | Best effort | No real camera | LiveKit video publish often fails; use physical device for video E2E |

## Devices Tested (This Pass)

| Device | OS | Result |
|--------|-----|--------|
| Build agent | — | `./gradlew :androidApp:assembleDebug` ✅ |
| Build agent | — | `:shared:testAndroidHostTest --tests "com.yapyap.core.call.*"` ✅ |
| Physical devices | — | **Not run** — manual E2E required with `LIVEKIT_DISABLED` unset |

## Production Readiness Checklist

- [x] LiveKit connect / publish / subscribe (Android controller)
- [x] LiveKit connect / publish / subscribe (iOS Swift bridge)
- [x] Stub / disabled mode preserves call flow
- [x] Stage logging for media pipeline
- [x] Mute / camera / switch camera / speaker routing
- [x] Video renderer (remote full + local PiP)
- [x] Room reconnect + track republish
- [x] Incoming ringtone + outgoing ringback
- [x] Android foreground service + audio focus
- [x] Lazy runtime permissions with retry / settings
- [ ] Android Telecom incoming (killed app)
- [ ] iOS CallKit incoming (killed app)
- [ ] Cross-platform E2E matrix (A↔A, A↔iOS, i↔iOS)
- [ ] Physical device verification with real LiveKit server

## Enabling Real Media

1. Ensure backend returns valid LiveKit URL + provider token (not stub).
2. Set `LIVEKIT_DISABLED=0` or remove the env override.
3. Grant microphone (voice) or camera + microphone (video) when prompted.
4. Watch logs for `CALL_MEDIA_LIVE_MODE` then `CALL_ROOM_CONNECTED`.

## Recommended Next Steps

1. **Phase 2 native calling** — Android `ConnectionService`, iOS PushKit + CallKit.
2. **Manual E2E** — two physical devices, voice + video, foreground + background.
3. **Align iOS LiveKit SDK** to 2.24.x for parity with Android if Swift 2.15.1 causes interoperability issues.
