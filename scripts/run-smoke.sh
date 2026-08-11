#!/usr/bin/env bash
# Run the whole Maestro smoke suite. Every smoke flow is critical by definition:
# if any of them fails, the suite fails and the exit code says so.
#
#   scripts/run-smoke.sh
#
# Exit codes: 0 all passed | 1 at least one flow failed | 3 could not run (no device)
set -uo pipefail

cd "$(dirname "$0")/.."

SUITE_DIR="maestro/smoke"

[ -d "$SUITE_DIR" ] || { echo "ERROR: $SUITE_DIR not found" >&2; exit 2; }

# Collected with a read loop rather than mapfile: macOS ships bash 3.2, which has no mapfile.
flows=()
while IFS= read -r line; do
  flows+=("$line")
done < <(find "$SUITE_DIR" -maxdepth 1 -name '*.yaml' ! -name 'config.yaml' | sort)

if [ "${#flows[@]}" -eq 0 ]; then
  echo "ERROR: no flows found in $SUITE_DIR" >&2
  exit 2
fi

command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not on PATH" >&2; exit 2; }
if [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')" = "0" ]; then
  echo "SKIPPED — NO DEVICE AVAILABLE"
  echo "  Start an emulator, then re-run scripts/run-smoke.sh"
  exit 3
fi

# One shared tag for the suite so created data is traceable to this run.
export MAESTRO_RUN_TAG="${MAESTRO_RUN_TAG:-smoke-$(date +%H%M%S)-$$}"

echo "=============================================="
echo " SMOKE SUITE  (${#flows[@]} flows)"
echo " run tag: $MAESTRO_RUN_TAG"
echo "=============================================="

passed=0
failed=0
failed_names=()

for flow in "${flows[@]}"; do
  echo
  echo "---- $flow ----"
  if scripts/run-maestro.sh "$flow"; then
    passed=$((passed + 1))
  else
    failed=$((failed + 1))
    failed_names+=("$flow")
    # Keep going: knowing whether 1 or 5 flows broke is diagnostic information.
  fi
done

echo
echo "=============================================="
echo " SMOKE RESULT: $passed passed, $failed failed"
if [ "$failed" -gt 0 ]; then
  echo " FAILED FLOWS (all smoke flows are critical):"
  for f in "${failed_names[@]}"; do echo "   - $f"; done
  echo "=============================================="
  echo " Artifacts under artifacts/maestro/. Next: failure-analyst agent."
  exit 1
fi
echo " ALL SMOKE FLOWS PASSED"
echo "=============================================="
exit 0
