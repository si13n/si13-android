# Maestro UI tests

CLI-only. **Maestro Studio is not required** for anything in this directory.

```
maestro/
├── config.yaml     workspace config (flow selection, excluded tags)
├── common/         reusable fragments, included with runFlow
├── smoke/          critical-path suite — must always be green
└── regression/     deeper, slower coverage — added deliberately, not by default
```

## Running

```bash
scripts/run-smoke.sh                                     # the whole smoke suite
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml  # one flow
maestro check-syntax maestro/smoke/01-app-launch.yaml    # static check, no device needed
maestro test --include-tags smoke maestro/               # by tag
```

Prerequisites: an attached device (`adb devices`) and the debug APK installed:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`run-maestro.sh` writes JUnit XML, debug output and (on failure) logcat to
`artifacts/maestro/<timestamp>/`, and **preserves the exit code**:

| Exit | Meaning |
|---|---|
| `0` | passed |
| `1` | a flow failed — a real result |
| `3` | could not run (no device) — reported as `SKIPPED`, never as a pass |

## The two app behaviours every flow must handle

These are properties of Forgetty, not bugs. Flows that ignore them are flaky.

**1. The login bottom sheet appears on every cold launch while signed out.**
`MainActivity.onCreate` shows `LoginBottomSheet` whenever `AuthRepository.isAuthenticated()`
is false. Dismiss it with `id: continue_as_guest_button`. `common/launch-fresh.yaml` does
this for you.

**2. Debug builds seed ~100 demo tasks asynchronously after a data clear.**
`MainActivity.seedDebugTasksIfNeeded()` (gated on `FLAG_DEBUGGABLE` plus a SharedPreferences
flag) inserts a large fixture set in the background. Therefore:

- **Never assert on a task count.** The insert is async; the count is a race.
- **Never assert on a seeded task title.** Those strings are fixture data.
- When a flow needs a specific task, **create it** with a unique title.

`scripts/run-maestro.sh` injects `MAESTRO_RUN_TAG`, so flows use titles like
`QA Lab smoke ${MAESTRO_RUN_TAG}`. That keeps repeated runs, parallel shards and the
seeded fixtures from ever colliding.

> **Verified gotcha (Maestro 2.8.0):** a flow-level `env:` default **wins over** `-e` on the
> command line. An `env: MAESTRO_RUN_TAG: local` block therefore silently overrides the
> unique per-run tag, and every run reuses one title. So the flows carry **no** `env:`
> default, and `${MAESTRO_RUN_TAG}` must come from the wrapper scripts.
>
> A bare `maestro test maestro/` still passes, because the un-substituted placeholder is
> used consistently for both the create and the search — but it gives up uniqueness. Use
> `scripts/run-smoke.sh`.
>
> This was found by checking the stored task title with `maestro hierarchy` rather than
> trusting the console echo, which prints the raw template either way.

> The existing Espresso suite solves the same problem differently — `HomeTaskTest.clearState()`
> sets the seed flag to `true` and deletes all rows, giving it an empty database. Maestro has
> no in-process hook to do that, so the smoke suite works *with* the seed instead of against
> it. Read `app/src/androidTest/java/com/si13/app/HomeTaskTest.kt` before inventing new
> isolation logic.

## Conventions

- One purpose per flow; if the name needs "and", write two flows.
- Numbered filenames in `smoke/` so a human can read the suite in order.
- Every flow carries `tags:` so suites are selectable.
- Shared setup lives in `common/` and is included with `runFlow`.
- Every flow must pass `maestro check-syntax` — the PostToolUse hook enforces this on save.

### Selectors, in strict order of preference

1. `id:` — the resource id (`id: "task_input"` matches `com.si13.app:id/task_input`)
2. accessibility text / `contentDescription`
3. visible text — only when the text *is* the thing under test
4. nothing else

**Coordinate taps (`point:`) are banned** and `scripts/verify-results.sh` fails the build if
one appears. They survive a total UI rewrite while proving nothing. If a view has no id,
that is a finding to report — the fix is adding an id to the layout.

### Waiting

**No hard pauses.** Verified on Maestro 2.8.0: neither `sleep:` nor `wait:` is a real Maestro
command — `check-syntax` rejects both. The quality gate still greps for `sleep:` as a
belt-and-braces guard. The pause you *can* accidentally write is `waitForAnimationToEnd` with
a long timeout, or `extendedWaitUntil` on a condition that is already true.

Wait on a condition instead:

```yaml
- extendedWaitUntil:
    visible:
      id: "home_header"
    timeout: 15000
```

If you cannot express the condition, you have found either a missing test id or a real
product race. Report it; do not sleep around it.

## Current coverage, and why it is small

| Flow | Proves | Why it earns a UI test |
|---|---|---|
| `01-app-launch` | cold launch reaches a rendered Home | if launch breaks, no other result means anything |
| `02-navigate-bottom-nav` | Home / Stats / Settings all reachable and inflate | a broken nav graph makes every feature unreachable |
| `03-create-and-find-task` | a created task persists and is findable | the core loop; no unit test covers sheet → repository → Room → list |

Three flows is the point, not a limitation. UI tests are the most expensive tests to write,
run, diagnose and maintain. Everything provable at a lower level is covered by the 12 JVM
unit test classes in `app/src/test/` and the Espresso suite in `app/src/androidTest/`.

Adding a fourth flow should require an argument about risk that a unit test cannot address.
