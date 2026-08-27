# CLAUDE.md — Agentic Android Engineering Lab

## Purpose

This repository is Forgetty (`com.si13.app`), a real native Kotlin Android app plus an
**Agentic Android Engineering Harness** for planning, development, automated testing,
independent verification and failure analysis.

Key docs: `docs/agentic-engineering-lab.md`, `docs/architecture.md`,
`docs/task-contract.md`, `docs/quality-gates.md`, `docs/agent-workflow.md`.

The goal is not maximum agent count. The goal is a small role-based system with deterministic
guardrails, explicit handoffs and evidence-backed claims.

## Core principles

- **Agent = engineering responsibility / authority boundary. Skill = technology/procedure.**
- Keep static project context small; load detailed skills only when needed.
- Quality is shared ownership and starts in planning.
- Use the lowest reliable test level that proves the behavior.
- AI-generated code is a draft until independently verified.
- The writer is never the final authority on correctness.
- Evidence means a real command, exit code, result and artifact — not confidence.
- Flaky tests are defects; do not hide them with sleeps/retries.
- Stable resource ids/accessibility semantics are product quality features.

## Five agents

| Agent | Writes source? | Responsibility | Final PASS? |
|---|---:|---|---:|
| `planner` | no | requirement, impact, risk, test strategy, ownership, sequencing, AC | no |
| `android-developer` | yes | production Kotlin/resources/production config | no |
| `android-test-engineer` | yes | unit, instrumented, Espresso, Maestro, test infra | no |
| `verifier` | no | independent evidence against requirement/contract | **yes** |
| `failure-analyst` | no | classify/root-cause failures and route repair | no |

There is intentionally no QA-planner, Espresso-agent or Maestro-agent. Roles represent work
ownership; frameworks stay in skills.

`planner`, `verifier` and `failure-analyst` have scoped `PreToolUse` Bash guardrails that block
source/git mutation. Omitting Write/Edit is not the only protection.

## Skills and context

Always-relevant role knowledge may be preloaded. Framework-specific testing/debugging skills
should be invoked through the `Skill` tool **just in time**, not all loaded into every agent.

Relevant project skills:

- `change` — executable `/change` orchestration workflow
- `android-development`
- `unit-testing`
- `android-instrumented-testing`
- `espresso-testing`
- `maestro-testing`
- `android-debugging`
- `qa-risk-analysis`
- `verification-gates`
- `ci-debugging`

For the test engineer/verifier: UNIT loads `unit-testing`; non-UI Android runtime loads
`android-instrumented-testing`; Espresso loads instrumented + Espresso; Maestro loads
`maestro-testing`. Load debugging skills only when diagnosing.

## Standard workflow

For non-trivial feature, bug-fix or automated-test changes, prefer:

```text
/change <requirement>
  ↓
TASK CONTRACT
  ↓
planner
  ↓
human approval
  ↓
implementation sequence
  ↓
android-developer and/or android-test-engineer
  ↓
verifier
  ↓
PASS | INCONCLUSIVE | FAIL → failure-analyst/owner → verifier
```

The main Claude Code session is the orchestrator, not a sixth implementation agent.

Runtime handoffs live under `artifacts/agent-runs/TASK-*/` and are gitignored. Agents should
read the contract files instead of depending on lossy conversation summaries.

## Test levels

```text
UNIT / JVM                 pure Kotlin logic, fakes, mapping, sorting
      ↓
ANDROID INSTRUMENTED       Room migrations, Context, prefs, runtime without UI focus
      ↓
ESPRESSO UI                in-process Activity/Fragment/View behavior
      ↓
MAESTRO E2E                few critical packaged-app cross-screen journeys
      ↓
MANUAL / EXPLORATORY       judgement, real credentials/hardware/device concerns
```

Do not mirror the same assertion at every level. Each level must buy a distinct signal.

## Ownership rules

- `android-developer`: `app/src/main/`, product resources/manifest, production architecture.
- `android-test-engineer`: `app/src/test/`, `app/src/androidTest/`, `maestro/`, test infra.
- Shared files such as `app/build.gradle.kts` have **one owner per task**.
- Production dependencies normally route to developer; test dependencies normally route to
  test engineer. If both need one file, planner assigns one owner and records the other role's
  requested change.
- Agents must not silently expand contract scope.

## Sequencing modes

Planner chooses one:

- `TEST_FIRST` — deterministic logic/bug with a useful failing regression test first.
- `PRODUCT_FIRST` — product surface/testability must exist before useful test implementation.
- `PARALLEL` — only disjoint files + stable contract + no dependency on uncommitted output.
- `TEST_ONLY` — test/infrastructure only.
- `PRODUCT_ONLY` — automation deliberately not justified; residual/manual risk explicit.

## Definition of done

A task is done only when:

- original acceptance criteria are checked one by one;
- actual diff matches approved scope and one-owner-per-file routing;
- production build passes when production code changed;
- relevant tests pass at the level that proves the behavior;
- required device evidence ran, or final verdict is `INCONCLUSIVE`;
- failures were understood rather than hidden;
- `verifier` issues `VERDICT: PASS`.

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
Implementers may only say **ready for independent verification**.

## Important commands

```bash
# environment / static harness
adb devices
scripts/check-environment.sh
scripts/validate-harness.sh                 # requires Maestro CLI
scripts/validate-harness.sh --allow-missing-maestro

# production + JVM
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug

# Android instrumented / Espresso
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
scripts/run-espresso-with-allure.sh

# Maestro
maestro check-syntax maestro/smoke/01-app-launch.yaml
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml
scripts/run-smoke.sh

# complete gate
scripts/verify-results.sh
scripts/verify-results.sh --no-build --allow-skips   # explicit partial/static run
```

`verify-results.sh`: `0=PASS` (or explicit PARTIAL with `--allow-skips`), `1=FAIL`,
`3=INCOMPLETE` because required evidence was skipped/unavailable.

## Repo facts

- App id `com.si13.app`; debug APK `app/build/outputs/apk/debug/app-debug.apk`.
- Signed-out cold launch shows a login bottom sheet; Maestro setup handles guest continuation.
- Debug builds can seed ~100 demo tasks asynchronously after state clear; never assert on seed
  counts/titles.
- Existing instrumented/UI tests neutralize seed state; read `app/src/androidTest/README.md`
  and existing isolation helpers before changing setup.
- Room migrations are high risk and require real migration evidence.
- `artifacts/` is gitignored execution evidence.

## Never do this

- Weaken/delete a correct assertion to get green.
- Add arbitrary sleeps/blind retries/coordinate taps as a repair strategy.
- Claim a test passed without actual execution evidence.
- Convert unavailable required evidence into PASS.
- Commit secrets/fake credentials.
- Force-push, rewrite history or bypass the role/ownership contract.
