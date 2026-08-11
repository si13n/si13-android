#!/usr/bin/env bash
# Dump the device log into artifacts/ with a timestamped name.
#
#   scripts/collect-logcat.sh              -> full buffer + a filtered app-only extract
#   scripts/collect-logcat.sh --clear      -> clear the buffer instead (call BEFORE a run)
#
# Uses 'adb logcat -d' (dump and exit). It never tails, so it can never hang CI.
set -uo pipefail

cd "$(dirname "$0")/.."

APP_ID="com.si13.app"
OUT_DIR="artifacts"

command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not on PATH" >&2; exit 2; }

DEVICE=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
if [ -z "${DEVICE:-}" ]; then
  echo "SKIPPED — NO DEVICE AVAILABLE"
  exit 3
fi

if [ "${1:-}" = "--clear" ]; then
  adb -s "$DEVICE" logcat -c
  echo "logcat buffer cleared on $DEVICE"
  echo "Run your test now, then call scripts/collect-logcat.sh to capture just that window."
  exit 0
fi

mkdir -p "$OUT_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
FULL="$OUT_DIR/logcat-$STAMP.txt"
APP="$OUT_DIR/logcat-$STAMP-app.txt"
CRASH="$OUT_DIR/logcat-$STAMP-crashes.txt"

adb -s "$DEVICE" logcat -d > "$FULL"
echo "full buffer   -> $FULL ($(wc -l < "$FULL" | tr -d ' ') lines)"

# App-scoped extract. grep exit 1 (no matches) is normal, so do not let it kill the script.
grep -iE "si13|forgetty" "$FULL" > "$APP" 2>/dev/null || true
echo "app lines     -> $APP ($(wc -l < "$APP" | tr -d ' ') lines)"

# Narrow patterns on purpose. A broad "androidruntime" match also catches the benign
# "AndroidRuntime: VM exiting with result code 0", and a crash detector that cries wolf
# gets ignored.
grep -iE "fatal exception|anr in |^.*E AndroidRuntime|beginning of crash|process crashed" "$FULL" \
  > "$CRASH" 2>/dev/null || true
crash_lines=$(wc -l < "$CRASH" | tr -d ' ')
echo "crash/ANR     -> $CRASH ($crash_lines lines)"

if [ "$crash_lines" -gt 0 ]; then
  echo
  echo "!! CRASH OR ANR FOUND — this is very likely the real root cause:"
  head -20 "$CRASH" | sed 's/^/   /'
fi
