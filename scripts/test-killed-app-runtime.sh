#!/usr/bin/env bash
# Killed-app runtime path capture (Android physical device).
# Prerequisites: debug APK installed, bob@test.com logged in, backend on :8080.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/docs/killed-app-runtime-$(date +%Y%m%d-%H%M%S)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
BASE_URL="${TEST_BASE_URL:-http://localhost:8080}"
PKG=com.yapyap

mkdir -p "$OUT_DIR"
echo "Output: $OUT_DIR"

send_msg() {
  local content="$1"
  echo ">>> SEND alice→bob: $content"
  node -e "
    const BASE='$BASE_URL';
    (async () => {
      const login = await fetch(BASE + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: 'alice@test.com', password: 'password123' }),
      }).then(r => r.json());
      if (!login.success) throw new Error(JSON.stringify(login));
      const token = login.data.accessToken;
      const conv = await fetch(BASE + '/api/conversations/direct', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ email: 'bob@test.com' }),
      }).then(r => r.json());
      const convId = conv.data.conversation.id;
      const send = await fetch(BASE + '/api/conversations/' + convId + '/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ content: '$content', message_type: 'text' }),
      }).then(r => r.json());
      console.log('conv=' + convId + ' messageId=' + send.data.message.id);
    })().catch(e => { console.error(e); process.exit(1); });
  "
}

echo "=== Phase 0: force-stop $PKG ==="
"$ADB" shell am force-stop "$PKG"
sleep 2
PID=$("$ADB" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
echo "pidof after force-stop: '${PID:-empty}'"

echo "=== Phase 1: capture logs while sending push ==="
"$ADB" logcat -c || true
"$ADB" logcat -v time YapRuntimeDiag:I FirebaseMessaging:I PushNotifications:D SocketIO:D '*:S' \
  > "$OUT_DIR/phase1-push.log" 2>&1 &
LOG1=$!
sleep 1

TS=$(date +%H%M%S)
send_msg "KilledApp-$TS"
echo "Waiting 20s for FCM delivery..."
sleep 20

kill "$LOG1" 2>/dev/null || true
wait "$LOG1" 2>/dev/null || true

echo "=== Phase 2: notification dump ==="
"$ADB" shell dumpsys notification --noredact 2>/dev/null | grep -A 30 "$PKG" > "$OUT_DIR/notifications.txt" || true

echo "=== Phase 3: tap notification (open app via launcher intent) ==="
"$ADB" logcat -c || true
"$ADB" logcat -v time YapRuntimeDiag:I '*:S' > "$OUT_DIR/phase2-tap.log" 2>&1 &
LOG2=$!
sleep 1

# Open most recent notification via status bar tap simulation: launch main activity as OS would
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
  "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
sleep 8

kill "$LOG2" 2>/dev/null || true
wait "$LOG2" 2>/dev/null || true

echo "=== Extract key lines ==="
grep -E 'ENTER|FCM_RAW|APPLICATION_|SYSTEM_|MESSAGE_ACK|SOCKET_|QUEUE_ACK|FLUSH_ACK|PendingIntent|PushIntentParser|NAVIGATION_|CHAT_OPENED|DEFERRED|NOTIFICATION_BLOCK' \
  "$OUT_DIR"/phase*.log > "$OUT_DIR/key-lines.log" 2>/dev/null || true

echo ""
echo "Key runtime lines:"
cat "$OUT_DIR/key-lines.log" 2>/dev/null || echo "(none)"
echo ""
echo "Done. Full logs: $OUT_DIR"
