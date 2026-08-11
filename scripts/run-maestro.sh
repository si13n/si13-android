#!/usr/bin/env bash
# Run a single Maestro flow (or a folder of flows) against a connected Android device.
#
#   scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml
#   scripts/run-maestro.sh maestro/smoke
#
# Checks for a device first, writes artifacts, and PRESERVES Maestro's exit code so the
# verifier and CI have something real to gate on.
set -uo pipefail

cd "$(dirname "$0")/.."

FLOW="${1:-}"
if [ -z "$FLOW" ]; then
  echo "usage: scripts/run-maestro.sh <flow.yaml|flow-dir> [extra maestro args...]" >&2
  exit 2
fi
shift || true

if [ ! -e "$FLOW" ]; then
  echo "ERROR: flow not found: $FLOW" >&2
  exit 2
fi

command -v maestro >/dev/null 2>&1 || {
  echo "ERROR: maestro not on PATH." >&2
  echo "  install: curl -Ls \"https://get.maestro.mobile.dev\" | bash" >&2
  echo "  then:    export PATH=\"\$HOME/.maestro/bin:\$PATH\"" >&2
  exit 2
}
command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not on PATH." >&2; exit 2; }

# --- device gate -------------------------------------------------------------------
devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
count=$(printf '%s\n' "$devices" | grep -c . || true)

if [ "$count" -eq 0 ]; then
  echo "SKIPPED — NO DEVICE AVAILABLE"
  echo "  'adb devices' shows no device in state 'device'."
  echo "  Start an emulator, or run: maestro start-device --platform android"
  exit 3            # 3 = could not run. Distinct from 1 = test failed.
fi

DEVICE=$(printf '%s\n' "$devices" | head -1)
if [ "$count" -gt 1 ]; then
  echo "NOTE: $count devices attached; using $DEVICE"
fi

# --- unique data tag ---------------------------------------------------------------
# Flows create tasks titled "... ${MAESTRO_RUN_TAG}" so parallel or repeated runs never
# collide on data, and no flow depends on the ~100 seeded demo tasks.
RUN_TAG="${MAESTRO_RUN_TAG:-$(date +%H%M%S)-$$}"

STAMP=$(date +%Y%m%d-%H%M%S)
OUT_DIR="artifacts/maestro/$STAMP"
mkdir -p "$OUT_DIR"

echo "Flow:    $FLOW"
echo "Device:  $DEVICE"
echo "Run tag: $RUN_TAG"
echo "Output:  $OUT_DIR"
echo

adb -s "$DEVICE" logcat -c >/dev/null 2>&1 || true

maestro --device "$DEVICE" test \
  -e MAESTRO_RUN_TAG="$RUN_TAG" \
  --format junit \
  --output "$OUT_DIR/junit.xml" \
  --debug-output "$OUT_DIR/debug" \
  "$@" \
  "$FLOW"
status=$?

echo
if [ "$status" -ne 0 ]; then
  echo "MAESTRO FAILED (exit $status)"
  # Capture the device log for the failure-analyst. Never fatal if it fails.
  adb -s "$DEVICE" logcat -d > "$OUT_DIR/logcat.txt" 2>/dev/null || true
  echo "  junit:       $OUT_DIR/junit.xml"
  echo "  debug output:$OUT_DIR/debug   (screenshots, command log)"
  echo "  logcat:      $OUT_DIR/logcat.txt"
  echo
  echo "Next: use the failure-analyst agent. Do NOT add sleeps or retries."
else
  echo "MAESTRO PASSED (exit 0)"
  echo "  junit: $OUT_DIR/junit.xml"
fi

exit "$status"
