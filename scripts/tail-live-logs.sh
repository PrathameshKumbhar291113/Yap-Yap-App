#!/usr/bin/env bash
#
# Live dual-platform log tail for push/API testing.
#
# Usage (run in a dedicated terminal while you test):
#   ./scripts/tail-live-logs.sh
#   ./scripts/tail-live-logs.sh --clear          # clear Android logcat buffer first
#   ./scripts/tail-live-logs.sh --android-only
#   ./scripts/tail-live-logs.sh --ios-only
#
# Prerequisites:
#   - Android physical device connected (adb devices)
#   - iOS Simulator booted (open Simulator or run app once)
#   - Debug builds on both (Ktor + YapRuntimeDiag logging enabled)
#
# Backend: run separately in another terminal:
#   cd ../Yap-Yap-Backend && npm run dev
#
set -eo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

CLEAR_ANDROID=false
ANDROID=true
IOS=true

for arg in "$@"; do
  case "$arg" in
    --clear) CLEAR_ANDROID=true ;;
    --android-only) IOS=false ;;
    --ios-only) ANDROID=false ;;
    -h|--help)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (try --help)"
      exit 1
      ;;
  esac
done

prefix_lines() {
  local label="$1"
  while IFS= read -r line || [ -n "$line" ]; do
    printf '%s %s\n' "$label" "$line"
  done
}

filter_ios_line() {
  grep -iE \
    'YapRuntimeDiag|\(Ktor\)|Ktor |SocketIO|REQUEST:|RESPONSE:|METHOD:|FROM:|FirebaseMessaging|UserNotifications.*Adding|YapYapRealtime|PushNotifications|PushPolicy|PushCoordinator|DeliveryAck|MESSAGE_DELIVERY|\[HTTP\]|\[Network\]|\[Socket\]|\[Firebase\]|\[RemoteNotification\]|\[Lifecycle\]|\[BackgroundTask\]|\[Delivery\]' \
    || true
}

android_device_id() {
  "$ADB" devices 2>/dev/null | awk '$2 == "device" { print $1; exit }'
}

echo "══════════════════════════════════════════════════════════════"
echo " YapYap live logs — Android physical + iOS Simulator"
echo " Project: $ROOT"
echo " Press Ctrl+C to stop"
echo "══════════════════════════════════════════════════════════════"

PIDS=()
cleanup() {
  local pid
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT INT TERM

if $ANDROID; then
  if [ ! -x "$ADB" ]; then
    echo "[WARN] adb not found at $ADB — skipping Android"
  else
    DEVICE="$(android_device_id || true)"
    if [ -z "${DEVICE:-}" ]; then
      echo "[WARN] No Android device connected — skipping Android (run: $ADB devices)"
    else
      echo "[OK] Android device: $DEVICE"
      if $CLEAR_ANDROID; then
        "$ADB" logcat -c || true
        echo "[OK] Android logcat cleared"
      fi
      "$ADB" logcat -v time \
        YapRuntimeDiag:I Ktor:I PushNotifications:D SocketIO:D FirebaseMessaging:I \
        '*:S' 2>&1 | prefix_lines "[ANDROID]" &
      PIDS+=("$!")
    fi
  fi
fi

if $IOS; then
  if ! xcrun simctl list devices booted 2>/dev/null | grep -q '(Booted)'; then
    echo "[WARN] No booted iOS Simulator — open Simulator or run the app from Xcode"
  else
    SIM_NAME="$(xcrun simctl list devices booted 2>/dev/null | grep '(Booted)' | head -1 | sed 's/ *(.*//')"
    echo "[OK] iOS Simulator: ${SIM_NAME:-booted}"
    xcrun simctl spawn booted log stream --style compact --level info 2>&1 \
      | filter_ios_line \
      | prefix_lines "[IOS]    " &
    PIDS+=("$!")
  fi
fi

if [ "${#PIDS[@]}" -eq 0 ]; then
  echo "[ERROR] No log sources started."
  echo "        - Connect Android: $ADB devices"
  echo "        - Boot iOS Simulator: open Simulator.app or run from Xcode"
  exit 1
fi

echo ""
echo "Watch for:"
echo "  [HTTP] RESPONSE:      API status code"
echo "  [Network] BASE_URL=   resolved API host per app"
echo "  [Socket] connected     realtime path"
echo "  Firebase / UserNotifications — push path"
echo ""

wait
