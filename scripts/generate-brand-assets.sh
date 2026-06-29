#!/usr/bin/env bash
# Regenerate brand assets from SVG sources.
# - Android adaptive icon: scripts/app-icon-source.svg (light bg + inset teal tile)
# - iOS AppIcon: scripts/app-icon-ios.svg (full-bleed teal, matches Android home-screen crop)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/scripts/render-ios-app-icon.sh"
