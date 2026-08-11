# Regression flows

Slower, deeper UI coverage. **Not** part of the smoke gate.

Runs on demand or on a schedule, never on every commit:

```bash
maestro test --include-tags regression maestro/
scripts/run-maestro.sh maestro/regression/<flow>.yaml
```

## What belongs here

A flow earns a place here only if **all** of these are true:

1. It covers a user-visible journey across real screens.
2. No unit or integration test could prove the same thing. (Sorting rules, date maths and
   mapping belong in `app/src/test/` — they are already covered there.)
3. It is deterministic: it creates its own data and asserts on outcomes it controls.
4. Its failure would be actionable, not merely interesting.

## What does not belong here

| Candidate | Where it goes instead | Why |
|---|---|---|
| Sort order correctness | `TaskSorterTest` (unit) | pure logic, already covered |
| Recurrence rule maths | `TaskRepeatRuleTest` (unit) | pure logic, no UI needed |
| Room v4→v5 migration | `TaskDatabaseMigrationTest` (instrumented) | needs a real DB, not a real UI |
| Firestore field defaults | `FirestoreTaskMapperTest` (unit) | contract test on the mapper |
| Google sign-in with real credentials | manual | needs live auth; automating it means storing credentials |
| Widgets, launcher shortcuts, notifications | manual / exploratory | outside the app process; Maestro cannot address them reliably |
| Visual polish, animation feel | exploratory | needs human judgement |

## Candidate flows (designed, deliberately not yet implemented)

Listed so the intent is visible. Each would need a `qa-test-designer` argument for why UI
level is required before it gets written.

| Candidate | Would prove | Level argument |
|---|---|---|
| Complete a task and see it move to Completed | the completion state transition survives the full stack | strong — crosses UI, repository and Room |
| Swipe to delete, then Undo | destructive action is recoverable | strong — gesture-driven, unit-untestable |
| Filter chips narrow the visible list | Today / High priority / Completed filters wire through | medium — `TaskPresentation` covers the logic; UI proves the wiring |
| List ↔ Calendar view switch | calendar mode renders and stays in sync | medium |
| Task survives process death | persistence, not just in-memory state | strong — `clearState: false` plus a force-stop |

Empty by design. An empty regression suite with a documented rationale is more honest than
a dozen shallow flows that mostly re-test the smoke path.

## Rules (same as smoke)

- Stable `id:` selectors; **no coordinate taps**.
- **No `sleep:`** — wait on conditions with `extendedWaitUntil`.
- Create your own data with a unique title; never assert on the ~100 seeded demo tasks.
- Reuse `../common/launch-fresh.yaml` for setup.
- Tag with `regression` so it stays out of the smoke gate.
