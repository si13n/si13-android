# Agentic Mobile QA Lab

> **The implementation agent is not the final authority on correctness.
> Completion requires independent evidence.**

## Goal

Show a practical agentic engineering workflow for **Mobile QA Automation** — one where AI
does real work but is never allowed to grade its own homework.

The system under test is **Forgetty**, the real Kotlin Android todo app in this repository
(see the [root README](../README.md)). The QA Lab is the layer around it: five subagents with
separated authority, five skills, a hook, Maestro UI automation, executable quality gates,
and CI.

This is not a demo app built to make tests pass. The tests run against 43 Kotlin source
files, Room, Firestore and Firebase Auth — including the awkward parts.

## Layout

```
si13/
├── CLAUDE.md                  project instructions every agent reads first
├── .claude/
│   ├── agents/                5 subagents (planner, designer, implementer, verifier, analyst)
│   ├── skills/                5 skills (maestro, adb, risk, gates, ci)
│   ├── hooks/quick-check.sh   PostToolUse: cheap static checks on every edit
│   └── settings.json
├── maestro/                   CLI-only UI automation: common/, smoke/, regression/
├── scripts/                   the executable quality gates
├── artifacts/                 all run evidence (gitignored)
├── docs/                      architecture, workflow, gates, demo
├── app/                       Forgetty — the system under test
└── .github/workflows/
    ├── mobile-tests.yml       build + static validation, opt-in emulator job
    └── espresso.yml           pre-existing Espresso + Allure suite
```

Full diagrams: [architecture.md](architecture.md).

## Agent roles

| Agent | Can it edit files? | Can it declare success? | Purpose |
|---|---|---|---|
| `planner` | no | no | analyze, plan, define acceptance criteria before code exists |
| `qa-test-designer` | no | no | risk-based coverage; assign the right test level and justify it |
| `maestro-implementer` | **yes** | **no** | implement automation; reports "ready for independent verification" |
| `verifier` | no | **yes** | independently verify; issues `PASS` / `FAIL` / `INCONCLUSIVE` |
| `failure-analyst` | no | no | classify and root-cause a failure without touching code |

The two constraints are the whole design: the implementer is denied the *authority* to
approve, and the verifier is denied the *ability* to fix. Neither can collapse the loop.

## Skills

| Skill | Teaches |
|---|---|
| `maestro-testing` | CLI-first Maestro, flow structure, stable id selectors, no sleeps, debugging element-not-found |
| `android-debugging` | adb, logcat, install/clear/force-stop, and a fixed outside-in debugging order |
| `qa-risk-analysis` | risk-based testing, shift-left, shared ownership, choosing the test level |
| `verification-gates` | evidence standard; IMPLEMENTED vs VERIFIED; verdict rules |
| `ci-debugging` | local-pass/CI-fail, Gradle, emulator startup, caches, parallel data conflicts |

## Workflow

```
SPEC → ANALYZE → PLAN → REVIEW PLAN → IMPLEMENT → BUILD → TEST → VERIFY
                                                              ↓ FAIL
                                          ANALYZE FAILURE → FIX → RE-RUN → FINAL VERIFICATION
```

1. **planner** analyzes and plans (read-only)
2. **qa-test-designer** designs risk-based coverage when test design is needed (read-only)
3. **human or planner approves the plan** — a real gate, before code exists
4. **maestro-implementer** implements
5. **verifier** independently verifies
6. **failure-analyst** diagnoses if verification fails
7. **maestro-implementer** fixes
8. **verifier** re-runs
9. Final summary with evidence

A worked end-to-end trace — including a real failure and its fix — is in
[agent-workflow.md](agent-workflow.md).

## Quality gates

```bash
scripts/verify-results.sh              # all gates
scripts/verify-results.sh --no-build   # fast static-only pass
```

Reports `PASS` / `FAIL` / `SKIPPED` per gate, and **never turns a skip into a pass**:

```
 RESULT: 8 passed, 0 failed, 2 skipped

 SKIPPED gates are NOT passes. Unverified means unverified:
   - gradle assembleDebug: --no-build requested
```

Some rules are enforced mechanically rather than merely documented — the gate and CI both
fail the build if a hard `sleep:` or a coordinate `point:` appears anywhere under `maestro/`.
Details: [quality-gates.md](quality-gates.md).

## Running locally

