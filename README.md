# SI13 Android

SI13 is a Kotlin Android todo app with guest mode, Google/Firebase sign-in, local task storage, authenticated task sync, task priorities, and Espresso coverage for the main flows.

## Features

- Home screen with a scrollable todo list.
- Add tasks with duplicate-submit protection.
- Complete tasks by tapping the task row.
- Toggle high priority from the right-side priority circle.
- Sort tasks dynamically: high priority first, then newest first.
- Scroll to the newly added or recently reprioritized task.
- Gear settings menu for showing completed tasks and deleting all tasks with confirmation.
- Guest mode backed by Room.
- Authenticated mode backed by Firestore.
- Google sign-in through Firebase Authentication and Credential Manager.
- Profile screen for guest/authenticated account state.

## Project Structure

```text
app/                                Android app module
app/src/main/java/com/si13/app      Kotlin source
app/src/main/res                    Layouts, drawables, strings, navigation
app/src/test                        Unit tests
app/src/androidTest                 Espresso tests
gradle/libs.versions.toml           Dependency versions
```

Key classes:

- `MainActivity` hosts bottom navigation and startup auth/import prompts.
- `HomeFragment` renders tasks, settings, completion, priority, and delete flows.
- `TaskRepository` selects local or remote storage and owns task validation/sorting.
- `LocalTaskDataSource` stores guest tasks in Room.
- `RemoteTaskDataSource` stores authenticated tasks in Firestore.
- `AuthRepository`, `GoogleAuthClient`, and `GoogleSignInHandler` handle sign-in state.

## Data Model

Tasks contain text, completion state, timestamps, and optional high priority.

Guest tasks are stored in `guest_tasks.db` with Room. Authenticated tasks are stored under the signed-in user's Firestore task collection. When a user signs in with existing guest tasks, the app can import or discard those local tasks.

## Build And Test

Debug build:

```bash
./gradlew assembleDebug
```

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Instrumented test APK:

```bash
./gradlew assembleDebugAndroidTest
```

Connected emulator/device tests:

```bash
./gradlew connectedDebugAndroidTest
```

Common local verification:

```bash
./gradlew testDebugUnitTest assembleDebugAndroidTest assembleDebug
```

## Dependencies

- AndroidX AppCompat, Core KTX, ConstraintLayout, Navigation
- Material Components
- Room
- Firebase Auth and Firestore
- Android Credential Manager and Google Identity
- Coil
- Espresso and Allure Android test reporting

## Firebase

Firebase is configured through:

```text
app/google-services.json
```

Google sign-in requires a valid `default_web_client_id` generated from the Firebase configuration.

<img width="1000" height="1052" alt="Screenshot_20260727_164858" src="https://github.com/user-attachments/assets/16d61908-e2ec-41c9-9766-99c1f5af0df3" />

