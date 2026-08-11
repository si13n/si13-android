---
name: verification-gates
description: The evidence standard for this repo — no claim of success without evidence, and the difference between IMPLEMENTED and VERIFIED. Use before reporting any task complete, when reviewing another agent's work, and when deciding PASS / FAIL / INCONCLUSIVE.
---

# Verification gates

## The rule

> **NO CLAIM OF SUCCESS WITHOUT EVIDENCE.**

An agent's confidence is not evidence. A summary of a command is not evidence. Evidence is
a command, its **exit code**, and its **output** — reproducible by someone else.

## IMPLEMENTED is not VERIFIED

| | IMPLEMENTED | VERIFIED |
|---|---|---|
| Means | the change has been written | the change has been proven to satisfy the requirement |
| Who says it | the implementer | an independent verifier |
| Based on | intent | evidence |
| Can be wrong | frequently | rarely, and traceably |

Most agent failures are this confusion. Code was produced, so the task felt done. Nothing
was executed, nothing was compared to the requirement, and "done" was reported anyway.

Legitimate states to report:

- **IMPLEMENTED, NOT VERIFIED** — written, nothing executed yet. Honest and useful.
- **VERIFIED** — evidence gathered, criteria checked, verdict issued.
- **NOT VERIFIED** — could not be executed here. Must state *why* and *how the human can
  verify it*. This is an acceptable outcome; pretending otherwise is not.

## Available gates

| Gate | Command | Proves |
|---|---|---|
| **BUILD** | `./gradlew assembleDebug` | it compiles and packages |
| **UNIT TEST** | `./gradlew testDebugUnitTest` | logic behaves (fastest real signal) |
| **STATIC CHECK** | `./gradlew lintDebug`, `maestro check-syntax`, `bash -n` | no syntax/lint defects |
| **UI TEST** | `scripts/run-smoke.sh`, `./gradlew connectedDebugAndroidTest` | the journey works on a device |
| **GIT DIFF REVIEW** | `git diff`, `git diff --name-only` | only intended changes, no scope creep |
| **ACCEPTANCE CRITERIA REVIEW** | read the plan, check each item | it does what was actually asked |
| **ARTIFACT REVIEW** | inspect `artifacts/` | the run really happened, and just now |

Not every task needs every gate. Every task needs the gates that bear on *its* requirement,
and the report must say which ones ran.

## Reading exit codes, not vibes

```bash
./gradlew assembleDebug --console=plain ; echo "EXIT=$?"
maestro test maestro/smoke/01-app-launch.yaml ; echo "EXIT=$?"
```

- `0` is the only success.
- A log full of green ticks with exit code `1` is a **failure**.
- `|| true`, `set +e` around a test command, and swallowed pipeline codes destroy the gate.
  Finding one in a diff is itself a finding.
- Verify artifacts are from **this** run — check the timestamp. Stale artifacts have passed
  many a review.

## A green test is not automatically a correct test

Before accepting any test as evidence, answer:

1. **Would it fail if the feature were broken?** If you cannot argue yes, it proves nothing.
   The strongest check is to reason through (or actually try) the mutation: break the
   feature, confirm red.
2. **Does it assert on the outcome**, or merely that a screen appeared?
3. **Is it tautological** — asserting on a value the test itself just set?
4. **Is it asserting on something incidental** — seeded demo data, a count, a timestamp,
   sort order that happens to hold today?
5. **Was the assertion weakened to make it pass?** Check the diff for deleted or loosened
   assertions.

A test that passes for the wrong reason is worse than no test: it buys false confidence and
nobody looks again.

## Verdicts

- **PASS** — every acceptance criterion is backed by evidence you gathered yourself, the
  diff is in scope, and the tests would catch a regression.
- **FAIL** — a criterion is unmet, a test is unfit for purpose, or the diff exceeds scope.
- **INCONCLUSIVE** — the evidence could not be gathered (no device, missing tool, blocked
  build).

**Never round INCONCLUSIVE up to PASS.** "The code looks right and it would probably pass"
is the sentence this whole repo exists to prevent. `SKIPPED — NO DEVICE AVAILABLE` is a
respectable result; a fabricated PASS is not.

## Forbidden ways to make a gate green

- Weakening or deleting an assertion. Only ever change an assertion when the **requirement**
  proves the old one was wrong — and then say so explicitly in the report.
- Adding `sleep` or a retry to get past a race.
- `continueOnFailure`, `|| true`, `set +e`, ignoring a non-zero exit.
- Excluding the failing test from the suite.
- Rerunning until it happens to pass, then reporting the green run.

Each of these converts a **known** problem into an **unknown** one. That is a strictly worse
position than red.

## Reporting template

```
## Evidence
- BUILD: ./gradlew assembleDebug → EXIT=0
- UNIT:  ./gradlew testDebugUnitTest → EXIT=0 (N tests, 0 failures)
- UI:    scripts/run-smoke.sh → EXIT=0, artifacts/maestro/junit.xml
- DIFF:  3 files changed, all expected

## Acceptance criteria
1. <criterion> — MET (evidence: ...)
2. <criterion> — NOT MET (...)
3. <criterion> — UNVERIFIED (no device attached; run `scripts/run-smoke.sh` locally)

## VERDICT: PASS | FAIL | INCONCLUSIVE
```
