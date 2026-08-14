# Quality gates

## Standard

> **No claim of success without evidence.**

Evidence is a reproducible command, exit code and result/artifact. Agent confidence and
summaries are not evidence.

## IMPLEMENTED vs VERIFIED

| State | Meaning | Who may say it |
|---|---|---|
| IMPLEMENTED | code/test change was written | implementation agents |
| NOT VERIFIED | required evidence could not be gathered | anyone, with reason |
| VERIFIED | acceptance criteria were proven with evidence | `verifier` only |

## Gates

| Gate | Typical command | Signal |
|---|---|---|
| Harness structure | `scripts/verify-results.sh` | expected roles/skills/workflows exist |
| Shell/YAML syntax | `bash -n`, YAML parse | harness files are structurally valid |
| Maestro syntax/rules | `maestro check-syntax`, rule grep | E2E flows obey repo conventions |
| Build | `./gradlew assembleDebug` | production compiles/packages |
| JVM unit | `./gradlew testDebugUnitTest` | pure logic/repository contracts |
| Espresso/instrumented | `./gradlew connectedDebugAndroidTest` | Android runtime + Room behavior |
| Maestro | `scripts/run-smoke.sh` | critical cross-screen journeys |
| Diff scope | `git diff --name-only` vs plan | role and scope boundaries respected |
| Acceptance criteria | verifier evidence map | requested behavior was actually proven |

Not every feature needs every framework. The **planner** selects relevant gates; the
**verifier** confirms those gates actually prove the requirement.

## Full repository gate

```bash
scripts/verify-results.sh
```

With no device, device suites are `SKIPPED` and explicitly listed. For a fast static-only
harness check:

```bash
scripts/verify-results.sh --no-build
```

`--no-build` skips build/unit and both device suites; it exists for validating harness
structure/syntax quickly.

## Exit codes are contracts

`0` is success for a command that actually ran. A skipped command is not a pass. A wall of
green log text with a non-zero process exit is still a failure.

Maestro wrapper semantics remain explicit:

| Exit | Meaning |
|---|---|
| `0` | passed |
| `1` | test failed |
| `3` | could not run because no device is available |

## A green test must be meaningful

Before a test counts as evidence:

1. Would it fail if the feature were broken?
2. Does it assert the real outcome rather than incidental rendering?
3. Is setup deterministic and independent from previous runs?
4. Is the assertion tautological or tied to seeded/unstable data?
5. Was anything weakened/excluded/retried merely to get green?

When risk justifies it, mutation thinking is the strongest challenge: deliberately break the
behavior and confirm the test goes red.

## Forbidden ways to go green

- weaken/delete a correct assertion;
- arbitrary sleeps or retry-until-green;
- `|| true`, `set +e`, swallowed quality-gate exits;
- excluding a failing test without a requirement-based reason;
- converting `SKIPPED`, missing-device or missing-tool evidence into PASS.

## Human gates

Human judgement remains important at two places:

1. approving non-trivial plan/architecture/risk trade-offs before implementation;
2. accepting the final verifier PASS and any explicitly carried residual risk.
