#!/usr/bin/env bash
# Remove Polymarket Overlay and its hold-Menu toggle, leaving any other accessibility services as they were.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
SVC=com.samarei.pmoverlay/com.samarei.pmoverlay.KeyToggleService
CURRENT=$(tools/venv/bin/python -I tools/tv.py "settings get secure enabled_accessibility_services" | tr -d '\r\n')
case "$CURRENT" in null|"") OTHERS="" ;; *) OTHERS=$(echo "$CURRENT" | tr ':' '\n' | grep -v "^$SVC\$" | paste -sd: - || true) ;; esac
printf '%s\n' \
  "settings put secure enabled_accessibility_services '${OTHERS}'" \
  "pm uninstall com.samarei.pmoverlay" \
  | tools/venv/bin/python -I tools/tv.py --batch
echo "Removed."
