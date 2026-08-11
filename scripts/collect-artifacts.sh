#!/usr/bin/env bash
# Gather the evidence from the latest runs into one timestamped bundle under artifacts/.
# Copies text-sized outputs and *references* large binaries rather than duplicating them.
#
#   scripts/collect-artifacts.sh
set -uo pipefail

cd "$(dirname "$0")/.."

STAMP=$(date +%Y%m%d-%H%M%S)
BUNDLE="artifacts/bundle-$STAMP"
mkdir -p "$BUNDLE"

copied=0
note() { printf '  %s\n' "$1"; }

# ---------------------------------------------------------------- environment metadata
{
  echo "collected_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "host_os:      $(uname -srm)"
  echo "git_commit:   $(git rev-parse HEAD 2>/dev/null || echo n/a)"
  echo "git_branch:   $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo n/a)"
  echo "git_dirty:    $(if [ -n "$(git status --porcelain 2>/dev/null)" ]; then echo yes; else echo no; fi)"
  echo "java:         $(java -version 2>&1 | head -1 || echo n/a)"
  echo "adb:          $(adb version 2>/dev/null | head -1 || echo n/a)"
  echo "maestro:      $(maestro --version 2>/dev/null | head -1 || echo n/a)"
  echo "devices:"
  adb devices -l 2>/dev/null | sed 's/^/  /' || echo "  n/a"
} > "$BUNDLE/environment.txt"
note "environment.txt"

# ------------------------------------------------------------------- git state (text)
git status --short > "$BUNDLE/git-status.txt" 2>/dev/null || true
git diff --stat     > "$BUNDLE/git-diff-stat.txt" 2>/dev/null || true
note "git-status.txt, git-diff-stat.txt"

# --------------------------------------------------------------------- Maestro results
latest_maestro=$(find artifacts/maestro -maxdepth 1 -type d -name '20*' 2>/dev/null | sort | tail -1)
if [ -n "${latest_maestro:-}" ]; then
  mkdir -p "$BUNDLE/maestro"
  find "$latest_maestro" -maxdepth 1 -name '*.xml' -exec cp {} "$BUNDLE/maestro/" \; 2>/dev/null || true
  find "$latest_maestro" -maxdepth 1 -name '*.txt' -exec cp {} "$BUNDLE/maestro/" \; 2>/dev/null || true
  # Screenshots are the one binary worth keeping — they are small and they answer questions.
  find "$latest_maestro" -name '*.png' 2>/dev/null | head -20 | while IFS= read -r png; do
    cp "$png" "$BUNDLE/maestro/" 2>/dev/null || true
  done
  note "maestro/ (from $latest_maestro)"
  copied=$((copied + 1))
else
  note "maestro/ — none found (run scripts/run-smoke.sh first)"
fi

# ---------------------------------------------------------------------- unit test results
if [ -d app/build/test-results ]; then
  mkdir -p "$BUNDLE/unit-tests"
  find app/build/test-results -name '*.xml' -exec cp {} "$BUNDLE/unit-tests/" \; 2>/dev/null || true
  note "unit-tests/ ($(find "$BUNDLE/unit-tests" -name '*.xml' | wc -l | tr -d ' ') xml files)"
  copied=$((copied + 1))
fi

# -------------------------------------------------------------- instrumentation results
if [ -d app/build/outputs/androidTest-results ]; then
  mkdir -p "$BUNDLE/instrumentation-tests"
  find app/build/outputs/androidTest-results -name '*.xml' \
    -exec cp {} "$BUNDLE/instrumentation-tests/" \; 2>/dev/null || true
  note "instrumentation-tests/"
  copied=$((copied + 1))
fi

# ------------------------------------------------------------------------- build logs
find artifacts -maxdepth 1 -name 'build-*.log' 2>/dev/null | sort | tail -3 | while IFS= read -r log; do
  cp "$log" "$BUNDLE/" 2>/dev/null || true
done
find artifacts -maxdepth 1 -name 'logcat-*.txt' 2>/dev/null | sort | tail -3 | while IFS= read -r log; do
  cp "$log" "$BUNDLE/" 2>/dev/null || true
done

# ------------------------------------------- large binaries: reference, do not duplicate
{
  echo "# Large outputs are referenced, not copied, to keep the bundle small."
  echo
  for f in app/build/outputs/apk/debug/app-debug.apk \
           app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk; do
    if [ -f "$f" ]; then
      echo "$f  ($(du -h "$f" | cut -f1), modified $(date -r "$f" +%Y-%m-%dT%H:%M:%S))"
    fi
  done
  for d in app/build/reports/androidTests/connected app/build/reports/tests/testDebugUnitTest \
           app/build/reports/lint-results-debug.html; do
    [ -e "$d" ] && echo "$d  (HTML report)"
  done
} > "$BUNDLE/large-outputs.txt"
note "large-outputs.txt (references only)"

echo
echo "Bundle: $BUNDLE"
echo "Size:   $(du -sh "$BUNDLE" | cut -f1)"
[ "$copied" -eq 0 ] && echo "NOTE: no test results found — nothing has been executed yet."
