---
name: maestro-testing
description: Project-specific Maestro practices for the Forgetty Android app (com.si13.forgetty) — CLI-only workflow, flow structure, stable resource-id selectors, assertions, reusable flows, deterministic state, and how to debug element-not-found. Use when writing, running, reviewing or debugging any Maestro flow in maestro/.
---

# Maestro testing in this repo

App under test: **`com.si13.forgetty`** (Forgetty). Flows live in `maestro/`.

## CLI-first — Maestro Studio is not required

Everything here works from the terminal. Studio is a convenience, never a dependency, and
no instruction in this repo may assume it.

```bash
maestro --version
maestro list-devices
adb devices                                        # a device must be attached first

maestro check-syntax maestro/smoke/01-app-launch.yaml   # static check, no device needed
maestro test maestro/smoke/01-app-launch.yaml           # run one flow
maestro test maestro/smoke                              # run a folder
maestro test --include-tags smoke maestro/               # run by tag
maestro hierarchy                                        # dump the live view tree
```

Wrappers that add device checks, artifacts and exit-code handling:

```bash
scripts/run-maestro.sh maestro/smoke/01-app-launch.yaml
scripts/run-smoke.sh
```

## Flow structure

Every flow starts with a YAML header block, `---`, then commands:

```yaml
appId: com.si13.forgetty
name: Descriptive flow name
tags:
  - smoke
---
- runFlow: ../common/launch-fresh.yaml
- assertVisible:
    id: "home_header"
```

Conventions:

- One purpose per flow. If you need "and", write two flows.
- Numbered file names in `smoke/` to make execution order obvious to a human reader.
- Shared setup goes in `maestro/common/` and is pulled in with `runFlow`.
- Tag everything (`smoke`, `regression`) so suites are selectable.

## Two things about this app you must handle

**1. A login bottom sheet appears on every cold launch while signed out.**
`MainActivity.onCreate` shows `LoginBottomSheet` when not authenticated. Dismiss it:

```yaml
- tapOn:
    id: "continue_as_guest_button"
```

**2. Debug builds seed ~100 demo tasks asynchronously after data is cleared.**
`MainActivity.seedDebugTasksIfNeeded()` runs once per data-clear on debuggable builds.
Consequences:

- **Never assert on task counts.** The insert is async; the count is a race.
- **Never assert on a seeded task title** (`"Buy groceries and household supplies"` etc.).
  Those strings are fixture data and may change.
- When a flow needs a specific task, **create it** with a unique title:
  `Maestro smoke ${MAESTRO_RUN_TAG}` (see `run-maestro.sh`, which passes `-e`).

## Selectors — in strict order of preference

1. **`id:`** — the resource id. Matched against the full resource id as a regex, so
   `id: "task_input"` matches `com.si13.forgetty:id/task_input`. **This is the default choice.**
2. **accessibility text** — `contentDescription`, e.g. the bottom-nav buttons expose
   `Home`, `Stats`, `Settings`, `New task`.
3. **visible text** — only for text that *is* the thing under test. Text is a locale- and
   copy-change liability; an id is not.
4. **nothing else.**

Verify an id exists before using it:

```bash
grep -rn 'android:id="@+id/task_input"' app/src/main/res/layout/
```

### Useful ids in this app

| Screen | Ids |
|---|---|
| Bottom nav | `homeFragment`, `statsFragment`, `profileFragment`, `add_task_fab` |
| Login sheet | `continue_as_guest_button`, `sign_in_with_google_button` |
| Home | `home_header`, `home_content`, `task_list`, `task_progress_card`, `empty_tasks_container` |
| Home actions | `task_search_button`, `task_filter_button`, `task_sort_button`, `task_settings_button` |
| Home filters | `status_filter_all`, `status_filter_today`, `status_filter_high`, `status_filter_completed` |
| View mode | `list_view_button`, `calendar_view_button`, `calendar_grid`, `calendar_month_title` |
| Add task | `task_input`, `add_task_button`, `add_task_close`, `add_task_priority`, `add_task_notes` |
| Task row | `task_title`, `task_checkbox`, `task_priority_button`, `task_delete_action` |

