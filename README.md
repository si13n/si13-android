# Forgetty Android

Forgetty is a native Kotlin todo application built with Android Views, Material Components, Fragments, Room, Firebase Authentication, and Firestore. It works locally in guest mode and switches to user-scoped cloud storage after sign-in.

## Screenshots

<table>
  <tr>
    <th>Home</th>
    <th>Add task</th>
    <th>Profile</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/forgetty-home.png" alt="Forgetty Home screen" width="220" /></td>
    <td><img src="docs/screenshots/forgetty-add-task.png" alt="Forgetty Add Task screen" width="220" /></td>
    <td><img src="docs/screenshots/forgetty-profile.png" alt="Forgetty guest Profile screen" width="220" /></td>
  </tr>
</table>

## Features

- List and calendar Home modes with localized dates and first-day-of-week support.
- Smart Overdue, Today, Upcoming, No due date, and Completed sections.
- Search across task titles, notes, tags, and list names.
- All, Today, High priority, and Completed filters.
- Priority, newest, oldest, alphabetical, and due-date sorting.
- Personal, Work, Shared, Shopping, and custom task lists.
- Progress summaries calculated from the current task scope.
- Swipe reveal, first-tap deletion, retry, and Undo.
- Full Add Task experience with notes, reminders, recurrence, tags, subtasks, attachments, list selection, voice entry, and local suggestions.
- Editable Task Detail bottom sheet with autosave state, completion, duplication, sharing, and deletion.
- Guest mode backed by Room and authenticated mode backed by user-scoped Firestore.
- Google sign-in through Firebase Authentication and Android Credential Manager.
- Safe guest-task import after sign-in.
- System, light, and dark appearance settings.
- Native notification reminders, launcher shortcuts, task export, and `RemoteViews` home-screen widgets.
- Accessibility-friendly touch targets, descriptions, semantic state labels, and dynamic text layouts.

## Architecture

The project keeps the existing Activity, Fragment, RecyclerView, repository, and data-source architecture:

```text
app/src/main/java/com/si13/app     Kotlin application code
app/src/main/res                   XML layouts, themes, drawables, widgets
app/src/test                       Unit tests
app/src/androidTest                Espresso and Room migration tests
app/schemas                        Exported Room schemas
```

Important components:

- `MainActivity` hosts navigation, the centered Add action, and extension deep links.
- `HomeFragment` owns list/calendar presentation, search, filters, sorting, sections, and swipe behavior.
- `TaskRepository` selects guest or authenticated storage and coordinates task mutations.
- `LocalTaskDataSource` stores guest tasks in Room.
- `RemoteTaskDataSource` stores authenticated tasks in Firestore.
- `TaskReminderScheduler` schedules and handles native reminder notifications.
- `ForgettyWidgets` supplies compact, Today-list, and progress widgets.
- `AuthRepository`, `GoogleAuthClient`, and `GoogleSignInHandler` preserve the real sign-in flow.

## Data model

Tasks support completion timestamps, high priority, due date and time, notes, reminders, recurrence, lists, tags, subtasks, attachment references, location-reminder metadata, and assignees.

Room uses an explicit version 4 to 5 migration; existing rows remain readable. Firestore parsing supplies safe defaults for fields missing from older documents. Guest tasks are not deleted until a remote batch import succeeds.

Attachments currently use Android system-picker URI references. Cross-user collaboration and remote attachment uploads require application-specific Firestore rules and Firebase Storage ownership policies before they can be enabled safely.

## Build and test

Use the checked-in Gradle wrapper:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Firebase configuration

Firebase is configured through `app/google-services.json`. Google sign-in requires a valid `default_web_client_id` generated from that Firebase project.

Firestore task documents remain scoped to the authenticated user. Do not enable cross-user shared lists without corresponding ownership, membership, invitation, rules, and index changes.
