#!/bin/sh
# Starts the ReLink call-audio helper (uid 2000) over authorized USB ADB without putting secrets
# in shell arguments or debug receivers. Reads the one-time 32-hex code from stdin / TTY.
set -eu

PKG="${RELINK_PKG:-app.relink.android}"
PORT="${1:-}"
[ -n "$PORT" ] || { echo "Usage: $0 <port> [serial]" >&2; exit 2; }
SERIAL_ARG=""
if [ "${2:-}" != "" ]; then
  SERIAL_ARG="-s $2"
fi

# Verify package path (prefer ReLink release id, fallback legacy debug/tether if installed).
APK=$(adb $SERIAL_ARG shell "pm path $PKG" 2>/dev/null | sed -n 's/^package://p' | head -n1 | tr -d '\r')
if [ -z "$APK" ] && [ "$PKG" = "app.relink.android" ]; then
  PKG="app.tether.android"
  APK=$(adb $SERIAL_ARG shell "pm path $PKG" 2>/dev/null | sed -n 's/^package://p' | head -n1 | tr -d '\r')
fi
[ -n "$APK" ] || { echo "ReLink APK not found on device via adb" >&2; exit 1; }

printf "Enter 32-char one-time code from ReLink UI (hidden input): " >&2
if [ -t 0 ]; then
  stty -echo
  IFS= read -r RAW_TOKEN
  stty echo
  printf "\n" >&2
else
  IFS= read -r RAW_TOKEN
fi

TOKEN=$(printf '%s' "$RAW_TOKEN" | tr -d '[:space:]-' | tr '[:upper:]' '[:lower:]')
printf '%s' "$TOKEN" | grep -Eq '^[0-9a-f]{32}$' || { echo "Invalid token: expected 32 hex chars" >&2; exit 2; }

LOG_FILE="/data/local/tmp/${PKG}-helper.log"
CMD="(umask 077 && _tf=\$(mktemp '/data/local/tmp/.${PKG}.XXXXXX') && IFS= read -r _t && [ -n \"\$_t\" ] && printf '%s\n' \"\$_t\" > \"\$_tf\" || { rm -f \"\${_tf:-}\"; echo shell-init-failed:TOKEN_INPUT; exit 1; }; setsid sh -c \"CLASSPATH=$APK exec app_process / app.tether.helper.HelperMain serve-file $PORT '\$_tf' --replace\" </dev/null >'$LOG_FILE' 2>&1 & sleep 1; if kill -0 \$! 2>/dev/null; then echo process-alive; else rm -f \"\$_tf\"; echo process-exited; grep -E '^shell-init-failed:[A-Z_]+' '$LOG_FILE' | tail -1; fi)"

printf '%s\n' "$TOKEN" | adb $SERIAL_ARG shell "$CMD"
