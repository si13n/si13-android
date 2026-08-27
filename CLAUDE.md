# CLAUDE.md — Agentic Mobile QA Lab

## Project purpose

This repository demonstrates **Agentic Engineering for Mobile QA**.

The system under test is **Forgetty**, a real native Kotlin Android todo app
(`com.si13.app`) that already lives in this repo. `README.md` documents the application.

The QA Lab layer around it (`.claude/`, `maestro/`, `scripts/`, `docs/`) exists to show
how AI agents can do useful mobile QA work **under quality gates that require evidence**.

Key docs: `docs/agentic-qa-lab.md` (overview) · `maestro/README.md` (UI tests) ·
`app/src/androidTest/README.md` (instrumented tests) · `docs/quality-gates.md` (the gates).

The goal is not a big app. The goal is a workflow that is honest about what it proved.

## Core principles

- Quality is shared engineering ownership, not a separate department.
- Shift quality left: cheapest reliable signal, earliest possible point.
- Use risk-based testing. Coverage of risk, not coverage of clicks.
- Do not automate everything. Some things belong to exploratory testing.
- Put each test at the **lowest reliable test level** that can prove the requirement.
- Automated tests must be deterministic and useful. A flaky test is a broken test.
- Never hide instability with endless retries. Retries hide root causes.
- No hardcoded sleeps unless truly unavoidable — and then justify them in a comment.
- Prefer stable test identifiers (`android:id`, `contentDescription`) over text/coordinates.
- AI-generated code is a **draft until verified**.
- Evidence is required before declaring success.

## Agentic workflow

For feature or test work, use this sequence:

1. **planner** — analyze, plan, define acceptance criteria (read-only)
2. **qa-test-designer** — when test design is needed: choose levels, justify them (read-only)
3. **Human or planner approval of the plan** — a real gate, before code exists
4. **maestro-implementer** — implement the change
5. **verifier** — independently verify against the original requirement
6. **failure-analyst** — if verification fails: classify and root-cause (read-only)
7. **maestro-implementer** — fix
8. **verifier** — re-run
9. Final summary with evidence

### The rule that matters

**The same agent that writes the implementation is NOT the final authority on
correctness.** The implementer reports "ready for independent verification". Only the
verifier issues `VERDICT: PASS`.

An agent saying "done" is a claim. A build log, an exit code and an artifact are evidence.

## Definition of done

A task is done only when the relevant evidence exists:

- [ ] build succeeded (real exit code, not a summary of one)
- [ ] relevant tests passed at the level that actually proves the requirement
- [ ] original acceptance criteria were checked one by one
- [ ] no unrelated files were modified (`git diff --stat` reviewed)
- [ ] any failures are understood, not merely gone
- [ ] final verifier reports **PASS**

If something could not be executed on this machine, it is reported as
**NOT VERIFIED** with instructions for how to verify it. It is never reported as passing.

## Agent skills

### Issue tracker

Issues live in GitHub Issues. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout: root `CONTEXT.md` and `docs/adr/` for architecture decisions. See `docs/agents/domain.md`.

## Never do this

- Weaken or delete an assertion to make a test green. Only change an assertion when the
  requirement proves the assertion was wrong — and say so explicitly.
- Add `sleep` / retries to paper over a race.
- Claim a test passed without the command output.
- Commit secrets, or invent fake credentials.
- Force push, rewrite history, or run destructive git commands.

## Important commands

```bash
# environment
adb devices
scripts/check-environment.sh

# build
./gradlew assembleDebug                 # or: scripts/build-android.sh
./gradlew testDebugUnitTest             # JVM unit tests (fastest signal)
./gradlew lintDebug

# Maestro UI tests (CLI only — Maestro Studio is not required)
maestro check-syntax maestro/smoke/01-app-launch.yaml
maestro test maestro/smoke/01-app-launch.yaml
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml
scripts/run-smoke.sh

# Espresso (pre-existing suite, needs a device)
./gradlew connectedDebugAndroidTest

# evidence
scripts/collect-logcat.sh
scripts/collect-artifacts.sh
scripts/verify-results.sh              # the deterministic quality gate
```

## Repo facts an agent must know

- Package / appId: `com.si13.app`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Launcher activity is `SplashActivity` (~650 ms), which then starts `MainActivity`.
- **A login bottom sheet appears on every cold launch while signed out.** Flows must
  dismiss it via `id: continue_as_guest_button`. This is app behaviour, not a bug.
- **Debug builds seed ~100 demo tasks asynchronously on first launch after data is
  cleared** (`MainActivity.seedDebugTasksIfNeeded`, gated on `FLAG_DEBUGGABLE` and the
  `MainActivity` SharedPreferences flag). Therefore: never assert on task counts or on
  seeded task titles. Assert on structure, or on data the flow itself created with a
  unique title.
- Existing Espresso tests isolate themselves in `clearState()` by setting the seed flag
  to `true` — read `app/src/androidTest/java/com/si13/app/HomeTaskTest.kt` before
  inventing new isolation logic.
- `artifacts/` is gitignored except `.gitkeep`. Put all run output there.
