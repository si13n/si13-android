# Quality gates

## The standard

> **No claim of success without evidence.**

Evidence is a command, its exit code, and its output — reproducible by someone else. An
agent's confidence is not evidence. A summary of a command is not evidence.

## IMPLEMENTED vs VERIFIED

| | IMPLEMENTED | VERIFIED |
|---|---|---|
| Means | the change was written | the change was proven to satisfy the requirement |
| Who says it | the implementer | an independent verifier |
| Based on | intent | evidence |

Three legitimate states, and the third is not a failure:

- **IMPLEMENTED, NOT VERIFIED** — written, nothing executed yet.
- **VERIFIED** — evidence gathered, criteria checked, verdict issued.
- **NOT VERIFIED** — could not be executed here; states *why* and *how the human can verify*.

## The gates

| Gate | Command | Proves | Gating? |
|---|---|---|---|
| Required files | `scripts/verify-results.sh` gate 1 | the repo structure is intact | yes |
| Shell syntax | `bash -n scripts/*.sh` | scripts parse | yes |
| Flow syntax | `maestro check-syntax` | flows are valid Maestro | yes |
| Flow rules | grep for `sleep:` / `point:` | the repo's own rules hold | yes |
| Agent/skill definitions | frontmatter check | the agentic layer loads | yes |
| Build | `./gradlew assembleDebug` | it compiles and packages | yes |
| Unit tests | `./gradlew testDebugUnitTest` | logic behaves | yes |
| UI smoke | `scripts/run-smoke.sh` | the journeys work on a device | yes, when a device exists |
| Lint | `./gradlew lintDebug` | code health | no — advisory, triaged |
| Git diff review | `git diff --name-only` vs the plan | no scope creep | yes (human/verifier) |
| Acceptance criteria | read the plan, check each | it does what was asked | yes (human/verifier) |

Run them all:

```bash
scripts/verify-results.sh              # everything
scripts/verify-results.sh --no-build   # fast static-only pass
```

## Exit codes are the contract

```bash
./gradlew assembleDebug --console=plain ; echo "EXIT=$?"
scripts/run-smoke.sh ; echo "EXIT=$?"
```

`0` is the only success. A log full of green ticks with exit code 1 is a failure.

`scripts/run-maestro.sh` distinguishes three outcomes on purpose:

| Exit | Meaning |
|---|---|
| `0` | passed |
| `1` | a flow failed — a real result |
| `3` | could not run — `SKIPPED — NO DEVICE AVAILABLE` |

Collapsing `3` into either `0` or `1` would be the lie. It is neither a pass nor a defect.

## SKIPPED is not PASS

`scripts/verify-results.sh` reports skipped gates separately and prints them again in the
summary:

```
 RESULT: 9 passed, 0 failed, 1 skipped

 SKIPPED gates are NOT passes. Unverified means unverified:
   - maestro smoke suite: SKIPPED — NO DEVICE AVAILABLE

 VERDICT: PASS WITH SKIPS — the skipped gates above were not proven.
```

It exits `0`, because running without an emulator is an expected situation and not a defect —
but it can never be mistaken for full verification. **`INCONCLUSIVE` is never rounded up to
`PASS`.**

## A green test is not automatically a correct test

Before any test counts as evidence:

1. **Would it fail if the feature were broken?** If you cannot argue yes, it proves nothing.
2. Does it assert on the **outcome**, or merely that a screen rendered?
3. Is it **tautological** — asserting a value the test itself just set?
4. Is it asserting on something **incidental** — seeded data, a count, a timestamp?
5. Was an assertion **weakened** to make it pass? Check the diff.

The strongest available answer to (1) is a mutation test: break the feature, confirm red.
This was done for the smoke suite — see `docs/agent-workflow.md` §9. Both mutations went red
before the suite was treated as evidence.

## Forbidden ways to go green

Each converts a **known** problem into an **unknown** one, which is strictly worse than red:

- Weakening or deleting an assertion. *Only* legitimate when the requirement proves the old
  assertion was wrong — and then it must be stated explicitly in the report.
- Adding a `sleep` or a retry to get past a race.
- `continueOnFailure`, `|| true`, `set +e`, ignoring a non-zero exit code.
- Excluding the failing test from the suite.
- Rerunning until it happens to pass, then reporting the green run.

The first two are mechanically blocked: `verify-results.sh` and CI both fail the build if
`sleep:` or a coordinate `point:` appears anywhere under `maestro/`, and the PostToolUse hook
warns the moment one is written. A rule nobody checks is a suggestion.

## Why retries are not a fix

A retry does not make a test pass; it makes a failure intermittent. The bug stays, the signal
degrades, and the team learns that red sometimes means nothing. Once that happens, the suite
has stopped being a quality gate and become a ritual.

Legitimate retry: genuine infrastructure flakiness (a runner losing its network). Even then
it must be logged, visible, and paired with a ticket — never a silent default.

## The human gates

Two points where a person, not an agent, decides:

1. **Plan approval**, before code exists — the cheapest place to catch a wrong approach.
2. **Accepting a `PASS`**, including reviewing what was `SKIPPED` and what the verifier
   admitted it could not prove.

Agents are good at producing and checking work. They are not accountable for it. Keeping a
human at those two points is what makes the loop trustworthy rather than merely fast.
