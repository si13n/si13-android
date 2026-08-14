#!/usr/bin/env bash
# PreToolUse guard for read-only agent roles.
# Usage from agent frontmatter:
#   guard-agent-bash.sh planner|verifier|failure-analyst
#
# It is not a shell sandbox. It is a deterministic policy layer that blocks the common
# source/history mutation paths that a prompt-only "read-only" instruction cannot enforce.
set -uo pipefail

profile="${1:-readonly}"
payload=$(cat)

if command -v jq >/dev/null 2>&1; then
  command_text=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty')
elif command -v python3 >/dev/null 2>&1; then
  command_text=$(printf '%s' "$payload" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin).get("tool_input",{}).get("command", ""))
except Exception:
    print("")')
else
  echo "guard-agent-bash: cannot inspect Bash command (jq/python3 missing)" >&2
  exit 2
fi

[ -n "$command_text" ] || exit 0

deny() {
  printf 'guard-agent-bash BLOCKED [%s]: %s\nCommand: %s\n' "$profile" "$1" "$command_text" >&2
  exit 2
}

# Git/history/worktree mutation is forbidden for every read-only role.
if printf '%s' "$command_text" | grep -Eiq '(^|[;&|[:space:]])git[[:space:]]+(add|commit|push|pull|fetch|reset|checkout|switch|restore|clean|rebase|merge|cherry-pick|revert|stash|tag|branch)([[:space:]]|$)'; then
  deny "git mutation is outside this agent's authority"
fi

# Direct filesystem mutation. Builds/tests may create their own outputs internally; the agent
# should invoke those tools/scripts rather than editing source through shell side channels.
if printf '%s' "$command_text" | grep -Eiq '(^|[;&|[:space:]])(rm|mv|cp|touch|mkdir|rmdir|truncate|install|chmod|chown|ln|patch|tee|rsync)[[:space:]]'; then
  deny "direct filesystem mutation is blocked for this read-only role"
fi
if printf '%s' "$command_text" | grep -Eiq '(^|[;&|[:space:]])sed[[:space:]][^;&|]*(-i|--in-place)'; then
  deny "in-place sed would modify files"
fi
if printf '%s' "$command_text" | grep -Eiq '(^|[;&|[:space:]])perl[[:space:]]+[^;&|]*-p?i'; then
  deny "in-place perl would modify files"
fi
if printf '%s' "$command_text" | grep -Eq '(^|[[:space:]])(>|>>)[[:space:]]*[^&[:space:]]'; then
  deny "shell output redirection to a file is blocked; use a read-only command or an approved test script"
fi
if printf '%s' "$command_text" | grep -Eiq '(^|[;&|[:space:]])dd[[:space:]][^;&|]*of='; then
  deny "dd output would modify files/devices"
fi

# Planner is stricter: inspection only, not execution that changes build/app/device state.
if [ "$profile" = "planner" ]; then
  if printf '%s' "$command_text" | grep -q './gradlew'; then
    if ! printf '%s' "$command_text" | grep -Eq '\./gradlew[[:space:]]+(tasks|properties|help)([[:space:]]|$)'; then
      deny "planner may inspect Gradle metadata but may not build or run tests"
    fi
  fi
  if printf '%s' "$command_text" | grep -Eq '(^|[;&|[:space:]])adb[[:space:]]+'; then
    if ! printf '%s' "$command_text" | grep -Eq 'adb[[:space:]]+devices([[:space:]]|$)'; then
      deny "planner may inspect adb devices but may not mutate/query live app state via adb shell"
    fi
  fi
  if printf '%s' "$command_text" | grep -Eq '(^|[;&|[:space:]])maestro[[:space:]]+'; then
    if ! printf '%s' "$command_text" | grep -Eq 'maestro[[:space:]]+(check-syntax|hierarchy)([[:space:]]|$)'; then
      deny "planner may inspect Maestro syntax/hierarchy but may not execute flows"
    fi
  fi
  if printf '%s' "$command_text" | grep -Eq '(^|[;&|[:space:]])scripts/'; then
    if ! printf '%s' "$command_text" | grep -Eq 'scripts/check-environment\.sh([[:space:]]|$)'; then
      deny "planner may run only the read-only environment checker from scripts/"
    fi
  fi
fi

exit 0
