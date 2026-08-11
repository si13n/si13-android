#!/usr/bin/env bash
# The deterministic quality gate for this repo.
#
#   scripts/verify-results.sh              # structure + static + build + unit + smoke (if device)
#   scripts/verify-results.sh --no-build   # skip the Gradle build (fast static-only pass)
#
# Reports every gate as PASS / FAIL / SKIPPED and returns non-zero for REAL failures only.
# A gate that could not run is SKIPPED, never PASS. It does not fake success.
set -uo pipefail

cd "$(dirname "$0")/.."

DO_BUILD=1
[ "${1:-}" = "--no-build" ] && DO_BUILD=0

pass=0; fail=0; skip=0
results=""

record() {   # record <PASS|FAIL|SKIPPED> <gate> <detail>
  results="${results}$1|$2|$3
"
  case "$1" in
    PASS)    pass=$((pass + 1)); printf '  [PASS]    %-26s %s\n' "$2" "$3" ;;
    FAIL)    fail=$((fail + 1)); printf '  [FAIL]    %-26s %s\n' "$2" "$3" ;;
    SKIPPED) skip=$((skip + 1)); printf '  [SKIPPED] %-26s %s\n' "$2" "$3" ;;
  esac
}

echo "=============================================="
echo " QUALITY GATE"
echo " $(date -u +%Y-%m-%dT%H:%M:%SZ)  commit $(git rev-parse --short HEAD 2>/dev/null || echo n/a)"
echo "=============================================="
echo
echo "GATE 1 — required files"

REQUIRED="
CLAUDE.md
README.md
.claude/settings.json
.claude/hooks/quick-check.sh
.claude/agents/planner.md
.claude/agents/qa-test-designer.md
.claude/agents/maestro-implementer.md
.claude/agents/verifier.md
.claude/agents/failure-analyst.md
.claude/skills/maestro-testing/SKILL.md
.claude/skills/android-debugging/SKILL.md
.claude/skills/qa-risk-analysis/SKILL.md
.claude/skills/verification-gates/SKILL.md
.claude/skills/ci-debugging/SKILL.md
maestro/README.md
maestro/regression/README.md
app/src/androidTest/README.md
scripts/check-environment.sh
scripts/build-android.sh
scripts/run-maestro.sh
scripts/run-smoke.sh
scripts/collect-logcat.sh
scripts/collect-artifacts.sh
scripts/verify-results.sh
docs/agentic-qa-lab.md
docs/architecture.md
docs/agent-workflow.md
docs/quality-gates.md
docs/demo-scenario.md
.github/workflows/mobile-tests.yml
"
missing=""
for f in $REQUIRED; do
  [ -f "$f" ] || missing="$missing $f"
done
if [ -n "$missing" ]; then
  record FAIL "required files" "missing:$missing"
else
  record PASS "required files" "$(echo "$REQUIRED" | grep -c .) files present"
fi

# ---------------------------------------------------------------------------- gate 2
echo
echo "GATE 2 — static checks"

sh_bad=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] || continue
  bash -n "$s" 2>/dev/null || sh_bad="$sh_bad $s"
done
if [ -n "$sh_bad" ]; then
  record FAIL "shell syntax (bash -n)" "failed:$sh_bad"
else
  record PASS "shell syntax (bash -n)" "all scripts parse"
fi

not_exec=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] && [ ! -x "$s" ] && not_exec="$not_exec $s"
done
if [ -n "$not_exec" ]; then
  record FAIL "scripts executable" "not executable:$not_exec"
else
  record PASS "scripts executable" "all scripts executable"
fi

if command -v maestro >/dev/null 2>&1; then
  flow_bad=""
  flow_n=0
  for f in $(find maestro -name '*.yaml' ! -name 'config.yaml' | sort); do
    flow_n=$((flow_n + 1))
    maestro check-syntax "$f" >/dev/null 2>&1 || flow_bad="$flow_bad $f"
  done
  if [ "$flow_n" -eq 0 ]; then
    record FAIL "maestro check-syntax" "no flows found under maestro/"
  elif [ -n "$flow_bad" ]; then
    record FAIL "maestro check-syntax" "invalid:$flow_bad"
  else
    record PASS "maestro check-syntax" "$flow_n flows valid"
  fi
else
  record SKIPPED "maestro check-syntax" "maestro not installed"
fi

# Enforce the repo's own automation rules, so they are not just documentation.
if grep -rnE '^[[:space:]]*-?[[:space:]]*sleep:' maestro/ >/dev/null 2>&1; then
  record FAIL "no hard sleeps in flows" "$(grep -rlE '^[[:space:]]*-?[[:space:]]*sleep:' maestro/ | tr '\n' ' ')"