```bash
scripts/check-environment.sh           # git, java, adb, maestro + device list
scripts/build-android.sh               # ./gradlew assembleDebug
./gradlew testDebugUnitTest            # 12 unit test classes — the fastest real signal
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Running the tests

CLI only. **Maestro Studio is not required.**

```bash
scripts/run-smoke.sh                                      # the whole smoke suite
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml   # one flow
maestro check-syntax maestro/smoke/01-app-launch.yaml     # static, no device needed
./gradlew connectedDebugAndroidTest                       # the Espresso suite
```

Three smoke flows, deliberately:

| Flow | Proves |
|---|---|
| `01-app-launch` | cold launch from a clean state reaches a rendered Home |
| `02-navigate-bottom-nav` | Home / Stats / Settings all reachable and inflate |
| `03-create-and-find-task` | a created task persists and is findable by search |

Three is the point, not a gap. Everything provable more cheaply lives in the 12 JVM unit
test classes and the [instrumented suite](../app/src/androidTest/README.md). See
[maestro/README.md](../maestro/README.md).

## Failure investigation

```bash
scripts/collect-logcat.sh              # timestamped dump + crash/ANR extract
scripts/collect-artifacts.sh           # bundle evidence: junit, logs, screenshots, env
```

Failed runs leave JUnit XML, screenshots and logcat in `artifacts/maestro/<timestamp>/`.
The `failure-analyst` agent classifies each failure as one of `PRODUCT`, `TEST`, `LOCATOR`,
`TIMING`, `TEST_DATA`, `DEVICE`, `ANDROID_SYSTEM`, `BUILD`, `CI_INFRASTRUCTURE`,
`ENVIRONMENT`, `UNKNOWN` — and is explicitly forbidden from recommending sleeps or retries.

Distinguishing `TEST` from `LOCATOR` from `PRODUCT` is most of the skill. Getting it wrong is
how teams end up "fixing" tests that were correctly reporting a bug.

## CI

[`mobile-tests.yml`](../.github/workflows/mobile-tests.yml) — two jobs:

1. **build-and-validate** — every PR and push: shell syntax, `maestro check-syntax`, flow
   rule enforcement, agent/skill validation, `assembleDebug`, unit tests, lint (advisory),
   artifact upload.
2. **ui-tests** — emulator + Maestro smoke, **opt-in** via `workflow_dispatch`.

The split is deliberate. Emulator jobs are the biggest source of flake in mobile CI; keeping
them off the default path means a device problem can never redden the build signal and train
people to ignore a failing check. [`espresso.yml`](../.github/workflows/espresso.yml)
(pre-existing) already runs instrumented tests with an emulator on every PR.

## Why separate implementer and verifier?

An agent that writes code and then judges it will grade generously. It is not lying — the
same reasoning produced both the code and the assessment, so the mistake and the review share
a root cause. Asking it "is this correct?" samples the same flawed model twice.

Separation breaks the shared cause. The verifier starts from the *requirement*, not from the
diff; it re-reads the acceptance criteria before looking at the code, runs the commands
itself, and reads exit codes rather than prose.

It happened here during bootstrap. The implementer produced a syntactically valid flow that
passed `maestro check-syntax` and looked right. Executed on a device, it failed:
`profile_account_card is visible` — because the suite runs as a guest and that card is
*correctly* hidden for guests. The implementer believed it was done. The verifier proved
otherwise. Full trace: [agent-workflow.md](agent-workflow.md).

## Interview talking points

1. **Evidence over assertion.** Every claim here maps to a command, an exit code and an
   artifact. `SKIPPED — NO DEVICE AVAILABLE` is a respectable result; a fabricated `PASS` is
   not.
2. **A green test is not automatically a correct test.** The smoke suite's key assertion was
   mutation tested — the feature was broken on purpose to confirm the test goes red. Until
   that passed, green meant nothing.
3. **Determinism by design, not by retry.** The two real hazards in this app (a login sheet
   on every cold launch, ~100 demo tasks seeded asynchronously) were found by *reading the
   app* and designed around. No sleeps, no retries; both are mechanically banned.
4. **Right test at the right level.** Three UI flows against twelve unit test classes. The
   `qa-test-designer` must justify why a cheaper level cannot prove the requirement, and must
   list what it deliberately did not automate.
5. **Classification before repair.** Separating diagnosis from fixing is what keeps a `TEST`
   failure from being mislabelled `LOCATOR` and "fixed" by weakening the assertion that was
   correctly reporting a bug.

Full details: [architecture.md](architecture.md) · [agent-workflow.md](agent-workflow.md) ·
[quality-gates.md](quality-gates.md) · [demo-scenario.md](demo-scenario.md)
