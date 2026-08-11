# Demo scenario — failure recovery, reproducible on demand

A live demo of the full loop: a working test breaks, agents diagnose and fix it, and a
verifier proves the fix. Takes about 10 minutes.

**The repository is left in a working state after bootstrap.** This document tells you how to
break it on purpose, and how to put it back.

## Prerequisites

```bash
scripts/check-environment.sh          # git, java, adb, maestro
adb devices                            # one device in state "device"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/run-smoke.sh                   # confirm green BEFORE you break anything
```

Starting from a known-green baseline is the whole point. Debugging a demo that was already
broken teaches nothing.

---

## Demo A — a renamed test id (classification: `LOCATOR`)

The most common real-world mobile QA failure: a developer renames a view id, and UI
automation breaks even though the product is fine.

### Step 1 — break the product's id (not the test)

```bash
# Rename the Home header id in the layout the flow depends on.
sed -i '' 's/@+id\/home_header/@+id\/home_header_v2/' app/src/main/res/layout/fragment_home.xml
grep -rn "home_header" app/src/main/java/com/si13/app/HomeFragment.kt
```

`HomeFragment` does not reference `home_header` by id, so the app still builds and runs. Only
the test breaks — exactly the real-world scenario.

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2 — watch it fail with a real exit code

```bash
scripts/run-smoke.sh ; echo "EXIT=$?"
```

Expect: all three flows fail (all use `common/launch-fresh.yaml`), exit `1`, and artifacts
under `artifacts/maestro/<timestamp>/` including screenshots and logcat.

### Step 3 — failure analyst

> Use the failure-analyst agent to investigate the smoke suite failure.

Expected reasoning:

1. Check logcat for a crash → none → **not `PRODUCT`**.
2. `maestro hierarchy | grep home_header` → shows `home_header_v2`.
3. `git diff app/src/main/res/layout/fragment_home.xml` → the rename.

Expected output: **Classification `LOCATOR`**, confidence `HIGH`, root cause = the id was
renamed in the layout, recommended fix = *decide which name is correct*, then align.

### Step 4 — the interesting question

`LOCATOR` failures have two valid fixes, and choosing between them is an engineering
decision, not a mechanical one:

- **If the rename was intentional** → update the flows to the new id.
- **If the rename was accidental** → revert the layout. The test found a real regression.

An agent must not silently pick one. This is the moment to say out loud: *the test did its
job by refusing to pass.*

### Step 5 — fix and re-verify

```bash
git checkout app/src/main/res/layout/fragment_home.xml
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/run-smoke.sh ; echo "EXIT=$?"      # back to 0
```

### Restore

```bash
git checkout app/src/main/res/layout/fragment_home.xml
git status --short          # must be clean
```

---

## Demo B — a wrong assumption about behaviour (classification: `TEST`)

This one actually happened during bootstrap. It is the more instructive failure, because the
test was wrong and the app was right.

### Step 1 — reintroduce the original mistake

In `maestro/smoke/02-navigate-bottom-nav.yaml`, replace the guest-state assertions with the
naive one:

```yaml
# replace:
- assertVisible:
    id: "profile_guest_container"
- assertNotVisible:
    id: "profile_account_card"
# with:
- assertVisible:
    id: "profile_account_card"
```

### Step 2 — fail

```bash
scripts/run-maestro.sh maestro/smoke/02-navigate-bottom-nav.yaml ; echo "EXIT=$?"
# [Failed] Assertion is false: id: profile_account_card is visible
```

### Step 3 — failure analyst

Expected chain:

- no crash in logcat
- the id exists (`fragment_profile.xml:38`) → **not `LOCATOR`**
- `ProfileFragment.kt:137` → `accountCard.isVisible = state.showProfileCard`
- `ProfileViewModel.kt:26` → `showProfileCard get() = user != null`
- the suite runs as **guest**, so `user == null` → the card is *correctly* hidden

**Classification `TEST`.** The app is right; the test asserted behaviour the requirement never
specified.

### Step 4 — the fix must be stronger, not weaker

The temptation is to delete the failing assertion. That is forbidden here — it would remove
coverage to buy green.

The correct fix asserts the **actual guest contract**: `profile_guest_container` visible,
`profile_account_card` not visible. That is more coverage than before, not less.

This is the distinction to make explicitly in an interview: *changing an assertion because
the requirement proves it was wrong is correct; changing it because it is inconvenient is
fraud.*

### Restore

```bash
git checkout maestro/smoke/02-navigate-bottom-nav.yaml
```

---

## Demo C — mutation test: does a green test prove anything? (2 minutes)

The fastest way to show that green is not enough.

```bash
# Break the feature, keep the test. It MUST go red.
mkdir -p /tmp/mut/maestro && cp -r maestro/common /tmp/mut/maestro/ && mkdir -p /tmp/mut/maestro/smoke
sed 's/id: "add_task_button"/id: "add_task_close"/' \
  maestro/smoke/03-create-and-find-task.yaml > /tmp/mut/maestro/smoke/mutant.yaml

maestro --device "$(adb devices | awk 'NR==2{print $1}')" \
  test -e MAESTRO_RUN_TAG=mutant /tmp/mut/maestro/smoke/mutant.yaml
echo "EXIT=$?   # must be 1 — if it is 0, the test proves nothing"
```

Cancelling instead of saving means the task is never created. A test with a real assertion
must notice. Both mutations documented in `docs/agent-workflow.md` §9 were verified to fail.

Nothing to restore — this runs from `/tmp`.

---

## Demo D — the quality gate refuses to fake a pass (1 minute)

```bash
adb emu kill                        # or just stop the emulator
scripts/verify-results.sh --no-build ; echo "EXIT=$?"
```

Expect `PASS WITH SKIPS`, with the smoke suite listed as
`SKIPPED — NO DEVICE AVAILABLE` and a reminder that skipped gates are not passes.

Then show that the rules are mechanically enforced, not just documented:

```bash
printf 'appId: com.si13.app\nname: bad\ntags:\n  - smoke\n---\n- sleep: 3000\n' \
  > maestro/smoke/99-bad.yaml
scripts/verify-results.sh --no-build ; echo "EXIT=$?"
rm maestro/smoke/99-bad.yaml
```

Verified: exits `1`, failing two gates at once —

```
  [FAIL]    maestro check-syntax       invalid: maestro/smoke/99-bad.yaml
  [FAIL]    no hard sleeps in flows    maestro/smoke/99-bad.yaml
```

Both fire because `sleep:` is not a real Maestro command *and* the repo bans hard pauses.
Swap in `- tapOn:\n    point: 50%,50%` to trip the coordinate-tap gate instead (also verified,
exit `1`).

Also demonstrates the PostToolUse hook: ask Claude to write a flow containing `sleep:` and the
hook warns immediately, on save, before it is ever run.

---

## Suggested demo order

| # | Demo | Time | Shows |
|---|---|---|---|
| 1 | D — gate refuses to fake a pass | 1 min | evidence standard, mechanical enforcement |
| 2 | A — renamed id (`LOCATOR`) | 5 min | classification, and that a fix can be a revert |
| 3 | C — mutation test | 2 min | green ≠ correct |
| 4 | B — wrong assumption (`TEST`) | 5 min | assertions get stronger, never weaker |

## Safety

Every demo is reverted with `git checkout` of a single file, or runs entirely from `/tmp`.
Nothing in this document deletes history, force-pushes, or touches a remote. Confirm with
`git status --short` when you are done.
