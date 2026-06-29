#!/usr/bin/env bash
# Regenerate iOS AppIcon from scripts/app-icon-ios.svg (full-bleed teal, matches Android on home screen).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SVG="$ROOT/scripts/app-icon-ios.svg"
OUT="$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"

npx --yes @resvg/resvg-js-cli "$SVG" "$OUT" --fit-width 1024 --fit-height 1024
echo "Wrote $OUT"
