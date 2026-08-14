# Forgetty Android

Forgetty is a native Kotlin todo application built with Android Views, Material Components,
Fragments, Room, Firebase Authentication and Firestore. It works locally in guest mode and
switches to user-scoped cloud storage after sign-in.

This repository also contains the **Agentic Android Engineering Lab** — a role-based agentic
engineering harness for Android development and QA. It demonstrates planning, production
implementation, unit/Espresso/Maestro automation, independent verification, failure analysis,
quality gates and CI around the real app.

## Agentic Engineering

The harness uses five agents organized by responsibility:

| Agent | Responsibility |
|---|---|
| `planner` | requirement + architecture impact + risks + test strategy + owner routing |
| `android-developer` | production Kotlin/resources/Gradle |
| `android-test-engineer` | JVM, Espresso/instrumented/Room and Maestro automation |
| `verifier` | independent final evidence and PASS/FAIL/INCONCLUSIVE |
| `failure-analyst` | root-cause classification and repair routing |

Frameworks are **skills**, not agent identities. The test engineer loads `unit-testing`,
`espresso-testing` or `maestro-testing` as the planned test level requires.

See:

- [Agentic Android Engineering Lab](docs/agentic-engineering-lab.md)
- [Architecture](docs/architecture.md)
- [Quality gates](docs/quality-gates.md)
- [Real failure/recovery trace](docs/agent-workflow.md)
- [Claude project instructions](CLAUDE.md)

## Screenshots

Screenshots use emulator-only fixture data; production builds do not include hardcoded sample tasks.

<table>
  <tr>
    <th>Home</th><th>Sort tasks</th><th>Add task</th><th>Profile</th>
  </tr>
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
app/src/androidTest                Espresso/instrumented + Room migration tests
app/schemas                        exported Room schemas
maestro/                           critical Maestro E2E flows
.claude/                           agents, skills and hooks
scripts/                           build/test/evidence gates
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

Use the checked-in Gradle wrapper:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Harness wrappers:

```bash
scripts/check-environment.sh
scripts/build-android.sh
scripts/run-smoke.sh
scripts/verify-results.sh
```

### Test levels

| Level | Location | Current shape | Runs on |
|---|---|---|---|
| JVM unit | `app/src/test/` | 12 classes | no device |
| Espresso/instrumented | `app/src/androidTest/` | 5 classes / 44 tests | device |
| Maestro E2E | `maestro/smoke/` | 3 critical flows | device |

Most coverage deliberately lives below E2E. Details:

- [Espresso/instrumented tests](app/src/androidTest/README.md)
- [Maestro tests](maestro/README.md)
- [Regression-flow policy](maestro/regression/README.md)

## Continuous integration

| Workflow | Purpose |
|---|---|
| [PR Checks](.github/workflows/pr-checks.yml) | static checks, build/unit, then reusable device suites |
| [Android Espresso Tests](.github/workflows/espresso.yml) | emulator + instrumented tests + Allure |
| [Android Maestro Tests](.github/workflows/maestro-tests.yml) | emulator + Maestro smoke + artifacts |

Cheap checks run before emulator work so a broken build does not waste device minutes.

## Firebase configuration

Firebase is configured through `app/google-services.json`. Google sign-in requires a valid
`default_web_client_id` generated from that Firebase project.

Firestore task documents remain scoped to the authenticated user. Do not enable cross-user
shared lists without corresponding ownership, membership, invitation, rules and indexes.
