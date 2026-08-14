#!/usr/bin/env bash
# Deterministic repository quality gate.
#
#   scripts/verify-results.sh              full gate; device suites run when a device exists
#   scripts/verify-results.sh --no-build   fast static-only harness validation
#
# PASS / FAIL / SKIPPED are distinct. Missing evidence is never converted to PASS.
set -uo pipefail

cd "$(dirname "$0")/.."
mkdir -p artifacts

STATIC_ONLY=0
[ "${1:-}" = "--no-build" ] && STATIC_ONLY=1

pass=0; fail=0; skip=0
results=""

record() {
  results="${results}$1|$2|$3
"
  case "$1" in
    PASS)    pass=$((pass + 1)); printf '  [PASS]    %-30s %s\n' "$2" "$3" ;;
    FAIL)    fail=$((fail + 1)); printf '  [FAIL]    %-30s %s\n' "$2" "$3" ;;
    SKIPPED) skip=$((skip + 1)); printf '  [SKIPPED] %-30s %s\n' "$2" "$3" ;;
  esac
}

echo "=============================================="
echo " AGENTIC ANDROID ENGINEERING QUALITY GATE"
echo " $(date -u +%Y-%m-%dT%H:%M:%SZ)  commit $(git rev-parse --short HEAD 2>/dev/null || echo n/a)"
echo "=============================================="

echo
echo "GATE 1 — harness structure"
REQUIRED="
CLAUDE.md
README.md
.claude/settings.json
.claude/hooks/quick-check.sh
.claude/agents/planner.md
.claude/agents/android-developer.md
.claude/agents/android-test-engineer.md
.claude/agents/verifier.md
.claude/agents/failure-analyst.md
.claude/skills/android-development/SKILL.md
.claude/skills/unit-testing/SKILL.md
.claude/skills/espresso-testing/SKILL.md
.claude/skills/maestro-testing/SKILL.md
.claude/skills/android-debugging/SKILL.md
.claude/skills/qa-risk-analysis/SKILL.md
.claude/skills/verification-gates/SKILL.md
.claude/skills/ci-debugging/SKILL.md
docs/agentic-engineering-lab.md
docs/architecture.md
docs/agent-workflow.md
docs/quality-gates.md
app/src/androidTest/README.md
maestro/README.md
scripts/check-environment.sh
scripts/run-maestro.sh
scripts/run-smoke.sh
scripts/verify-results.sh
.github/workflows/pr-checks.yml
.github/workflows/espresso.yml
.github/workflows/maestro-tests.yml
"
missing=""
for f in $REQUIRED; do [ -f "$f" ] || missing="$missing $f"; done
if [ -n "$missing" ]; then
  record FAIL "required files" "missing:$missing"
else
  record PASS "required files" "$(echo "$REQUIRED" | grep -c .) files present"
fi

FORBIDDEN=".claude/agents/qa-test-designer.md .claude/agents/maestro-implementer.md"
legacy=""
for f in $FORBIDDEN; do [ -e "$f" ] && legacy="$legacy $f"; done
if [ -n "$legacy" ]; then
  record FAIL "legacy role files removed" "still present:$legacy"
else
  record PASS "legacy role files removed" "role topology is responsibility-based"
fi

echo
echo "GATE 2 — static checks"
sh_bad=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] || continue
  bash -n "$s" 2>/dev/null || sh_bad="$sh_bad $s"
done
[ -n "$sh_bad" ] && record FAIL "shell syntax" "failed:$sh_bad" || record PASS "shell syntax" "all scripts parse"

not_exec=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] && [ ! -x "$s" ] && not_exec="$not_exec $s"
done
[ -n "$not_exec" ] && record FAIL "scripts executable" "not executable:$not_exec" || record PASS "scripts executable" "all scripts executable"

