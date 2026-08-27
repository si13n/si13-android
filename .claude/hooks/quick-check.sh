#!/usr/bin/env bash
# PostToolUse hook: cheap, non-destructive sanity checks on the file just written.
# Exit 2 feeds a real defect back to Claude; advisory warnings exit 0.
set -uo pipefail

payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty')
elif command -v python3 >/dev/null 2>&1; then
  file_path=$(printf '%s' "$payload" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin).get("tool_input",{})
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

yaml_parse_check() {
  command -v python3 >/dev/null 2>&1 || return 2
  python3 -c 'import yaml' >/dev/null 2>&1 || return 2
  python3 -c '
import sys,yaml
try:
    list(yaml.safe_load_all(open(sys.argv[1],encoding="utf-8")))
except Exception as e:
    print(e); sys.exit(1)
' "$1" 2>&1 || return 1
  return 0
}

case "$file_path" in
  *.sh)
    if ! out=$(bash -n "$file_path" 2>&1); then errors+=("bash -n failed:"$'\n'"$out"); fi
    if [ ! -x "$file_path" ] && [[ "$file_path" == */scripts/* || "$file_path" == scripts/* || "$file_path" == */hooks/* || "$file_path" == .claude/hooks/* ]]; then
      warnings+=("not executable — run: chmod +x $file_path")
    fi
    head -1 "$file_path" | grep -q '^#!' || warnings+=("missing shebang on line 1")
    if grep -qE '(\|\|[[:space:]]*true|set \+e)' "$file_path"; then
      warnings+=("contains '|| true' or 'set +e' — confirm no quality-gate exit code is swallowed")
    fi
    ;;

  maestro/*.yaml|maestro/*.yml|*/maestro/*.yaml|*/maestro/*.yml)
    out=$(yaml_parse_check "$file_path"); rc=$?
    [ "$rc" -eq 1 ] && errors+=("YAML does not parse:"$'\n'"$out")
    base=$(basename "$file_path")
    if [ ${#errors[@]} -eq 0 ] && [ "$base" != "config.yaml" ] && command -v maestro >/dev/null 2>&1; then
      if ! out=$(maestro check-syntax "$file_path" 2>&1); then errors+=("maestro check-syntax failed:"$'\n'"$out"); fi
    fi
    if [ "$base" != "config.yaml" ]; then
      grep -qE '^appId:' "$file_path" || warnings+=("no 'appId:' header — intentional only for a runFlow fragment")
      grep -qE '^tags:' "$file_path" || warnings+=("no 'tags:' — flow cannot be selected by suite")
    fi
    if grep -qE '^[[:space:]]*-?[[:space:]]*sleep:' "$file_path"; then
      errors+=("hard sleep is forbidden — wait on an observable condition")
    fi
    if grep -qE '^[[:space:]]*point:' "$file_path"; then
      errors+=("coordinate point is forbidden — use a stable resource id/accessibility semantic")
    fi
    ;;

  *.yaml|*.yml)
    out=$(yaml_parse_check "$file_path"); rc=$?
    [ "$rc" -eq 1 ] && errors+=("YAML does not parse:"$'\n'"$out")
    ;;

  *.kt|*.kts)
    if [ "$(grep -c '{' "$file_path")" -ne "$(grep -c '}' "$file_path")" ]; then
      warnings+=("unbalanced { } brace count — verify with Gradle compilation")
    fi
    ;;

  .claude/agents/*.md|*/.claude/agents/*.md|.claude/skills/*/SKILL.md|*/.claude/skills/*/SKILL.md)
    if ! head -1 "$file_path" | grep -q '^---$'; then
      errors+=("must start with YAML frontmatter on line 1")
    else
      grep -qE '^name:' "$file_path" || errors+=("frontmatter is missing 'name:'")
      grep -qE '^description:' "$file_path" || errors+=("frontmatter is missing 'description:'")
    fi
    ;;
esac

if [ ${#warnings[@]} -gt 0 ]; then
  printf 'quick-check [warn] %s\n' "$(basename "$file_path")"
  for w in "${warnings[@]}"; do printf '  - %s\n' "$w"; done
fi

if [ ${#errors[@]} -gt 0 ]; then
  {
    printf 'quick-check FAILED: %s\n' "$file_path"
    for e in "${errors[@]}"; do printf '  - %s\n' "$e"; done
  } >&2
  exit 2
fi

exit 0
