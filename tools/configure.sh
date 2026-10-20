#!/usr/bin/env bash
# Change overlay settings over the network. Every option is optional.
#
#   TV_HOST=192.168.1.50 tools/configure.sh [--on|--off] [--delay 30] [--corner top-right]
#                                           [--markets "slug-or-url,..."] [--auto] [--leagues "nfl,nba"]
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --on) ARGS+=(--ez enabled true) ;;
    --off) ARGS+=(--ez enabled false) ;;
    --delay) ARGS+=(--ei delay "$2"); shift ;;
    --corner) ARGS+=(--es corner "$2"); shift ;;
    --markets) ARGS+=(--es markets "'${2//\'/}'"); shift ;;
    --auto) ARGS+=(--es markets "''") ;;
    --leagues) ARGS+=(--es leagues "'${2//\'/}'"); shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
tools/venv/bin/python -I tools/tv.py "am broadcast -n com.samarei.pmoverlay/.ConfigReceiver -a com.samarei.pmoverlay.CONFIGURE ${ARGS[*]:-}" \
  | sed -n 's/.*data="\(.*\)"/\1/p'
