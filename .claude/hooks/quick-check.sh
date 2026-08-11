#!/usr/bin/env bash
# PostToolUse hook: cheap, fast, non-destructive sanity checks on the file just written.
#
# CONTRACT
#   stdin : the PostToolUse JSON payload from Claude Code
#   exit 0: nothing to say (or advisory warnings only, printed to stdout)
#   exit 2: a real defect was found — the message on stderr goes back to Claude to fix
#
# DESIGN RULES (see .claude/README-hooks.md for the full rationale)
#   - Read-only. It never edits, formats, installs or git-commits anything.
#   - Fast. Static checks only. It MUST NOT build, install an APK, boot an emulator,
#     or run a UI test. Full verification is a separate, deliberate step.
#   - Honest. It does not hide failures; it also does not block on style opinions.
#     Syntax errors block (exit 2). Anti-patterns warn (exit 0).
set -uo pipefail

TIMEOUT_GUARD=25   # documented ceiling; individual checks below are all sub-second

payload=$(cat)

# Extract the edited path. jq if available, else a small python fallback, else give up quietly.
if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty')
elif command -v python3 >/dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin).get("tool_input", {})
    print(d.get("file_path") or d.get("notebook_path") or "")
except Exception:
    print("")')
else
  exit 0
fi

[ -n "${file_path:-}" ] || exit 0
[ -f "$file_path" ] || exit 0

errors=()
warnings=()

# Parse-check a YAML file (multi-document, as Maestro flows are).
# Returns 0 = parses, 1 = real parse error (message on stdout), 2 = cannot check.
# The 2 case matters: PyYAML is not in every system python3, and a missing checker must
# skip rather than be reported as a failure — or as a pass.
yaml_parse_check() {
  command -v python3 >/dev/null 2>&1 || return 2
  python3 -c 'import yaml' >/dev/null 2>&1 || return 2
  python3 -c '
import sys, yaml
try:
    list(yaml.safe_load_all(open(sys.argv[1], encoding="utf-8")))
except Exception as e:
    print(e); sys.exit(1)
' "$1" 2>&1 || return 1
  return 0
}

case "$file_path" in

  # ---------------------------------------------------------------- shell scripts
  *.sh)
    if ! out=$(bash -n "$file_path" 2>&1); then
      errors+=("bash -n failed:"$'\n'"$out")
    fi
    if [ ! -x "$file_path" ] && [[ "$file_path" == */scripts/* || "$file_path" == */hooks/* ]]; then
      warnings+=("not executable — run: chmod +x $file_path")
    fi
    if head -1 "$file_path" | grep -qv '^#!'; then
      warnings+=("missing shebang on line 1")
    fi
    # A test wrapper that swallows exit codes silently defeats every quality gate.
    if grep -qE '(\|\|[[:space:]]*true|set \+e)' "$file_path"; then
      warnings+=("contains '|| true' or 'set +e' — make sure a test exit code is not being swallowed")
    fi
    ;;

  # ------------------------------------------------------------- maestro flow YAML
  */maestro/*.yaml|*/maestro/*.yml)
    # 1. YAML parses at all. Maestro flows are multi-document (header --- commands).
    out=$(yaml_parse_check "$file_path"); rc=$?
    [ "$rc" -eq 1 ] && errors+=("YAML does not parse:"$'\n'"$out")

    base=$(basename "$file_path")

    # 2. Real Maestro syntax validation — no device required, sub-second.
    #    config.yaml is workspace configuration, not a flow, so check-syntax rejects it.
    if [ ${#errors[@]} -eq 0 ] && [ "$base" != "config.yaml" ] && command -v maestro >/dev/null 2>&1; then
      if ! out=$(maestro check-syntax "$file_path" 2>&1); then
        errors+=("maestro check-syntax failed:"$'\n'"$out")
      fi
    fi

    # 3. Repo conventions. Advisory: reported, never silently auto-fixed.
    if [ "$base" != "config.yaml" ]; then
      grep -qE '^appId:' "$file_path" || warnings+=("no 'appId:' header — intentional only for a runFlow fragment")
      grep -qE '^(tags:|[[:space:]]*- )' "$file_path" >/dev/null 2>&1 || true
      grep -qE '^tags:' "$file_path" || warnings+=("no 'tags:' — flow cannot be selected by suite")
    fi
    if grep -qE '^[[:space:]]*-?[[:space:]]*sleep:' "$file_path"; then
      warnings+=("uses 'sleep:' — CLAUDE.md forbids hard sleeps; wait on a condition with extendedWaitUntil")
    fi
    if grep -qE '^[[:space:]]*point:' "$file_path"; then
      warnings+=("uses coordinate 'point:' — prefer a stable resource id; if no id exists, that is a finding to report")
    fi
    ;;

  # ----------------------------------------------------- other YAML (CI workflows)
  *.yaml|*.yml)
    out=$(yaml_parse_check "$file_path"); rc=$?
    [ "$rc" -eq 1 ] && errors+=("YAML does not parse:"$'\n'"$out")
    ;;

  # ------------------------------------------------------------- Kotlin and Gradle
  # Deliberately lightweight: compiling here would make every edit cost ~30s+.
  # Real compilation belongs to scripts/build-android.sh, run on purpose.
  *.kt|*.kts)
    if [ "$(grep -c '{' "$file_path")" -ne "$(grep -c '}' "$file_path")" ]; then
      warnings+=("unbalanced { } brace count — may be fine (braces in strings/comments), verify with: ./gradlew assembleDebug")
    fi
    ;;

  # ------------------------------------------------- agent / skill markdown definitions
  */.claude/agents/*.md|*/.claude/skills/*/SKILL.md)
    if ! head -1 "$file_path" | grep -q '^---$'; then
      errors+=("must start with '---' YAML frontmatter on line 1")
    else
      grep -qE '^name:' "$file_path"        || errors+=("frontmatter is missing 'name:'")
      grep -qE '^description:' "$file_path" || errors+=("frontmatter is missing 'description:'")
    fi
    ;;
esac

# Advisory output goes to stdout and does not block.
if [ ${#warnings[@]} -gt 0 ]; then
  printf 'quick-check [warn] %s\n' "$(basename "$file_path")"
  for w in "${warnings[@]}"; do printf '  - %s\n' "$w"; done
fi

# Real defects go to stderr with exit 2, which feeds the message back to Claude.
if [ ${#errors[@]} -gt 0 ]; then
  {
    printf 'quick-check FAILED: %s\n' "$file_path"
    for e in "${errors[@]}"; do printf '  - %s\n' "$e"; done
  } >&2
  exit 2
fi

exit 0
