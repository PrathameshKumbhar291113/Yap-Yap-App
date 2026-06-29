#!/usr/bin/env bash
set -euo pipefail

adb reverse tcp:8080 tcp:8080
echo "Device localhost:8080 -> host localhost:8080"