else
  record PASS "no hard sleeps in flows" "none found"
fi

if grep -rnE '^[[:space:]]*point:' maestro/ >/dev/null 2>&1; then
  record FAIL "no coordinate taps" "$(grep -rlE '^[[:space:]]*point:' maestro/ | tr '\n' ' ')"
else
  record PASS "no coordinate taps" "none found"
fi

if command -v python3 >/dev/null 2>&1; then
  meta_bad=$(python3 - <<'PY'
import glob, sys
bad = []
for path in sorted(glob.glob(".claude/agents/*.md") + glob.glob(".claude/skills/*/SKILL.md")):
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    if not lines or lines[0].strip() != "---":
        bad.append(path + " (no frontmatter)"); continue
    try:
        end = lines.index("---", 1)
    except ValueError:
        bad.append(path + " (unterminated frontmatter)"); continue
    fm = "\n".join(lines[1:end])
    for key in ("name:", "description:"):
        if not any(l.startswith(key) for l in fm.split("\n")):
            bad.append(path + " (missing " + key + ")")
print(" ".join(bad))
PY
)
  if [ -n "$meta_bad" ]; then
    record FAIL "agent/skill frontmatter" "$meta_bad"
  else
    record PASS "agent/skill frontmatter" "all definitions valid"
  fi
else
  record SKIPPED "agent/skill frontmatter" "python3 not available"
fi

# ---------------------------------------------------------------------------- gate 3
echo
echo "GATE 3 — build and unit tests"

if [ "$DO_BUILD" -eq 0 ]; then
  record SKIPPED "gradle assembleDebug" "--no-build requested"
  record SKIPPED "gradle unit tests" "--no-build requested"
elif [ ! -x ./gradlew ]; then
  record SKIPPED "gradle assembleDebug" "no executable ./gradlew"
  record SKIPPED "gradle unit tests" "no executable ./gradlew"
else
  if ./gradlew assembleDebug --console=plain -q > artifacts/gate-build.log 2>&1; then
    record PASS "gradle assembleDebug" "exit 0 (artifacts/gate-build.log)"
  else
    record FAIL "gradle assembleDebug" "non-zero exit — see artifacts/gate-build.log"
  fi

  if ./gradlew testDebugUnitTest --console=plain -q > artifacts/gate-unit.log 2>&1; then
    record PASS "gradle unit tests" "exit 0 (artifacts/gate-unit.log)"
  else
    record FAIL "gradle unit tests" "non-zero exit — see artifacts/gate-unit.log"
  fi
fi

# ---------------------------------------------------------------------------- gate 4
echo
echo "GATE 4 — UI smoke tests"

if ! command -v adb >/dev/null 2>&1; then
  record SKIPPED "maestro smoke suite" "adb not installed"
elif ! command -v maestro >/dev/null 2>&1; then
  record SKIPPED "maestro smoke suite" "maestro not installed"
elif [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" = "0" ]; then
  record SKIPPED "maestro smoke suite" "SKIPPED — NO DEVICE AVAILABLE"
else
  scripts/run-smoke.sh
  smoke_status=$?
  case "$smoke_status" in
    0) record PASS    "maestro smoke suite" "all flows passed" ;;
    3) record SKIPPED "maestro smoke suite" "SKIPPED — NO DEVICE AVAILABLE" ;;
    *) record FAIL    "maestro smoke suite" "exit $smoke_status — see artifacts/maestro/" ;;
  esac
fi

# ---------------------------------------------------------------------------- summary
echo
echo "=============================================="
echo " RESULT: $pass passed, $fail failed, $skip skipped"
echo "=============================================="

if [ "$skip" -gt 0 ]; then
  echo
  echo " SKIPPED gates are NOT passes. Unverified means unverified:"
  printf '%s' "$results" | awk -F'|' '$1=="SKIPPED" {printf "   - %s: %s\n", $2, $3}'
fi

if [ "$fail" -gt 0 ]; then
  echo
  echo " FAILED gates:"
  printf '%s' "$results" | awk -F'|' '$1=="FAIL" {printf "   - %s: %s\n", $2, $3}'
  echo
  echo " VERDICT: FAIL"
  exit 1
fi

if [ "$skip" -gt 0 ]; then
  echo
  echo " VERDICT: PASS WITH SKIPS — the skipped gates above were not proven."
  # Exit 0: skips are an honest, expected outcome (e.g. CI with no emulator), not a failure.
  # They are reported loudly so nobody mistakes this for full verification.
  exit 0
fi

echo
echo " VERDICT: PASS — every gate ran and passed."
exit 0
