#!/usr/bin/env bash
# Single source of truth for static Agentic Android Engineering harness validation.
# CI calls this directly; verify-results.sh calls the same script locally.
set -uo pipefail

cd "$(dirname "$0")/.."

ALLOW_MISSING_MAESTRO=0
[ "${1:-}" = "--allow-missing-maestro" ] && ALLOW_MISSING_MAESTRO=1

fail=0
ok()   { printf '  [PASS] %s\n' "$1"; }
bad()  { printf '  [FAIL] %s\n' "$1" >&2; fail=1; }
skip() { printf '  [SKIP] %s\n' "$1"; }

echo "HARNESS STATIC VALIDATION"

REQUIRED="
CLAUDE.md
.claude/settings.json
.claude/hooks/quick-check.sh
.claude/hooks/guard-agent-bash.sh
.claude/agents/planner.md
.claude/agents/android-developer.md
.claude/agents/android-test-engineer.md
.claude/agents/verifier.md
.claude/agents/failure-analyst.md
.claude/skills/change/SKILL.md
.claude/skills/android-development/SKILL.md
.claude/skills/unit-testing/SKILL.md
.claude/skills/android-instrumented-testing/SKILL.md
.claude/skills/espresso-testing/SKILL.md
.claude/skills/maestro-testing/SKILL.md
.claude/skills/android-debugging/SKILL.md
.claude/skills/qa-risk-analysis/SKILL.md
.claude/skills/verification-gates/SKILL.md
.claude/skills/ci-debugging/SKILL.md
docs/agentic-engineering-lab.md
docs/architecture.md
docs/task-contract.md
docs/quality-gates.md
scripts/validate-harness.sh
scripts/verify-results.sh
.github/workflows/pr-checks.yml
.github/workflows/espresso.yml
.github/workflows/maestro-tests.yml
"
missing=""
for f in $REQUIRED; do [ -f "$f" ] || missing="$missing $f"; done
[ -z "$missing" ] && ok "required harness files present" || bad "missing required files:$missing"

expected="android-developer.md android-test-engineer.md failure-analyst.md planner.md verifier.md"
actual="$(for f in .claude/agents/*.md; do basename "$f"; done | sort | tr '\n' ' ' | sed 's/ $//')"
[ "$actual" = "$expected" ] && ok "exact five-role agent topology" || bad "agent topology mismatch: $actual"

sh_bad=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] || continue
  bash -n "$s" >/dev/null 2>&1 || sh_bad="$sh_bad $s"
done
[ -z "$sh_bad" ] && ok "shell syntax" || bad "shell syntax failed:$sh_bad"

not_exec=""
for s in scripts/*.sh .claude/hooks/*.sh; do
  [ -f "$s" ] && [ ! -x "$s" ] && not_exec="$not_exec $s"
done
[ -z "$not_exec" ] && ok "scripts/hooks executable" || bad "not executable:$not_exec"

if command -v python3 >/dev/null 2>&1; then
  meta_bad=$(python3 - <<'PY'
import glob
bad=[]
for path in sorted(glob.glob('.claude/agents/*.md')+glob.glob('.claude/skills/*/SKILL.md')):
    lines=open(path,encoding='utf-8').read().splitlines()
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
  [ -z "$meta_bad" ] && ok "agent/skill frontmatter" || bad "$meta_bad"
else
  bad "python3 required to validate agent/skill definitions"
fi

if grep -rnE '^[[:space:]]*-?[[:space:]]*sleep:' maestro/ >/dev/null 2>&1; then
  bad "hard sleep found under maestro/"
else
  ok "no hard sleeps in Maestro"
fi
if grep -rnE '^[[:space:]]*point:' maestro/ >/dev/null 2>&1; then
  bad "coordinate point found under maestro/"
else
  ok "no coordinate taps in Maestro"
fi

if command -v maestro >/dev/null 2>&1; then
  flow_bad=""; count=0
  while IFS= read -r f; do
    count=$((count+1))
    maestro check-syntax "$f" >/dev/null 2>&1 || flow_bad="$flow_bad $f"
  done < <(find maestro -name '*.yaml' ! -name 'config.yaml' | sort)
  if [ "$count" -eq 0 ]; then
    bad "no Maestro flows found"
  elif [ -n "$flow_bad" ]; then
    bad "invalid Maestro flow(s):$flow_bad"
  else
    ok "Maestro syntax ($count flows)"
  fi
elif [ "$ALLOW_MISSING_MAESTRO" -eq 1 ]; then
  skip "Maestro CLI not installed; syntax not proven"
else
  bad "Maestro CLI is required for full static harness validation"
fi

# Do not let old workflow names creep back into canonical docs/config. Exclude this validator
# itself because the forbidden token is necessarily present in the pattern below.
if grep -Rsn --exclude-dir=.git --exclude='*.lock' --exclude='validate-harness.sh' 'mobile-tests.yml' CLAUDE.md README.md docs .github >/dev/null 2>&1; then
  bad "stale mobile-tests.yml reference found"
else
  ok "no stale workflow references"
fi

if [ "$fail" -ne 0 ]; then
  echo "HARNESS VALIDATION: FAIL" >&2
  exit 1
fi

echo "HARNESS VALIDATION: PASS"
exit 0
