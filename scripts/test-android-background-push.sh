#!/usr/bin/env bash
# Android-only background push test (physical device + real backend).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/docs/android-push-test-$(date +%Y%m%d-%H%M%S)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
BASE_URL="${TEST_BASE_URL:-http://localhost:8080}"

mkdir -p "$OUT_DIR"
echo "Output: $OUT_DIR"

send_msg() {
  local label="$1" email="$2" partner="$3" content="$4"
  echo ">>> SEND [$label] from $email: $content"
  node -e "
    const BASE='$BASE_URL';
    (async () => {
      const login = await fetch(BASE + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: '$email', password: 'password123' }),
      }).then(r => r.json());
      if (!login.success) throw new Error(JSON.stringify(login));
      const token = login.data.accessToken;
      const conv = await fetch(BASE + '/api/conversations/direct', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ email: '$partner' }),
      }).then(r => r.json());
      const convId = conv.data.conversation.id;
      const send = await fetch(BASE + '/api/conversations/' + convId + '/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({ content: '$content', message_type: 'text' }),
      }).then(r => r.json());
      console.log('    conv=' + convId + ' messageId=' + send.data.message.id);
    })().catch(e => { console.error(e); process.exit(1); });
  "
}

"$ADB" logcat -c || true
"$ADB" logcat -v time -s YapRuntimeDiag:I FirebaseMessaging:I PushNotifications:D SocketIO:D > "$OUT_DIR/android.log" 2>&1 &
LOG_PID=$!
trap 'kill $LOG_PID 2>/dev/null || true' EXIT

echo "Launching app (log in as bob@test.com if needed)..."
"$ADB" shell am start -n com.yapyap/.MainActivity
sleep 12

echo "Backgrounding Android..."
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3

TS=$(date +%H%M%S)
send_msg "immediate" "alice@test.com" "bob@test.com" "AndroidBg-immediate-$TS"
sleep 30
send_msg "30s" "alice@test.com" "bob@test.com" "AndroidBg-30s-$TS"
sleep 30
send_msg "1m" "alice@test.com" "bob@test.com" "AndroidBg-1m-$TS"
sleep 60
send_msg "2m" "alice@test.com" "bob@test.com" "AndroidBg-2m-$TS"
sleep 60
send_msg "3m" "alice@test.com" "bob@test.com" "AndroidBg-3m-$TS"
sleep 10

kill $LOG_PID 2>/dev/null || true
trap - EXIT

grep -E "Registration|Firebase|Notification|PushPolicy|Realtime|Socket" "$OUT_DIR/android.log" > "$OUT_DIR/android-key.log" || true

echo ""
echo "Key log lines:"
tail -40 "$OUT_DIR/android-key.log" 2>/dev/null || tail -40 "$OUT_DIR/android.log"
echo ""
echo "Done. Full log: $OUT_DIR/android.log"