if command -v maestro >/dev/null 2>&1; then
  flow_bad=""; flow_n=0
  while IFS= read -r f; do
    flow_n=$((flow_n + 1))
    maestro check-syntax "$f" >/dev/null 2>&1 || flow_bad="$flow_bad $f"
  done < <(find maestro -name '*.yaml' ! -name 'config.yaml' | sort)
  if [ "$flow_n" -eq 0 ]; then
    record FAIL "maestro syntax" "no flows found"
  elif [ -n "$flow_bad" ]; then
    record FAIL "maestro syntax" "invalid:$flow_bad"
  else
    record PASS "maestro syntax" "$flow_n flows valid"
  fi
else
  record SKIPPED "maestro syntax" "maestro not installed"
fi

if grep -rnE '^[[:space:]]*-?[[:space:]]*sleep:' maestro/ >/dev/null 2>&1; then
  record FAIL "no hard sleeps" "sleep-shaped command found under maestro/"
else
  record PASS "no hard sleeps" "none found"
fi

if grep -rnE '^[[:space:]]*point:' maestro/ >/dev/null 2>&1; then
  record FAIL "no coordinate taps" "point selector found under maestro/"
else
  record PASS "no coordinate taps" "none found"
fi

if command -v python3 >/dev/null 2>&1; then
  meta_bad=$(python3 - <<'PY'
import glob
bad=[]
files=sorted(glob.glob('.claude/agents/*.md')+glob.glob('.claude/skills/*/SKILL.md'))
for path in files:
    lines=open(path,encoding='utf-8').read().split('\n')
    if not lines or lines[0].strip()!='---':
        bad.append(path+' (no frontmatter)'); continue
    try: end=lines.index('---',1)
    except ValueError:
        bad.append(path+' (unterminated frontmatter)'); continue
    fm=lines[1:end]
    for key in ('name:','description:'):
        if not any(line.startswith(key) for line in fm): bad.append(path+' (missing '+key+')')
print(' '.join(bad))
PY
)
  [ -n "$meta_bad" ] && record FAIL "agent/skill frontmatter" "$meta_bad" || record PASS "agent/skill frontmatter" "all definitions valid"
else
  record SKIPPED "agent/skill frontmatter" "python3 not available"
fi

if [ "$STATIC_ONLY" -eq 1 ]; then
  echo
echo "GATE 3 — build + JVM tests"
  record SKIPPED "gradle assembleDebug" "--no-build requested"
  record SKIPPED "gradle unit tests" "--no-build requested"
  echo
echo "GATE 4 — Espresso / instrumented"
  record SKIPPED "espresso suite" "--no-build requested"
  echo
echo "GATE 5 — Maestro smoke"
  record SKIPPED "maestro smoke suite" "--no-build requested"
else
  echo
echo "GATE 3 — build + JVM tests"
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
echo "GATE 4 — Espresso / instrumented"
  if ! command -v adb >/dev/null 2>&1; then
    record SKIPPED "espresso suite" "adb not installed"
  elif [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" = "0" ]; then
    record SKIPPED "espresso suite" "NO DEVICE AVAILABLE"
  elif [ ! -x ./gradlew ]; then
    record SKIPPED "espresso suite" "no executable ./gradlew"
  elif ./gradlew connectedDebugAndroidTest --console=plain -q > artifacts/gate-espresso.log 2>&1; then
    record PASS "espresso suite" "exit 0 (artifacts/gate-espresso.log)"
  else
    record FAIL "espresso suite" "non-zero exit (artifacts/gate-espresso.log)"
  fi

  echo
echo "GATE 5 — Maestro smoke"
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
  echo " SKIPPED gates are NOT passes:"
  printf '%s' "$results" | awk -F'|' '$1=="SKIPPED" {printf "   - %s: %s\n", $2, $3}'
fi

if [ "$fail" -gt 0 ]; then
  echo
  echo " VERDICT: FAIL"
  exit 1
fi

if [ "$skip" -gt 0 ]; then
  echo
  echo " VERDICT: PASS WITH SKIPS — skipped evidence was not proven."
  exit 0
fi

echo
echo " VERDICT: PASS — every configured gate ran and passed."
exit 0