### Coordinates are forbidden

`- tapOn: point: 50%,50%` is banned. It breaks on every screen size, survives a total UI
rewrite while proving nothing, and hides the fact that a view has no test id. If a view is
not addressable, **that is a finding to report** — the fix is adding an id to the layout.

## Assertions

Assert on the thing the requirement is about:

```yaml
- assertVisible:
    id: "task_progress_card"
- assertVisible: "Maestro smoke ${MAESTRO_RUN_TAG}"    # data this flow created
- assertNotVisible:
    id: "empty_tasks_container"
```

A flow whose only assertion is "the home screen rendered" proves that the app launches. It
does not prove the feature. Ask: **would this assertion fail if the feature were broken?**

## Waiting — never sleep

```yaml
# CORRECT — wait for a condition, with a bounded timeout
- extendedWaitUntil:
    visible:
      id: "home_header"
    timeout: 15000
```

Verified on Maestro 2.8.0: there is **no `sleep:` and no `wait:` command** — `check-syntax`
rejects both with `Invalid Command`. So the sleep-shaped anti-patterns you can actually
write here are:

```yaml
# WRONG — a fixed pause dressed up as a wait
- waitForAnimationToEnd:
    timeout: 5000
```

```yaml
# WRONG — extendedWaitUntil on something already true, used to burn time
- extendedWaitUntil:
    visible:
      id: "home_content"     # already visible; this is a padded sleep
    timeout: 8000
```

Rules:

- No hard pauses. The repo greps for `sleep:` as a belt-and-braces guard even though Maestro
  would reject it anyway.
- `waitForAnimationToEnd` is legitimate for a genuinely animating screen, but a long timeout
  on it is a sleep. Prefer a condition.
- `extendedWaitUntil` must wait on a **condition that is not yet true**, not be used as a
  padded sleep.
- Timeouts are generous but finite (10–20 s). A too-short timeout creates flakiness; an
  infinite one creates a hung CI job.
- If you cannot express the condition, you have found a missing test id or a real product
  race. **Report it. Do not sleep around it.**

## Deterministic state

```yaml
- launchApp:
    appId: com.si13.forgetty
    clearState: true      # wipes app data — the login sheet and the seeder will return
```

Other resets, from the shell:

```bash
adb shell pm clear com.si13.forgetty
adb shell am force-stop com.si13.forgetty
```

A flow must never depend on what a previous flow left behind. `run-smoke.sh` runs flows in
order, but each flow must still stand alone.

## Debugging "element not found"

Work in this order — do not start by changing the selector.

1. **Is the app even up?** `adb logcat -d | grep -iE "fatal|androidruntime"` — a crash
   makes everything "not found". That is `PRODUCT`, not `LOCATOR`.
2. **Dump the live hierarchy:** `maestro hierarchy > /tmp/h.txt` then grep it. This shows
   you what is *actually* on screen right now.
3. **Is something covering it?** The login bottom sheet, the task-import dialog, a system
   permission dialog, or the keyboard.
4. **Does the id still exist?** `grep -rn 'task_input' app/src/main/res/layout/` — if it was
   renamed, that is `LOCATOR` and the fix is the flow.
5. **Is it off-screen?** Add `- scrollUntilVisible:` rather than a blind `scroll`.
6. **Is it a timing issue?** Wrap in `extendedWaitUntil` on the condition — not a sleep.

Failed-run output (screenshots, command log) lands under `artifacts/maestro/` via
`run-maestro.sh --debug-output`. Read it before guessing.

## Artifacts and reports

```bash
maestro test --format junit --output artifacts/maestro/junit.xml \
             --debug-output artifacts/maestro/debug maestro/smoke
```

`run-maestro.sh` and `run-smoke.sh` do this for you and **preserve the exit code**, which
is what the verifier and CI actually gate on.
