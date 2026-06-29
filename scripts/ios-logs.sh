#!/usr/bin/env bash
# Stream YapYap iOS runtime logs in the terminal (Android logcat equivalent).
#
# Usage:
#   ./scripts/ios-logs.sh          # booted simulator
#   ./scripts/ios-logs.sh device   # connected physical iPhone
#   ./scripts/ios-logs.sh file     # tail Documents/yapyap-runtime.log (simulator)
#   ./scripts/ios-logs.sh crash    # show latest YapYap crash report path + summary
set -euo pipefail

PREDICATE='processImagePath CONTAINS[c] "YapYap" OR eventMessage CONTAINS[c] "YapRuntimeDiag" OR eventMessage CONTAINS[c] "YapYap" OR eventMessage CONTAINS[c] "SocketIO" OR eventMessage CONTAINS[c] "Outbox"'

MODE="${1:-sim}"

case "$MODE" in
  sim|simulator)
    echo "Streaming simulator logs (Ctrl+C to stop)..."
    xcrun simctl spawn booted log stream --style compact --level debug --predicate "$PREDICATE"
    ;;
  device)
    echo "Streaming device logs (Ctrl+C to stop)..."
    log stream --device --style compact --level debug --predicate "$PREDICATE"
    ;;
  file)
    APP_ID="${IOS_BUNDLE_ID:-com.yapyap.YapYap}"
    CONTAINER="$(xcrun simctl get_app_container booted "$APP_ID" data 2>/dev/null || true)"
    if [[ -z "$CONTAINER" ]]; then
      echo "Could not resolve simulator app container for $APP_ID" >&2
      echo "Set IOS_BUNDLE_ID if your bundle id differs." >&2
      exit 1
    fi
    LOG_PATH="$CONTAINER/Documents/yapyap-runtime.log"
    echo "$LOG_PATH"
    if [[ -f "$LOG_PATH" ]]; then
      echo "--- tail ---"
      tail -f "$LOG_PATH"
    else
      echo "(file not created yet — launch the debug app first)"
    fi
    ;;
  crash)
    CRASH_DIR="${HOME}/Library/Logs/DiagnosticReports"
    LATEST="$(ls -t "$CRASH_DIR"/YapYap-*.ips 2>/dev/null | head -1 || true)"
    if [[ -z "$LATEST" ]]; then
      echo "No YapYap crash reports found in $CRASH_DIR" >&2
      exit 1
    fi
    echo "$LATEST"
    echo "--- exception ---"
    rg -o '"exception"[^}]+}' "$LATEST" | head -1 || true
    echo "--- app frames (first 20) ---"
    rg -o 'chatModule[^"]*|OutgoingMessageOutbox[^"]*|ChatRepositoryImpl[^"]*|Koin[^"]*' "$LATEST" | head -20 || true
    ;;
  *)
    echo "Usage: $0 [sim|device|file|crash]" >&2
    exit 1
    ;;
esac
