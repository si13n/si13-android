# Forgetty Android

Forgetty is a native Kotlin todo application built with Android Views, Material Components,
Fragments, Room, Firebase Authentication and Firestore. It works locally in guest mode and
switches to user-scoped cloud storage after sign-in.

This repository also contains the **Agentic Android Engineering Lab** — a role-based harness
for planning, production development, risk-based automated testing, independent verification,
failure analysis, deterministic guardrails and CI around the real app.

## Agentic Engineering

Five agents are organized by responsibility:

| Agent | Responsibility |
|---|---|
| `planner` | requirement + architecture impact + risk + test strategy + ownership + sequencing |
| `android-developer` | production Kotlin/resources/config |
| `android-test-engineer` | JVM, Android instrumented, Espresso UI and Maestro automation |
| `verifier` | independent final evidence and PASS/FAIL/INCONCLUSIVE |
| `failure-analyst` | root-cause classification and repair routing |

Frameworks are **skills**, not agent identities. Test/debugging skills load just in time so a
unit-test task does not carry Espresso/Maestro instructions unless they are needed.

For a real change, use the executable workflow:

```text
/change Add or fix <behavior>
```

It creates a gitignored task contract, calls the planner, asks for human approval, executes the
planned role/sequence, then requires independent verification.

## How to use the agents

Run Claude Code from the repository root. For normal feature, bug-fix or test work, use
`/change` instead of manually calling individual agents. The main Claude Code session acts as
the orchestrator and routes work to the correct role.

### Create a new feature

Describe the user-visible behavior and important constraints:

```text
/change Add an "Overdue only" filter on Home. It should work for guest and signed-in users,
persist while the app is open, and existing filters must continue to work.
```

The workflow will:

```text
requirement
    ↓
planner
    ↓
risk + acceptance criteria + test level + file ownership + implementation sequence
    ↓
human approval
    ↓
android-developer and/or android-test-engineer
    ↓
verifier
    ↓
PASS | FAIL | INCONCLUSIVE
```

Review the planner output before approving it. In particular, check the acceptance criteria,
chosen test level, files expected to change and implementation sequence. After approval, the
agents implement only the routed scope. The agent that writes code cannot approve its own
work; final PASS belongs only to `verifier`.

Another example:

```text
/change Add a setting that lets the user choose Monday or Sunday as the first day of the week.
The choice must persist after app restart and affect the calendar view.
```

The planner may route production code to `android-developer`, persistence/runtime coverage to
Android instrumented tests, and UI behavior to Espresso only when that level adds a distinct
signal.

### Create a new automated test

Use the same `/change` workflow. State the behavior that must be proven rather than choosing a
framework unless the framework itself is part of the requirement.

Preferred:

```text
/change Add automated coverage proving that tasks without a due date are sorted after tasks
with a due date. Do not change production behavior unless the current implementation is wrong.
```

The planner should select the lowest reliable test level. For pure sorting logic this should
normally become a JVM unit test rather than Espresso or Maestro.

For Android runtime/UI behavior:

```text
/change Add automated coverage proving that the appearance preference is restored after the
Activity is recreated.
```

For a real cross-screen journey where E2E is justified:

```text
/change Add automated coverage for the critical journey: create a task, search for the unique
title, open it and verify the saved details. Use Maestro only if lower levels cannot prove the
whole journey.
```

You can explicitly request a framework when that is the actual goal:

```text
/change Add an Espresso regression test for the guest Profile state after navigating from Home.
Do not modify production code unless a missing stable selector or real product defect requires it.
```

### What to expect during a run

Each `/change` execution creates a gitignored task contract under:

```text
artifacts/agent-runs/TASK-*/
```

Typical files are:

```text
requirement.md
plan.md
approval.md
developer-report.md      # when production code is involved
test-report.md           # when automated tests are involved
verification.md
failure-analysis.md      # only when diagnosis is needed
summary.md
```

These files are the handoff boundary between agent contexts. Agents should read the contract
instead of relying on a conversational summary.

If verification fails, do not manually ask an implementer to "make it green". The harness
routes a clear failure to the correct owner, or invokes `failure-analyst` first when root cause
is uncertain. After every repair, work returns to `verifier`.

### Choosing the right command

| Goal | Command |
|---|---|
| New Android feature | `/change <describe feature and constraints>` |
| Bug fix | `/change <describe observed vs expected behavior>` |
| New unit/instrumented/Espresso/Maestro coverage | `/change <describe behavior to prove>` |
| Full local repository gate | `scripts/verify-results.sh` |
| Static harness validation only | `scripts/validate-harness.sh` |
| Maestro smoke suite only | `scripts/run-smoke.sh` |

