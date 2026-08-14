# Regression flows

Slower, deeper E2E coverage. **Not** part of the smoke gate.

```bash
maestro test --include-tags regression maestro/
scripts/run-maestro.sh maestro/regression/<flow>.yaml
```

## What belongs here

A flow earns a place here only if all are true:

1. It covers a user-visible journey across real screens.
2. No JVM or Espresso/instrumented test can prove the same requirement more cheaply.
3. It is deterministic and creates/controls its own test data.
4. Its failure is actionable.
5. The `planner` selected MAESTRO in the approved test strategy.

## What goes elsewhere

| Candidate | Better level | Why |
|---|---|---|
| Sort order correctness | UNIT | pure logic |
| Recurrence maths | UNIT | pure Kotlin rule |
| Room migration | ESPRESSO / instrumented | real DB/runtime, no E2E UI needed |
| Firestore field defaults | UNIT | mapper/contract logic |
| Google sign-in with real credentials | MANUAL | live auth and account/security concerns |
| Visual polish / animation feel | EXPLORATORY | human judgement |

## Candidate flows

These are ideas, not a backlog commitment. The planner must still justify MAESTRO before the
`android-test-engineer` implements one.

| Candidate | Would prove |
|---|---|
| Complete a task and see it move to Completed | full visible completion transition |
| Swipe delete then Undo | gesture-driven recovery path |
| List ↔ Calendar switch | cross-view wiring and rendered state |
| Task survives process restart | user-visible persistence across process lifecycle |

Empty or small is acceptable. A few high-value E2E flows are better than duplicating the
lower-level suite.

## Rules

- Stable ids; no coordinate taps.
- No arbitrary sleeps; wait on observable conditions.
- Create unique data; never assert on debug seed counts/titles.
- Reuse `../common/` setup.
- Tag regression flows with `regression`.
