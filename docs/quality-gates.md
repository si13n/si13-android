# Quality gates

## Standard

> **No claim of success without evidence. Missing evidence is not PASS.**

Evidence is a command, exit code and output/artifact reproducible by someone else. An agent's
confidence or implementation summary is not evidence.

## Authority

| State | Meaning | Authority |
|---|---|---|
| IMPLEMENTED | change was written | implementation agent |
| VERIFIED | requirement proven with evidence | verifier only |
| INCONCLUSIVE | required evidence unavailable | verifier / gate |

Implementers say **ready for independent verification**, never VERIFIED.

## Static harness gate

`scripts/validate-harness.sh` is the single executable source for static harness policy. It
checks required files, exact five-agent topology, shell syntax/executable bits, agent/skill
frontmatter, Maestro anti-patterns/syntax and stale workflow references.

PR CI installs Maestro and calls the same script. `verify-results.sh` calls it locally. Rules
are not duplicated across YAML and shell.

```bash
scripts/validate-harness.sh
scripts/validate-harness.sh --allow-missing-maestro   # syntax explicitly not proven
```

## Runtime gates

| Gate | Typical command | Proves |
|---|---|---|
| Build | `./gradlew assembleDebug` | production compiles/packages |
| JVM unit | `./gradlew testDebugUnitTest` | pure logic/contracts |
| Android instrumented | `./gradlew connectedDebugAndroidTest` | Android runtime/Room/etc. |
| Espresso UI | targeted/full `connectedDebugAndroidTest` | in-process UI behavior |
| Maestro E2E | `scripts/run-maestro.sh <flow>` | packaged cross-screen journey |
| Diff/ownership | `git diff --name-only` + task contract | scope and one-owner-per-file |
| Acceptance criteria | contract + evidence | requested behavior, not merely green tests |

Not every task needs every framework; every task needs the gates selected by its risk/test
strategy.

## `verify-results.sh` exit contract

```bash
scripts/verify-results.sh
```

- `0` — **PASS**: every configured required gate ran and passed.
- `1` — **FAIL**: a required gate failed.
- `3` — **INCOMPLETE**: required evidence was skipped/unavailable.

For an intentionally partial run:

```bash
scripts/verify-results.sh --no-build --allow-skips
```

This may exit 0 so it is usable in scripts, but its verdict is **PARTIAL**, not PASS. The
caller explicitly accepted that evidence is incomplete.

## Read-only authority guard

`planner`, `verifier` and `failure-analyst` omit Write/Edit and also have agent-scoped
`PreToolUse` Bash hooks. The guard blocks common source/history mutation paths before shell
execution. Verifier/failure analyst may still run builds/tests/diagnostics that mutate build
outputs or device state; they may not repair source.

The guard is a deterministic policy layer, not a claim that regex is a perfect shell sandbox.
Its purpose is to materially enforce the role boundary rather than relying only on prompt text.

## Post-edit gate

`.claude/hooks/quick-check.sh` runs after Write/Edit. Syntax/structural defects block. Maestro
hard sleeps and coordinate taps are blocking errors, not advisory warnings. Expensive builds
and device tests remain deliberate verification steps rather than running after every edit.

## A green test is not automatically a correct test

Before using a test as evidence:

1. Would it fail if the target regression existed?
2. Does it assert the outcome rather than mere rendering?
3. Is it tautological or dependent on incidental seeded state/count/time?
4. Is it at the lowest reliable test level?
5. Was an assertion weakened to make implementation green?

Mutation thinking is the strongest practical challenge: deliberately break the behavior and
confirm the test goes red when worthwhile.

## Forbidden ways to go green

- weaken/delete a correct assertion;
- add sleep/retry to pass a race;
- swallow exit codes with `|| true`, `set +e` or `continueOnFailure` around a gate;
- exclude a failing test;
- rerun until a flaky test happens to pass and report only green;
- convert skipped/unavailable required evidence into PASS.

## Human gates

1. Approve/revise non-trivial plans before implementation.
2. Accept the final verifier PASS and review residual/manual risk.

Agents produce and check work; the human remains accountable for scope and acceptance.