Normally you **do not need to choose `planner`, `android-developer`, `android-test-engineer`,
`verifier` or `failure-analyst` manually**. `/change` coordinates them. Direct invocation is
mainly useful when debugging or demonstrating one role in isolation.

See:

- [Agentic Android Engineering Lab](docs/agentic-engineering-lab.md)
- [Architecture](docs/architecture.md)
- [Task contract](docs/task-contract.md)
- [Quality gates](docs/quality-gates.md)
- [Real failure/recovery trace](docs/agent-workflow.md)
- [Claude project instructions](CLAUDE.md)

## Screenshots

Screenshots use emulator-only fixture data; production builds do not include hardcoded sample tasks.

<table>
  <tr><th>Home</th><th>Sort tasks</th><th>Add task</th><th>Profile</th></tr>
  <tr>
    <td><img src="docs/screenshots/forgetty-home.png" alt="Forgetty Home screen" width="180" /></td>
    <td><img src="docs/screenshots/forgetty-sort.png" alt="Forgetty Sort screen" width="180" /></td>
    <td><img src="docs/screenshots/forgetty-add-task.png" alt="Forgetty Add Task" width="180" /></td>
    <td><img src="docs/screenshots/forgetty-profile.png" alt="Forgetty Profile" width="180" /></td>
  </tr>
</table>

## Application architecture

```text
app/src/main/java/com/si13/app     Kotlin application code
app/src/main/res                   XML layouts, themes, drawables, widgets
app/src/test                       JVM/unit tests
app/src/androidTest                Android instrumented + Espresso + Room migration tests
app/schemas                        exported Room schemas
maestro/                           critical Maestro E2E flows
.claude/                           agents, on-demand skills and hooks
scripts/                           build/test/evidence/static harness gates
```

Important components:

- `MainActivity` hosts navigation and extension/deep-link entry points.
- `HomeFragment` owns list/calendar presentation, search, filters, sorting and sections.
- `TaskRepository` selects guest/local or authenticated/remote storage.
- `LocalTaskDataSource` stores guest tasks in Room.
- `RemoteTaskDataSource` stores authenticated tasks in Firestore.
- `AuthRepository`, `GoogleAuthClient` and `GoogleSignInHandler` preserve the real sign-in flow.
- `TaskReminderScheduler` handles native reminders.
- `ForgettyWidgets` supplies home-screen widgets.

## Features

- List and calendar Home modes with localized dates and first-day-of-week support.
- Overdue, Today, Upcoming, No due date and Completed sections.
- Search across titles, notes, tags and list names.
- Status filters and multiple sort modes.
- Personal, Work, Shared, Shopping and custom task lists.
- Add Task with notes, reminders, recurrence, tags, subtasks, attachments and lists.
- Editable task details with completion, duplication, sharing and deletion.
- Guest mode backed by Room; authenticated mode backed by user-scoped Firestore.
- Google sign-in via Firebase Authentication and Android Credential Manager.
- Guest-task import after sign-in.
- System/light/dark appearance.
- Native notification reminders, shortcuts, export and `RemoteViews` widgets.

## Build and test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Harness commands:

```bash
scripts/check-environment.sh
scripts/validate-harness.sh
scripts/run-smoke.sh
scripts/verify-results.sh
```

### Test levels

| Level | Location | Primary signal | Runs on |
|---|---|---|---|
| JVM unit | `app/src/test/` | pure logic/repository with fakes | JVM |
| Android instrumented | `app/src/androidTest/` | Room/Context/prefs/runtime | device |
| Espresso UI | `app/src/androidTest/` | Activity/Fragment/View behavior | device |
| Maestro E2E | `maestro/smoke/` | critical packaged-app journeys | device |

Most coverage deliberately lives below E2E. Details:

- [Instrumented/Espresso tests](app/src/androidTest/README.md)
- [Maestro tests](maestro/README.md)
- [Regression-flow policy](maestro/regression/README.md)

## Continuous integration

| Workflow | Purpose |
|---|---|
| [PR Checks](.github/workflows/pr-checks.yml) | shared static harness validator, build/unit, then device suites |
| [Android Espresso Tests](.github/workflows/espresso.yml) | emulator + instrumented/Espresso + Allure |
| [Android Maestro Tests](.github/workflows/maestro-tests.yml) | emulator + Maestro smoke + artifacts |

The same `scripts/validate-harness.sh` defines static harness rules locally and in CI so they
cannot silently drift apart.

## Firebase configuration

Firebase is configured through `app/google-services.json`. Google sign-in requires a valid
`default_web_client_id` generated from that Firebase project.

Firestore task documents remain scoped to the authenticated user. Do not enable cross-user
shared lists without corresponding ownership, membership, invitation, rules and indexes.
