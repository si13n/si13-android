#!/usr/bin/env bash
# Deterministic repository quality gate.
#
# Exit contract:
#   0 PASS (all required evidence ran) OR PARTIAL only when --allow-skips is explicit
#   1 FAIL
#   3 INCOMPLETE (required evidence skipped/unavailable)
set -uo pipefail

cd "$(dirname "$0")/.."
mkdir -p artifacts

STATIC_ONLY=0
ALLOW_SKIPS=0
for arg in "$@"; do
  case "$arg" in
    --no-build) STATIC_ONLY=1 ;;
    --allow-skips) ALLOW_SKIPS=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

pass=0; fail=0; skip=0
results=""
record() {
  results="${results}$1|$2|$3
"
  case "$1" in
    PASS)    pass=$((pass+1)); printf '  [PASS]    %-30s %s\n' "$2" "$3" ;;
    FAIL)    fail=$((fail+1)); printf '  [FAIL]    %-30s %s\n' "$2" "$3" ;;
    SKIPPED) skip=$((skip+1)); printf '  [SKIPPED] %-30s %s\n' "$2" "$3" ;;
  esac
}

echo "=============================================="
echo " AGENTIC ANDROID ENGINEERING QUALITY GATE"
echo " $(date -u +%Y-%m-%dT%H:%M:%SZ)  commit $(git rev-parse --short HEAD 2>/dev/null || echo n/a)"
echo "=============================================="

echo
echo "GATE 1 — harness static validation"
if command -v maestro >/dev/null 2>&1; then
  if scripts/validate-harness.sh > artifacts/gate-harness.log 2>&1; then
    record PASS "harness validation" "exit 0 (artifacts/gate-harness.log)"
  else
    record FAIL "harness validation" "non-zero exit (artifacts/gate-harness.log)"
  fi
else
  if scripts/validate-harness.sh --allow-missing-maestro > artifacts/gate-harness.log 2>&1; then
    record PASS "harness validation" "non-Maestro static rules passed"
    record SKIPPED "maestro syntax" "Maestro CLI not installed"
  else
    record FAIL "harness validation" "non-zero exit (artifacts/gate-harness.log)"
  fi
fi

if [ "$STATIC_ONLY" -eq 1 ]; then
  echo
echo "GATE 2 — build + JVM tests"
  record SKIPPED "gradle assembleDebug" "--no-build requested"
  record SKIPPED "gradle unit tests" "--no-build requested"
  echo
echo "GATE 3 — Android instrumented / Espresso"
  record SKIPPED "instrumented suite" "--no-build requested"
  echo
echo "GATE 4 — Maestro smoke"
  record SKIPPED "maestro smoke suite" "--no-build requested"
else
  echo
echo "GATE 2 — build + JVM tests"
  if [ ! -x ./gradlew ]; then
    record SKIPPED "gradle assembleDebug" "no executable ./gradlew"
    record SKIPPED "gradle unit tests" "no executable ./gradlew"
  else
    if ./gradlew assembleDebug --console=plain -q > artifacts/gate-build.log 2>&1; then
      record PASS "gradle assembleDebug" "exit 0 (artifacts/gate-build.log)"
    else
      record FAIL "gradle assembleDebug" "non-zero exit (artifacts/gate-build.log)"
    fi
    if ./gradlew testDebugUnitTest --console=plain -q > artifacts/gate-unit.log 2>&1; then
      record PASS "gradle unit tests" "exit 0 (artifacts/gate-unit.log)"
    else
      record FAIL "gradle unit tests" "non-zero exit (artifacts/gate-unit.log)"
    fi
  fi

  echo
echo "GATE 3 — Android instrumented / Espresso"
  if ! command -v adb >/dev/null 2>&1; then
    record SKIPPED "instrumented suite" "adb not installed"
  elif [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" = "0" ]; then
    record SKIPPED "instrumented suite" "NO DEVICE AVAILABLE"
  elif [ ! -x ./gradlew ]; then
    record SKIPPED "instrumented suite" "no executable ./gradlew"
  elif ./gradlew connectedDebugAndroidTest --console=plain -q > artifacts/gate-instrumented.log 2>&1; then
    record PASS "instrumented suite" "exit 0 (artifacts/gate-instrumented.log)"
  else
    record FAIL "instrumented suite" "non-zero exit (artifacts/gate-instrumented.log)"
  fi

  echo
echo "GATE 4 — Maestro smoke"
  if ! command -v adb >/dev/null 2>&1; then
    record SKIPPED "maestro smoke suite" "adb not installed"
  elif ! command -v maestro >/dev/null 2>&1; then
    record SKIPPED "maestro smoke suite" "maestro not installed"
  elif [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" = "0" ]; then
    record SKIPPED "maestro smoke suite" "NO DEVICE AVAILABLE"
  else
    scripts/run-smoke.sh
    rc=$?
    case "$rc" in
      0) record PASS "maestro smoke suite" "all flows passed" ;;
      3) record SKIPPED "maestro smoke suite" "NO DEVICE AVAILABLE" ;;
      *) record FAIL "maestro smoke suite" "exit $rc — see artifacts/maestro/" ;;
    esac
  fi
fi

echo
echo "=============================================="
echo " RESULT: $pass passed, $fail failed, $skip skipped"
echo "=============================================="

if [ "$skip" -gt 0 ]; then
  echo
  echo " SKIPPED evidence is not PASS:"
  printf '%s' "$results" | awk -F'|' '$1=="SKIPPED" {printf "   - %s: %s\n", $2, $3}'
fi

if [ "$fail" -gt 0 ]; then
  echo
echo " VERDICT: FAIL"
  exit 1
fi

if [ "$skip" -gt 0 ]; then
  if [ "$ALLOW_SKIPS" -eq 1 ]; then
    echo
echo " VERDICT: PARTIAL — caller explicitly allowed skipped evidence."
    exit 0
  fi
  echo
echo " VERDICT: INCOMPLETE — required evidence was skipped or unavailable."
  exit 3
fi

echo
echo " VERDICT: PASS — every configured gate ran and passed."
exit 0
