#!/usr/bin/env bash
# Build a per-clientMessageId timeline from Android logcat + optional backend logs.
#
# Usage:
#   adb logcat -d | ./scripts/outbox-trace-timeline.sh
#   adb logcat -d | ./scripts/outbox-trace-timeline.sh burst-a-xxx burst-b-xxx
set -euo pipefail

IDS=("$@")

filter_line() {
  if [[ ${#IDS[@]} -eq 0 ]]; then
    rg -i "OutboxTrace|OUTBOX_TRACE|MESSAGE_NEW_RECEIVED|SOCKET_CONNECTED|CONVERSATION_" || true
  else
    local pattern
    pattern=$(IFS='|'; echo "${IDS[*]}")
    rg -i "OutboxTrace|OUTBOX_TRACE|MESSAGE_NEW_RECEIVED|SOCKET_CONNECTED|CONVERSATION_|${pattern}" || true
  fi
}

echo "=== Raw pipeline events (chronological) ==="
filter_line | sed -n 's/.*\(OutboxTrace\|OUTBOX_TRACE\|MESSAGE_NEW\|SOCKET_CONNECTED\|CONVERSATION_\|CHAT_\|UI_MESSAGES\).*/\0/p' | head -200

echo ""
echo "=== Per clientMessageId stages ==="
mapfile -t lines < <(filter_line)

for id in "${IDS[@]}"; do
  echo ""
  echo "Message ${id}"
  echo "--------------------------------"
  for line in "${lines[@]}"; do
    if [[ "$line" == *"$id"* ]]; then
      ts=$(echo "$line" | rg -o '[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}' | head -1 || echo "??:??:??")
      stage=$(echo "$line" | rg -o 'stage=[A-Z_]+' | head -1 | cut -d= -f2 || echo "$line")
      echo "${ts} ${stage}"
    fi
  done
done
