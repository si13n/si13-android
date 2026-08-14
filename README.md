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
