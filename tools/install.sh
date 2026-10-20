#!/usr/bin/env bash
# Build Polymarket Overlay, install it on the TV, grant "show over other apps", turn on the hold-Menu
# toggle (added next to any accessibility services already on), and open it.
#
# Usage: TV_HOST=192.168.1.50 tools/install.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 tools/install.sh}"
PKG=com.samarei.pmoverlay
SVC=$PKG/$PKG.KeyToggleService

[ -x tools/venv/bin/python ] || { python3 -m venv tools/venv && tools/venv/bin/pip install -q -r tools/requirements.txt; }
PY="tools/venv/bin/python -I"

# JDK 17 exactly: java_home without -F silently falls back to another version.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
  for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 "$(/usr/libexec/java_home -F -v 17 2>/dev/null || true)"; do
    if [ -n "$j" ] && [ -x "$j/bin/java" ]; then JAVA_HOME=$j; break; fi
  done
fi
[ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17' \
  || { echo "Needs JDK 17: brew install openjdk@17 (or set JAVA_HOME to a JDK 17)." >&2; exit 1; }
export JAVA_HOME
if [ ! -f local.properties ]; then
  SDK="${ANDROID_HOME:-$([ -d ~/Library/Android/sdk ] && echo ~/Library/Android/sdk || echo /opt/homebrew/share/android-commandlinetools)}"
  echo "sdk.dir=$SDK" > local.properties
fi
rm -f app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug -q --console=plain
[ -f app/build/outputs/apk/debug/app-debug.apk ] || { echo "Build failed; nothing installed." >&2; exit 1; }

$PY tools/b64push.py app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/pmoverlay.apk
CURRENT=$($PY tools/tv.py "settings get secure enabled_accessibility_services" | tr -d '\r\n')
case "$CURRENT" in null|"") OTHERS="" ;; *) OTHERS=$(echo "$CURRENT" | tr ':' '\n' | grep -v "^$SVC\$" | paste -sd: - || true) ;; esac
WITH=${OTHERS:+$OTHERS:}$SVC
printf '%s\n' \
  "pm install -r /data/local/tmp/pmoverlay.apk" \
  "rm -f /data/local/tmp/pmoverlay.apk" \
  "appops set $PKG SYSTEM_ALERT_WINDOW allow" \
  "settings put secure enabled_accessibility_services '${OTHERS}'" \
  "sleep 1" \
  "settings put secure enabled_accessibility_services '$WITH'" \
  "settings put secure accessibility_enabled 1" \
  "am start -n $PKG/.MainActivity" \
  | $PY tools/tv.py --batch
echo "Installed. Hold Menu (≡) on the remote in any app to show or hide the overlay."
