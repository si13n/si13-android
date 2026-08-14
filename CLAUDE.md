# CLAUDE.md — Agentic Android Engineering Lab

## Project purpose

This repository demonstrates **Agentic Engineering for native Android development and QA**.

The system is Forgetty (`com.si13.app`), a real Kotlin Android todo application. The harness
around it (`.claude/`, `scripts/`, `maestro/`, CI and evidence artifacts) exists to show how AI
agents can plan, implement, test, diagnose and verify changes under explicit engineering
constraints.

Key docs: `docs/agentic-engineering-lab.md` · `docs/architecture.md` ·
`docs/quality-gates.md` · `docs/agent-workflow.md`.

The goal is not maximum agent count. The goal is a small role-based system whose claims are
backed by evidence.

## Core principles

- **Roles represent engineering responsibilities; frameworks are skills.** Do not create a
  new agent just because a new testing tool appears.
- Quality is shared engineering ownership.
- Shift quality left: use the cheapest reliable signal at the earliest point.
- Risk-based testing: coverage of meaningful risk, not coverage of clicks.
- Put each automated check at the lowest reliable test level that proves the requirement.
- AI-generated code is a draft until independently verified.
- The agent that writes a change is not the final authority on correctness.
- Evidence is a real command, exit code, result and artifact — not confidence or prose.
- Flaky tests are defects. Do not hide instability with retries or arbitrary sleeps.
- Prefer stable resource ids and accessibility semantics over text or coordinates.

## Agent topology — five roles

| Agent | Writes? | Responsibility | Can issue final PASS? |
|---|---:|---|---:|
| `planner` | no | requirement, impact, risks, test strategy, acceptance criteria, routing | no |
| `android-developer` | yes | production Kotlin/resources/Gradle | no |
| `android-test-engineer` | yes | unit, Espresso/instrumented, Room tests, Maestro, test infra | no |
| `verifier` | no | independent evidence against original requirement | **yes** |
| `failure-analyst` | no | classify and root-cause failures, then route repair | no |

There is intentionally no `qa-test-designer`, `espresso-agent` or `maestro-implementer`.
Planning includes test strategy, and `android-test-engineer` uses framework-specific skills.

## Skills — technology and procedure

Relevant project skills live under `.claude/skills/`:

- `android-development`
- `unit-testing`
- `espresso-testing`
- `maestro-testing`
- `android-debugging`
- `qa-risk-analysis`
- `verification-gates`
- `ci-debugging`

Use skills as dynamic procedural knowledge. Keep project-wide hard rules here; keep detailed
framework knowledge in the corresponding skill.

## Workflow

For feature, bug-fix and test work:

1. **planner** — inspect the repo, define risk, owners, test level, acceptance criteria and
   exact verification plan.
2. **human approval** — approve or correct the plan before code exists when the change is
   non-trivial.
3. **android-developer** and/or **android-test-engineer** — implement only their routed scope.
4. **verifier** — independently inspect diff, run relevant gates and issue PASS / FAIL /
   INCONCLUSIVE.
5. On FAIL with uncertain cause: **failure-analyst** classifies root cause.
6. Route the fix to the correct implementer, then return to **verifier**.

The main Claude Code session acts as orchestrator. A dedicated orchestrator agent is not
needed until orchestration itself becomes a separate responsibility.

### Authority rule

**Implementation authority and verification authority must stay separate.**

`android-developer` and `android-test-engineer` may say "ready for independent verification".
Only `verifier` may issue `VERDICT: PASS`.

## Definition of done

A task is done only when the relevant evidence exists:

- [ ] original acceptance criteria checked one by one
- [ ] `git diff --name-only` matches approved scope and ownership
- [ ] production build succeeded when production code changed
- [ ] relevant automated tests passed at the level that actually proves the behavior
- [ ] required device-level checks either ran or are explicitly `UNVERIFIED`
- [ ] failures were understood, not merely made to disappear
- [ ] final verifier reports **PASS**

If something cannot run on the current machine, report **NOT VERIFIED / INCONCLUSIVE** and
state how to obtain the missing evidence.

## Test-level policy

```text
JVM unit tests                cheapest / most coverage
        ↓
Espresso + instrumented/Room  Android runtime behavior
        ↓
Maestro                       few critical cross-screen journeys
        ↓
Manual / exploratory          human judgement / real-account/device concerns
```

Do not mirror the same assertion at every level. Each level must buy a distinct signal.

## Important commands

```bash
# environment
adb devices
scripts/check-environment.sh

# production + JVM
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug

# Espresso / instrumented
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
scripts/run-espresso-with-allure.sh

# Maestro
maestro check-syntax maestro/smoke/01-app-launch.yaml
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml
scripts/run-smoke.sh

# full deterministic repo gate
scripts/verify-results.sh
scripts/verify-results.sh --no-build

# evidence
scripts/collect-logcat.sh
scripts/collect-artifacts.sh
```

## Repo facts agents must know

- App id: `com.si13.app`; debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- A login bottom sheet appears on cold launch while signed out; Maestro setup handles guest
  continuation.
- Debug builds can seed ~100 demo tasks asynchronously after state clear. Never assert on
  seeded task counts or titles.
- Existing Espresso tests neutralize the seed in their isolation setup. Read
  `app/src/androidTest/README.md` and `HomeTaskTest.clearState()` before inventing new state
  handling.
- Room migrations are high-risk and deserve real migration tests.
- `artifacts/` is the evidence location and is gitignored except `.gitkeep`.

## Never do this

- Weaken/delete a correct assertion to get green.
- Add arbitrary sleeps or blind retries to paper over a race.
- Claim a test passed without the command result and exit code.
- Convert a skipped/unavailable device test into a pass.
- Commit secrets or fake credentials.
- Force-push, rewrite history or use destructive git commands from agents.
