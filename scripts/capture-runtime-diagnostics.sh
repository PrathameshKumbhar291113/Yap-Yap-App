#!/usr/bin/env bash
# Captures YapRuntimeDiag logs during Scenario A/B reproduction.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/docs/runtime-diagnostics-$(date +%Y%m%d-%H%M%S)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
BASE_URL="${TEST_BASE_URL:-http://localhost:8080}"
WAIT_BG="${DIAG_BG_WAIT_SEC:-150}"

mkdir -p "$OUT_DIR"

echo "Output: $OUT_DIR"

# Start log capture
"$ADB" logcat -c || true
"$ADB" logcat -v time -s YapRuntimeDiag:* PushNotifications:* SocketIO:* > "$OUT_DIR/android.log" 2>&1 &
ANDROID_LOG_PID=$!

xcrun simctl spawn booted log stream --style compact --predicate 'eventMessage CONTAINS "YapRuntimeDiag" OR eventMessage CONTAINS "SocketIO"' > "$OUT_DIR/ios.log" 2>&1 &
IOS_LOG_PID=$!

cleanup() {
  kill "$ANDROID_LOG_PID" 2>/dev/null || true
  kill "$IOS_LOG_PID" 2>/dev/null || true
}
trap cleanup EXIT

sleep 2

# Launch apps (assumes already installed; user logged in as alice/android + bob/ios)
"$ADB" shell am start -n com.yapyap/.MainActivity >/dev/null 2>&1 || true
xcrun simctl launch booted com.yapyap.YapYap >/dev/null 2>&1 || true
sleep 5

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

echo "========== SCENARIO A =========="
echo "Background iOS simulator..."
xcrun simctl io booted press home 2>/dev/null || osascript -e 'tell application "Simulator" to activate' -e 'tell application "System Events" to key code 36' 2>/dev/null || true
sleep 3

send_msg "A-initial" "alice@test.com" "bob@test.com" "ScenarioA-initial-$(date +%H%M%S)"
sleep 10
send_msg "A-mid" "alice@test.com" "bob@test.com" "ScenarioA-mid-$(date +%H%M%S)"

echo "Waiting ${WAIT_BG}s (background task expiry window)..."
sleep "$WAIT_BG"

send_msg "A-after-wait" "alice@test.com" "bob@test.com" "ScenarioA-after-wait-$(date +%H%M%S)"
sleep 10

echo "Foreground iOS..."
xcrun simctl launch booted com.yapyap.YapYap >/dev/null 2>&1 || true
sleep 5
send_msg "A-after-foreground" "alice@test.com" "bob@test.com" "ScenarioA-after-foreground-$(date +%H%M%S)"
sleep 10

echo "========== SCENARIO B =========="
echo "Background Android physical..."
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3

send_msg "B-initial" "bob@test.com" "alice@test.com" "ScenarioB-initial-$(date +%H%M%S)"
sleep 15
send_msg "B-second" "bob@test.com" "alice@test.com" "ScenarioB-second-$(date +%H%M%S)"
sleep 10

echo "Done. Logs in $OUT_DIR"
sleep 3
