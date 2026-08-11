#!/usr/bin/env bash
# Build the debug APK with the checked-in Gradle wrapper.
#   scripts/build-android.sh              -> assembleDebug
#   scripts/build-android.sh <gradleTask> -> that task instead
# Exits with Gradle's exit code. Never masks a build failure.
set -euo pipefail

cd "$(dirname "$0")/.."

TASK="${1:-assembleDebug}"
APK="app/build/outputs/apk/debug/app-debug.apk"
LOG_DIR="artifacts"
LOG="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S).log"

if [ ! -x ./gradlew ]; then
  echo "ERROR: ./gradlew not found or not executable." >&2
  echo "  fix: chmod +x gradlew" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"

echo "Running: ./gradlew $TASK"
echo "Log:     $LOG"
echo

set +e
./gradlew "$TASK" --console=plain 2>&1 | tee "$LOG"
status=${PIPESTATUS[0]}
set -e

echo
if [ "$status" -ne 0 ]; then
  echo "BUILD FAILED (exit $status)"
  echo "Task: $TASK"
  echo "Full log: $LOG"
  echo
  echo "First errors:"
  grep -nE "^(e: |ERROR|FAILURE|\* What went wrong)" "$LOG" | head -20 | sed 's/^/  /'
  echo
  echo "Next: re-run with --stacktrace, or use the failure-analyst agent."
  exit "$status"
fi

echo "BUILD OK (exit 0)"
if [ "$TASK" = "assembleDebug" ] && [ -f "$APK" ]; then
  echo "APK: $APK ($(du -h "$APK" | cut -f1))"
fi
