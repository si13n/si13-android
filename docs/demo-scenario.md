# Demo scenarios — failure recovery on demand

These demos intentionally break a known-good state so the harness can show classification,
repair routing and independent verification. Restore every mutation before moving on.

## Prerequisites

```bash
scripts/check-environment.sh
adb devices
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/run-smoke.sh
```

Start from green. A failure demo is useful only when the baseline was proven first.

## Demo A — renamed resource id (`LOCATOR`)

Temporarily rename a stable id used by the Maestro flows in the layout, rebuild, reinstall and
run smoke. The product may still render while the automation can no longer address the old id.

Expected failure-analyst path:

1. no crash/ANR;
2. hierarchy shows the new id;
3. git diff shows the rename;
4. classification `LOCATOR`, confidence HIGH.

The repair depends on intent: if the rename was product-approved, update automation; if it was
accidental, revert production. The analyst must not choose silently.

Restore the layout, rebuild and require verifier evidence again.

## Demo B — wrong test assumption (`TEST`)

Reintroduce the historical bad assertion in
`maestro/smoke/02-navigate-bottom-nav.yaml`: assert that `profile_account_card` is visible for
a guest.

The flow should fail. Investigation should show:

- the id exists;
- guest mode intentionally hides the account card;
- guest-specific UI is the real contract.

Expected classification: `TEST`. The correct repair is not deleting the assertion; it is
asserting the actual guest state (`profile_guest_container` visible and account card hidden).
Then verifier reruns the real flow.

## Demo C — mutation thinking

Create a temporary mutant outside the repo that cancels instead of saving a task. Run the
mutant flow and require a non-zero result. If the flow still passes, the supposedly green test
does not prove the save behavior.

Example:

```bash
mkdir -p /tmp/mut/maestro/smoke
cp -r maestro/common /tmp/mut/maestro/
sed 's/id: "add_task_button"/id: "add_task_close"/' \
  maestro/smoke/03-create-and-find-task.yaml > /tmp/mut/maestro/smoke/mutant.yaml
maestro --device "$(adb devices | awk 'NR==2{print $1}')" \
  test -e MAESTRO_RUN_TAG=mutant /tmp/mut/maestro/smoke/mutant.yaml
echo "EXIT=$?   # expected non-zero"
```

Nothing in the repository needs restoring because the mutant lives under `/tmp`.

## Demo D — missing evidence is not PASS

Stop the emulator, then run:

```bash
scripts/verify-results.sh --no-build ; echo "EXIT=$?"
```

Expected result:

```text
VERDICT: INCOMPLETE — required evidence was skipped or unavailable.
EXIT=3
```

For a deliberately static/partial check, opt in explicitly:

```bash
scripts/verify-results.sh --no-build --allow-skips ; echo "EXIT=$?"
```

That may exit 0 for scripting convenience, but the verdict is `PARTIAL`, never PASS.

The harness rules themselves are mechanical. If Claude writes a Maestro flow containing a
hard `sleep:` or coordinate `point:`, `quick-check.sh` blocks it immediately with exit 2.
If such a file is introduced outside Claude Code, `scripts/validate-harness.sh` / PR CI fails.

## Demo E — read-only agent guardrail

Ask `planner`, `verifier` or `failure-analyst` to perform a forbidden Bash mutation such as a
git commit/reset or direct file write. The agent-scoped `PreToolUse` hook must block the tool
call **before execution**.

Then run a permitted command appropriate for the role (for example `git diff`, or a verifier
build/test command) to show that the guardrail restricts authority without making the role
useless.

## Suggested order

| Demo | Shows |
|---|---|
| D | honest PASS/FAIL/INCOMPLETE semantics |
| E | prompt rule converted into deterministic PreToolUse enforcement |
| A | locator classification and intent-sensitive repair |
| C | green test challenged by mutation |
| B | wrong test vs wrong product distinction |

## Safety

Use a disposable feature branch/worktree for intentional source mutations. Never force-push
or rewrite shared history. Confirm the repository is restored with `git status --short` after
each demo.
